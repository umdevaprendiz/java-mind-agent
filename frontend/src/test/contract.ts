/**
 * Lê `api/openapi.yaml` e valida respostas contra os schemas do contrato (Ajv, JSON Schema 2020-12).
 * É o que impede os mocks de se afastarem do YAML: o contrato manda, não o código.
 */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import AjvModule from 'ajv/dist/2020.js'
import addFormatsModule from 'ajv-formats'
import { parse } from 'yaml'

type Json = Record<string, unknown>
// Ajv e ajv-formats são CommonJS; o import default pode vir embrulhado.
const Ajv = ((AjvModule as unknown as { default?: unknown }).default ??
  AjvModule) as typeof AjvModule
const addFormats = ((addFormatsModule as unknown as { default?: unknown }).default ??
  addFormatsModule) as typeof addFormatsModule

export interface Operation {
  operationId: string
  method: string
  /** Caminho do contrato, ex.: `/api/models/{modelId}/pull`. */
  path: string
  responses: Record<string, Json>
}

const CONTRACT_PATH = fileURLToPath(new URL('../../../api/openapi.yaml', import.meta.url))

export const contract = parse(readFileSync(CONTRACT_PATH, 'utf8')) as Json & {
  paths: Record<string, Record<string, Json>>
  components: { schemas: Record<string, Json> }
}

const METHODS = ['get', 'post', 'put', 'patch', 'delete']

export const operations: Operation[] = Object.entries(contract.paths).flatMap(([path, item]) =>
  METHODS.filter((m) => item[m]).map((method) => {
    const op = item[method] as Json
    return {
      operationId: op.operationId as string,
      method: method.toUpperCase(),
      path,
      responses: (op.responses ?? {}) as Record<string, Json>,
    }
  }),
)

// ---------------------------------------------------------------- validação
const ajv = new Ajv({ strict: false, allErrors: true })
addFormats(ajv)
for (const format of ['int32', 'int64', 'float', 'double']) ajv.addFormat(format, true)
ajv.addSchema({ $id: 'contract', components: contract.components })

/** Reescreve `$ref: '#/...'` para apontar ao documento `contract` registrado no Ajv. */
function rewriteRefs(node: unknown): unknown {
  if (Array.isArray(node)) return node.map(rewriteRefs)
  if (node && typeof node === 'object') {
    return Object.fromEntries(
      Object.entries(node).map(([key, value]) => [
        key,
        key === '$ref' && typeof value === 'string' && value.startsWith('#/')
          ? `contract${value}`
          : rewriteRefs(value),
      ]),
    )
  }
  return node
}

/** Resolve `$ref` de nível raiz de uma resposta (`#/components/responses/NotFound`). */
function deref(node: Json): Json {
  const ref = node.$ref
  if (typeof ref !== 'string') return node
  let current: unknown = contract
  for (const part of ref.replace(/^#\//, '').split('/')) current = (current as Json)[part]
  return current as Json
}

const cache = new Map<string, ReturnType<typeof ajv.compile>>()
function validatorFor(schema: Json, key: string) {
  let validate = cache.get(key)
  if (!validate) {
    validate = ajv.compile(rewriteRefs(schema) as Json)
    cache.set(key, validate)
  }
  return validate
}

/** Acha a operação do contrato para um método e uma URL concretos. */
export function findOperation(method: string, pathname: string): Operation | undefined {
  return operations.find((op) => {
    if (op.method !== method.toUpperCase()) return false
    const pattern = new RegExp('^' + op.path.replace(/\{[^}]+\}/g, '[^/]+') + '$')
    return pattern.test(pathname)
  })
}

export interface ContractIssue {
  operationId: string
  where: string
  problems: string[]
}

function issues(
  operationId: string,
  where: string,
  validate: ReturnType<typeof ajv.compile>,
  data: unknown,
) {
  if (validate(data)) return []
  return [
    {
      operationId,
      where,
      problems: (validate.errors ?? []).map((e) => `${e.instancePath || '/'} ${e.message}`),
    },
  ] satisfies ContractIssue[]
}

/** Valida o corpo de uma resposta JSON contra o schema declarado para aquele status. */
export function validateJsonResponse(
  op: Operation,
  status: number,
  contentType: string,
  body: unknown,
) {
  const response = op.responses[String(status)]
  if (!response) {
    return [
      {
        operationId: op.operationId,
        where: `status ${status}`,
        problems: ['status não declarado no contrato'],
      },
    ]
  }
  const content = (deref(response).content ?? {}) as Record<string, Json>
  const mediaType = contentType.split(';')[0].trim()
  const media = content[mediaType]
  if (!media) {
    return Object.keys(content).length === 0 && body === undefined
      ? []
      : [
          {
            operationId: op.operationId,
            where: `status ${status}`,
            problems: [`tipo de conteúdo não declarado: ${mediaType}`],
          },
        ]
  }
  if (!media.schema) return []
  return issues(
    op.operationId,
    `${status} ${mediaType}`,
    validatorFor(media.schema as Json, `${op.operationId}:${status}:${mediaType}`),
    body,
  )
}

/** Valida um evento SSE: `event:` igual ao `type` e o JSON dentro do schema da união do stream. */
export function validateSseEvent(op: Operation, name: string, data: unknown) {
  const media = (deref(op.responses['200']).content as Record<string, Json>)['text/event-stream']
  const list: ContractIssue[] = []
  const type = (data as { type?: string }).type
  if (name !== type) {
    list.push({
      operationId: op.operationId,
      where: 'event',
      problems: [`event "${name}" difere de type "${type}"`],
    })
  }
  list.push(
    ...issues(
      op.operationId,
      `sse ${name}`,
      validatorFor(media.schema as Json, `${op.operationId}:sse`),
      data,
    ),
  )
  return list
}
