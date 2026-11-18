import { Cup } from '@/app/Cup'
import { CheckIcon } from '@/app/icons'
import { useApp } from '@/app/session'
import type { TierItem } from '@/features/models/useTiers'
import { TierAction } from '@/ui/TierAction'
import { fmtGb, tierDesc, tierName, tierNote } from '@/ui/tierText'

function LevelCard({ item }: { item: TierItem }) {
  const { t, tiers } = useApp()
  const selected = item.id === tiers.selected
  const off = item.status === 'no-fit'
  return (
    <div className="jma-cupcard" data-selected={selected} data-status={item.status}>
      <button
        type="button"
        role="radio"
        aria-checked={selected}
        aria-disabled={!item.selectable}
        className="jma-cupcard__hit"
        onClick={() => tiers.select(item.id)}
      >
        <Cup tier={item.id} size={92} dashed={off} />
        <strong className="jma-display jma-cupcard__name">{tierName(t, item.id)}</strong>
        <span className="jma-cupcard__desc">{tierDesc(t, item.id)}</span>
        <small className="jma-mute">{tierNote(t, item)}</small>
        {selected && (
          <span className="jma-badge jma-badge--accent">
            <CheckIcon /> {t('tier.chosen')}
          </span>
        )}
      </button>
      <TierAction item={item} />
    </div>
  )
}

/** Níveis: quatro xícaras (Baixo, Médio, Alto, Extra) e o que este computador aguenta. */
export function LevelsScreen() {
  const { t, tiers } = useApp()
  const hw = tiers.hardware
  const used = hw ? Math.max(0, hw.totalRamGb - hw.freeRamGb) : 0
  return (
    <div className="jma-levels">
      <header className="jma-levels__head">
        <h1 className="jma-display">{t('tier.screen.title')}</h1>
        <p className="jma-mute">{t('tier.screen.subtitle')}</p>
      </header>
      {tiers.loading ? (
        <p className="jma-mute" aria-busy="true">
          {t('tier.label')}…
        </p>
      ) : tiers.failed || tiers.items.length === 0 ? (
        <p className="jma-alert jma-alert--error" role="alert">
          {t('error.NETWORK')}
        </p>
      ) : (
        <div className="jma-levels__grid" role="radiogroup" aria-label={t('tier.label')}>
          {tiers.items.map((item) => (
            <LevelCard key={item.id} item={item} />
          ))}
        </div>
      )}
      {hw && (
        <footer className="jma-levels__foot jma-panel">
          <div className="jma-levels__mem">
            <span className="jma-mute">
              {t('tier.hardwareLine', { total: fmtGb(hw.totalRamGb, 0), used: fmtGb(used) })}
              {hw.cpuOnly && ` ${t('tier.cpuOnly')}`}
            </span>
            <div
              className="jma-bar"
              role="img"
              aria-label={t('bench.memoryOf', {
                used: fmtGb(used),
                total: fmtGb(hw.totalRamGb, 0),
              })}
            >
              <span style={{ width: `${hw.totalRamGb ? (used / hw.totalRamGb) * 100 : 0}%` }} />
            </div>
          </div>
        </footer>
      )}
    </div>
  )
}
