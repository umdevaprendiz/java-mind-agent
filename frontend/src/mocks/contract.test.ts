// @vitest-environment node
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import * as api from '@/api/endpoints'
import { ApiError } from '@/api/client'
import { readSse } from '@/api/sse'
import type { ChatStreamEvent } from '@/api/types'
import { handlers } from '@/mocks/handlers'
import { server } from '@/mocks/node'
import { SEED_IDS } from '@/mocks/state'
import {
  type ContractIssue,
  findOperation,
  operations,
  validateJsonResponse,
  validateSseEvent,
} from '@/test/contract'

const issues: ContractIssue[] = []
const exercised = new Set<string>()
const pending: Promise<void>[] = []

beforeEach(() => {
  issues.length = 0
  exercised.clear()
  pending.length = 0
  server.events.on('response:mocked', ({ request, response }) => {
    const op = findOperation(request.method, new URL(request.url).pathname)
    if (!op) {
      issues.push({
        operationId: '?',
        where: request.url,
        problems: ['rota sem operação no contrato'],
      })
      return
    }
    exercised.add(op.operationId)
    exercised.add(`${op.operationId}:${response.status}`)
    const clone = response.clone()
    const type = clone.headers.get('content-type') ?? ''
    pending.push(
      (async () => {
        if (type.startsWith('text/event-stream')) {
          for await (const message of readSse(clone.body!)) {
            issues.push(...validateSseEvent(op, message.event, JSON.parse(message.data)))
          }
        } else if (type.includes('json')) {
          issues.push(...validateJsonResponse(op, response.status, type, await clone.json()))
        } else if (type === '') {
          issues.push(...validateJsonResponse(op, response.status, '', undefined))
        }
      })(),
    )
  })
})

afterEach(() => server.events.removeAllListeners())

const tolerate = async <T>(call: Promise<T>): Promise<T | ApiError> => {
  try {
    return await call
  } catch (error) {
    if (error instanceof ApiError) return error
    throw error
  }
}

