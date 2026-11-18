import { createContext, useContext } from 'react'
import type { ChatController } from '@/features/chat/useChat'
import type { TierController } from '@/features/models/useTiers'
import type { PdfController } from '@/features/pdf/usePdfFlow'
import type { Translate } from '@/i18n'
import type { ThemeMode } from '@/stores/ui'

/** Estado que as telas compartilham: chat, nível (xícara) e fluxo de PDF sobrevivem à troca de tela. */
export interface AppSession {
  t: Translate
  theme: ThemeMode
  setTheme: (theme: ThemeMode) => void
  chat: ChatController
  tiers: TierController
  pdf: PdfController
}

export const AppContext = createContext<AppSession | null>(null)

export function useApp(): AppSession {
  const value = useContext(AppContext)
  if (!value) throw new Error('useApp precisa estar dentro do Shell')
  return value
}
