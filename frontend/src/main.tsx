import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { App } from './App'
import { createQueryClient } from './features/queries'
import '@fontsource/atkinson-hyperlegible/400.css'
import '@fontsource/atkinson-hyperlegible/700.css'
import '@fontsource-variable/jetbrains-mono/wght.css'
import './index.css'

const queryClient = createQueryClient()

/**
 * Os mocks (MSW) simulam toda a API, inclusive os fluxos SSE. `npm run dev:backend` desliga os mocks
 * (VITE_MOCKS=off) e usa o proxy para o back-end real em localhost:8080.
 */
async function enableMocks() {
  if (import.meta.env.VITE_MOCKS === 'off') return
  const { startMocks } = await import('./mocks/browser')
  await startMocks()
}

void enableMocks().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </StrictMode>,
  )
})
