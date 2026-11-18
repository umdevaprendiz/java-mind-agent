import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'
import { startPageMocks } from './page'
import { mockControls } from './state'

export const worker = setupWorker(...handlers)

declare global {
  interface Window {
    /** Controles dos mocks para demonstrações: `__jmMocks.setOllamaDown(true)`, `.failNextPull()`... */
    __jmMocks?: typeof mockControls
  }
}

/**
 * Liga os mocks do MSW. Usa o service worker (pedidos aparecem na aba Network) e, se o navegador não
 * conseguir registrá-lo — ou com `?mocks=page` —, atende o `fetch` dentro da própria página.
 * Pedidos fora de `/api` (assets, HMR) passam direto.
 */
export async function startMocks(): Promise<'service-worker' | 'page'> {
  window.__jmMocks = mockControls
  const forcePage = new URLSearchParams(window.location.search).get('mocks') === 'page'
  if (!forcePage && 'serviceWorker' in navigator) {
    try {
      await worker.start({
        onUnhandledFrame: 'bypass',
        quiet: true,
        serviceWorker: { url: '/mockServiceWorker.js' },
      })
      return 'service-worker'
    } catch (error) {
      console.info('[mocks] service worker indisponível; usando mocks em página.', error)
    }
  }
  startPageMocks()
  return 'page'
}
