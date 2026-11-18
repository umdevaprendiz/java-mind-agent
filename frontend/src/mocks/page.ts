import { FetchInterceptor } from '@mswjs/interceptors/fetch'
import { getResponse } from 'msw'
import { handlers } from './handlers'

/**
 * Mocks "em página": os mesmos handlers do MSW, mas atendendo o `fetch` da própria página (sem service
 * worker). É o plano B quando o navegador não registra o service worker (janelas embutidas, modo
 * privado, `?mocks=page`) e funciona com os fluxos SSE, pois a resposta em `ReadableStream` chega
 * direto a quem chamou `fetch`.
 */
export function startPageMocks() {
  const interceptor = new FetchInterceptor()
  interceptor.on('request', async ({ request, controller }) => {
    const url = new URL(request.url)
    if (url.origin !== window.location.origin || !url.pathname.startsWith('/api/')) return
    const response = await getResponse(handlers, request)
    if (response) controller.respondWith(response)
  })
  interceptor.apply()
}
