import { useCallback } from 'react'
import type { Language } from '@/api/types'
import { useUiStore } from '@/stores/ui'
import { en } from './en'
import { ptBR, type MessageKey } from './pt-BR'

export type { MessageKey }
export type Params = Record<string, string | number>

const dictionaries: Record<Language, Record<MessageKey, string>> = { 'pt-BR': ptBR, en }

export function translate(language: Language, key: MessageKey, params?: Params): string {
  const text = dictionaries[language][key] ?? ptBR[key] ?? key
  if (!params) return text
  return text.replace(/\{(\w+)\}/g, (_, name: string) => String(params[name] ?? `{${name}}`))
}

export type Translate = (key: MessageKey, params?: Params) => string

/** Tradutor ligado ao idioma da interface (padrão pt-BR; en previsto pela decisão A5). */
export function useT(): Translate {
  const language = useUiStore((s) => s.language)
  return useCallback((key, params) => translate(language, key, params), [language])
}

/** Mensagem amigável para um `ErrorCode` do contrato. */
export function errorText(t: Translate, code: string, fallback?: string): string {
  const key = `error.${code}` as MessageKey
  return key in ptBR ? t(key) : (fallback ?? t('error.generic'))
}

export function formatDuration(seconds: number, language: Language): string {
  if (seconds < 90)
    return language === 'en' ? `${Math.round(seconds)} s` : `${Math.round(seconds)} s`
  const minutes = Math.round(seconds / 60)
  if (minutes < 90) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest ? `${hours} h ${rest} min` : `${hours} h`
}
