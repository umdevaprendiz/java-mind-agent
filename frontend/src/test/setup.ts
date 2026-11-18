import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { server } from '@/mocks/node'
import { resetMockState } from '@/mocks/state'
import { mockConfig } from '@/mocks/util'

beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))

beforeEach(() => {
  mockConfig.speed = 0 // mocks instantâneos nos testes
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetMockState()
  try {
    localStorage.clear()
  } catch {
    // ambiente sem localStorage (testes em Node)
  }
})

afterAll(() => server.close())
