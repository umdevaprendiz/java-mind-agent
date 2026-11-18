import type { QueryClient } from '@tanstack/react-query'
import { create } from 'zustand'
import { ApiError } from '@/api/client'
import { pullModel } from '@/api/endpoints'
import type { ModelId } from '@/api/types'
import { queryKeys } from '@/features/queries'

/** Estado de um download de modelo em andamento (ou que acabou de falhar). */
export interface PullState {
  status: string
  percent: number | null
  completedBytes: number | null
  totalBytes: number | null
  error?: { code: string; message: string; retryable: boolean }
}

interface PullStore {
  pulls: Partial<Record<ModelId, PullState>>
}

export const usePullStore = create<PullStore>(() => ({ pulls: {} }))

const controllers = new Map<ModelId, AbortController>()

function patch(id: ModelId, next: PullState | undefined) {
  usePullStore.setState((s) => {
    const pulls = { ...s.pulls }
    if (next) pulls[id] = next
    else delete pulls[id]
    return { pulls }
  })
}

/**
 * Baixa um modelo mostrando o progresso. Vive fora dos componentes de propósito: o seletor do campo de
 * mensagem e a tela de custo enxergam o mesmo download, e sair da tela não o cancela.
 */
export async function startPull(id: ModelId, queryClient: QueryClient): Promise<void> {
  if (controllers.has(id)) return
  const controller = new AbortController()
  controllers.set(id, controller)
  patch(id, { status: 'pulling manifest', percent: null, completedBytes: null, totalBytes: null })
  try {
    const result = await pullModel(id, {
      signal: controller.signal,
      onEvent: (event) => {
        if (event.type === 'progress') {
          const previous = usePullStore.getState().pulls[id]
          patch(id, {
            status: event.status,
            // O percentual pode recuar quando uma camada nova aparece; o contrato avisa. Mostramos como veio.
            percent: event.percent ?? previous?.percent ?? null,
            completedBytes: event.completedBytes ?? previous?.completedBytes ?? null,
            totalBytes: event.totalBytes ?? previous?.totalBytes ?? null,
          })
        } else if (event.type === 'error') {
          patch(id, {
            status: 'error',
            percent: null,
            completedBytes: null,
            totalBytes: null,
            error: {
              code: event.code,
              message: event.message,
              retryable: event.retryable ?? false,
            },
          })
        }
      },
    })
    const current = usePullStore.getState().pulls[id]
    if (result.aborted || !current?.error) patch(id, undefined)
  } catch (error) {
    const api = error instanceof ApiError ? error : ApiError.network(error)
    patch(id, {
      status: 'error',
      percent: null,
      completedBytes: null,
      totalBytes: null,
      error: { code: api.code, message: api.message, retryable: api.retryable },
    })
  } finally {
    controllers.delete(id)
    await queryClient.invalidateQueries({ queryKey: queryKeys.models })
  }
}

export function cancelPull(id: ModelId) {
  controllers.get(id)?.abort()
}

export function clearPullError(id: ModelId) {
  if (usePullStore.getState().pulls[id]?.error) patch(id, undefined)
}
