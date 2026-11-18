import { HttpResponse } from 'msw'
import type { ErrorCode } from '@/api/types'

/** `speed` multiplica todas as esperas dos mocks: 1 = ritmo realista, 0 = instantâneo (testes). */
export const mockConfig = { speed: 1 }

export function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = Math.floor(Math.random() * 16)
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

export const nowIso = () => new Date().toISOString()

/** Espera `ms * speed`; resolve na hora se o sinal abortar (quem chama checa `signal.aborted`). */
export function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  const wait = ms * mockConfig.speed
  return new Promise((resolve) => {
    if (signal?.aborted) return resolve()
    const timer = setTimeout(done, wait)
    function done() {
      signal?.removeEventListener('abort', onAbort)
      resolve()
    }
    function onAbort() {
      clearTimeout(timer)
      done()
    }
    signal?.addEventListener('abort', onAbort, { once: true })
  })
}

// ------------------------------------------------------------- problem+json
const STATUS_TITLES: Record<number, string> = {
  400: 'Bad Request',
  404: 'Not Found',
  409: 'Conflict',
  413: 'Payload Too Large',
  415: 'Unsupported Media Type',
  422: 'Unprocessable Content',
  500: 'Internal Server Error',
  503: 'Service Unavailable',
}

export function problem(
  status: number,
  code: ErrorCode,
  detail: string,
  extra: { retryable?: boolean; errors?: { field: string; message: string }[] } = {},
) {
  return HttpResponse.json(
    {
      type: 'about:blank',
      title: STATUS_TITLES[status] ?? 'Error',
      status,
      detail,
      code,
      ...extra,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export const notFound = (what: string) => problem(404, 'NOT_FOUND', `${what} não encontrado(a)`)

// --------------------------------------------------------------------- SSE
export interface SseWriter {
  /** Envia um evento: `event:` = `type` do JSON, como manda o contrato. */
  send(event: { type: string } & Record<string, unknown>): void
  /** Linha de comentário (keep-alive), ignorada pelos clientes. */
  comment(text: string): void
  sleep(ms: number): Promise<void>
  /** `true` quando o cliente fechou a conexão (cancelou). */
  readonly closed: boolean
}

/**
 * Resposta `text/event-stream` alimentada por um roteiro assíncrono. Fechar a conexão (abort do
 * cliente) interrompe o roteiro, como o servidor real interrompe a geração.
 */
export function sseResponse(
  request: Request,
  script: (out: SseWriter) => Promise<void>,
  init: { headers?: Record<string, string> } = {},
): Response {
  const encoder = new TextEncoder()
  const cancelled = new AbortController()
  const onAbort = () => cancelled.abort()
  request.signal.addEventListener('abort', onAbort, { once: true })

  const stream = new ReadableStream<Uint8Array>({
    async start(controller) {
      const enqueue = (text: string) => {
        if (cancelled.signal.aborted) return
        try {
          controller.enqueue(encoder.encode(text))
        } catch {
          cancelled.abort()
        }
      }
      const out: SseWriter = {
        send: (event) => enqueue(`event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`),
        comment: (text) => enqueue(`: ${text}\n\n`),
        sleep: (ms) => sleep(ms, cancelled.signal),
        get closed() {
          return cancelled.signal.aborted
        },
      }
      try {
        await script(out)
      } finally {
        request.signal.removeEventListener('abort', onAbort)
        try {
          controller.close()
        } catch {
          // já fechado pelo cancelamento
        }
      }
    },
    cancel() {
      cancelled.abort()
    },
  })

  return new HttpResponse(stream, {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache',
      ...init.headers,
    },
  })
}

/** Quebra um texto em pedaços de ~2 palavras, preservando espaços e quebras de linha. */
export function chunkText(text: string, wordsPerChunk = 2): string[] {
  const parts = text.match(/\S+\s*|\s+/g) ?? []
  const chunks: string[] = []
  for (let i = 0; i < parts.length; i += wordsPerChunk)
    chunks.push(parts.slice(i, i + wordsPerChunk).join(''))
  return chunks
}
