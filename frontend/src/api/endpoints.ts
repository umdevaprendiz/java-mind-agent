/**
 * Uma função por operação do contrato (`operationId` entre parênteses).
 * Os testes de contrato chamam estas funções contra o MSW, então cliente e mocks não se afastam do YAML.
 */
import { apiUrl, errorFromResponse, request } from './client'
import { streamSse, type StreamOptions, type StreamResult } from './sse'
import type {
  Answer,
  Attachment,
  ChatRequest,
  ChatStreamEvent,
  CorpusSource,
  Conversation,
  ConversationPage,
  CreateConversationRequest,
  CreateJobRequest,
  CreateQuestionRequest,
  ExportFormat,
  Job,
  JobEstimate,
  JobStreamEvent,
  JavaVersion,
  JavaVersionInfo,
  KnowledgeHit,
  Message,
  ModelCatalog,
  ModelId,
  ModelPullStreamEvent,
  PdfDocument,
  PdfDocumentPage,
  PdfStreamEvent,
  Question,
  RunCodeRequest,
  RunCodeResult,
  Settings,
  SystemStatus,
  Tier,
  UpdateConversationRequest,
  UpdateQuestionRequest,
} from './types'

export type { Answer }

// ------------------------------------------------------------------ Chat
/** POST /api/chat (sendChatMessage) — stream SSE. */
export const sendChatMessage = (
  body: ChatRequest,
  options: Omit<StreamOptions<ChatStreamEvent>, 'method' | 'body'>,
): Promise<StreamResult> =>
  streamSse<ChatStreamEvent>('/api/chat', { ...options, method: 'POST', body })

// ---------------------------------------------------------- Conversations
export const listConversations = (params: { page?: number; size?: number; q?: string } = {}) =>
  request<ConversationPage>('/api/conversations', { query: params })
export const createConversation = (body?: CreateConversationRequest) =>
  request<Conversation>('/api/conversations', { method: 'POST', json: body })
export const getConversation = (id: string) => request<Conversation>(`/api/conversations/${id}`)
export const updateConversation = (id: string, body: UpdateConversationRequest) =>
  request<Conversation>(`/api/conversations/${id}`, { method: 'PATCH', json: body })
export const deleteConversation = (id: string) =>
  request<void>(`/api/conversations/${id}`, { method: 'DELETE' })
export const listMessages = (id: string) => request<Message[]>(`/api/conversations/${id}/messages`)
export const truncateMessages = (id: string, fromMessageId: number) =>
  request<void>(`/api/conversations/${id}/messages`, { method: 'DELETE', query: { fromMessageId } })
export const uploadAttachment = (id: string, file: Blob, filename?: string) => {
  const form = new FormData()
  form.append('file', file, filename)
  return request<Attachment>(`/api/conversations/${id}/attachments`, { method: 'POST', form })
}
export const deleteAttachment = (id: string, attachmentId: string) =>
  request<void>(`/api/conversations/${id}/attachments/${attachmentId}`, { method: 'DELETE' })

// ----------------------------------------------------------------- Models
export const getModelCatalog = () => request<ModelCatalog>('/api/models')
/** POST /api/models/{modelId}/pull (pullModel) — stream SSE de progresso. */
export const pullModel = (
  modelId: ModelId,
  options: Omit<StreamOptions<ModelPullStreamEvent>, 'method' | 'body'>,
) => streamSse<ModelPullStreamEvent>(`/api/models/${modelId}/pull`, { ...options, method: 'POST' })
export const deleteModel = (modelId: ModelId) =>
  request<void>(`/api/models/${modelId}`, { method: 'DELETE' })
export const warmUpModel = (modelId: ModelId) =>
  request<void>(`/api/models/${modelId}/warmup`, { method: 'POST' })
export const unloadModel = (modelId: ModelId) =>
  request<void>(`/api/models/${modelId}/unload`, { method: 'POST' })

// --------------------------------------------------------------- Settings
export const getSettings = () => request<Settings>('/api/settings')
export const updateSettings = (body: Settings) =>
  request<Settings>('/api/settings', { method: 'PUT', json: body })

// -------------------------------------------------------------------- PDF
export const listPdfs = (params: { page?: number; size?: number } = {}) =>
  request<PdfDocumentPage>('/api/pdfs', { query: params })
