import type { ErrorCode, ProblemDetail } from './types'

/** Base da API. Em dev é vazia (mesma origem: MSW ou proxy do Vite); em outros ambientes use VITE_API_BASE. */
const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? ''

export function apiUrl(path: string): string {
  const origin = typeof window === 'undefined' ? 'http://localhost' : window.location.origin
  return new URL(BASE + path, origin).toString()
}

/** Erro da API. `code` vem do `problem+json`; `NETWORK` significa que nem chegou ao servidor. */
export class ApiError extends Error {
  readonly status: number
  readonly code: ErrorCode | 'NETWORK'
  readonly retryable: boolean
  readonly problem?: ProblemDetail

  constructor(
    message: string,
    options: {
      status: number
      code: ErrorCode | 'NETWORK'
      retryable?: boolean
      problem?: ProblemDetail
    },
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.retryable = options.retryable ?? false
    this.problem = options.problem
  }

  static network(cause: unknown): ApiError {
    const reason = cause instanceof Error ? cause.message : String(cause)
    return new ApiError(`Não foi possível falar com o servidor (${reason})`, {
      status: 0,
      code: 'NETWORK',
      retryable: true,
    })
  }
}

/** Converte uma resposta de erro (`application/problem+json`) em {@link ApiError}. */
export async function errorFromResponse(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // corpo vazio ou não-JSON: cai no erro genérico abaixo
  }
  return new ApiError(problem?.detail ?? problem?.title ?? `Erro ${response.status}`, {
    status: response.status,
    code: problem?.code ?? 'INTERNAL_ERROR',
    retryable: problem?.retryable ?? false,
    problem,
  })
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, string | number | boolean | null | undefined>
  json?: unknown
  form?: FormData
  signal?: AbortSignal
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', query, json, form, signal } = options
  const url = new URL(apiUrl(path))
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null) url.searchParams.set(key, String(value))
  }
  let response: Response
  try {
    response = await fetch(url, {
      method,
      signal,
      headers: {
        Accept: 'application/json',
        ...(json === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: form ?? (json === undefined ? undefined : JSON.stringify(json)),
    })
  } catch (error) {
    throw ApiError.network(error)
  }
  if (!response.ok) throw await errorFromResponse(response)
  if (response.status === 204 || response.status === 202) {
    const text = await response.text()
    return (text ? JSON.parse(text) : undefined) as T
  }
  return (await response.json()) as T
}
