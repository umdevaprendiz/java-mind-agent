import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Em dev os pedidos a /api são interceptados pelo MSW (service worker) antes de chegar aqui.
// Com `npm run dev:backend` (VITE_MOCKS=off) o proxy abaixo leva tudo para o Spring Boot.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  preview: {
    port: 4173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    testTimeout: 15000,
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
