import { createJSONStorage, type StateStorage } from 'zustand/middleware'

/** `localStorage` pode lançar (janela privada, dados bloqueados): nesse caso o estado vive só em memória. */
function safeStorage(): StateStorage {
  const memory = new Map<string, string>()
  const fallback: StateStorage = {
    getItem: (key) => memory.get(key) ?? null,
    setItem: (key, value) => void memory.set(key, value),
    removeItem: (key) => void memory.delete(key),
  }
  return {
    getItem: (key) => {
      try {
        return localStorage.getItem(key)
      } catch {
        return fallback.getItem(key)
      }
    },
    setItem: (key, value) => {
      try {
        localStorage.setItem(key, value)
      } catch {
        fallback.setItem(key, value)
      }
    },
    removeItem: (key) => {
      try {
        localStorage.removeItem(key)
      } catch {
        fallback.removeItem(key)
      }
    },
  }
}

export const persistStorage = createJSONStorage(() => safeStorage())
