import { useApp } from '@/app/session'
import type { ChatMessage, ToolRun } from '@/features/chat/chatReducer'
import type { MessageKey } from '@/i18n'
import { fmtGb } from './tierText'

function Steps({ tools }: { tools: ToolRun[] }) {
  const { t } = useApp()
  if (tools.length === 0) return null
  return (
    <section className="jma-bench__sec">
      <h3>{t('chat.tools')}</h3>
      <ol className="jma-tools">
        {tools.map((tool) => {
          const name = `tool.${tool.tool}` as MessageKey
          const label = t(name) === name ? tool.tool : t(name)
          const state = tool.ok === undefined ? 'running' : tool.ok ? 'ok' : 'failed'
          return (
            <li key={tool.callId} className="jma-tool" data-state={state}>
              <span className="jma-tool__dot" aria-hidden="true" />
              <span className="jma-tool__name">{label}</span>
              {tool.durationMs !== undefined && (
                <span className="jma-tool__time">{(tool.durationMs / 1000).toFixed(1)} s</span>
              )}
              {tool.summary && <span className="jma-tool__summary">{tool.summary}</span>}
              {tool.output && <code className="jma-tool__output">{tool.output}</code>}
            </li>
          )
        })}
      </ol>
    </section>
  )
}

/**
 * "Bancada" ao lado da conversa: o que o agente fez na última resposta (passos e código executado),
 * as fontes que usou e quanto da memória do computador está em uso.
 */
export function Bench({ message }: { message?: ChatMessage }) {
  const { t, tiers } = useApp()
  const hw = tiers.hardware
  const used = hw ? Math.max(0, hw.totalRamGb - hw.freeRamGb) : 0
  const sources = message?.sources ?? []
  return (
    <aside className="jma-bench jma-panel" aria-label={t('bench.title')}>
      {message && message.tools.length > 0 ? (
        <Steps tools={message.tools} />
      ) : (
        <p className="jma-bench__empty">{t('bench.empty')}</p>
      )}
      {sources.length > 0 && (
        <section className="jma-bench__sec">
          <h3>{t('chat.sources')}</h3>
          <ul className="jma-sources">
            {sources.map((source, index) => (
              <li key={source.title}>
                <em>{String(index + 1).padStart(2, '0')}</em>
                <a
                  href={source.url ?? undefined}
                  target="_blank"
                  rel="noreferrer noopener"
                  title={source.snippet ?? ''}
                >
                  {source.title}
                </a>
              </li>
            ))}
          </ul>
        </section>
      )}
      {hw && (
        <section className="jma-bench__mem">
          <div className="jma-bench__memrow">
            <span>{t('bench.memory')}</span>
            <span className="jma-mute">
              {t('bench.memoryOf', { used: fmtGb(used), total: fmtGb(hw.totalRamGb, 0) })}
            </span>
          </div>
          <div
            className="jma-bar"
            role="img"
            aria-label={t('bench.memoryOf', { used: fmtGb(used), total: fmtGb(hw.totalRamGb, 0) })}
          >
            <span style={{ width: `${hw.totalRamGb ? (used / hw.totalRamGb) * 100 : 0}%` }} />
          </div>
        </section>
      )}
    </aside>
  )
}
