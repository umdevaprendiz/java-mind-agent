import type {
  AgentPhase,
  ChatEvent,
  ChatStreamEvent,
  ErrorCode,
  SourceRef,
  Tier,
} from '@/api/types'

/** O que o agente está fazendo agora; vem dos eventos SSE e vira a frase de "carregando" do chat. */
export type AgentActivity = 'idle' | AgentPhase | 'thinking' | 'writing' | 'error'

export interface ToolRun {
  callId: string
  tool: string
  arguments: Record<string, unknown>
  /** `undefined` enquanto a ferramenta roda. */
  ok?: boolean
  summary?: string
  output?: string | null
  durationMs?: number
}

export interface ChatError {
  code: ErrorCode | 'NETWORK'
  message: string
  retryable: boolean
}

export interface ChatMessage {
  /** Id local (não é o id do servidor). */
  id: string
  role: 'user' | 'assistant'
  text: string
  thinking: string
  tools: ToolRun[]
  sources: SourceRef[]
  usage?: ChatEvent<'usage'>
  status: 'streaming' | 'done' | 'error' | 'stopped'
  error?: ChatError
  tier?: Tier
  model?: string
  serverMessageId?: number
}

export interface ChatState {
  conversationId: string | null
  messages: ChatMessage[]
  streaming: boolean
  activity: AgentActivity
  /** Frase do último `status` do agente ("Carregando o modelo na memória"). */
  statusMessage: string | null
}

export const initialChatState: ChatState = {
  conversationId: null,
  messages: [],
  streaming: false,
  activity: 'idle',
  statusMessage: null,
}

export type ChatAction =
  | { type: 'send'; text: string; localIds: [string, string]; tier: Tier; fresh?: boolean }
  | { type: 'event'; event: ChatStreamEvent }
  | { type: 'fail'; error: ChatError }
  | { type: 'stop' }
  | { type: 'drop-last-exchange' }
  | { type: 'load'; conversationId: string; messages: ChatMessage[] }
  | { type: 'reset' }

function updateLast(state: ChatState, fn: (message: ChatMessage) => ChatMessage): ChatState {
  const last = state.messages[state.messages.length - 1]
  if (!last || last.role !== 'assistant') return state
  return { ...state, messages: [...state.messages.slice(0, -1), fn(last)] }
}

/** Reduz um evento do stream de chat. Eventos de tipo desconhecido são ignorados (evolução compatível). */
function applyEvent(state: ChatState, event: ChatStreamEvent): ChatState {
  switch (event.type) {
    case 'start':
      return {
        ...updateLast(state, (m) => ({ ...m, tier: event.tier, model: event.model })),
        conversationId: event.conversationId,
        activity: 'loading_model',
      }
    case 'status':
      return { ...state, activity: event.phase, statusMessage: event.message ?? null }
    case 'thinking':
      return {
        ...updateLast(state, (m) => ({ ...m, thinking: m.thinking + event.text })),
        activity: 'thinking',
      }
    case 'token':
      return {
        ...updateLast(state, (m) => ({ ...m, text: m.text + event.text })),
        activity: 'writing',
      }
    case 'tool_call':
      return updateLast(state, (m) => ({
        ...m,
        tools: [...m.tools, { callId: event.callId, tool: event.tool, arguments: event.arguments }],
      }))
    case 'tool_result':
      return updateLast(state, (m) => ({
        ...m,
        tools: m.tools.map((tool) =>
          tool.callId === event.callId
            ? {
                ...tool,
                ok: event.ok,
                summary: event.summary,
                output: event.output,
                durationMs: event.durationMs,
              }
            : tool,
        ),
      }))
    case 'source':
      return updateLast(state, (m) =>
        m.sources.some((s) => s.title === event.source.title)
          ? m
          : { ...m, sources: [...m.sources, event.source] },
      )
    case 'usage':
      return updateLast(state, (m) => ({ ...m, usage: event }))
    case 'done':
      return {
        ...updateLast(state, (m) => ({
          ...m,
          status: event.finishReason === 'cancelled' ? 'stopped' : 'done',
          serverMessageId: event.assistantMessageId,
        })),
        streaming: false,
        activity: 'idle',
        statusMessage: null,
      }
    case 'error':
      return failState(state, {
        code: event.code,
        message: event.message,
        retryable: event.retryable ?? false,
      })
    default:
      return state
  }
}

function failState(state: ChatState, error: ChatError): ChatState {
  return {
    ...updateLast(state, (m) => ({ ...m, status: 'error', error })),
    streaming: false,
    activity: 'error',
    statusMessage: null,
  }
}

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'send': {
      const [userId, assistantId] = action.localIds
      // `fresh`: a pergunta abre uma conversa nova em vez de continuar a que está na tela.
      const base = action.fresh ? initialChatState : state
      return {
        ...base,
        streaming: true,
        activity: 'loading_model',
        statusMessage: null,
        messages: [
          ...base.messages,
          {
            id: userId,
            role: 'user',
            text: action.text,
            thinking: '',
            tools: [],
            sources: [],
            status: 'done',
          },
          {
            id: assistantId,
            role: 'assistant',
            text: '',
            thinking: '',
            tools: [],
            sources: [],
            status: 'streaming',
            tier: action.tier,
          },
        ],
      }
    }
    case 'event':
      return applyEvent(state, action.event)
    case 'fail':
      return failState(state, action.error)
    case 'stop':
      return {
        ...updateLast(state, (m) => ({ ...m, status: 'stopped' })),
        streaming: false,
        activity: 'idle',
        statusMessage: null,
      }
    case 'drop-last-exchange':
      return { ...state, messages: state.messages.slice(0, -2), activity: 'idle' }
    case 'load':
      return {
        ...initialChatState,
        conversationId: action.conversationId,
        messages: action.messages,
      }
    case 'reset':
      return initialChatState
  }
}
