import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useReducer, useRef } from 'react'
import { ApiError } from '@/api/client'
import { listMessages, sendChatMessage } from '@/api/endpoints'
import type { Tier } from '@/api/types'
import { queryKeys } from '@/features/queries'
import { useUiStore } from '@/stores/ui'
import { chatReducer, initialChatState, type ChatState } from './chatReducer'
import { toChatMessages } from './history'

export interface ChatController extends ChatState {
  /** `fresh` abre uma conversa nova em vez de continuar a atual. */
  send: (text: string, options?: { tier?: Tier; fresh?: boolean }) => Promise<void>
  stop: () => void
  /** Reenvia a última pergunta (depois de uma falha ou para regenerar). */
  retry: () => Promise<void>
  /** Abre uma conversa já salva (lança `ApiError` se não conseguir ler as mensagens). */
  open: (conversationId: string) => Promise<void>
  /** Começa uma conversa nova. */
  reset: () => void
}

let counter = 0
const localId = () => `local-${++counter}`

/** Estado e ações do chat com streaming SSE. Uma instância só, na casca do app. */
export function useChat(): ChatController {
  const [state, dispatch] = useReducer(chatReducer, initialChatState)
  const queryClient = useQueryClient()
  const controller = useRef<AbortController | null>(null)
  const stateRef = useRef(state)
  useEffect(() => {
    stateRef.current = state
  }, [state])

  useEffect(() => () => controller.current?.abort(), [])

  const send = useCallback(
    async (text: string, options: { tier?: Tier; fresh?: boolean } = {}) => {
      const message = text.trim()
      if (!message || stateRef.current.streaming) return
      const { tier: selected, javaVersion, language } = useUiStore.getState()
      const tier = options.tier ?? selected
      const conversationId = options.fresh ? null : stateRef.current.conversationId
      const abort = new AbortController()
      controller.current = abort
      dispatch({
        type: 'send',
        text: message,
        localIds: [localId(), localId()],
        tier,
        fresh: options.fresh,
      })
      try {
        const result = await sendChatMessage(
          { conversationId, message, tier, javaVersion, language },
          { signal: abort.signal, onEvent: (event) => dispatch({ type: 'event', event }) },
        )
        // Stream que acabou sem `done` nem `error` é uma queda de conexão, nunca um sucesso falso.
        if (!result.aborted && stateRef.current.streaming) {
          dispatch({
            type: 'fail',
            error: {
              code: 'NETWORK',
              message: 'A conexão terminou antes do fim da resposta.',
              retryable: true,
            },
          })
        }
      } catch (error) {
        const api = error instanceof ApiError ? error : ApiError.network(error)
        dispatch({
          type: 'fail',
          error: { code: api.code, message: api.message, retryable: api.retryable },
        })
      } finally {
        if (controller.current === abort) controller.current = null
        // A conversa nova (ou o título dela) passa a aparecer na lista do Início.
        void queryClient.invalidateQueries({ queryKey: queryKeys.conversations })
      }
    },
    [queryClient],
  )

  const stop = useCallback(() => {
    controller.current?.abort()
    controller.current = null
    dispatch({ type: 'stop' })
  }, [])

  const retry = useCallback(async () => {
    const messages = stateRef.current.messages
    const lastUser = [...messages].reverse().find((m) => m.role === 'user')
    if (!lastUser || stateRef.current.streaming) return
    dispatch({ type: 'drop-last-exchange' })
    await send(lastUser.text)
  }, [send])

  const open = useCallback(async (conversationId: string) => {
    // Lê primeiro: se falhar, a conversa que estava na tela continua intacta.
    const messages = await listMessages(conversationId)
    controller.current?.abort()
    controller.current = null
    dispatch({ type: 'load', conversationId, messages: toChatMessages(messages) })
  }, [])

  const reset = useCallback(() => {
    controller.current?.abort()
    controller.current = null
    dispatch({ type: 'reset' })
  }, [])

  return { ...state, send, stop, retry, open, reset }
}
