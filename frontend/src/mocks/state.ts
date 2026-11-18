/**
 * Estado em memória do "servidor" simulado. É o que faz o mock parecer vivo: baixar o modelo Forte
 * muda `GET /api/models`, enviar um PDF cria questões, resolver um lote preenche as respostas.
 */
import type {
  Answer,
  Attachment,
  Conversation,
  CorpusSource,
  Job,
  Message,
  ModelId,
  ModelInfo,
  PdfDocument,
  Question,
  Settings,
  Tier,
} from '@/api/types'
import {
  createModels,
  createSources,
  QUESTION_SEEDS,
  type QuestionSeed,
  SRC_RECORDS,
} from './fixtures'
import { mockConfig, nowIso, uuid } from './util'

/** Ids fixos dos dados de exemplo, para links de demonstração (`?pdf=results`). */
export const SEED_IDS = {
  conversation: '3f2b8c1e-5d0a-4f6e-9a47-1c2d3e4f5a6b',
  pdfSolved: '7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d',
  pdfReview: '7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c99',
  jobSolved: '0b0b0b0b-1111-4222-8333-0c0c0c0c0c0c',
} as const

export interface JobRun {
  listeners: Set<(event: { type: string }) => void>
  /** Libera o laço quando o lote é retomado ou cancelado. */
  wake: (() => void) | null
  /** Avisa o lote que alguém já está ouvindo os eventos (o lote espera um instante por isso). */
  onListener: (() => void) | null
  cancelled: boolean
  /** Questões que o lote deve resolver, em ordem. */
  questionIds: string[]
}

export interface MockState {
  ollamaDown: boolean
  /** Faz o próximo download de modelo falhar no meio (demonstra o evento error). */
  failNextPull: boolean
  /** Simula o Docker parado: o sandbox responde 503 e o status vira DEGRADED. */
  sandboxDown: boolean
  models: Record<ModelId, ModelInfo>
  pulling: Set<ModelId>
  settings: Settings
  conversations: Map<string, Conversation>
  messages: Map<string, Message[]>
  nextMessageId: number
  attachments: Map<string, Attachment>
  pdfs: Map<string, PdfDocument>
  questions: Map<string, Question[]>
  jobs: Map<string, Job>
  jobRuns: Map<string, JobRun>
  sources: CorpusSource[]
}

function seedAnswer(
  seed: QuestionSeed,
  questionId: string,
  tier: Tier,
  jobId: string | null,
): Answer {
  const { answer } = seed
  return {
    id: uuid(),
    questionId,
    jobId,
    status: 'COMPLETED',
    chosenAlternative: answer.chosen,
    explanation: answer.explanation,
    steps: answer.steps,
    javaVersion: seed.javaVersion,
    confidence: answer.confidence,
    sources: answer.sources,
    verifiedByExecution: answer.verified,
    executionOutput: answer.output,
    tier,
    model:
      tier === 'low'
        ? 'qwen2.5:3b'
        : tier === 'medium'
          ? 'qwen2.5:7b'
          : tier === 'strong'
            ? 'qwen2.5:14b'
            : 'qwen2.5:32b',
    errorMessage: null,
    createdAt: nowIso(),
  }
}

export { seedAnswer }

export function questionFromSeed(seed: QuestionSeed, pdfId: string, ordinal: number): Question {
  const at = nowIso()
  return {
    id: uuid(),
    pdfDocumentId: pdfId,
    ordinal,
    label: seed.label,
    type: seed.type,
    statement: seed.statement,
    code: seed.code,
    alternatives: seed.alternatives,
    javaVersion: seed.javaVersion,
    pageNumber: seed.pageNumber,
    needsReview: seed.needsReview,
    latestAnswer: null,
    createdAt: at,
    updatedAt: at,
  }
}

