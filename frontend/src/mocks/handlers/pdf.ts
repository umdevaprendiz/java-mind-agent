import { http, HttpResponse } from 'msw'
import type {
  Answer,
  CreateJobRequest,
  CreateQuestionRequest,
  Job,
  PdfDocument,
  Question,
  Tier,
  UpdateQuestionRequest,
} from '@/api/types'
import { TIERS } from '@/api/types'
import { QUESTION_SEEDS, SECONDS_PER_QUESTION } from '../fixtures'
import { type JobRun, questionFromSeed, seedAnswer, state } from '../state'
import { nowIso, notFound, problem, sleep, sseResponse, uuid } from '../util'

const MAX_PDF_BYTES = 50 * 1024 * 1024
const FINISHED = ['COMPLETED', 'FAILED', 'CANCELLED']
/** Duração simulada de cada questão no lote, por tier (ms, antes do multiplicador de velocidade). */
const QUESTION_MS: Record<Tier, number> = { low: 700, medium: 1100, strong: 1700, extra: 2600 }

const isTier = (value: unknown): value is Tier => TIERS.includes(value as Tier)

function refreshCounts(pdf: PdfDocument) {
  const list = state.questions.get(pdf.id) ?? []
  pdf.questionCount = list.length
  pdf.needsReviewCount = list.filter((q) => q.needsReview).length
  pdf.updatedAt = nowIso()
}

function renumber(list: Question[]) {
  list.forEach((q, i) => {
    q.ordinal = i + 1
  })
}

const validation = (field: string, message: string) =>
  problem(400, 'VALIDATION_FAILED', 'Requisição inválida', { errors: [{ field, message }] })

// ----------------------------------------------------------------- lote (job)
function emit(run: JobRun, event: { type: string } & Record<string, unknown>) {
  run.listeners.forEach((listener) => listener(event))
}

function answerFor(question: Question, tier: Tier, jobId: string): Answer {
  const seed = QUESTION_SEEDS.find(
    (s) => s.label === question.label && s.statement === question.statement,
  )
  if (!seed) {
    return {
      ...seedAnswer(QUESTION_SEEDS[0], question.id, tier, jobId),
      chosenAlternative: question.alternatives?.[0]?.label ?? null,
      explanation: 'Resposta simulada para uma questão criada ou editada à mão.',
      steps: ['Questão não faz parte do simulado de exemplo'],
      sources: [],
      verifiedByExecution: false,
      executionOutput: null,
      confidence: 0.5,
    }
  }
  // O Baixo não dá conta de questões dissertativas: falha por tempo, para a UI mostrar o erro por questão.
  if (seed.type === 'ESSAY' && tier === 'low') {
    return {
      ...seedAnswer(seed, question.id, tier, jobId),
      status: 'FAILED',
      chosenAlternative: null,
      explanation: null,
      steps: null,
      sources: [],
      confidence: null,
      verifiedByExecution: false,
      executionOutput: null,
      errorMessage: 'Tempo esgotado ao resolver a questão',
    }
  }
  const answer = seedAnswer(seed, question.id, tier, jobId)
  // Tiers menores têm menos certeza.
  answer.confidence = Math.max(
    0.4,
    (seed.answer.confidence ?? 0.8) - (tier === 'low' ? 0.15 : tier === 'medium' ? 0.02 : 0),
  )
  return answer
}

async function runJob(job: Job, run: JobRun) {
  await sleep(300)
  // Dá um instante para o front abrir o stream de eventos antes da primeira questão (sem travar se ninguém vier).
  await Promise.race([
    new Promise<void>((resolve) => {
      run.onListener = resolve
    }),
    new Promise<void>((resolve) => setTimeout(resolve, 250)),
  ])
  if (run.cancelled) return
  job.status = 'RUNNING'
  job.startedAt = nowIso()
  emit(run, { type: 'job_status', job: { ...job } })

  const questions = state.questions.get(job.pdfDocumentId) ?? []
  for (const questionId of run.questionIds) {
    while ((job.status as string) === 'PAUSED' && !run.cancelled) {
      await new Promise<void>((resolve) => {
        run.wake = resolve
      })
    }
    if (run.cancelled) return
    const question = questions.find((q) => q.id === questionId)
    if (!question) continue

    emit(run, { type: 'question_started', questionId, ordinal: question.ordinal })
    await sleep(QUESTION_MS[job.tier])
    if (run.cancelled) return

    const answer = answerFor(question, job.tier, job.id)
    question.latestAnswer = answer
    question.updatedAt = nowIso()
    job.completedQuestions++
    if (answer.status === 'FAILED') {
      emit(run, {
        type: 'question_failed',
        questionId,
        code: 'GENERATION_TIMEOUT',
        message: answer.errorMessage,
      })
    } else {
      emit(run, { type: 'question_answered', questionId, answer })
    }
    emit(run, { type: 'job_status', job: { ...job } })
  }

  job.status = 'COMPLETED'
  job.finishedAt = nowIso()
  emit(run, { type: 'job_status', job: { ...job } })
  emit(run, { type: 'done' })
}

