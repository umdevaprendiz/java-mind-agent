import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ClipIcon, SendIcon, StopIcon } from '@/app/icons'
import { Logo } from '@/app/Logo'
import { useApp } from '@/app/session'
import type { ChatMessage } from '@/features/chat/chatReducer'
import { errorText, type MessageKey } from '@/i18n'
import { JavaPicker } from './JavaPicker'
import { Markdown } from './Markdown'
import { tierName } from './tierText'

const SUGGESTIONS: MessageKey[] = [
  'chat.suggestion.1',
  'chat.suggestion.2',
  'chat.suggestion.3',
  'chat.suggestion.4',
]

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

function ErrorBanner({ message }: { message: ChatMessage }) {
  const { t, chat, tiers } = useApp()
  const navigate = useNavigate()
  if (!message.error) return null
  const { code, retryable } = message.error
  const missing = code === 'MODEL_NOT_INSTALLED'
  return (
    <div className="jma-alert jma-alert--error" role="alert" data-code={code}>
      <p>{errorText(t, code, message.error.message)}</p>
      <div className="jma-alert__actions">
        {missing && (
          <button
            type="button"
            className="jma-btn jma-btn--small"
            onClick={() => {
              tiers.pull(message.tier ?? tiers.selected)
              void navigate('/niveis')
            }}
          >
            {t('tier.download')}
          </button>
        )}
        {(retryable || code === 'NETWORK') && (
          <button
            type="button"
            className="jma-btn jma-btn--small jma-btn--ghost"
            onClick={() => void chat.retry()}
          >
            {t('chat.retry')}
          </button>
        )}
      </div>
    </div>
  )
}

function MessageView({ message }: { message: ChatMessage }) {
  const { t, chat } = useApp()
  const assistant = message.role === 'assistant'
  const waiting = assistant && message.status === 'streaming' && !message.text
  const phaseKey = `phase.${chat.activity}` as MessageKey
  const seconds = message.usage?.totalDurationMs
  if (!assistant) {
    return (
      <article className="jma-msg jma-msg--user" data-status={message.status}>
        <p className="jma-msg__text">{message.text}</p>
      </article>
    )
  }
  return (
    <article className="jma-msg jma-msg--assistant" data-status={message.status}>
      <header className="jma-msg__head">
        <Logo size={24} />
        <span className="jma-msg__who">JMA</span>
        {(message.tier || seconds != null) && (
          <span className="jma-msg__meta">
            {[
              message.tier ? tierName(t, message.tier) : null,
              seconds != null ? `${Math.round(seconds / 1000)} s` : null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </span>
        )}
      </header>
      <div className="jma-msg__body">
        {message.thinking && (
          <details className="jma-think" open={message.status === 'streaming' && !message.text}>
            <summary>{t('chat.thinking')}</summary>
            <p>{message.thinking}</p>
          </details>
        )}
        {waiting && (
          <p className="jma-phase" role="status" data-phase={chat.activity}>
            <span className="jma-dots" aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
            {chat.statusMessage ?? t(phaseKey)}
          </p>
        )}
        {message.text && <Markdown source={message.text} />}
        {message.status === 'stopped' && <p className="jma-note">{t('chat.stopped')}</p>}
        {(message.sources.length > 0 || message.tools.length > 0) && (
          <p className="jma-msg__chips">
            {message.sources.length > 0 && (
              <span className="jma-chip">
                {t('chat.sourcesCount', { n: message.sources.length })}
              </span>
            )}
            {message.tools.some((tool) => tool.tool === 'run_java') && (
              <span className="jma-chip">{t('chat.codeRan')}</span>
            )}
          </p>
        )}
        <ErrorBanner message={message} />
      </div>
    </article>
  )
}

export function Composer({
  autoFocus = false,
  onSent,
  placeholder,
  fresh = false,
}: {
  autoFocus?: boolean
  onSent?: () => void
  placeholder?: string
  /** Cada pergunta abre uma conversa nova (usado no Início). */
  fresh?: boolean
}) {
  const { t, chat } = useApp()
  const [draft, setDraft] = useState('')
  const input = useRef<HTMLTextAreaElement>(null)

  // O campo cresce com o texto (até 180px) e se ajusta de novo quando a janela muda de largura.
  useEffect(() => {
    const el = input.current
    if (!el) return
    const fit = () => {
      el.style.height = 'auto'
      el.style.height = `${Math.min(el.scrollHeight, 180)}px`
    }
    fit()
    window.addEventListener('resize', fit)
    return () => window.removeEventListener('resize', fit)
  }, [draft])

  const submit = (event?: FormEvent) => {
    event?.preventDefault()
    if (chat.streaming) return
    const text = draft.trim()
    if (!text) return
    setDraft('')
    void chat.send(text, { fresh })
    onSent?.()
  }
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      submit()
    }
  }
  return (
    <form className="jma-composer" onSubmit={submit}>
      <div className="jma-field">
        <textarea
          ref={input}
          className="jma-composer__input"
          rows={1}
          autoFocus={autoFocus}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={onKeyDown}
          placeholder={placeholder ?? t('chat.placeholder')}
          aria-label={placeholder ?? t('chat.placeholder')}
        />
        <div className="jma-composer__bar">
          <div className="jma-composer__tools">
            <Link className="jma-chip" to="/provas">
              <ClipIcon /> {t('chat.attach')}
            </Link>
            <JavaPicker />
          </div>
          {chat.streaming ? (
            <button
              type="button"
              className="jma-send jma-send--stop"
              aria-label={t('chat.stop')}
              title={t('chat.stop')}
              onClick={chat.stop}
            >
              <StopIcon />
            </button>
          ) : (
            <button
              type="submit"
              className="jma-send"
              aria-label={t('chat.send')}
              title={t('chat.send')}
              disabled={!draft.trim()}
            >
              <SendIcon />
            </button>
          )}
        </div>
      </div>
    </form>
  )
}

/** Conversa: mensagens com streaming e o campo de pergunta. O nível fica no cabeçalho do app. */
export function ChatPanel({ className = '' }: { className?: string }) {
  const { t, chat } = useApp()
  const scroller = useRef<HTMLDivElement>(null)
  const last = chat.messages[chat.messages.length - 1]

  useEffect(() => {
    const el = scroller.current
    if (el)
      el.scrollTo({ top: el.scrollHeight, behavior: prefersReducedMotion() ? 'auto' : 'smooth' })
  }, [chat.messages.length, last?.text.length])

  return (
    <section
      className={`jma-chat ${className}`}
      data-streaming={chat.streaming}
      aria-label={t('nav.chat')}
    >
      <div className="jma-chat__scroll" ref={scroller} role="log" aria-live="polite">
        <div className="jma-chat__col">
          {chat.messages.length > 0 && (
            <button
              type="button"
              className="jma-btn jma-btn--small jma-btn--ghost jma-chat__new"
              onClick={chat.reset}
            >
              {t('chat.new')}
            </button>
          )}
          {chat.messages.length === 0 ? (
            <div className="jma-chat__empty">
              <h2>{t('chat.empty.title')}</h2>
              <p>{t('chat.empty.hint')}</p>
              <div className="jma-chat__suggestions">
                {SUGGESTIONS.map((key) => (
                  <button
                    key={key}
                    type="button"
                    className="jma-chip"
                    onClick={() => void chat.send(t(key))}
                  >
                    {t(key)}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            chat.messages.map((message) => <MessageView key={message.id} message={message} />)
          )}
        </div>
      </div>
      <div className="jma-chat__foot">
        <Composer />
        <p className="jma-hint">{t('chat.hint')}</p>
      </div>
    </section>
  )
}
