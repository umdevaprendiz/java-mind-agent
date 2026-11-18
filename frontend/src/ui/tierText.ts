import type { Tier } from '@/api/types'
import type { MessageKey, Translate } from '@/i18n'
import type { TierItem } from '@/features/models/useTiers'

export const tierName = (t: Translate, id: Tier) => t(`tier.${id}` as MessageKey)
export const tierDesc = (t: Translate, id: Tier) => t(`tier.${id}.desc` as MessageKey)

export const fmtGb = (n: number, digits = 1) =>
  n.toLocaleString('pt-BR', { maximumFractionDigits: digits })

/** Linha de apoio de um nível, em linguagem simples: o estado dele ou o tempo de resposta. */
export function tierNote(t: Translate, item: TierItem): string {
  switch (item.status) {
    case 'no-fit':
      return t('tier.doesNotFit')
    case 'unreachable':
      return t('tier.hostUnreachable')
    case 'downloading':
      return t('tier.downloading')
    default:
      return t(`tier.speed.${item.speed}` as MessageKey)
  }
}
