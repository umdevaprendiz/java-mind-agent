/**
 * Leitor de Server-Sent Events sobre `fetch`.
 *
 * `EventSource` só faz GET e o chat/pull são POST, então lemos o corpo da resposta em stream.
 * Escrevemos o parser em vez de usar `@microsoft/fetch-event-source` porque aquele pacote pausa a
 * conexão quando a aba fica oculta (queremos continuar recebendo tokens) e tenta reconectar sozinho
 * (um chat não deve reenviar a pergunta). O formato segue o padrão WHATWG: campos `event`/`data`/`id`/
 * `retry`, linhas `:` são comentários (keep-alive) e uma linha em branco fecha a mensagem.
 */
import { ApiError, apiUrl, errorFromResponse } from './client'

export interface SseMessage {
  event: string
  data: string
  id?: string
}

/** Quebra o stream de bytes em mensagens SSE. Funciona com pedaços cortados em qualquer ponto. */
export async function* readSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseMessage> {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let event = ''
  let data: string[] = []
  let id: string | undefined
  let first = true

  const flush = (): SseMessage | null => {
    if (data.length === 0) {
      event = ''
      return null
    }
    const message: SseMessage = { event: event || 'message', data: data.join('\n'), id }
    event = ''
    data = []
    return message
  }

  const handleLine = (line: string): SseMessage | null => {
    if (line === '') return flush()
    if (line.startsWith(':')) return null // comentário / keep-alive
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') data.push(value)
    else if (field === 'id') id = value
    return null // `retry` e campos desconhecidos são ignorados
  }

  try {
    for (;;) {
      const { done, value } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      if (first && buffer.length > 0) {
        if (buffer.charCodeAt(0) === 0xfeff) buffer = buffer.slice(1)
        first = false
      }
      // Linhas terminam em \n, \r\n ou \r. Um \r no fim do buffer pode ser o começo de \r\n: espera.
      let start = 0
      for (let i = 0; i < buffer.length; i++) {
        const ch = buffer[i]
        if (ch !== '\n' && ch !== '\r') continue
        if (ch === '\r' && i === buffer.length - 1 && !done) break
        const message = handleLine(buffer.slice(start, i))
        if (ch === '\r' && buffer[i + 1] === '\n') i++
        start = i + 1
        if (message) yield message
      }
      buffer = buffer.slice(start)
      if (done) {
        if (buffer) {
          const message = handleLine(buffer)
          if (message) yield message
        }
        const last = flush()
        if (last) yield last
        return
      }
    }
  } finally {
    reader.releaseLock()
  }
}

export interface StreamOptions<T> {
  method?: 'GET' | 'POST'
  body?: unknown
  signal?: AbortSignal
  /** Chamado a cada evento reconhecido, na ordem. Tipos desconhecidos também chegam: ignore-os. */
  onEvent: (event: T) => void
}

export interface StreamResult {
  /** `true` quando o cliente cancelou (AbortController); nenhum erro é lançado nesse caso. */
  aborted: boolean
}

/**
 * Abre um endpoint `text/event-stream` e entrega cada evento já convertido de JSON.
 * Falhas anteriores ao stream (4xx/5xx `problem+json`) viram {@link ApiError}.
 * O nome em `event:` é igual ao `type` do JSON; se o `data` não trouxer `type`, usamos o nome.
 */
export async function streamSse<T extends { type: string }>(
  path: string,
  { method = 'GET', body, signal, onEvent }: StreamOptions<T>,
): Promise<StreamResult> {
  let response: Response
  try {
    response = await fetch(apiUrl(path), {
      method,
      signal,
      headers: {
        Accept: 'text/event-stream',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch (error) {
    if (isAbort(error)) return { aborted: true }
    throw ApiError.network(error)
  }
  if (!response.ok) throw await errorFromResponse(response)
  if (!response.body) throw ApiError.network(new Error('Resposta sem corpo'))

  try {
    for await (const message of readSse(response.body)) {
      let parsed: unknown
      try {
        parsed = JSON.parse(message.data)
      } catch {
        continue // linha de dados que não é JSON: ignora, como manda o contrato
      }
      if (typeof parsed !== 'object' || parsed === null) continue
      const event = parsed as { type?: unknown }
      const type = typeof event.type === 'string' ? event.type : message.event
      onEvent({ ...(parsed as object), type } as T)
    }
  } catch (error) {
    if (isAbort(error) || signal?.aborted) return { aborted: true }
    throw ApiError.network(error)
  }
  return { aborted: signal?.aborted ?? false }
}

function isAbort(error: unknown): boolean {
  return error instanceof DOMException && error.name === 'AbortError'
}