export const uploadPdf = (file: Blob, filename?: string) => {
  const form = new FormData()
  form.append('file', file, filename)
  return request<PdfDocument>('/api/pdfs', { method: 'POST', form })
}
export const getPdf = (pdfId: string) => request<PdfDocument>(`/api/pdfs/${pdfId}`)
export const deletePdf = (pdfId: string) =>
  request<void>(`/api/pdfs/${pdfId}`, { method: 'DELETE' })
/** GET /api/pdfs/{pdfId}/events (streamPdfEvents) — progresso da extração e da segmentação. */
export const streamPdfEvents = (
  pdfId: string,
  options: Omit<StreamOptions<PdfStreamEvent>, 'method' | 'body'>,
) => streamSse<PdfStreamEvent>(`/api/pdfs/${pdfId}/events`, options)
export const listQuestions = (pdfId: string) => request<Question[]>(`/api/pdfs/${pdfId}/questions`)
export const createQuestion = (pdfId: string, body: CreateQuestionRequest) =>
  request<Question>(`/api/pdfs/${pdfId}/questions`, { method: 'POST', json: body })
export const updateQuestion = (pdfId: string, questionId: string, body: UpdateQuestionRequest) =>
  request<Question>(`/api/pdfs/${pdfId}/questions/${questionId}`, { method: 'PATCH', json: body })
export const deleteQuestion = (pdfId: string, questionId: string) =>
  request<void>(`/api/pdfs/${pdfId}/questions/${questionId}`, { method: 'DELETE' })
export const estimateJob = (pdfId: string, tier: Tier) =>
  request<JobEstimate>(`/api/pdfs/${pdfId}/estimate`, { query: { tier } })
export const listJobs = (pdfId: string) => request<Job[]>(`/api/pdfs/${pdfId}/jobs`)
export const createJob = (pdfId: string, body: CreateJobRequest) =>
  request<Job>(`/api/pdfs/${pdfId}/jobs`, { method: 'POST', json: body })
/** URL de download do gabarito (exportPdfResults); use em `<a href download>`. */
export const exportPdfUrl = (pdfId: string, format: ExportFormat, jobId?: string) => {
  const url = new URL(apiUrl(`/api/pdfs/${pdfId}/export`))
  url.searchParams.set('format', format)
  if (jobId) url.searchParams.set('jobId', jobId)
  return url.pathname + url.search
}
export const exportPdfResults = async (pdfId: string, format: ExportFormat, jobId?: string) => {
  const response = await fetch(apiUrl(exportPdfUrl(pdfId, format, jobId)))
  if (!response.ok) throw await errorFromResponse(response)
  return response.blob()
}

// ------------------------------------------------------------------- Jobs
export const getJob = (jobId: string) => request<Job>(`/api/jobs/${jobId}`)
/** GET /api/jobs/{jobId}/events (streamJobEvents). */
export const streamJobEvents = (
  jobId: string,
  options: Omit<StreamOptions<JobStreamEvent>, 'method' | 'body'>,
) => streamSse<JobStreamEvent>(`/api/jobs/${jobId}/events`, options)
export const pauseJob = (jobId: string) =>
  request<Job>(`/api/jobs/${jobId}/pause`, { method: 'POST' })
export const resumeJob = (jobId: string) =>
  request<Job>(`/api/jobs/${jobId}/resume`, { method: 'POST' })
export const cancelJob = (jobId: string) =>
  request<Job>(`/api/jobs/${jobId}/cancel`, { method: 'POST' })

// -------------------------------------------------------------- Knowledge
export const listJavaVersions = () => request<JavaVersionInfo[]>('/api/knowledge/versions')
export const listCorpusSources = () => request<CorpusSource[]>('/api/knowledge/sources')
export const getCorpusSource = (sourceId: string) =>
  request<CorpusSource>(`/api/knowledge/sources/${sourceId}`)
export const ingestCorpusSource = (sourceId: string) =>
  request<CorpusSource>(`/api/knowledge/sources/${sourceId}/ingest`, { method: 'POST' })
export const searchKnowledge = (params: { q: string; javaVersion?: JavaVersion; limit?: number }) =>
  request<KnowledgeHit[]>('/api/knowledge/search', { query: params })

// ---------------------------------------------------------------- Sandbox
export const runJavaCode = (
  body: Omit<RunCodeRequest, 'enablePreview'> & { enablePreview?: boolean },
) => request<RunCodeResult>('/api/sandbox/run', { method: 'POST', json: body })

// ----------------------------------------------------------------- System
export const getSystemStatus = () => request<SystemStatus>('/api/system/status')
