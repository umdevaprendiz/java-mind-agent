import { useApp } from '@/app/session'
import { useSystemStatus } from '@/features/queries'

/** Avisos do ambiente (Ollama desligado, sandbox sem Docker), vindos de `GET /api/system/status`. */
export function Notices() {
  const { t } = useApp()
  const status = useSystemStatus()
  if (!status.data) return null
  const { ollama, sandbox } = status.data
  if (ollama.reachable && sandbox.available) return null
  return (
    <div className="jma-notices" role="status">
      {!ollama.reachable && <p className="jma-alert jma-alert--error">{t('system.ollamaDown')}</p>}
      {!sandbox.available && <p className="jma-alert jma-alert--warn">{t('system.sandboxDown')}</p>}
    </div>
  )
}
