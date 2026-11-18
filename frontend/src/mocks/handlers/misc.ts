import { http, HttpResponse } from 'msw'
import type { KnowledgeHit, RunCodeRequest, Settings, SourceRef } from '@/api/types'
import { TIERS } from '@/api/types'
import {
  JAVA_VERSIONS,
  SRC_GENERICS,
  SRC_RECORDS,
  SRC_RECORDS_DOC,
  SRC_STREAMS,
  SRC_STRINGS,
  SRC_VTHREADS,
} from '../fixtures'
import { state } from '../state'
import { nowIso, notFound, problem, sleep } from '../util'

const validation = (field: string, message: string) =>
  problem(400, 'VALIDATION_FAILED', 'Requisição inválida', { errors: [{ field, message }] })

const KNOWLEDGE: Array<{ source: SourceRef; content: string }> = [
  {
    source: SRC_RECORDS,
    content: 'Records são classes que atuam como portadoras transparentes de dados imutáveis.',
  },
  {
    source: SRC_RECORDS_DOC,
    content: 'Record é a classe base comum de todas as classes record da linguagem Java.',
  },
  {
    source: SRC_STREAMS,
    content: 'Uma sequência de elementos que suporta operações agregadas sequenciais e paralelas.',
  },
  {
    source: SRC_GENERICS,
    content: 'Os argumentos de tipo podem ser curingas: ?, ? extends T e ? super T.',
  },
  {
    source: SRC_VTHREADS,
    content: 'Threads virtuais são leves e aumentam a vazão de aplicações concorrentes.',
  },
  {
    source: SRC_STRINGS,
    content: 'O operador == compara referências; para comparar conteúdo, use equals.',
  },
]

function appliesTo(source: SourceRef, version: number | null): boolean {
  if (version === null) return true
  return (source.javaVersionMin ?? 0) <= version && (source.javaVersionMax ?? 99) >= version
}

/** Execução simulada: imprime os `System.out.println` literais e as somas simples. */
function fakeRun(request: RunCodeRequest) {
  const { code, javaVersion } = request
  if (/while\s*\(\s*true\s*\)/.test(code)) {
    return {
      status: 'TIMEOUT' as const,
      stdout: '',
      stderr: 'Tempo limite excedido (10 s)',
      exitCode: null,
      compileErrors: [],
      durationMs: 10000,
      javaVersion,
    }
  }
  if (/\bERRO\b|;;;|undefinedVariable/.test(code)) {
    return {
      status: 'COMPILE_ERROR' as const,
      stdout: '',
      stderr: 'Main.java:3: error: cannot find symbol',
      exitCode: 1,
      compileErrors: [
        { line: 3, column: 9, message: 'cannot find symbol: variable undefinedVariable' },
      ],
      durationMs: 920,
      javaVersion,
    }
  }
  const lines: string[] = []
  for (const match of code.matchAll(/System\.out\.println\(([^;]*)\);/g)) {
    const arg = match[1].trim()
    const text = /^"(.*)"$/.exec(arg)
    const sum = /^(\d+)\s*\+\s*(\d+)$/.exec(arg)
    lines.push(text ? text[1] : sum ? String(Number(sum[1]) + Number(sum[2])) : arg)
  }
  return {
    status: 'SUCCESS' as const,
    stdout: lines.length ? lines.join('\n') + '\n' : '',
    stderr: '',
    exitCode: 0,
    compileErrors: [],
    durationMs: 1840,
    javaVersion,
  }
}

