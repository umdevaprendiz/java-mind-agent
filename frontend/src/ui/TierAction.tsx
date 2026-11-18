import { useApp } from '@/app/session'
import type { TierItem } from '@/features/models/useTiers'
import { fmtGb, tierName } from './tierText'

function PullProgress({ item }: { item: TierItem }) {
  const { t } = useApp()
  const pull = item.pull
  if (!pull || pull.error) return null
  const percent = pull.percent
  return (
    <div className="jma-pull">
      <div
        className="jma-bar"
        role="progressbar"
        aria-label={`${tierName(t, item.id)}: ${t('tier.downloading')}`}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
      >
        <span style={{ width: `${percent ?? 12}%` }} />
      </div>
      <span className="jma-pull__text">
        {percent === null
          ? t('tier.downloading')
          : t('tier.pullStatus', { status: t('tier.downloading'), percent: Math.round(percent) })}
      </span>
    </div>
  )
}

/** Instalar / cancelar / aviso de um nível. Fica vazio quando o nível já está pronto. */
export function TierAction({ item }: { item: TierItem }) {
  const { t, tiers } = useApp()
  if (item.status === 'downloading') {
    return (
      <div className="jma-tieraction">
        <PullProgress item={item} />
        <button
          type="button"
          className="jma-btn jma-btn--small jma-btn--ghost"
          onClick={() => tiers.cancelPull(item.id)}
        >
          {t('tier.cancelDownload')}
        </button>
      </div>
    )
  }
  if (item.status === 'not-installed' || item.status === 'download-failed') {
    const size = item.model.sizeGb
    return (
      <div className="jma-tieraction">
        {item.pull?.error && (
          <p className="jma-tieraction__warn" role="alert">
            {item.pull.error.message}
          </p>
        )}
        <button
          type="button"
          className="jma-btn jma-btn--small"
          onClick={() => tiers.pull(item.id)}
        >
          {item.status === 'download-failed'
            ? t('tier.retryDownload')
            : size
              ? t('tier.downloadSize', { gb: fmtGb(size, 0) })
              : t('tier.download')}
        </button>
      </div>
    )
  }
  return null
}
