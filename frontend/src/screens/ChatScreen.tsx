import { useApp } from '@/app/session'
import { Bench } from '@/ui/Bench'
import { ChatPanel } from '@/ui/ChatPanel'

/** Conversa (layout "Mesa"): o chat à esquerda e, à direita, a bancada com passos, fontes e memória. */
export function ChatScreen() {
  const { chat } = useApp()
  const lastAssistant = [...chat.messages].reverse().find((m) => m.role === 'assistant')
  return (
    <div className="jma-table">
      <ChatPanel />
      <Bench message={lastAssistant} />
    </div>
  )
}