export const miscHandlers = [
  // -------------------------------------------------------------- settings
  http.get('*/api/settings', () => HttpResponse.json(state.settings)),

  http.put('*/api/settings', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as Settings | null
    if (!body || !TIERS.includes(body.defaultTier))
      return validation('defaultTier', 'deve ser low, medium, strong ou extra')
    if (body.language !== 'pt-BR' && body.language !== 'en')
      return validation('language', 'deve ser pt-BR ou en')
    const version = body.defaultJavaVersion
    if (version != null && (!Number.isInteger(version) || version < 1 || version > 99)) {
      return validation('defaultJavaVersion', 'deve ser um inteiro de 1 a 99 ou null')
    }
    state.settings = {
      defaultTier: body.defaultTier,
      defaultJavaVersion: version ?? null,
      language: body.language,
    }
    return HttpResponse.json(state.settings)
  }),

  // ------------------------------------------------------------- knowledge
  http.get('*/api/knowledge/versions', () => HttpResponse.json(JAVA_VERSIONS)),

  http.get('*/api/knowledge/sources', () => HttpResponse.json(state.sources)),

  http.get('*/api/knowledge/sources/:sourceId', ({ params }) => {
    const source = state.sources.find((s) => s.id === String(params.sourceId))
    return source ? HttpResponse.json(source) : notFound('Fonte')
  }),

  http.post('*/api/knowledge/sources/:sourceId/ingest', ({ params }) => {
    const source = state.sources.find((s) => s.id === String(params.sourceId))
    if (!source) return notFound('Fonte')
    if (state.ollamaDown) {
      return problem(
        503,
        'OLLAMA_UNAVAILABLE',
        'A indexação precisa do modelo de embeddings, e o Ollama está fora do ar.',
        {
          retryable: true,
        },
      )
    }
    if (source.status === 'INGESTING')
      return problem(409, 'CONFLICT', 'Esta fonte já está sendo indexada.')
    source.status = 'INGESTING'
    source.errorMessage = null
    void sleep(2500).then(() => {
      source.status = 'READY'
      source.chunkCount = source.chunkCount || 1500
      source.lastIngestedAt = nowIso()
    })
    return HttpResponse.json({ ...source }, { status: 202 })
  }),

  http.get('*/api/knowledge/search', ({ request }) => {
    const url = new URL(request.url)
    const q = url.searchParams.get('q')?.trim()
    if (!q || q.length > 500) return validation('q', 'é obrigatório (1 a 500 caracteres)')
    const limit = Math.min(20, Math.max(1, Number(url.searchParams.get('limit') ?? 5) || 5))
    const versionParam = url.searchParams.get('javaVersion')
    const version = versionParam === null ? null : Number(versionParam)
    if (version !== null && (!Number.isInteger(version) || version < 1 || version > 99)) {
      return validation('javaVersion', 'deve ser um inteiro de 1 a 99')
    }
    if (state.ollamaDown) {
      return problem(
        503,
        'OLLAMA_UNAVAILABLE',
        'A busca precisa do modelo de embeddings, e o Ollama está fora do ar.',
        {
          retryable: true,
        },
      )
    }
    const words = q.toLowerCase().split(/\s+/)
    const hits: KnowledgeHit[] = KNOWLEDGE.filter((item) => appliesTo(item.source, version))
      .map((item) => {
        const haystack = `${item.source.title} ${item.content}`.toLowerCase()
        const matches = words.filter((w) => haystack.includes(w)).length
        return {
          content: item.content,
          source: item.source,
          score: Math.round((0.35 + (0.6 * matches) / words.length) * 100) / 100,
        }
      })
      .sort((a, b) => b.score - a.score)
      .slice(0, limit)
    return HttpResponse.json(hits)
  }),

  // --------------------------------------------------------------- sandbox
  http.post('*/api/sandbox/run', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as RunCodeRequest | null
    if (!body?.code || body.code.trim() === '') return validation('code', 'não pode estar vazio')
    if (!Number.isInteger(body.javaVersion) || body.javaVersion < 1 || body.javaVersion > 99) {
      return validation('javaVersion', 'é obrigatório (1 a 99)')
    }
    if (state.sandboxDown) {
      return problem(
        503,
        'SANDBOX_UNAVAILABLE',
        'O sandbox precisa do Docker, e ele não está em execução.',
        { retryable: true },
      )
    }
    await sleep(600)
    return HttpResponse.json(fakeRun(body))
  }),

  // ---------------------------------------------------------------- system
  http.get('*/api/system/status', () =>
    HttpResponse.json({
      status: state.ollamaDown ? 'DOWN' : state.sandboxDown ? 'DEGRADED' : 'UP',
      ollama: {
        reachable: !state.ollamaDown,
        version: state.ollamaDown ? null : '0.40.3',
        baseUrl: 'http://localhost:11434',
      },
      database: { up: true },
      sandbox: {
        available: !state.sandboxDown,
        reason: state.sandboxDown ? 'Docker não está em execução' : null,
      },
    }),
  ),
]
