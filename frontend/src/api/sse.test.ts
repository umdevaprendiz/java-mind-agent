// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { readSse, type SseMessage } from './sse'

/** Entrega o texto em pedaços de tamanhos dados, para exercitar cortes em qualquer ponto. */
function streamOf(text: string, sizes: number[] = [text.length]): ReadableStream<Uint8Array> {
  const bytes = new TextEncoder().encode(text)
  let offset = 0
  let i = 0
  return new ReadableStream({
    pull(controller) {
      if (offset >= bytes.length) return controller.close()
      const size = sizes[i++ % sizes.length]
      controller.enqueue(bytes.slice(offset, offset + size))
      offset += size
    },
  })
}

async function collect(stream: ReadableStream<Uint8Array>): Promise<SseMessage[]> {
  const out: SseMessage[] = []
  for await (const message of readSse(stream)) out.push(message)
  return out
}

describe('readSse', () => {
  const raw =
    'event: token\ndata: {"type":"token","text":"Um record"}\n\n' +
    ': keep-alive\n\n' +
    'event: done\ndata: {"type":"done"}\n\n'

  it('lê mensagens com event e data e ignora comentários', async () => {
    const messages = await collect(streamOf(raw))
    expect(messages).toEqual([
      { event: 'token', data: '{"type":"token","text":"Um record"}', id: undefined },
      { event: 'done', data: '{"type":"done"}', id: undefined },
    ])
  })

  it.each([[[1]], [[3, 7]], [[5, 2, 11]], [[64]]])(
    'aguenta pedaços cortados em qualquer ponto %j',
    async (sizes) => {
      const messages = await collect(streamOf(raw, sizes))
      expect(messages.map((m) => m.event)).toEqual(['token', 'done'])
      expect(JSON.parse(messages[0].data).text).toBe('Um record')
    },
  )

  it('aceita CRLF e CR como fim de linha', async () => {
    const messages = await collect(
      streamOf('event: a\r\ndata: 1\r\n\r\nevent: b\rdata: 2\r\r', [4]),
    )
    expect(messages.map((m) => [m.event, m.data])).toEqual([
      ['a', '1'],
      ['b', '2'],
    ])
  })

  it('junta várias linhas data com \\n e usa "message" sem event', async () => {
    const messages = await collect(streamOf('data: linha 1\ndata: linha 2\n\n'))
    expect(messages).toEqual([{ event: 'message', data: 'linha 1\nlinha 2', id: undefined }])
  })

  it('preserva acentos mesmo quando um caractere UTF-8 é cortado ao meio', async () => {
    const text = 'event: token\ndata: {"type":"token","text":"ação é Java"}\n\n'
    const messages = await collect(streamOf(text, [1]))
    expect(JSON.parse(messages[0].data).text).toBe('ação é Java')
  })

  it('despacha a última mensagem mesmo sem a linha em branco final', async () => {
    const messages = await collect(streamOf('event: done\ndata: {"type":"done"}'))
    expect(messages).toHaveLength(1)
  })

  it('ignora BOM, retry e campos desconhecidos; guarda o id', async () => {
    const messages = await collect(
      streamOf('﻿retry: 3000\nfoo: bar\nid: 7\nevent: x\ndata: {}\n\n'),
    )
    expect(messages).toEqual([{ event: 'x', data: '{}', id: '7' }])
  })

  it('não gera mensagem para blocos sem data', async () => {
    expect(await collect(streamOf('event: x\n\n: so comentario\n\n'))).toEqual([])
  })
})
