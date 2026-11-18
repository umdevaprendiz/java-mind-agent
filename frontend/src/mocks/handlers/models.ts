import { http, HttpResponse } from 'msw'
import type { ModelCatalog, ModelId, ModelInfo } from '@/api/types'
import { HARDWARE, PULL_SIZE_GB } from '../fixtures'
import { state } from '../state'
import { notFound, problem, sleep, sseResponse } from '../util'

const MODEL_IDS: ModelId[] = ['low', 'medium', 'strong', 'extra', 'embedding']
export const isModelId = (id: unknown): id is ModelId => MODEL_IDS.includes(id as ModelId)

const OLLAMA_DOWN = 'O Ollama não está respondendo em http://localhost:11434.'

/** Como o catálogo vê um modelo: com o Ollama fora do ar tudo vira HOST_UNREACHABLE. */
function view(info: ModelInfo): ModelInfo {
  if (!state.ollamaDown) return { ...info }
  return { ...info, loaded: false, available: false, unavailableReason: 'HOST_UNREACHABLE' }
}

export function currentCatalog(): ModelCatalog {
  const { models } = state
  return {
    hardware: HARDWARE,
    defaultTier: state.settings.defaultTier,
    tiers: [models.low, models.medium, models.strong, models.extra].map(view),
    embedding: view(models.embedding),
  }
}

function markInstalled(model: ModelInfo, sizeGb: number) {
  Object.assign(model, {
    installed: true,
    sizeGb,
    diskUsageGb: sizeGb,
    available: model.fits,
    unavailableReason: model.fits ? null : 'DOES_NOT_FIT',
    supportsTools: model.kind === 'chat' ? true : null,
  } satisfies Partial<ModelInfo>)
}

export const modelHandlers = [
  http.get('*/api/models', () => HttpResponse.json(currentCatalog())),

  http.post('*/api/models/:modelId/pull', ({ request, params }) => {
    const id = params.modelId
    if (!isModelId(id)) return notFound('Modelo')
    const model = state.models[id]
    if (state.ollamaDown)
      return problem(503, 'OLLAMA_UNAVAILABLE', OLLAMA_DOWN, { retryable: true })
    if (!model.fits) {
      return problem(
        422,
        'MODEL_DOES_NOT_FIT',
        `O modelo ${model.label} pede ${model.minRamGb} GB de RAM e esta máquina tem ${HARDWARE.totalRamGb} GB.`,
      )
    }
    if (state.pulling.has(id)) return problem(409, 'CONFLICT', 'Este modelo já está sendo baixado.')

    state.pulling.add(id)
    const failing = state.failNextPull
    state.failNextPull = false

    return sseResponse(request, async (out) => {
      try {
        const total = Math.round((PULL_SIZE_GB[id] ?? model.sizeGb ?? 2) * 1e9)
        const firstLayer = Math.round(total * 0.93)
        out.comment('keep-alive')
        out.send({
          type: 'progress',
          status: 'pulling manifest',
          completedBytes: null,
          totalBytes: null,
          percent: null,
        })
        await out.sleep(500)

        // Camada grande: o total conhecido é só ela. Depois aparece uma segunda camada e o
        // percentual recua um pouco, como no Ollama de verdade (o contrato avisa disso).
        const steps = 20
        for (let i = 1; i <= steps && !out.closed; i++) {
          const completed = Math.round((firstLayer * i) / steps)
          out.send({
            type: 'progress',
            status: 'downloading',
            completedBytes: completed,
            totalBytes: firstLayer,
            percent: Math.round((completed / firstLayer) * 1000) / 10,
          })
          if (failing && i === 8) {
            out.send({
              type: 'error',
              code: 'OLLAMA_UNAVAILABLE',
              message:
                'A conexão com o Ollama caiu durante o download. Tente de novo: continua de onde parou.',
              retryable: true,
            })
            return
          }
          await out.sleep(160)
        }
        for (let i = 1; i <= 5 && !out.closed; i++) {
          const completed = firstLayer + Math.round(((total - firstLayer) * i) / 5)
          out.send({
            type: 'progress',
            status: 'downloading',
            completedBytes: completed,
            totalBytes: total,
            percent: Math.round((completed / total) * 1000) / 10,
          })
          await out.sleep(160)
        }
        for (const status of ['verifying sha256 digest', 'writing manifest', 'success']) {
          if (out.closed) return
          out.send({
            type: 'progress',
            status,
            completedBytes: total,
            totalBytes: total,
            percent: 100,
          })
          await out.sleep(250)
        }
        if (out.closed) return
        markInstalled(model, Math.round((total / 1e9) * 10) / 10)
        out.send({ type: 'done' })
      } finally {
        state.pulling.delete(id)
      }
    })
  }),

  http.delete('*/api/models/:modelId', ({ params }) => {
    const id = params.modelId
    if (!isModelId(id)) return notFound('Modelo')
    const model = state.models[id]
    if (state.ollamaDown)
      return problem(503, 'OLLAMA_UNAVAILABLE', OLLAMA_DOWN, { retryable: true })
    if (state.pulling.has(id)) return problem(409, 'CONFLICT', 'O modelo está sendo baixado agora.')
    if (!model.installed) return notFound('Modelo instalado')
    Object.assign(model, {
      installed: false,
      loaded: false,
      sizeGb: null,
      diskUsageGb: null,
      available: false,
      unavailableReason: model.fits ? 'NOT_INSTALLED' : 'DOES_NOT_FIT',
      supportsTools: null,
      measuredTokensPerSecond: null,
    } satisfies Partial<ModelInfo>)
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('*/api/models/:modelId/warmup', ({ params }) => {
    const id = params.modelId
    if (!isModelId(id)) return notFound('Modelo')
    const model = state.models[id]
    if (state.ollamaDown)
      return problem(503, 'OLLAMA_UNAVAILABLE', OLLAMA_DOWN, { retryable: true })
    if (!model.fits) {
      return problem(
        422,
        'MODEL_DOES_NOT_FIT',
        `O modelo ${model.label} não cabe na memória desta máquina.`,
      )
    }
    if (!model.installed) {
      return problem(422, 'MODEL_NOT_INSTALLED', `O modelo ${model.label} não está instalado.`)
    }
    // Responde já; o carregamento termina em segundo plano e aparece como `loaded`.
    void sleep(1500).then(() => {
      model.loaded = true
    })
    return new HttpResponse(null, { status: 202 })
  }),

  http.post('*/api/models/:modelId/unload', ({ params }) => {
    const id = params.modelId
    if (!isModelId(id)) return notFound('Modelo')
    if (state.ollamaDown)
      return problem(503, 'OLLAMA_UNAVAILABLE', OLLAMA_DOWN, { retryable: true })
    state.models[id].loaded = false
    return new HttpResponse(null, { status: 204 })
  }),
]
