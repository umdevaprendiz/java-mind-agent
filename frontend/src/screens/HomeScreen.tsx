import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '@/api/client'
import { ArrowIcon } from '@/app/icons'
import { Cup } from '@/app/Cup'
import { useApp } from '@/app/session'
import { useConversations, useRecentPdfs } from '@/features/queries'
import { errorText } from '@/i18n'
import { useUiStore } from '@/stores/ui'
import { Composer } from '@/ui/ChatPanel'

function today(language: string) {
  const text = new Date().toLocaleDateString(language, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  })
  return text.charAt(0).toUpperCase() + text.slice(1)
}

const shortDate = (iso: string, language: string) =>
  new Date(iso).toLocaleDateString(language, { day: 'numeric', month: 'short' })

/** Início: uma pergunta de cara, três caminhos, as conversas e as provas recentes para continuar. */
export function HomeScreen() {
  const { t, chat, pdf, tiers } = useApp()
  const navigate = useNavigate()
  const language = useUiStore((s) => s.language)
  const recents = useRecentPdfs()
  const conversations = useConversations()
  const items = recents.data?.items ?? []
  const chats = conversations.data?.items ?? []
  const [openError, setOpenError] = useState<string | null>(null)

  const openConversation = async (id: string) => {
    setOpenError(null)
    try {
      await chat.open(id)
      void navigate('/conversa')
    } catch (error) {
      const api = error instanceof ApiError ? error : ApiError.network(error)
      setOpenError(errorText(t, api.code, api.message))
    }
  }

  const actions = [
    { to: '/conversa', title: t('home.card.ask.title'), text: t('home.card.ask.text') },
    { to: '/provas', title: t('home.card.exam.title'), text: t('home.card.exam.text') },
    { to: '/conversa', title: t('home.card.code.title'), text: t('home.card.code.text') },
  ]

  return (
    <div className="jma-home">
      <div className="jma-home__col">
        <p className="jma-mute">{today(language)}</p>
        <h1 className="jma-display">{t('home.title')}</h1>
        <Composer
          fresh
          autoFocus
          placeholder={t('home.placeholder')}
          onSent={() => void navigate('/conversa')}
        />
        <div className="jma-home__cards">
          {actions.map((action) => (
            <Link key={action.title} to={action.to} className="jma-card jma-card--link">
              <strong>{action.title}</strong>
              <span>{action.text}</span>
              <ArrowIcon />
            </Link>
          ))}
        </div>
        {openError && (
          <p className="jma-alert jma-alert--error" role="alert">
            {openError}
          </p>
        )}
        {chats.length > 0 && (
          <section className="jma-home__recent" aria-label={t('home.conversations')}>
            <h2>{t('home.conversations')}</h2>
            <ul>
              {chats.map((item) => (
                <li key={item.id}>
                  <button type="button" onClick={() => void openConversation(item.id)}>
                    <span className="jma-home__title">
                      <span>{item.title}</span>
                      {item.lastMessagePreview && (
                        <small className="jma-mute">{item.lastMessagePreview}</small>
                      )}
                    </span>
                    <span className="jma-mute">
                      {t('home.messages', { n: item.messageCount })} ·{' '}
                      {shortDate(item.updatedAt, language)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}
        {items.length > 0 && (
          <section className="jma-home__recent" aria-label={t('home.recent')}>
            <h2>{t('home.recent')}</h2>
            <ul>
              {items.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => {
                      void pdf.open(item)
                      void navigate('/provas')
                    }}
                  >
                    <span>{item.filename}</span>
                    <span className="jma-mute">
                      {t('home.questions', { n: item.questionCount })}
                      <Cup tier={tiers.selected} size={20} />
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  )
}
