import type {
  Job,
  JobEstimate,
  JobStreamEvent,
  PdfDocument,
  PdfStatus,
  PdfStreamEvent,
  Question,
} from '@/api/types'

export type PdfStage = 'idle' | 'uploading' | 'processing' | 'review' | 'solving' | 'results'

export interface PdfError {
  code: string
  message: string
}

export interface PdfFlowState {
  stage: PdfStage
  filename: string | null
  pdf: PdfDocument | null
  extraction: {
    status: PdfStatus | null
    pagesDone: number
    pagesTotal: number
    message: string | null
  }
  questions: Question[]
  estimate: JobEstimate | null
  job: Job | null
  currentQuestionId: string | null
  /** Questões que falharam no lote atual (o evento `question_failed` não traz a resposta). */
  failed: Record<string, PdfError>
  error: PdfError | null
}

export const initialPdfState: PdfFlowState = {
  stage: 'idle',
  filename: null,
  pdf: null,
  extraction: { status: null, pagesDone: 0, pagesTotal: 0, message: null },
  questions: [],
  estimate: null,
  job: null,
  currentQuestionId: null,
  failed: {},
  error: null,
}

export type PdfAction =
  | { type: 'upload-start'; filename: string }
  | { type: 'uploaded'; pdf: PdfDocument }
  | { type: 'pdf-event'; event: PdfStreamEvent }
  | { type: 'opened'; pdf: PdfDocument; questions: Question[] }
  | { type: 'questions'; questions: Question[]; pdf?: PdfDocument }
  | { type: 'estimate'; estimate: JobEstimate | null }
  | { type: 'job'; job: Job }
  | { type: 'job-event'; event: JobStreamEvent }
  | { type: 'solved'; questions: Question[]; job?: Job }
  | { type: 'error'; error: PdfError }
  | { type: 'reset' }

export const hasAnswers = (questions: Question[]) => questions.some((q) => q.latestAnswer)

export function pdfReducer(state: PdfFlowState, action: PdfAction): PdfFlowState {
  switch (action.type) {
    case 'upload-start':
      return { ...initialPdfState, stage: 'uploading', filename: action.filename }
    case 'uploaded':
      return { ...state, stage: 'processing', pdf: action.pdf, filename: action.pdf.filename }
    case 'pdf-event': {
      const event = action.event
      if (event.type === 'pdf_status') {
        return {
          ...state,
          extraction: {
            status: event.status,
            pagesDone: event.pagesDone ?? state.extraction.pagesDone,
            pagesTotal: event.pagesTotal ?? state.extraction.pagesTotal,
            message: event.message ?? null,
          },
        }
      }
      if (event.type === 'error') {
        return { ...state, stage: 'idle', error: { code: event.code, message: event.message } }
      }
      return state
    }
    case 'opened':
      return {
        ...initialPdfState,
        stage: hasAnswers(action.questions) ? 'results' : 'review',
        filename: action.pdf.filename,
        pdf: action.pdf,
        questions: action.questions,
      }
    case 'questions':
      return {
        ...state,
        stage: state.stage === 'processing' ? 'review' : state.stage,
        questions: action.questions,
        pdf: action.pdf ?? state.pdf,
      }
    case 'estimate':
      return { ...state, estimate: action.estimate }
    case 'job':
      return {
        ...state,
        stage: 'solving',
        job: action.job,
        failed: {},
        currentQuestionId: null,
        error: null,
      }
    case 'job-event': {
      const event = action.event
      switch (event.type) {
        case 'job_status':
          return { ...state, job: event.job }
        case 'question_started':
          return { ...state, currentQuestionId: event.questionId }
        case 'question_answered':
          return {
            ...state,
            questions: state.questions.map((q) =>
              q.id === event.questionId ? { ...q, latestAnswer: event.answer } : q,
            ),
          }
        case 'question_failed':
          return {
            ...state,
            failed: {
              ...state.failed,
              [event.questionId]: { code: event.code, message: event.message },
            },
          }
        case 'error':
          return { ...state, error: { code: event.code, message: event.message } }
        default:
          return state
      }
    }
    case 'solved':
      return {
        ...state,
        stage: 'results',
        questions: action.questions,
        job: action.job ?? state.job,
        currentQuestionId: null,
      }
    case 'error':
      return {
        ...state,
        error: action.error,
        stage: state.stage === 'uploading' || state.stage === 'processing' ? 'idle' : state.stage,
      }
    case 'reset':
      return initialPdfState
  }
}
