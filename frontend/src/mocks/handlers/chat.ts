import { http, HttpResponse } from 'msw'
import type {
  Attachment,
  ChatRequest,
  Conversation,
  ConversationSummary,
  Language,
  Message,
  SourceRef,
  Tier,
  ToolCall,
} from '@/api/types'
import { pickScenario } from '../fixtures'
import { state } from '../state'
import { chunkText, nowIso, notFound, problem, sseResponse, uuid } from '../util'

const TOKEN_DELAY_MS: Record<Tier, number> = { low: 22, medium: 34, strong: 55, extra: 80 }
const MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024
const ATTACHMENT_EXTENSIONS = ['.java', '.zip', '.txt', '.log']

function summarize(conversation: Conversation): ConversationSummary {
  const list = state.messages.get(conversation.id) ?? []
  const last = [...list].reverse().find((m) => m.role === 'ASSISTANT') ?? list[list.length - 1]
  return {
    id: conversation.id,
    title: conversation.title,
    tier: conversation.tier,
    javaVersion: conversation.javaVersion,
    language: conversation.language,
    messageCount: list.length,
    lastMessagePreview: last ? last.content.slice(0, 200) : null,
    createdAt: conversation.createdAt,
    updatedAt: conversation.updatedAt,
  }
}

export const chatHandlers = [
  // ------------------------------------------------------------------ chat (SSE)
  http.post('*/api/chat', async ({ request }) => {
    const body = (await request.json().catch(() => null)) as ChatRequest | null
    const message = typeof body?.message === 'string' ? body.message : ''
    if (!body || message.trim() === '' || message.length > 20000) {
      return problem(400, 'VALIDATION_FAILED', 'Requisição inválida', {
        errors: [
          {
            field: 'message',
            message:
              message.length > 20000
                ? 'deve ter no máximo 20000 caracteres'
                : 'não pode estar vazio',
          },
        ],
      })
    }

    let conversation = body.conversationId
      ? state.conversations.get(body.conversationId)
      : undefined
    if (body.conversationId && !conversation) return notFound('Conversa')
    if (state.ollamaDown || /\[ollama-off\]/i.test(message)) {
      return problem(
        503,
        'OLLAMA_UNAVAILABLE',
        'O Ollama não está respondendo em http://localhost:11434.',
        {
          retryable: true,
        },
      )
    }

    const tier: Tier = body.tier ?? conversation?.tier ?? state.settings.defaultTier
    const model = state.models[tier]
    if (!model.fits) {
      return problem(
        422,
        'MODEL_DOES_NOT_FIT',
        `O tier ${model.label} pede ${model.minRamGb} GB de RAM e não cabe nesta máquina.`,
      )
    }
    if (!model.installed) {
      return problem(
        422,
        'MODEL_NOT_INSTALLED',
        `O modelo do tier ${model.label} não está instalado.`,
      )
    }

    const language: Language = body.language ?? state.settings.language
    const javaVersion = body.javaVersion ?? null
    const newConversation = !conversation
    if (!conversation) {
      const at = nowIso()
      conversation = {
        id: uuid(),
        title: message.trim().slice(0, 48),
        tier: body.tier ?? null,
        javaVersion,
        language,
        preferences: {},
        createdAt: at,
        updatedAt: at,
      }
      state.conversations.set(conversation.id, conversation)
      state.messages.set(conversation.id, [])
    }
    const conversationId = conversation.id
    const history = state.messages.get(conversationId) ?? []
    state.messages.set(conversationId, history)

    const userMessage: Message = {
      id: state.nextMessageId++,
      conversationId,
      role: 'USER',
      content: message,
      javaVersion,
      attachments: [],
      createdAt: nowIso(),
    }
    history.push(userMessage)
    conversation.updatedAt = nowIso()

    const scenario = pickScenario(message)
    const reply = scenario.reply[language === 'en' ? 'en' : 'pt-BR']
    const tokenDelay = TOKEN_DELAY_MS[tier]

    return sseResponse(request, async (out) => {
      const startedAt = Date.now()
      let firstTokenAt: number | null = null
      let produced = ''
      const toolCalls: ToolCall[] = []
      const sources: SourceRef[] = []

      out.comment('keep-alive')
      out.send({
        type: 'start',
        conversationId,
        newConversation,
        userMessageId: userMessage.id,
        tier,
        model: model.ollamaModel,
        javaVersion,
      })

      if (!model.loaded) {
        out.send({
          type: 'status',
          phase: 'loading_model',
          message: 'Carregando o modelo na memória',
        })
        await out.sleep(900)
        model.loaded = true
      }

      out.send({
        type: 'status',
        phase: 'retrieving',
        message: 'Consultando a documentação oficial',
      })
      out.send({
        type: 'tool_call',
        callId: 'call_1',
        tool: 'search_docs',
        arguments: { query: scenario.toolQuery, ...(javaVersion ? { javaVersion } : {}) },
      })
      await out.sleep(450)
      out.send({
        type: 'tool_result',
        callId: 'call_1',
        tool: 'search_docs',
        ok: true,
        summary: `${scenario.sources.length + 1} trechos encontrados`,
        output: null,
        durationMs: 85,
      })
      toolCalls.push({
        callId: 'call_1',
        tool: 'search_docs',
        arguments: { query: scenario.toolQuery },
        ok: true,
        summary: `${scenario.sources.length + 1} trechos encontrados`,
        durationMs: 85,
      })
      for (const source of scenario.sources) {
        out.send({ type: 'source', source })
        sources.push(source)
        await out.sleep(120)
      }

      if (scenario.runsCode) {
        out.send({
          type: 'status',
          phase: 'executing_code',
          message: 'Executando o exemplo no sandbox (Java 21)',
        })
        out.send({
          type: 'tool_call',
          callId: 'call_2',
          tool: 'run_java',
          arguments: { javaVersion: 21, mainClass: 'Main' },
        })
        await out.sleep(700)
        out.send({
          type: 'tool_result',
          callId: 'call_2',
          tool: 'run_java',
          ok: true,
          summary: 'Compilou e executou em 1,8 s',
          output: scenario.execOutput ?? '',
          durationMs: 1840,
        })
        toolCalls.push({
          callId: 'call_2',
          tool: 'run_java',
          arguments: { javaVersion: 21 },
          ok: true,
          summary: 'Compilou e executou em 1,8 s',
          durationMs: 1840,
        })
        out.send({
          type: 'status',
          phase: 'verifying',
          message: 'Conferindo a resposta com a saída',
        })
        await out.sleep(350)
      }

      out.send({ type: 'status', phase: 'generating', message: 'Escrevendo a resposta' })

      if (/\[timeout\]/i.test(message)) {
        await out.sleep(600)
        out.send({
          type: 'error',
          code: 'GENERATION_TIMEOUT',
          message: 'O modelo demorou demais para responder. Tente um tier menor.',
          retryable: true,
        })
        return
      }

      if (tier !== 'low') {
        for (const piece of scenario.thinking) {
          if (out.closed) return
          out.send({ type: 'thinking', text: piece })
          await out.sleep(260)
        }
      }

      const failAfter = /\[erro\]/i.test(message) ? 6 : Infinity
      let sent = 0
      for (const text of chunkText(reply)) {
        if (out.closed) break
        if (sent === failAfter) {
          out.send({
            type: 'error',
            code: 'OLLAMA_UNAVAILABLE',
            message: 'A conexão com o Ollama caiu no meio da resposta.',
            retryable: true,
          })
          return
        }
        firstTokenAt ??= Date.now()
        out.send({ type: 'token', text })
        produced += text
        sent++
        await out.sleep(tokenDelay)
      }

      const words = reply.split(/\s+/).length
      const completionTokens = Math.round(words * 1.6)
      const elapsed = Date.now() - startedAt
      const assistantMessage: Message = {
        id: state.nextMessageId++,
        conversationId,
        role: 'ASSISTANT',
        content: produced,
        tier,
        model: model.ollamaModel,
        javaVersion: javaVersion ?? 21,
        promptTokens: 312,
        completionTokens,
        tokensPerSecond: model.measuredTokensPerSecond ?? 4,
        sources,
        toolCalls,
        attachments: [],
        createdAt: nowIso(),
      }
      history.push(assistantMessage)

      if (out.closed) return
      out.send({
        type: 'usage',
        promptTokens: 312,
        completionTokens,
        tokensPerSecond: model.measuredTokensPerSecond ?? 4,
        timeToFirstTokenMs: firstTokenAt ? firstTokenAt - startedAt : null,
        totalDurationMs: elapsed,
      })
      out.send({
        type: 'done',
        assistantMessageId: assistantMessage.id,
        finishReason: 'stop',
        javaVersion: javaVersion ?? 21,
      })
    })
  }),

  // ------------------------------------------------------------ conversations
  http.get('*/api/conversations', ({ request }) => {
    const url = new URL(request.url)
    const page = Math.max(0, Number(url.searchParams.get('page') ?? 0) || 0)
    const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? 20) || 20))
    const q = url.searchParams.get('q')?.toLowerCase()
    let items = [...state.conversations.values()]
    if (q) {
      items = items.filter(
        (c) =>
          c.title.toLowerCase().includes(q) ||
          (state.messages.get(c.id) ?? []).some((m) => m.content.toLowerCase().includes(q)),
      )
    }
    items.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    return HttpResponse.json({
      items: items.slice(page * size, (page + 1) * size).map(summarize),
      page,
      size,
      totalItems: items.length,
      totalPages: Math.ceil(items.length / size),
    })
  }),

  http.post('*/api/conversations', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as {
      title?: string
      tier?: Tier | null
      javaVersion?: number | null
      language?: Language | null
    } | null
    if (body?.title !== undefined && (body.title.length < 1 || body.title.length > 200)) {
      return problem(400, 'VALIDATION_FAILED', 'Requisição inválida', {
        errors: [{ field: 'title', message: 'deve ter de 1 a 200 caracteres' }],
      })
    }
    const at = nowIso()
    const conversation: Conversation = {
      id: uuid(),
      title: body?.title ?? 'Nova conversa',
      tier: body?.tier ?? null,
      javaVersion: body?.javaVersion ?? null,
      language: body?.language ?? state.settings.language,
      preferences: {},
      createdAt: at,
      updatedAt: at,
    }
    state.conversations.set(conversation.id, conversation)
    state.messages.set(conversation.id, [])
    return HttpResponse.json(conversation, { status: 201 })
  }),

  http.get('*/api/conversations/:conversationId', ({ params }) => {
    const conversation = state.conversations.get(String(params.conversationId))
    return conversation ? HttpResponse.json(conversation) : notFound('Conversa')
  }),

  http.patch('*/api/conversations/:conversationId', async ({ request, params }) => {
    const conversation = state.conversations.get(String(params.conversationId))
    if (!conversation) return notFound('Conversa')
    const patch = (await request.json().catch(() => null)) as Record<string, unknown> | null
    if (!patch || typeof patch !== 'object')
      return problem(400, 'VALIDATION_FAILED', 'Requisição inválida')
    if (
      'title' in patch &&
      (typeof patch.title !== 'string' || patch.title.length < 1 || patch.title.length > 200)
    ) {
      return problem(400, 'VALIDATION_FAILED', 'Requisição inválida', {
        errors: [{ field: 'title', message: 'deve ter de 1 a 200 caracteres' }],
      })
    }
    // merge patch: ausente = não muda; null explícito em tier/javaVersion = volta ao padrão
    if ('title' in patch) conversation.title = patch.title as string
    if ('tier' in patch) conversation.tier = (patch.tier as Tier | null) ?? null
    if ('javaVersion' in patch)
      conversation.javaVersion = (patch.javaVersion as number | null) ?? null
    if ('language' in patch && patch.language) conversation.language = patch.language as Language
    if ('preferences' in patch && patch.preferences && typeof patch.preferences === 'object') {
      conversation.preferences = patch.preferences as Record<string, unknown>
    }
    conversation.updatedAt = nowIso()
    return HttpResponse.json(conversation)
  }),

  http.delete('*/api/conversations/:conversationId', ({ params }) => {
    const id = String(params.conversationId)
    if (!state.conversations.delete(id)) return notFound('Conversa')
    state.messages.delete(id)
    return new HttpResponse(null, { status: 204 })
  }),

  http.get('*/api/conversations/:conversationId/messages', ({ params }) => {
    const id = String(params.conversationId)
    if (!state.conversations.has(id)) return notFound('Conversa')
    return HttpResponse.json(state.messages.get(id) ?? [])
  }),

  http.delete('*/api/conversations/:conversationId/messages', ({ request, params }) => {
    const id = String(params.conversationId)
    if (!state.conversations.has(id)) return notFound('Conversa')
    const from = Number(new URL(request.url).searchParams.get('fromMessageId'))
    if (!Number.isInteger(from)) {
      return problem(400, 'VALIDATION_FAILED', 'Requisição inválida', {
        errors: [{ field: 'fromMessageId', message: 'é obrigatório' }],
      })
    }
    const list = state.messages.get(id) ?? []
    state.messages.set(
      id,
      list.filter((m) => m.id < from),
    )
    return new HttpResponse(null, { status: 204 })
  }),

  // -------------------------------------------------------------- attachments
  http.post('*/api/conversations/:conversationId/attachments', async ({ request, params }) => {
    const id = String(params.conversationId)
    if (!state.conversations.has(id)) return notFound('Conversa')
    const form = await request.formData().catch(() => null)
    const file = form?.get('file')
    if (!(file instanceof Blob)) {
      return problem(400, 'VALIDATION_FAILED', 'Requisição inválida', {
        errors: [{ field: 'file', message: 'é obrigatório' }],
      })
    }
    const filename = file instanceof File ? file.name : 'anexo.txt'
    if (!ATTACHMENT_EXTENSIONS.some((ext) => filename.toLowerCase().endsWith(ext))) {
      return problem(415, 'UNSUPPORTED_MEDIA_TYPE', 'Aceitamos apenas .java, .zip, .txt e .log.')
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      return problem(413, 'PAYLOAD_TOO_LARGE', 'O anexo passa do limite de 5 MB.')
    }
    const attachment: Attachment = {
      id: uuid(),
      conversationId: id,
      messageId: null,
      filename,
      contentType: file.type || 'text/plain',
      sizeBytes: file.size,
      createdAt: nowIso(),
    }
    state.attachments.set(attachment.id, attachment)
    return HttpResponse.json(attachment, { status: 201 })
  }),

  http.delete('*/api/conversations/:conversationId/attachments/:attachmentId', ({ params }) => {
    const attachment = state.attachments.get(String(params.attachmentId))
    if (!attachment || attachment.conversationId !== String(params.conversationId))
      return notFound('Anexo')
    state.attachments.delete(attachment.id)
    return new HttpResponse(null, { status: 204 })
  }),
]
