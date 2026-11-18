// @vitest-environment node
import { describe, expect, it } from 'vitest'
import * as api from '@/api/endpoints'
import type { ApiError } from '@/api/client'
import type {
  ChatStreamEvent,
  JobStreamEvent,
  ModelPullStreamEvent,
  PdfStreamEvent,
} from '@/api/types'
import { mockControls, SEED_IDS } from '@/mocks/state'
import { mockConfig } from '@/mocks/util'

async function chat(body: Parameters<typeof api.sendChatMessage>[0]) {
  const events: ChatStreamEvent[] = []
  await api.sendChatMessage(body, { onEvent: (e) => events.push(e) })
  return events
}
const types = (events: { type: string }[]) => events.map((e) => e.type)
const textOf = (events: ChatStreamEvent[]) =>
  events
    .filter((e) => e.type === 'token')
    .map((e) => (e as { text: string }).text)
    .join('')

describe('catálogo de modelos (seletor de custo)', () => {
  it('traz Baixo/Médio/Forte/Extra + embedding, Forte sem instalar e Extra que não cabe', async () => {
    const catalog = await api.getModelCatalog()
    expect(catalog.tiers.map((t) => t.label)).toEqual(['Baixo', 'Médio', 'Forte', 'Extra'])
    expect(catalog.embedding.kind).toBe('embedding')
    expect(catalog.defaultTier).toBe('medium')
    expect(catalog.hardware).toMatchObject({ cpuOnly: true, totalRamGb: 15.8 })
    const [low, medium, strong, extra] = catalog.tiers
    expect(low).toMatchObject({ available: true, installed: true })
    expect(medium).toMatchObject({ available: true, recommended: true })
    expect(strong).toMatchObject({
      available: false,
      unavailableReason: 'NOT_INSTALLED',
      fits: true,
    })
    expect(extra).toMatchObject({
      available: false,
      unavailableReason: 'DOES_NOT_FIT',
      fits: false,
    })
  })

  it('com o Ollama fora do ar, todos viram HOST_UNREACHABLE mas o hardware continua', async () => {
    mockControls.setOllamaDown(true)
    const catalog = await api.getModelCatalog()
    expect(catalog.tiers.every((t) => t.unavailableReason === 'HOST_UNREACHABLE')).toBe(true)
    expect(catalog.hardware.totalRamGb).toBe(15.8)
    expect((await api.getSystemStatus()).ollama.reachable).toBe(false)
  })
})

describe('download de modelo (pull SSE)', () => {
  it('emite progress → done, o percentual recua na 2ª camada e o modelo passa a instalado', async () => {
    const events: ModelPullStreamEvent[] = []
    await api.pullModel('strong', { onEvent: (e) => events.push(e) })
    const progress = events.filter((e) => e.type === 'progress')
    expect(events.at(-1)).toEqual({ type: 'done' })
    expect(progress[0]).toMatchObject({ status: 'pulling manifest', percent: null })
    const percents = progress
      .map((e) => e.percent)
      .filter((p): p is number => typeof p === 'number')
    expect(Math.max(...percents)).toBe(100)
    expect(percents.some((p, i) => i > 0 && p < percents[i - 1])).toBe(true) // recuo documentado no contrato
    const strong = (await api.getModelCatalog()).tiers[2]
    expect(strong).toMatchObject({ installed: true, available: true, unavailableReason: null })
    expect(strong.sizeGb).toBeCloseTo(9, 0)
  })

  it('o Extra não é baixado (422) e uma falha no meio chega como evento error', async () => {
    await expect(api.pullModel('extra', { onEvent: () => {} })).rejects.toMatchObject({
      status: 422,
      code: 'MODEL_DOES_NOT_FIT',
    })
    mockControls.failNextPull()
    const events: ModelPullStreamEvent[] = []
    await api.pullModel('strong', { onEvent: (e) => events.push(e) })
    expect(events.at(-1)).toMatchObject({
      type: 'error',
      code: 'OLLAMA_UNAVAILABLE',
      retryable: true,
    })
    expect((await api.getModelCatalog()).tiers[2].installed).toBe(false)
  })

  it('cancelar (abort) interrompe o download sem instalar', async () => {
    mockConfig.speed = 0.2
    const controller = new AbortController()
    const events: ModelPullStreamEvent[] = []
    const result = await api.pullModel('strong', {
      signal: controller.signal,
      onEvent: (e) => {
        events.push(e)
        if (events.length === 3) controller.abort()
      },
    })
    expect(result.aborted).toBe(true)
    expect(events.length).toBeLessThan(10)
    expect((await api.getModelCatalog()).tiers[2].installed).toBe(false)
  })
})

