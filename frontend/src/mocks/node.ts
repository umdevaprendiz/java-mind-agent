import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** Servidor MSW para os testes (Vitest). */
export const server = setupServer(...handlers)
