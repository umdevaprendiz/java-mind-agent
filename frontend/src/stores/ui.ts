import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { JavaVersion, Language, Tier } from '@/api/types'
import { persistStorage } from './storage'

export type ThemeMode = 'dark' | 'light'

interface UiState {
  language: Language
  /** Nível (xícara) escolhido; o seletor do app e o chat usam este. */
  tier: Tier
  javaVersion: JavaVersion | null
  /** Preto é o padrão; o claro (branco puro) é escolha do usuário. */
  theme: ThemeMode
  setLanguage: (language: Language) => void
  setTier: (tier: Tier) => void
  setJavaVersion: (version: JavaVersion | null) => void
  setTheme: (theme: ThemeMode) => void
}

export const useUiStore = create<UiState>()(
  persist(
    (set) => ({
      language: 'pt-BR',
      tier: 'medium',
      javaVersion: null,
      theme: 'dark',
      setLanguage: (language) => set({ language }),
      setTier: (tier) => set({ tier }),
      setJavaVersion: (javaVersion) => set({ javaVersion }),
      setTheme: (theme) => set({ theme }),
    }),
    {
      name: 'jma.ui',
      storage: persistStorage,
      version: 2,
      // v1 guardava tema por proposta de design (F33 antiga); o que sobra é só ignorado.
      migrate: (persisted) => persisted as UiState,
    },
  ),
)