describe('chat (SSE)', () => {
  it('segue a ordem do contrato: start → status → tool → source → thinking → token → usage → done', async () => {
    const events = await chat({ message: 'O que é um record?', tier: 'medium', language: 'pt-BR' })
    const order = types(events)
    expect(order[0]).toBe('start')
    expect(order.slice(-2)).toEqual(['usage', 'done'])
    for (const expected of ['thinking', 'tool_call', 'tool_result', 'source', 'token']) {
      expect(order).toContain(expected)
    }
    expect(order.indexOf('thinking')).toBeLessThan(order.indexOf('token'))
    expect(events[0]).toMatchObject({
      type: 'start',
      newConversation: true,
      tier: 'medium',
      model: 'qwen2.5:7b',
    })
    const phases = events
      .filter((e) => e.type === 'status')
      .map((e) => (e as { phase: string }).phase)
    expect(phases).toEqual([
      'loading_model',
      'retrieving',
      'executing_code',
      'verifying',
      'generating',
    ])
    expect(textOf(events)).toContain('```java')
  })

  it('o tier Baixo não "pensa"; a resposta em inglês vem em inglês', async () => {
    const events = await chat({ message: 'what are streams?', tier: 'low', language: 'en' })
    expect(types(events)).not.toContain('thinking')
    expect(textOf(events)).toContain('intermediate')
  })

  it('grava a conversa: título automático, duas mensagens e busca por texto', async () => {
    const events = await chat({ message: 'Explique generics e wildcards' })
    const { conversationId } = events[0] as { conversationId: string }
    const messages = await api.listMessages(conversationId)
    expect(messages.map((m) => m.role)).toEqual(['USER', 'ASSISTANT'])
    expect(messages[1].sources?.length).toBeGreaterThan(0)
    expect((await api.listConversations({ q: 'wildcards' })).items.map((c) => c.id)).toContain(
      conversationId,
    )
    expect((await api.getConversation(conversationId)).title).toBe('Explique generics e wildcards')
  })

  it('[erro] corta o stream com error retryable; [timeout] devolve GENERATION_TIMEOUT', async () => {
    const failed = await chat({ message: 'me explique records [erro]' })
    expect(failed.at(-1)).toMatchObject({
      type: 'error',
      code: 'OLLAMA_UNAVAILABLE',
      retryable: true,
    })
    expect(types(failed)).not.toContain('done')
    const timeout = await chat({ message: 'qualquer coisa [timeout]' })
    expect(timeout.at(-1)).toMatchObject({ type: 'error', code: 'GENERATION_TIMEOUT' })
  })

  it('falhas antes do stream voltam como problem+json com code estável', async () => {
    const attempt = (body: Parameters<typeof api.sendChatMessage>[0]) =>
      api.sendChatMessage(body, { onEvent: () => {} }).catch((e: ApiError) => e)
    expect(await attempt({ message: 'oi', tier: 'strong' })).toMatchObject({
      status: 422,
      code: 'MODEL_NOT_INSTALLED',
    })
    expect(await attempt({ message: 'oi', tier: 'extra' })).toMatchObject({
      status: 422,
      code: 'MODEL_DOES_NOT_FIT',
    })
    expect(await attempt({ message: '' })).toMatchObject({ status: 400, code: 'VALIDATION_FAILED' })
    expect(await attempt({ message: 'oi [ollama-off]' })).toMatchObject({
      status: 503,
      code: 'OLLAMA_UNAVAILABLE',
      retryable: true,
    })
    expect(
      await attempt({ message: 'oi', conversationId: '00000000-0000-4000-8000-000000000000' }),
    ).toMatchObject({ status: 404, code: 'NOT_FOUND' })
  })

  it('depois de baixar o Forte, o chat nesse tier passa a funcionar', async () => {
    await api.pullModel('strong', { onEvent: () => {} })
    const events = await chat({ message: 'o que é generics?', tier: 'strong' })
    expect(events[0]).toMatchObject({ type: 'start', tier: 'strong', model: 'qwen2.5:14b' })
    expect(events.at(-1)?.type).toBe('done')
  })
})

