import { useEffect, useMemo } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useChat } from '@/features/chat/useChat'
import { useTiers } from '@/features/models/useTiers'
import { usePdfFlow } from '@/features/pdf/usePdfFlow'
import { useT } from '@/i18n'
import { useUiStore } from '@/stores/ui'
import { LevelPill } from '@/ui/LevelPill'
import { Notices } from '@/ui/Notices'
import { MoonIcon, SunIcon } from './icons'
import { Logo } from './Logo'
import { AppContext, type AppSession } from './session'

const NAV = [
  { to: '/', key: 'nav.home', end: true },
  { to: '/conversa', key: 'nav.chat', end: false },
  { to: '/provas', key: 'nav.pdf', end: false },
  { to: '/niveis', key: 'nav.tiers', end: false },
] as const

/**
 * Casca do app: cabeçalho (marca "JMA", telas, nível e tema), avisos do ambiente e a tela atual.
 * O chat, o nível e o fluxo de PDF vivem aqui para sobreviver à troca de tela.
 */
export function Shell() {
  const t = useT()
  const theme = useUiStore((s) => s.theme)
  const setTheme = useUiStore((s) => s.setTheme)
  const language = useUiStore((s) => s.language)
  const chat = useChat()
  const tiers = useTiers()
  const pdf = usePdfFlow()

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.lang = language === 'en' ? 'en' : 'pt-BR'
  }, [theme, language])

  const session = useMemo<AppSession>(
    () => ({ t, theme, setTheme, chat, tiers, pdf }),
    [t, theme, setTheme, chat, tiers, pdf],
  )

  return (
    <AppContext.Provider value={session}>
      <div className="jma-app">
        <header className="jma-top">
          <NavLink to="/" className="jma-brand" aria-label={t('app.name')}>
            <Logo size={32} />
            <b>JMA</b>
          </NavLink>
          <nav className="jma-nav" aria-label={t('nav.views')}>
            {NAV.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end} className="jma-nav__item">
                {t(item.key)}
              </NavLink>
            ))}
          </nav>
          <div className="jma-top__tools">
            <LevelPill />
            <div className="jma-seg" role="group" aria-label={t('theme.label')}>
              <button
                type="button"
                aria-label={t('theme.light')}
                aria-pressed={theme === 'light'}
                onClick={() => setTheme('light')}
              >
                <SunIcon />
              </button>
              <button
                type="button"
                aria-label={t('theme.dark')}
                aria-pressed={theme === 'dark'}
                onClick={() => setTheme('dark')}
              >
                <MoonIcon />
              </button>
            </div>
          </div>
        </header>
        <Notices />
        <main className="jma-main">
          <Outlet />
        </main>
      </div>
    </AppContext.Provider>
  )
}