export function createState(): MockState {
  const models = Object.fromEntries(createModels().map((m) => [m.id, m])) as Record<
    ModelId,
    ModelInfo
  >

  const conversations = new Map<string, Conversation>()
  const messages = new Map<string, Message[]>()
  const convSeeds: Array<[string, string, Tier | null, number | null]> = [
    [SEED_IDS.conversation, 'Records no Java 21', 'medium', 21],
    ['11111111-aaaa-4bbb-8ccc-000000000001', 'Dúvida sobre generics', 'low', null],
    ['11111111-aaaa-4bbb-8ccc-000000000002', 'Diferença entre == e equals', 'medium', 8],
    ['11111111-aaaa-4bbb-8ccc-000000000003', 'Garbage collector no Java 8', null, 8],
    ['11111111-aaaa-4bbb-8ccc-000000000004', 'Streams vs. laços for', 'medium', 17],
  ]
  convSeeds.forEach(([id, title, tier, javaVersion], index) => {
    const at = new Date(Date.now() - (index + 1) * 3_600_000 * 5).toISOString()
    conversations.set(id, {
      id,
      title,
      tier,
      javaVersion,
      language: 'pt-BR',
      preferences: {},
      createdAt: at,
      updatedAt: at,
    })
  })
  messages.set(SEED_IDS.conversation, [
    {
      id: 1,
      conversationId: SEED_IDS.conversation,
      role: 'USER',
      content: 'Qual a diferença entre record e classe comum?',
      createdAt: '2026-11-12T19:00:00Z',
    },
    {
      id: 2,
      conversationId: SEED_IDS.conversation,
      role: 'ASSISTANT',
      content:
        'Um record é uma classe de dados imutável, introduzida de forma definitiva no Java 16.',
      tier: 'medium',
      model: 'qwen2.5:7b',
      javaVersion: 21,
      promptTokens: 312,
      completionTokens: 148,
      tokensPerSecond: 2.3,
      sources: [SRC_RECORDS],
      toolCalls: [],
      attachments: [],
      createdAt: '2026-11-12T19:05:00Z',
    },
  ])

  // PDF já resolvido (para ver o painel de resultados na hora) e outro só segmentado.
  const pdfs = new Map<string, PdfDocument>()
  const questions = new Map<string, Question[]>()
  const jobs = new Map<string, Job>()

  const solvedQuestions = QUESTION_SEEDS.map((seed, i) =>
    questionFromSeed(seed, SEED_IDS.pdfSolved, i + 1),
  )
  solvedQuestions.forEach((q, i) => {
    q.latestAnswer = seedAnswer(QUESTION_SEEDS[i], q.id, 'medium', SEED_IDS.jobSolved)
  })
  questions.set(SEED_IDS.pdfSolved, solvedQuestions)
  pdfs.set(SEED_IDS.pdfSolved, {
    id: SEED_IDS.pdfSolved,
    filename: 'simulado-oca.pdf',
    sizeBytes: 482113,
    pageCount: 8,
    status: 'SEGMENTED',
    errorMessage: null,
    questionCount: solvedQuestions.length,
    needsReviewCount: solvedQuestions.filter((q) => q.needsReview).length,
    createdAt: '2026-11-12T19:10:00Z',
    updatedAt: '2026-11-12T19:12:30Z',
  })
  jobs.set(SEED_IDS.jobSolved, {
    id: SEED_IDS.jobSolved,
    pdfDocumentId: SEED_IDS.pdfSolved,
    status: 'COMPLETED',
    tier: 'medium',
    javaVersion: null,
    totalQuestions: solvedQuestions.length,
    completedQuestions: solvedQuestions.length,
    errorMessage: null,
    createdAt: '2026-11-12T19:15:00Z',
    startedAt: '2026-11-12T19:15:02Z',
    finishedAt: '2026-11-12T19:31:40Z',
  })

  const reviewQuestions = QUESTION_SEEDS.slice(0, 5).map((seed, i) =>
    questionFromSeed(seed, SEED_IDS.pdfReview, i + 1),
  )
  questions.set(SEED_IDS.pdfReview, reviewQuestions)
  pdfs.set(SEED_IDS.pdfReview, {
    id: SEED_IDS.pdfReview,
    filename: 'prova-java-17.pdf',
    sizeBytes: 301552,
    pageCount: 5,
    status: 'SEGMENTED',
    errorMessage: null,
    questionCount: reviewQuestions.length,
    needsReviewCount: reviewQuestions.filter((q) => q.needsReview).length,
    createdAt: nowIso(),
    updatedAt: nowIso(),
  })

  return {
    ollamaDown: false,
    failNextPull: false,
    sandboxDown: false,
    models,
    pulling: new Set(),
    settings: { defaultTier: 'medium', defaultJavaVersion: null, language: 'pt-BR' },
    conversations,
    messages,
    nextMessageId: 100,
    attachments: new Map(),
    pdfs,
    questions,
    jobs,
    jobRuns: new Map(),
    sources: createSources(),
  }
}

export const state: MockState = createState()

/** Volta tudo ao estado inicial (usado entre testes e no botão de reset das demos). */
export function resetMockState() {
  for (const run of state.jobRuns.values()) {
    run.cancelled = true
    run.wake?.()
  }
  Object.assign(state, createState())
}

/** Controles expostos em `window.__jmMocks` para demonstrações no navegador. */
export const mockControls = {
  state,
  reset: resetMockState,
  /** 1 = ritmo realista; 0 = instantâneo; 3 = bem devagar. */
  setSpeed(speed: number) {
    mockConfig.speed = speed
  },
  /** Simula o Ollama desligado: tiers viram HOST_UNREACHABLE e o chat devolve 503. */
  setOllamaDown(down: boolean) {
    state.ollamaDown = down
  },
  /** O próximo download (pull) termina com evento `error` perto de 40%. */
  setSandboxDown(down: boolean) {
    state.sandboxDown = down
  },
  failNextPull(fail = true) {
    state.failNextPull = fail
  },
}