describe('fluxo de PDF e resolução em lote', () => {
  const pdfFile = (name = 'simulado-novo.pdf') =>
    new File(['%PDF-1.4 mock'], name, { type: 'application/pdf' })

  it('upload → progresso da extração → segmentação → 8 questões (2 para revisar)', async () => {
    const pdf = await api.uploadPdf(pdfFile())
    expect(pdf).toMatchObject({ status: 'UPLOADED', questionCount: 0 })
    const events: PdfStreamEvent[] = []
    await api.streamPdfEvents(pdf.id, { onEvent: (e) => events.push(e) })
    const statuses = events
      .filter((e) => e.type === 'pdf_status')
      .map((e) => (e as { status: string }).status)
    expect(statuses[0]).toBe('EXTRACTING')
    expect(statuses.at(-1)).toBe('SEGMENTED')
    expect(statuses).toContain('SEGMENTING')
    expect(events.at(-1)).toEqual({ type: 'done' })
    expect(await api.getPdf(pdf.id)).toMatchObject({
      status: 'SEGMENTED',
      questionCount: 8,
      needsReviewCount: 2,
    })
    const questions = await api.listQuestions(pdf.id)
    expect(questions.map((q) => q.ordinal)).toEqual([1, 2, 3, 4, 5, 6, 7, 8])
    expect(new Set(questions.map((q) => q.type))).toEqual(
      new Set(['MULTIPLE_CHOICE', 'TRUE_FALSE', 'CODE_OUTPUT', 'FILL_IN', 'ESSAY']),
    )
  })

  it('o mesmo arquivo enviado duas vezes devolve o PDF existente', async () => {
    const first = await api.uploadPdf(pdfFile('igual.pdf'))
    expect((await api.uploadPdf(pdfFile('igual.pdf'))).id).toBe(first.id)
  })

  it('PDF corrompido falha na extração com evento error', async () => {
    const pdf = await api.uploadPdf(pdfFile('prova-corrompida.pdf'))
    const events: PdfStreamEvent[] = []
    await api.streamPdfEvents(pdf.id, { onEvent: (e) => events.push(e) })
    expect(events.at(-1)).toMatchObject({ type: 'error' })
    expect((await api.getPdf(pdf.id)).status).toBe('FAILED')
  })

  it('revisão: editar limpa needsReview; criar e apagar renumeram', async () => {
    const pdfId = SEED_IDS.pdfReview
    const [q1, , q3] = await api.listQuestions(pdfId)
    expect(q3.needsReview).toBe(true)
    const edited = await api.updateQuestion(pdfId, q3.id, {
      statement: 'Qual interface recebe 1 argumento e devolve resultado?',
    })
    expect(edited.needsReview).toBe(false)
    expect((await api.getPdf(pdfId)).needsReviewCount).toBe(0)
    const created = await api.createQuestion(pdfId, {
      type: 'ESSAY',
      statement: 'Nova',
      afterQuestionId: q1.id,
    })
    expect(created.ordinal).toBe(2)
    await api.deleteQuestion(pdfId, created.id)
    expect((await api.listQuestions(pdfId)).map((q) => q.ordinal)).toEqual([1, 2, 3, 4, 5])
  })

  it('estimativa por tier, 409 antes da segmentação e 422 para tier indisponível', async () => {
    expect(await api.estimateJob(SEED_IDS.pdfSolved, 'medium')).toMatchObject({
      tier: 'medium',
      questions: 8,
      estimatedSeconds: 560,
      basis: 'MEASURED',
    })
    expect((await api.estimateJob(SEED_IDS.pdfSolved, 'strong')).basis).toBe('HEURISTIC')
    const fresh = await api.uploadPdf(pdfFile('pendente.pdf'))
    await expect(api.estimateJob(fresh.id, 'medium')).rejects.toMatchObject({
      status: 409,
      code: 'PDF_NOT_READY',
    })
    await expect(api.createJob(fresh.id, { tier: 'medium' })).rejects.toMatchObject({
      status: 409,
      code: 'PDF_NOT_READY',
    })
    await expect(api.createJob(SEED_IDS.pdfReview, { tier: 'strong' })).rejects.toMatchObject({
      status: 422,
      code: 'MODEL_NOT_INSTALLED',
    })
    await expect(api.createJob(SEED_IDS.pdfReview, { tier: 'extra' })).rejects.toMatchObject({
      status: 422,
      code: 'MODEL_DOES_NOT_FIT',
    })
  })

  it('o lote resolve questão a questão com eventos e deixa as respostas gravadas', async () => {
    const job = await api.createJob(SEED_IDS.pdfReview, { tier: 'medium' })
    expect(job).toMatchObject({ status: 'PENDING', totalQuestions: 5, completedQuestions: 0 })
    const events: JobStreamEvent[] = []
    await api.streamJobEvents(job.id, { onEvent: (e) => events.push(e) })
    expect(types(events)[0]).toBe('job_status')
    expect(types(events).at(-1)).toBe('done')
    expect(events.filter((e) => e.type === 'question_started')).toHaveLength(5)
    expect(events.filter((e) => e.type === 'question_answered')).toHaveLength(5)
    expect(await api.getJob(job.id)).toMatchObject({ status: 'COMPLETED', completedQuestions: 5 })
    const questions = await api.listQuestions(SEED_IDS.pdfReview)
    expect(questions.every((q) => q.latestAnswer?.status === 'COMPLETED')).toBe(true)
    expect(questions[0].latestAnswer).toMatchObject({
      chosenAlternative: 'c',
      verifiedByExecution: true,
      executionOutput: '12',
    })
  })

  it('no tier Baixo a questão dissertativa falha por tempo (question_failed) e o lote segue', async () => {
    const job = await api.createJob(SEED_IDS.pdfSolved, { tier: 'low' })
    const events: JobStreamEvent[] = []
    await api.streamJobEvents(job.id, { onEvent: (e) => events.push(e) })
    const failed = events.filter((e) => e.type === 'question_failed')
    expect(failed).toHaveLength(1)
    expect(failed[0]).toMatchObject({ code: 'GENERATION_TIMEOUT' })
    expect((await api.getJob(job.id)).completedQuestions).toBe(8)
  })

  it('pausar, retomar e cancelar o lote', async () => {
    mockConfig.speed = 0.05
    const job = await api.createJob(SEED_IDS.pdfSolved, { tier: 'medium' })
    await new Promise((r) => setTimeout(r, 40))
    expect((await api.pauseJob(job.id)).status).toBe('PAUSED')
    await expect(api.pauseJob(job.id)).rejects.toMatchObject({
      status: 409,
      code: 'JOB_NOT_ACTIVE',
    })
    await new Promise((r) => setTimeout(r, 120))
    const paused = await api.getJob(job.id)
    expect(paused.status).toBe('PAUSED')
    expect(paused.completedQuestions).toBeLessThan(8)
    expect((await api.resumeJob(job.id)).status).toBe('RUNNING')
    expect((await api.cancelJob(job.id)).status).toBe('CANCELLED')
    await expect(api.cancelJob(job.id)).rejects.toMatchObject({
      status: 409,
      code: 'JOB_NOT_ACTIVE',
    })
  })

  it('exporta o gabarito em Markdown', async () => {
    const text = await (await api.exportPdfResults(SEED_IDS.pdfSolved, 'md')).text()
    expect(text).toContain('# Gabarito comentado')
    expect(text).toContain('Verificada por execução')
  })
})