// -------------------------------------------------------------------- export
function markdownReport(pdf: PdfDocument, questions: Question[]): string {
  const lines = [`# Gabarito comentado — ${pdf.filename}`, '']
  for (const q of questions) {
    lines.push(`## Questão ${q.label ?? q.ordinal}`, '', q.statement, '')
    if (q.code) lines.push('```java', q.code, '```', '')
    const a = q.latestAnswer
    if (!a) {
      lines.push('_Sem resposta ainda._', '')
      continue
    }
    if (a.status === 'FAILED') {
      lines.push(`**Falhou:** ${a.errorMessage ?? 'erro desconhecido'}`, '')
      continue
    }
    if (a.chosenAlternative) lines.push(`**Resposta:** ${a.chosenAlternative}`, '')
    if (a.explanation) lines.push(a.explanation, '')
    if (a.verifiedByExecution)
      lines.push(`_Verificada por execução._ Saída: \`${a.executionOutput ?? ''}\``, '')
  }
  return lines.join('\n')
}

const MINIMAL_PDF = `%PDF-1.4
1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj
2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj
3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 300 100]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj
4 0 obj<</Length 52>>stream
BT /F1 14 Tf 20 50 Td (Gabarito simulado - mock) Tj ET
endstream
endobj
5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj
trailer<</Root 1 0 R/Size 6>>
%%EOF`

