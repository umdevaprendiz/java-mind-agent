import { useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useReducer, useRef } from 'react'
import { ApiError } from '@/api/client'
import {
  cancelJob,
  createJob,
  estimateJob,
  getJob,
  getPdf,
  listQuestions,
  pauseJob,
  resumeJob,
  streamJobEvents,
  streamPdfEvents,
  updateQuestion,
  uploadPdf,
} from '@/api/endpoints'
import type { PdfDocument, Tier } from '@/api/types'
import { queryKeys } from '@/features/queries'
import { useUiStore } from '@/stores/ui'
import { initialPdfState, pdfReducer, type PdfFlowState } from './pdfReducer'

export interface PdfController extends PdfFlowState {
  upload: (file: File) => Promise<void>
  /** Envia um PDF de exemplo gerado na hora (o mesmo arquivo reaproveita o PDF já enviado). */
  loadSample: () => Promise<void>
  open: (pdf: PdfDocument) => Promise<void>
  solve: (tier: Tier) => Promise<void>
  pause: () => Promise<void>
  resume: () => Promise<void>
  cancel: () => Promise<void>
  markReviewed: (questionId: string) => Promise<void>
  reset: () => void
}

const SAMPLE_PDF = '%PDF-1.4\n% Java Mind Agent: PDF de exemplo para os mocks\n%%EOF'

const asError = (error: unknown) => {
  const api = error instanceof ApiError ? error : ApiError.network(error)
  return { code: api.code, message: api.message }
}

/** Fluxo do PDF de questões: enviar → ler → revisar → resolver em lote → gabarito. */
export function usePdfFlow(): PdfController {
  const queryClient = useQueryClient()
  const [state, dispatch] = useReducer(pdfReducer, initialPdfState)
  const abort = useRef<AbortController | null>(null)
  const stateRef = useRef(state)
  useEffect(() => {
    stateRef.current = state
  }, [state])

  useEffect(() => () => abort.current?.abort(), [])

  const refreshList = useCallback(
    () => queryClient.invalidateQueries({ queryKey: queryKeys.pdfs }),
    [queryClient],
  )

  const finishProcessing = useCallback(async (pdfId: string) => {
    const [pdf, questions] = await Promise.all([getPdf(pdfId), listQuestions(pdfId)])
    dispatch({ type: 'questions', questions, pdf })
  }, [])

  const upload = useCallback(
    async (file: File) => {
      abort.current?.abort()
      const controller = new AbortController()
      abort.current = controller
      dispatch({ type: 'upload-start', filename: file.name })
      try {
        const pdf = await uploadPdf(file, file.name)
        dispatch({ type: 'uploaded', pdf })
        await streamPdfEvents(pdf.id, {
          signal: controller.signal,
          onEvent: (event) => dispatch({ type: 'pdf-event', event }),
        })
        if (controller.signal.aborted) return
        if (stateRef.current.stage === 'processing') await finishProcessing(pdf.id)
        void refreshList()
      } catch (error) {
        dispatch({ type: 'error', error: asError(error) })
      }
    },
    [finishProcessing, refreshList],
  )

  const loadSample = useCallback(
    () => upload(new File([SAMPLE_PDF], 'simulado-java.pdf', { type: 'application/pdf' })),
    [upload],
  )

  const open = useCallback(async (pdf: PdfDocument) => {
    try {
      const questions = await listQuestions(pdf.id)
      dispatch({ type: 'opened', pdf, questions })
    } catch (error) {
      dispatch({ type: 'error', error: asError(error) })
    }
  }, [])

  // Estimativa de tempo: refaz quando entra na revisão ou o tier muda.
  const tier = useUiStore((s) => s.tier)
  const pdfId = state.pdf?.id
  const inReview = state.stage === 'review'
  useEffect(() => {
    if (!inReview || !pdfId) return
    let cancelled = false
    estimateJob(pdfId, tier)
      .then((estimate) => !cancelled && dispatch({ type: 'estimate', estimate }))
      .catch(() => !cancelled && dispatch({ type: 'estimate', estimate: null }))
    return () => {
      cancelled = true
    }
  }, [inReview, pdfId, tier])

  const solve = useCallback(async (chosen: Tier) => {
    const pdf = stateRef.current.pdf
    if (!pdf) return
    const controller = new AbortController()
    abort.current = controller
    try {
      const job = await createJob(pdf.id, {
        tier: chosen,
        javaVersion: useUiStore.getState().javaVersion,
      })
      dispatch({ type: 'job', job })
      await streamJobEvents(job.id, {
        signal: controller.signal,
        onEvent: (event) => dispatch({ type: 'job-event', event }),
      })
      if (controller.signal.aborted) return
      const [questions, finalJob] = await Promise.all([listQuestions(pdf.id), getJob(job.id)])
      dispatch({ type: 'solved', questions, job: finalJob })
    } catch (error) {
      dispatch({ type: 'error', error: asError(error) })
    }
  }, [])

  const control = useCallback(async (action: typeof pauseJob) => {
    const job = stateRef.current.job
    if (!job) return
    try {
      const next = await action(job.id)
      dispatch({ type: 'job-event', event: { type: 'job_status', job: next } })
    } catch (error) {
      dispatch({ type: 'error', error: asError(error) })
    }
  }, [])

  const pause = useCallback(() => control(pauseJob), [control])
  const resume = useCallback(() => control(resumeJob), [control])
  const cancel = useCallback(async () => {
    await control(cancelJob)
    const pdf = stateRef.current.pdf
    if (pdf) dispatch({ type: 'solved', questions: await listQuestions(pdf.id) })
  }, [control])

  const markReviewed = useCallback(async (questionId: string) => {
    const pdf = stateRef.current.pdf
    const question = stateRef.current.questions.find((q) => q.id === questionId)
    if (!pdf || !question) return
    try {
      // Confirmar = salvar a questão como está; o servidor limpa `needsReview` ao editar.
      await updateQuestion(pdf.id, questionId, { statement: question.statement })
      dispatch({
        type: 'questions',
        questions: await listQuestions(pdf.id),
        pdf: await getPdf(pdf.id),
      })
    } catch (error) {
      dispatch({ type: 'error', error: asError(error) })
    }
  }, [])

  const reset = useCallback(() => {
    abort.current?.abort()
    dispatch({ type: 'reset' })
  }, [])

  return { ...state, upload, loadSample, open, solve, pause, resume, cancel, markReviewed, reset }
}
