import type { Message } from '@/api/types'
import type { ChatMessage } from './chatReducer'

/** Mensagens salvas no servidor → mensagens do chat. Só perguntas e respostas aparecem; o resto é interno. */
export function toChatMessages(messages: Message[]): ChatMessage[] {
  return messages.flatMap((message): ChatMessage[] => {
    if (message.role !== 'USER' && message.role !== 'ASSISTANT') return []
    const base = {
      id: `server-${message.id}`,
      text: message.content,
      thinking: '',
      status: 'done' as const,
      serverMessageId: message.id,
    }
    if (message.role === 'USER') {
      return [{ ...base, role: 'user', tools: [], sources: [] }]
    }
    return [
      {
        ...base,
        role: 'assistant',
        tier: message.tier ?? undefined,
        model: message.model ?? undefined,
        sources: message.sources ?? [],
        tools: (message.toolCalls ?? []).map((call) => ({
          callId: call.callId,
          tool: call.tool,
          arguments: call.arguments ?? {},
          ok: call.ok ?? true,
          summary: call.summary ?? undefined,
          durationMs: call.durationMs ?? undefined,
        })),
      },
    ]
  })
}