export const pdfHandlers = [
  // ----------------------------------------------------------------- PDFs
  http.get('*/api/pdfs', ({ request }) => {
    const url = new URL(request.url)
    const page = Math.max(0, Number(url.searchParams.get('page') ?? 0) || 0)
    const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? 20) || 20))
    const items = [...state.pdfs.values()].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return HttpResponse.json({
      items: items.slice(page * size, (page + 1) * size),
      page,
      size,
      totalItems: items.length,
      totalPages: Math.ceil(items.length / size),
    })
  }),

  http.post('*/api/pdfs', async ({ request }) => {
    const form = await request.formData().catch(() => null)
    const file = form?.get('file')
    if (!(file instanceof Blob)) return validation('file', 'é obrigatório')
    const filename = file instanceof File ? file.name : 'documento.pdf'
    if (!filename.toLowerCase().endsWith('.pdf') && file.type !== 'application/pdf') {
      return problem(415, 'UNSUPPORTED_MEDIA_TYPE', 'Envie um arquivo PDF.')
    }
    if (file.size > MAX_PDF_BYTES)
      return problem(413, 'PAYLOAD_TOO_LARGE', 'O PDF passa do limite de 50 MB.')

    // Mesmo conteúdo (aqui: mesmo nome e tamanho) devolve o PDF existente com 200.
    const existing = [...state.pdfs.values()].find(
      (p) => p.filename === filename && p.sizeBytes === file.size,
    )
    if (existing) return HttpResponse.json(existing, { status: 200 })

    const at = nowIso()
    const pdf: PdfDocument = {
      id: uuid(),
      filename,
      sizeBytes: file.size,
      pageCount: null,
      status: 'UPLOADED',
      errorMessage: null,
      questionCount: 0,
      needsReviewCount: 0,
      createdAt: at,
      updatedAt: at,
    }
    state.pdfs.set(pdf.id, pdf)
    return HttpResponse.json(pdf, { status: 201 })
  }),

  http.get('*/api/pdfs/:pdfId', ({ params }) => {
    const pdf = state.pdfs.get(String(params.pdfId))
    return pdf ? HttpResponse.json(pdf) : notFound('PDF')
  }),

  http.delete('*/api/pdfs/:pdfId', ({ params }) => {
    const id = String(params.pdfId)
    if (!state.pdfs.delete(id)) return notFound('PDF')
    state.questions.delete(id)
    for (const job of [...state.jobs.values()]) {
      if (job.pdfDocumentId !== id) continue
      const run = state.jobRuns.get(job.id)
      if (run) {
        run.cancelled = true
        run.wake?.()
      }
      state.jobs.delete(job.id)
    }
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/pdfs/:pdfId/events', ({ request, params }) => {
    const pdf = state.pdfs.get(String(params.pdfId))
    if (!pdf) return notFound('PDF')
    return sseResponse(request, async (out) => {
      out.comment('keep-alive')
      if (pdf.status === 'SEGMENTED' || pdf.status === 'FAILED') {
        out.send({
          type: 'pdf_status',
          status: pdf.status,
          pagesDone: pdf.pageCount,
          pagesTotal: pdf.pageCount,
          message: pdf.errorMessage,
        })
        out.send(
          pdf.status === 'FAILED'
            ? {
                type: 'error',
                code: 'INTERNAL_ERROR',
                message: pdf.errorMessage ?? 'Falha ao processar o PDF',
                retryable: false,
              }
            : { type: 'done' },
        )
        return
      }

      const total = pdf.pageCount ?? 8
      pdf.pageCount = total
      const corrupted = /corromp|corrupt/i.test(pdf.filename)
      pdf.status = 'EXTRACTING'
      for (let page = 0; page < total && !out.closed; page++) {
        out.send({
          type: 'pdf_status',
          status: 'EXTRACTING',
          pagesDone: page,
          pagesTotal: total,
          message: `OCR na página ${page + 1}`,
        })
        await out.sleep(260)
        if (corrupted && page === 2) {
          pdf.status = 'FAILED'
          pdf.errorMessage = 'Não foi possível ler o PDF: o arquivo parece corrompido.'
          out.send({
            type: 'pdf_status',
            status: 'FAILED',
            pagesDone: page,
            pagesTotal: total,
            message: pdf.errorMessage,
          })
          out.send({
            type: 'error',
            code: 'INTERNAL_ERROR',
            message: pdf.errorMessage,
            retryable: false,
          })
          return
        }
      }
      if (out.closed) return
      pdf.status = 'EXTRACTED'
      out.send({
        type: 'pdf_status',
        status: 'EXTRACTED',
        pagesDone: total,
        pagesTotal: total,
        message: 'Texto extraído',
      })
      await out.sleep(300)
      pdf.status = 'SEGMENTING'
      out.send({
        type: 'pdf_status',
        status: 'SEGMENTING',
        pagesDone: total,
        pagesTotal: total,
        message: 'Separando as questões',
      })
      await out.sleep(900)
      if (out.closed) return

      const questions = QUESTION_SEEDS.map((seed, i) => questionFromSeed(seed, pdf.id, i + 1))
      state.questions.set(pdf.id, questions)
      pdf.status = 'SEGMENTED'
      refreshCounts(pdf)
      out.send({
        type: 'pdf_status',
        status: 'SEGMENTED',
        pagesDone: total,
        pagesTotal: total,
        message: `${questions.length} questões encontradas`,
      })
      out.send({ type: 'done' })
    })
  }),

  // ------------------------------------------------------------ questões
  http.get('*/api/pdfs/:pdfId/questions', ({ params }) => {
    const id = String(params.pdfId)
    if (!state.pdfs.has(id)) return notFound('PDF')
    return HttpResponse.json(state.questions.get(id) ?? [])
  }),

  http.post('*/api/pdfs/:pdfId/questions', async ({ request, params }) => {
    const id = String(params.pdfId)
    const pdf = state.pdfs.get(id)
    if (!pdf) return notFound('PDF')
    const body = (await request.json().catch(() => null)) as CreateQuestionRequest | null
    if (!body?.statement || body.statement.trim() === '')
      return validation('statement', 'não pode estar vazio')
    if (!body.type) return validation('type', 'é obrigatório')
    const list = state.questions.get(id) ?? []
    state.questions.set(id, list)
    const at = nowIso()
    const question: Question = {
      id: uuid(),
      pdfDocumentId: id,
      ordinal: list.length + 1,
      label: body.label ?? null,
      type: body.type,
      statement: body.statement,
      code: body.code ?? null,
      alternatives: body.alternatives ?? null,
      javaVersion: body.javaVersion ?? null,
      pageNumber: null,
      needsReview: false,
      latestAnswer: null,
      createdAt: at,
      updatedAt: at,
    }
    const afterIndex = body.afterQuestionId
      ? list.findIndex((q) => q.id === body.afterQuestionId)
      : -1
    if (body.afterQuestionId && afterIndex === -1) return notFound('Questão')
    list.splice(afterIndex === -1 ? list.length : afterIndex + 1, 0, question)
    renumber(list)
    refreshCounts(pdf)
    return HttpResponse.json(question, { status: 201 })
  }),

  http.patch('*/api/pdfs/:pdfId/questions/:questionId', async ({ request, params }) => {
    const pdf = state.pdfs.get(String(params.pdfId))
    const question = state.questions
      .get(String(params.pdfId))
      ?.find((q) => q.id === String(params.questionId))
    if (!pdf) return notFound('PDF')
    if (!question) return notFound('Questão')
    const patch = (await request.json().catch(() => null)) as UpdateQuestionRequest | null
    if (!patch || typeof patch !== 'object') return validation('body', 'corpo inválido')
    if ('statement' in patch && (!patch.statement || patch.statement.trim() === '')) {
      return validation('statement', 'não pode estar vazio')
    }
    for (const key of [
      'label',
      'type',
      'statement',
      'code',
      'alternatives',
      'javaVersion',
    ] as const) {
      if (key in patch) Object.assign(question, { [key]: patch[key] })
    }
    question.needsReview = false // editar uma questão limpa a marca de revisão
    question.updatedAt = nowIso()
    refreshCounts(pdf)
    return HttpResponse.json(question)
  }),

  http.delete('*/api/pdfs/:pdfId/questions/:questionId', ({ params }) => {
    const id = String(params.pdfId)
    const pdf = state.pdfs.get(id)
    const list = state.questions.get(id)
    if (!pdf) return notFound('PDF')
    const index = list?.findIndex((q) => q.id === String(params.questionId)) ?? -1
    if (!list || index === -1) return notFound('Questão')
    list.splice(index, 1)
    renumber(list)
    refreshCounts(pdf)
    return new HttpResponse(null, { status: 204 })
  }),

  // ------------------------------------------------------------ estimativa
  http.get('*/api/pdfs/:pdfId/estimate', ({ request, params }) => {
    const pdf = state.pdfs.get(String(params.pdfId))
    if (!pdf) return notFound('PDF')
    const tier = new URL(request.url).searchParams.get('tier')
    if (!isTier(tier)) return validation('tier', 'deve ser low, medium, strong ou extra')
    if (pdf.status !== 'SEGMENTED')
      return problem(409, 'PDF_NOT_READY', 'O PDF ainda não foi separado em questões.')
    const questions = state.questions.get(pdf.id)?.length ?? 0
    const measured = state.models[tier].measuredTokensPerSecond != null
    return HttpResponse.json({
      tier,
      questions,
      estimatedSeconds: questions * SECONDS_PER_QUESTION[tier],
      basis: measured ? 'MEASURED' : 'HEURISTIC',
    })
  }),

  // ------------------------------------------------------------------ lotes
  http.get('*/api/pdfs/:pdfId/jobs', ({ params }) => {
    const id = String(params.pdfId)
    if (!state.pdfs.has(id)) return notFound('PDF')
    return HttpResponse.json(
      [...state.jobs.values()]
        .filter((j) => j.pdfDocumentId === id)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    )
  }),

  http.post('*/api/pdfs/:pdfId/jobs', async ({ request, params }) => {
    const id = String(params.pdfId)
    const pdf = state.pdfs.get(id)
    if (!pdf) return notFound('PDF')
    const body = (await request.json().catch(() => null)) as CreateJobRequest | null
    if (!body || !isTier(body.tier))
      return validation('tier', 'deve ser low, medium, strong ou extra')
    if (pdf.status !== 'SEGMENTED')
      return problem(409, 'PDF_NOT_READY', 'O PDF ainda não foi separado em questões.')
    const model = state.models[body.tier]
    if (!model.fits) {
      return problem(422, 'MODEL_DOES_NOT_FIT', `O tier ${model.label} não cabe nesta máquina.`)
    }
    if (!model.installed) {
      return problem(
        422,
        'MODEL_NOT_INSTALLED',
        `O modelo do tier ${model.label} não está instalado.`,
      )
    }
    const questions = state.questions.get(id) ?? []
    const questionIds = body.questionIds ?? questions.map((q) => q.id)
    const unknown = questionIds.find((qid) => !questions.some((q) => q.id === qid))
    if (unknown) return validation('questionIds', `questão ${unknown} não pertence ao PDF`)
    const busy = [...state.jobs.values()].some(
      (j) => j.pdfDocumentId === id && !FINISHED.includes(j.status),
    )
    if (busy) return problem(409, 'CONFLICT', 'Já existe um lote em andamento para este PDF.')

    const job: Job = {
      id: uuid(),
      pdfDocumentId: id,
      status: 'PENDING',
      tier: body.tier,
      javaVersion: body.javaVersion ?? null,
      totalQuestions: questionIds.length,
      completedQuestions: 0,
      errorMessage: null,
      createdAt: nowIso(),
      startedAt: null,
      finishedAt: null,
    }
    state.jobs.set(job.id, job)
    const run: JobRun = {
      listeners: new Set(),
      wake: null,
      onListener: null,
      cancelled: false,
      questionIds,
    }
    state.jobRuns.set(job.id, run)
    void runJob(job, run)
    return HttpResponse.json({ ...job }, { status: 202 })
  }),

  http.get('*/api/pdfs/:pdfId/export', ({ request, params }) => {
    const pdf = state.pdfs.get(String(params.pdfId))
    if (!pdf) return notFound('PDF')
    const format = new URL(request.url).searchParams.get('format')
    if (format !== 'md' && format !== 'pdf' && format !== 'docx')
      return validation('format', 'deve ser md, pdf ou docx')
    const questions = state.questions.get(pdf.id) ?? []
    if (!questions.some((q) => q.latestAnswer)) {
      return problem(409, 'CONFLICT', 'Nenhuma questão deste PDF foi resolvida ainda.')
    }
    const base = pdf.filename.replace(/\.pdf$/i, '')
    const disposition = `attachment; filename="gabarito-${base}.${format}"`
    if (format === 'md') {
      return new HttpResponse(markdownReport(pdf, questions), {
        headers: {
          'Content-Type': 'text/markdown; charset=utf-8',
          'Content-Disposition': disposition,
        },
      })
    }
    if (format === 'pdf') {
      return new HttpResponse(MINIMAL_PDF, {
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': disposition },
      })
    }
    return new HttpResponse(new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0x6d, 0x6f, 0x63, 0x6b]), {
      headers: {
        'Content-Type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'Content-Disposition': disposition,
      },
    })
  }),

  // ------------------------------------------------------------------- jobs
  http.get('*/api/jobs/:jobId', ({ params }) => {
    const job = state.jobs.get(String(params.jobId))
    return job ? HttpResponse.json(job) : notFound('Lote')
  }),

  http.get('*/api/jobs/:jobId/events', ({ request, params }) => {
    const job = state.jobs.get(String(params.jobId))
    if (!job) return notFound('Lote')
    return sseResponse(request, async (out) => {
      out.comment('keep-alive')
      out.send({ type: 'job_status', job: { ...job } })
      const run = state.jobRuns.get(job.id)
      if (!run || FINISHED.includes(job.status)) {
        out.send({ type: 'done' })
        return
      }
      const queue: { type: string }[] = []
      let notify: (() => void) | null = null
      const listener = (event: { type: string }) => {
        queue.push(event)
        notify?.()
      }
      run.listeners.add(listener)
      run.onListener?.()
      try {
        let finished = false
        while (!out.closed && !finished) {
          while (queue.length > 0) {
            const event = queue.shift()!
            out.send(event)
            if (event.type === 'done' || event.type === 'error') finished = true
          }
          if (!finished) {
            await Promise.race([new Promise<void>((resolve) => (notify = resolve)), out.sleep(400)])
          }
        }
      } finally {
        run.listeners.delete(listener) // fechar a conexão não cancela o lote
      }
    })
  }),

  http.post('*/api/jobs/:jobId/pause', ({ params }) => {
    const job = state.jobs.get(String(params.jobId))
    const run = state.jobRuns.get(String(params.jobId))
    if (!job || !run) return notFound('Lote')
    if (job.status !== 'RUNNING' && job.status !== 'PENDING')
      return problem(409, 'JOB_NOT_ACTIVE', 'O lote não está em andamento.')
    job.status = 'PAUSED'
    emit(run, { type: 'job_status', job: { ...job } })
    return HttpResponse.json({ ...job })
  }),

  http.post('*/api/jobs/:jobId/resume', ({ params }) => {
    const job = state.jobs.get(String(params.jobId))
    const run = state.jobRuns.get(String(params.jobId))
    if (!job || !run) return notFound('Lote')
    if (job.status !== 'PAUSED') return problem(409, 'JOB_NOT_ACTIVE', 'O lote não está pausado.')
    job.status = 'RUNNING'
    emit(run, { type: 'job_status', job: { ...job } })
    run.wake?.()
    return HttpResponse.json({ ...job })
  }),

  http.post('*/api/jobs/:jobId/cancel', ({ params }) => {
    const job = state.jobs.get(String(params.jobId))
    const run = state.jobRuns.get(String(params.jobId))
    if (!job || !run) return notFound('Lote')
    if (FINISHED.includes(job.status)) return problem(409, 'JOB_NOT_ACTIVE', 'O lote já terminou.')
    run.cancelled = true
    job.status = 'CANCELLED'
    job.finishedAt = nowIso()
    emit(run, { type: 'job_status', job: { ...job } })
    emit(run, { type: 'done' })
    run.wake?.()
    return HttpResponse.json({ ...job })
  }),
]