describe('contrato api/openapi.yaml × mocks MSW', () => {
  it('tem um handler para cada operação do contrato (e nenhum a mais)', () => {
    const fromHandlers = handlers
      .map((h) => {
        const info = (h as unknown as { info: { method: string; path: string } }).info
        return `${info.method.toUpperCase()} ${info.path.replace('*', '').replace(/:(\w+)/g, '{$1}')}`
      })
      .sort()
    const fromContract = operations.map((op) => `${op.method} ${op.path}`).sort()
    expect(fromHandlers).toEqual(fromContract)
    expect(operations).toHaveLength(42)
  })

  it('o validador não é vazio: acusa uma resposta fora do schema', async () => {
    server.use(http.get('*/api/models', () => HttpResponse.json({ hardware: {}, tiers: 'nope' })))
    await api.getModelCatalog()
    await Promise.all(pending)
    expect(issues.length).toBeGreaterThan(0)
    expect(issues[0].operationId).toBe('getModelCatalog')
  })

  it('percorre as 42 operações e toda resposta (JSON ou evento SSE) valida contra o schema', async () => {
    // --- sistema, configurações, conhecimento, sandbox
    await api.getSystemStatus()
    await api.getSettings()
    await api.updateSettings({ defaultTier: 'medium', defaultJavaVersion: 17, language: 'pt-BR' })
    await tolerate(
      api.updateSettings({
        defaultTier: 'nope' as never,
        defaultJavaVersion: null,
        language: 'pt-BR',
      }),
    )
    await api.listJavaVersions()
    const sources = await api.listCorpusSources()
    await api.getCorpusSource(sources[0].id)
    await tolerate(api.getCorpusSource('00000000-0000-4000-8000-000000000000'))
    await api.ingestCorpusSource(sources[3].id)
    await api.searchKnowledge({ q: 'records', javaVersion: 21 })
    await tolerate(api.searchKnowledge({ q: '' }))
    await api.runJavaCode({ code: 'System.out.println(1 + 1);', javaVersion: 21 })
    await api.runJavaCode({ code: 'ERRO', javaVersion: 21 })

    // --- modelos
    const catalog = await api.getModelCatalog()
    expect(catalog.tiers.map((t) => t.id)).toEqual(['low', 'medium', 'strong', 'extra'])
    expect(await tolerate(api.warmUpModel('medium'))).toBeUndefined()
    await api.unloadModel('medium')
    await tolerate(api.warmUpModel('strong')) // 422 não instalado
    await tolerate(api.warmUpModel('extra')) // 422 não cabe
    await tolerate(api.pullModel('extra', { onEvent: () => {} })) // 422 não cabe
    await tolerate(api.deleteModel('strong')) // 404 não instalado
    await api.pullModel('strong', { onEvent: () => {} })
    await api.deleteModel('strong')

    // --- conversas
    await api.listConversations({ q: 'record' })
    const created = await api.createConversation({ title: 'Teste' })
    await api.getConversation(created.id)
    await tolerate(api.getConversation('00000000-0000-4000-8000-000000000000'))
    await api.updateConversation(created.id, { title: 'Renomeada', tier: null })
    const events: ChatStreamEvent[] = []
    await api.sendChatMessage(
      { conversationId: created.id, message: 'O que é um record?', tier: 'medium' },
      { onEvent: (e) => events.push(e) },
    )
    expect(events.at(-1)?.type).toBe('done')
    await tolerate(api.sendChatMessage({ message: '  ' }, { onEvent: () => {} })) // 400
    await tolerate(api.sendChatMessage({ message: 'oi', tier: 'strong' }, { onEvent: () => {} })) // 422
    await tolerate(api.sendChatMessage({ message: 'oi [ollama-off]' }, { onEvent: () => {} })) // 503
    await tolerate(
      api.sendChatMessage(
        { conversationId: '00000000-0000-4000-8000-000000000000', message: 'oi' },
        { onEvent: () => {} },
      ),
    ) // 404
    const messages = await api.listMessages(created.id)
    await api.truncateMessages(created.id, messages[messages.length - 1].id)
    const attachment = await api.uploadAttachment(created.id, new File(['class A {}'], 'A.java'))
    await tolerate(api.uploadAttachment(created.id, new File(['x'], 'foto.png'))) // 415
    await api.deleteAttachment(created.id, attachment.id)
    await api.deleteConversation(created.id)

    // --- PDFs, questões, lotes
    await api.listPdfs()
    const pdf = await api.uploadPdf(
      new File(['%PDF-1.4'], 'prova-nova.pdf', { type: 'application/pdf' }),
    )
    await tolerate(api.uploadPdf(new File(['x'], 'nota.txt'))) // 415
    await api.getPdf(pdf.id)
    await api.streamPdfEvents(pdf.id, { onEvent: () => {} })
    const questions = await api.listQuestions(pdf.id)
    expect(questions).toHaveLength(8)
    const extra = await api.createQuestion(pdf.id, {
      type: 'ESSAY',
      statement: 'Explique o GC.',
      afterQuestionId: questions[0].id,
    })
    await tolerate(api.createQuestion(pdf.id, { type: 'ESSAY', statement: '' })) // 400
    await api.updateQuestion(pdf.id, extra.id, { statement: 'Explique o garbage collector.' })
    await api.deleteQuestion(pdf.id, extra.id)
    await api.estimateJob(pdf.id, 'medium')
    const job = await api.createJob(pdf.id, {
      tier: 'medium',
      questionIds: [questions[0].id, questions[3].id],
    })
    await tolerate(api.createJob(pdf.id, { tier: 'strong' })) // 422
    await api.streamJobEvents(job.id, { onEvent: () => {} })
    await api.getJob(job.id)
    await api.listJobs(pdf.id)
    await tolerate(api.pauseJob(job.id)) // 409: já terminou
    await tolerate(api.resumeJob(job.id)) // 409
    await tolerate(api.cancelJob(job.id)) // 409
    for (const format of ['md', 'pdf', 'docx'] as const) await api.exportPdfResults(pdf.id, format)
    await tolerate(api.exportPdfResults(SEED_IDS.pdfReview, 'md')) // 409: nada resolvido
    await api.deletePdf(pdf.id)

    await Promise.all(pending)
    expect(issues).toEqual([])
    const missing = operations
      .filter((op) => !exercised.has(op.operationId))
      .map((op) => op.operationId)
    expect(missing).toEqual([])
  })
})
