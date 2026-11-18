import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useMemo } from 'react'
import type { HardwareInfo, ModelInfo, Tier } from '@/api/types'
import { TIERS } from '@/api/types'
import { useModelCatalog } from '@/features/queries'
import { cancelPull, clearPullError, startPull, usePullStore, type PullState } from '@/stores/pull'
import { useUiStore } from '@/stores/ui'

export type TierStatus =
  | 'ready' // instalado, cabe e o Ollama responde
  | 'not-installed' // cabe, mas falta baixar (Forte, no mock)
  | 'downloading'
  | 'download-failed'
  | 'no-fit' // não cabe nesta máquina (Extra, no mock)
  | 'unreachable' // Ollama fora do ar

/** Um tier do seletor de custo (Baixo / Médio / Forte / Extra) já com o estado de uso resolvido. */
export interface TierItem {
  id: Tier
  index: number
  model: ModelInfo
  status: TierStatus
  selectable: boolean
  recommended: boolean
  ramGb: number
  /** Tamanho em disco; só se conhece depois de instalado. */
  diskGb: number | null
  tokensPerSecond: number | null
  speed: ModelInfo['speed']
  /** 1 (rápido) a 4 (muito lento), para medidores. */
  speedLevel: 1 | 2 | 3 | 4
  pull?: PullState
}

export interface TierController {
  loading: boolean
  failed: boolean
  items: TierItem[]
  hardware?: HardwareInfo
  selected: Tier
  selectedItem?: TierItem
  select: (tier: Tier) => void
  pull: (tier: Tier) => void
  cancelPull: (tier: Tier) => void
}

const SPEED_LEVEL: Record<ModelInfo['speed'], 1 | 2 | 3 | 4> = {
  fast: 1,
  medium: 2,
  slow: 3,
  very_slow: 4,
}

export function resolveStatus(model: ModelInfo, pull?: PullState): TierStatus {
  if (pull && !pull.error) return 'downloading'
  if (model.unavailableReason === 'HOST_UNREACHABLE') return 'unreachable'
  if (!model.fits || model.unavailableReason === 'DOES_NOT_FIT') return 'no-fit'
  if (pull?.error) return 'download-failed'
  if (!model.installed || model.unavailableReason === 'NOT_INSTALLED') return 'not-installed'
  return model.available ? 'ready' : 'unreachable'
}

export function useTiers(): TierController {
  const queryClient = useQueryClient()
  const catalog = useModelCatalog()
  const pulls = usePullStore((s) => s.pulls)
  const stored = useUiStore((s) => s.tier)
  const setTier = useUiStore((s) => s.setTier)

  const items = useMemo<TierItem[]>(() => {
    const models = catalog.data?.tiers ?? []
    return TIERS.flatMap((id, index) => {
      const model = models.find((m) => m.id === id)
      if (!model) return []
      const pull = pulls[id]
      const status = resolveStatus(model, pull)
      return [
        {
          id,
          index,
          model,
          status,
          selectable: status === 'ready',
          recommended: model.recommended,
          ramGb: model.minRamGb ?? 0,
          diskGb: model.diskUsageGb ?? model.sizeGb ?? null,
          tokensPerSecond: model.measuredTokensPerSecond ?? null,
          speed: model.speed,
          speedLevel: SPEED_LEVEL[model.speed],
          pull,
        },
      ]
    })
  }, [catalog.data, pulls])

  // Se o tier guardado deixou de estar disponível (ex.: modelo removido), cai no padrão do servidor.
  const storedItem = items.find((i) => i.id === stored)
  const fallback = catalog.data?.defaultTier ?? 'medium'
  const selected = storedItem?.selectable || items.length === 0 ? stored : fallback

  const select = useCallback(
    (tier: Tier) => {
      if (items.find((i) => i.id === tier)?.selectable) setTier(tier)
    },
    [items, setTier],
  )
  const pull = useCallback(
    (tier: Tier) => {
      clearPullError(tier)
      void startPull(tier, queryClient)
    },
    [queryClient],
  )

  return {
    loading: catalog.isPending,
    failed: catalog.isError,
    items,
    hardware: catalog.data?.hardware,
    selected,
    selectedItem: items.find((i) => i.id === selected),
    select,
    pull,
    cancelPull,
  }
}
