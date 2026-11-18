import { useId } from 'react'
import type { Tier } from '@/api/types'

/** Quanto a xícara de cada nível está cheia. */
const CUP_LEVEL: Record<Tier, number> = { low: 0.3, medium: 0.55, strong: 0.8, extra: 1 }

const BODY = 'M14 18H46V42A12 12 0 0 1 34 54H26A12 12 0 0 1 14 42Z'

/** Xícara que enche conforme o nível (Baixo, Médio, Alto, Extra). `dashed` = não cabe neste computador. */
export function Cup({
  tier,
  size = 40,
  dashed = false,
}: {
  tier: Tier
  size?: number
  dashed?: boolean
}) {
  const id = useId()
  const top = 54 - CUP_LEVEL[tier] * 36
  return (
    <svg
      className="jma-cup"
      viewBox="0 0 64 64"
      width={size}
      height={size}
      aria-hidden="true"
      focusable="false"
      data-dashed={dashed}
    >
      <defs>
        <clipPath id={id}>
          <path d={BODY} />
        </clipPath>
      </defs>
      {!dashed && (
        <g clipPath={`url(#${id})`}>
          <rect className="jma-cup__fill" x="12" y={top} width="38" height={62 - top} />
        </g>
      )}
      <path className="jma-cup__line" d={BODY} fill="none" strokeWidth="3" strokeLinejoin="round" />
      <path
        className="jma-cup__line"
        d="M46 24c8 0 12 4 12 9s-4 9-12 9"
        fill="none"
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  )
}
