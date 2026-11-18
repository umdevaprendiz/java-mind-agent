import { useApp } from '@/app/session'
import { useJavaVersions } from '@/features/queries'
import { useUiStore } from '@/stores/ui'

/** Versão do Java das respostas: "Auto" deixa o agente decidir pelo contexto. */
export function JavaPicker() {
  const { t } = useApp()
  const version = useUiStore((s) => s.javaVersion)
  const setVersion = useUiStore((s) => s.setJavaVersion)
  const versions = useJavaVersions()
  const options = versions.data?.map((v) => v.version) ?? [8, 11, 17, 21]
  return (
    <label className="jma-java">
      <span className="jma-sr">{t('java.label')}</span>
      <select
        className="jma-java__select"
        value={version ?? ''}
        onChange={(e) => setVersion(e.target.value ? Number(e.target.value) : null)}
        aria-label={t('java.label')}
      >
        <option value="">{`Java: ${t('java.auto')}`}</option>
        {options.map((v) => (
          <option key={v} value={v}>{`Java ${v === 4 ? '1.4' : v}`}</option>
        ))}
      </select>
    </label>
  )
}
