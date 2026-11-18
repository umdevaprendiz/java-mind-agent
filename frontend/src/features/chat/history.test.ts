import { describe, expect, it } from 'vitest'
import type { Message } from '@/api/types'
import { chatReducer, initialChatState, type ChatAction } from './chatReducer'
import { toChatMessages } from './history'

const base = {
  conversationId: '3f2b8c1e-5d0a-4f6e-9a47-1c2d3e4f5a6b',
  createdAt: '2026-11-12T19:00:00Z',
}

const saved: Message[] = [
  { ...base, id: 1, role: 'USER', content: 'O que é um record?' },
  {
    ...base,
    id: 2,
    role: 'ASSISTANT',
    content: 'Uma classe de dados imutável.',
    tier: 'medium',
    model: 'qwen2.5:7b',
    sources: [{ title: 'JEP 395: Records' }],
    toolCalls: [
      { callId: 'c1', tool: 'search_docs', ok: null, summary: '3 trechos', durationMs: 90 },
    ],
  },
  { ...base, id: 3, role: 'SYSTEM', content: 'interno' },
  { ...base, id: 4, role: 'TOOL', content: 'interno' },
]

describe('toChatMessages', () => {
  it('mantém só perguntas e respostas, na ordem', () => {
    const messages = toChatMessages(saved)
    expect(messages.map((m) => m.role)).toEqual(['user', 'assistant'])
    expect(messages.map((m) => m.serverMessageId)).toEqual([1, 2])
    expect(messages.every((m) => m.status === 'done')).toBe(true)
  })

  it('traz nível, fontes e passos da resposta; passo sem `ok` conta como concluído', () => {
    const [, answer] = toChatMessages(saved)
    expect(answer.tier).toBe('medium')
    expect(answer.sources).toHaveLength(1)
    expect(answer.tools).toEqual([
      {
        callId: 'c1',
        tool: 'search_docs',
        arguments: {},
        ok: true,
        summary: '3 trechos',
        durationMs: 90,
      },
    ])
  })
})

describe('chatReducer: abrir e começar conversas', () => {
  it('`load` troca a conversa inteira', () => {
    const state = chatReducer(initialChatState, {
      type: 'load',
      conversationId: base.conversationId,
      messages: toChatMessages(saved),
    })
    expect(state.conversationId).toBe(base.conversationId)
    expect(state.messages).toHaveLength(2)
    expect(state.streaming).toBe(false)
  })

  it('`send` continua a conversa aberta, e com `fresh` começa outra', () => {
    const open = chatReducer(initialChatState, {
      type: 'load',
      conversationId: base.conversationId,
      messages: toChatMessages(saved),
    })
    const action: ChatAction = {
      type: 'send',
      text: 'e uma classe?',
      localIds: ['a', 'b'],
      tier: 'medium',
    }
    const next = chatReducer(open, action)
    expect(next.conversationId).toBe(base.conversationId)
    expect(next.messages).toHaveLength(4)

    const fresh = chatReducer(open, { ...action, fresh: true })
    expect(fresh.conversationId).toBeNull()
    expect(fresh.messages).toHaveLength(2)
    expect(fresh.streaming).toBe(true)
  })
})
