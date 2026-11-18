/**
 * Aliases legíveis dos tipos gerados de `api/openapi.yaml` (`npm run gen:api` → `schema.d.ts`).
 * O contrato manda: se algo mudar lá, regenere o `schema.d.ts` e o compilador aponta o que quebrou.
 */
import type { components } from './schema'

type S = components['schemas']

// ---- básicos
export type Tier = S['Tier']
export type ModelId = S['ModelId']
export type JavaVersion = S['JavaVersion']
export type Language = S['Language']
export type ErrorCode = S['ErrorCode']
export type ProblemDetail = S['ProblemDetail']
export type SourceRef = S['SourceRef']
export type TeachingOptions = S['TeachingOptions']
export type PageInfo = S['PageInfo']

// ---- chat
export type ChatRequest = S['ChatRequest']
export type ChatStreamEvent = S['ChatStreamEvent']
export type ChatEvent<T extends ChatStreamEvent['type']> = Extract<ChatStreamEvent, { type: T }>
export type ToolCall = S['ToolCall']
export type Message = S['Message']
export type MessageRole = S['MessageRole']
export type Conversation = S['Conversation']
export type ConversationSummary = S['ConversationSummary']
export type ConversationPage = S['ConversationPage']
export type CreateConversationRequest = S['CreateConversationRequest']
export type UpdateConversationRequest = S['UpdateConversationRequest']
export type Attachment = S['Attachment']
export type AgentPhase = ChatEvent<'status'>['phase']

// ---- modelos
export type HardwareInfo = S['HardwareInfo']
export type ModelInfo = S['ModelInfo']
export type ModelCatalog = S['ModelCatalog']
export type ModelUnavailableReason = S['ModelUnavailableReason']
export type ModelPullStreamEvent = S['ModelPullStreamEvent']
export type PullProgressEvent = S['PullProgressEvent']
export type Settings = S['Settings']

// ---- PDF
export type PdfStatus = S['PdfStatus']
export type PdfDocument = S['PdfDocument']
export type PdfDocumentPage = S['PdfDocumentPage']
export type PdfStreamEvent = S['PdfStreamEvent']
export type Question = S['Question']
export type QuestionType = S['QuestionType']
export type Alternative = S['Alternative']
export type CreateQuestionRequest = S['CreateQuestionRequest']
export type UpdateQuestionRequest = S['UpdateQuestionRequest']
export type Answer = S['Answer']
export type Job = S['Job']
export type JobStatus = S['JobStatus']
export type JobEstimate = S['JobEstimate']
export type CreateJobRequest = S['CreateJobRequest']
export type JobStreamEvent = S['JobStreamEvent']
export type ExportFormat = 'md' | 'pdf' | 'docx'

// ---- conhecimento, sandbox, sistema
export type JavaVersionInfo = S['JavaVersionInfo']
export type CorpusSource = S['CorpusSource']
export type KnowledgeHit = S['KnowledgeHit']
export type RunCodeRequest = S['RunCodeRequest']
export type RunCodeResult = S['RunCodeResult']
export type SystemStatus = S['SystemStatus']

export const TIERS: readonly Tier[] = ['low', 'medium', 'strong', 'extra'] as const
