import { QueryClient, useQuery } from '@tanstack/react-query'
import {
  getModelCatalog,
  getSystemStatus,
  listConversations,
  listJavaVersions,
  listPdfs,
} from '@/api/endpoints'

export const queryKeys = {
  models: ['models'] as const,
  system: ['system', 'status'] as const,
  javaVersions: ['knowledge', 'versions'] as const,
  conversations: ['conversations'] as const,
  pdfs: ['pdfs'] as const,
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false },
    },
  })
}

export const useModelCatalog = () =>
  useQuery({ queryKey: queryKeys.models, queryFn: getModelCatalog, staleTime: 5_000 })

export const useSystemStatus = () =>
  useQuery({ queryKey: queryKeys.system, queryFn: getSystemStatus, refetchInterval: 30_000 })

export const useJavaVersions = () =>
  useQuery({ queryKey: queryKeys.javaVersions, queryFn: listJavaVersions, staleTime: Infinity })

export const useConversations = () =>
  useQuery({ queryKey: queryKeys.conversations, queryFn: () => listConversations({ size: 8 }) })

export const useRecentPdfs = () =>
  useQuery({ queryKey: queryKeys.pdfs, queryFn: () => listPdfs({ size: 5 }) })
