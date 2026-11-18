import { useEffect, useRef, useState, type DragEvent } from 'react'
import { exportPdfUrl } from '@/api/endpoints'
import type { Answer, Question } from '@/api/types'
import { ChevronIcon, DownloadIcon, UploadIcon } from '@/app/icons'
import { useApp } from '@/app/session'
import { errorText, formatDuration, type MessageKey } from '@/i18n'
import { useUiStore } from '@/stores/ui'
import { LevelPill } from './LevelPill'
import { CodeBlock } from './Markdown'

const fmtPercent = (n: number) => Math.round(n * 100)

function AnswerView({ answer, question }: { answer: Answer; question: Question }) {
  const { t } = useApp()
  if (answer.status === 'FAILED') {
    return (
      <div className="jma-alert jma-alert--error" role="alert">
        <strong>{t('pdf.failed')}:</strong> {answer.errorMessage ?? t('error.generic')}
      </div>
    )
  }
  const chosen = question.alternatives?.find((a) => a.label === answer.chosenAlternative)
  return (
    <div className="jma-answer" data-verified={answer.verifiedByExecution}>
      <div className="jma-answer__head">
        {answer.chosenAlternative && (
          <p className="jma-answer__chosen">
            <span className="jma-answer__label">{t('pdf.answer')}</span>
            <strong>{answer.chosenAlternative.toUpperCase()}</strong>
            {chosen && <span>{chosen.text}</span>}
          </p>
        )}
        <div className="jma-answer__badges">
          {answer.verifiedByExecution && (
            <span className="jma-badge jma-badge--ok">✓ {t('pdf.verified')}</span>
          )}
          {answer.confidence != null && (
            <span
              className="jma-confidence"
              title={t('pdf.confidence', { n: fmtPercent(answer.confidence) })}
            >
              <span className="jma-bar" aria-hidden="true">
                <span style={{ width: `${fmtPercent(answer.confidence)}%` }} />
              </span>
              {t('pdf.confidence', { n: fmtPercent(answer.confidence) })}
            </span>
          )}
        </div>
      </div>
      {answer.explanation && (
        <p className="jma-answer__text">
          <span className="jma-answer__label">{t('pdf.explanation')}</span> {answer.explanation}
        </p>
      )}
      {answer.steps && answer.steps.length > 0 && (
        <ol className="jma-answer__steps" aria-label={t('pdf.steps')}>
          {answer.steps.map((step, i) => (
            <li key={i}>{step}</li>
          ))}
        </ol>
      )}
      {answer.executionOutput && (
        <p className="jma-answer__output">
          <span className="jma-answer__label">{t('pdf.output')}</span>{' '}
          <code>{answer.executionOutput}</code>
        </p>
      )}
      {answer.sources && answer.sources.length > 0 && (
        <ul className="jma-sources" aria-label={t('pdf.sources')}>
          {answer.sources.map((s) => (
            <li key={s.title}>
              <a href={s.url ?? undefined} target="_blank" rel="noreferrer noopener">
                {s.kind && <span className="jma-sources__kind">{s.kind}</span>}
                {s.title}
              </a>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function QuestionCard({ question }: { question: Question }) {
  const { t, pdf } = useApp()
  const answer = question.latestAnswer
  const failed = pdf.failed[question.id]
  const current = pdf.currentQuestionId === question.id
  const state = current
    ? 'current'
    : failed || answer?.status === 'FAILED'
      ? 'failed'
      : answer
        ? 'answered'
        : 'pending'
  const reviewing = pdf.stage === 'review'
  return (
    <article
      className="jma-q"
      data-state={state}
      data-review={question.needsReview}
      data-type={question.type}
    >
      <header className="jma-q__head">
        <span className="jma-q__num">
          {t('pdf.question', { n: question.label ?? question.ordinal })}
        </span>
        <span className="jma-q__type">{t(`pdf.type.${question.type}` as MessageKey)}</span>
        {question.pageNumber && (
          <span className="jma-q__page">{t('pdf.page', { n: question.pageNumber })}</span>
        )}
        {question.javaVersion && <span className="jma-q__java">Java {question.javaVersion}</span>}
        {question.needsReview && (
          <span className="jma-badge jma-badge--warn">{t('pdf.needsReview')}</span>
        )}
        {current && <span className="jma-badge jma-badge--accent">{t('pdf.solving')}…</span>}
      </header>
      <p className="jma-q__statement">{question.statement.replaceAll('`', '')}</p>
      {question.code && <CodeBlock code={question.code} lang="java" />}
      {question.alternatives && question.alternatives.length > 0 && (
        <ol className="jma-alts">
          {question.alternatives.map((alt) => (
            <li
              key={alt.label}
              data-chosen={answer?.status === 'COMPLETED' && answer.chosenAlternative === alt.label}
            >
              <b>{alt.label}</b> {alt.text}
            </li>
          ))}
        </ol>
      )}
      {answer && <AnswerView answer={answer} question={question} />}
      {!answer && failed && (
        <div className="jma-alert jma-alert--error" role="alert">
          {errorText(t, failed.code, failed.message)}
        </div>
      )}
      {reviewing && question.needsReview && (
        <button
          type="button"
          className="jma-btn jma-btn--small jma-btn--ghost"
          onClick={() => void pdf.markReviewed(question.id)}
        >
          {t('pdf.markReviewed')}
        </button>
      )}
    </article>
  )
}

function Dropzone() {
  const { t, pdf } = useApp()
  const input = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)
  const take = (file?: File) => file && void pdf.upload(file)
  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setOver(false)
    take(event.dataTransfer.files[0])
  }
  return (
    <div className="jma-pdf__idle">
      <label
        className="jma-drop"
        data-over={over}
        onDragOver={(e) => {
          e.preventDefault()
          setOver(true)
        }}
        onDragLeave={() => setOver(false)}
        onDrop={onDrop}
      >
        <input
          ref={input}
          className="jma-sr"
          type="file"
          accept="application/pdf,.pdf"
          aria-label={t('pdf.drop')}
          onChange={(e) => take(e.target.files?.[0])}
        />
        <span className="jma-drop__icon" aria-hidden="true">
          <UploadIcon />
        </span>
        <span className="jma-drop__text">{over ? t('pdf.dropActive') : t('pdf.drop')}</span>
        <small>.pdf</small>
      </label>
      <div className="jma-pdf__actions">
        <button
          type="button"
          className="jma-btn jma-btn--ghost"
          onClick={() => void pdf.loadSample()}
        >
          {t('pdf.sample')}
        </button>
      </div>
    </div>
  )
}

function Processing() {
  const { t, pdf } = useApp()
  const { stage, extraction } = pdf
  const percent = extraction.pagesTotal
    ? (extraction.pagesDone / extraction.pagesTotal) * 100
    : null
  const step = stage === 'uploading' ? 0 : extraction.status === 'SEGMENTING' ? 2 : 1
  const steps: MessageKey[] = ['pdf.uploading', 'pdf.extracting', 'pdf.segmenting']
  return (
    <div className="jma-pdf__processing" role="status">
      <h3 className="jma-pdf__file">{pdf.filename}</h3>
      <ol className="jma-steps">
        {steps.map((key, index) => (
          <li key={key} data-state={index < step ? 'done' : index === step ? 'active' : 'todo'}>
            {t(key)}
          </li>
        ))}
      </ol>
      {step === 1 && (
        <div className="jma-pull">
          <div
            className="jma-bar"
            role="progressbar"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={percent ?? undefined}
            aria-label={t('pdf.extracting')}
          >
            <span style={{ width: `${percent ?? 8}%` }} />
          </div>
          <span className="jma-pull__text">
            {extraction.pagesTotal > 0 &&
              t('pdf.pages', { done: extraction.pagesDone, total: extraction.pagesTotal })}
            {extraction.message && ` · ${extraction.message}`}
          </span>
        </div>
      )}
    </div>
  )
}

function Review() {
  const { t, pdf, tiers } = useApp()
  const language = useUiStore((s) => s.language)
  const flagged = pdf.questions.filter((q) => q.needsReview).length
  const estimate = pdf.estimate
  return (
    <div className="jma-pdf__review">
      <header className="jma-pdf__bar">
        <div>
          <h3 className="jma-pdf__file">{pdf.filename}</h3>
          <p>
            {t('home.questions', { n: pdf.questions.length })} ·{' '}
            {flagged > 0 ? t('pdf.flagged', { n: flagged }) : t('pdf.noneFlagged')}
          </p>
        </div>
        <div className="jma-pdf__solve">
          <LevelPill />
          {estimate?.estimatedSeconds != null && (
            <p className="jma-pdf__estimate">
              {t('pdf.estimate', {
                time: formatDuration(estimate.estimatedSeconds, language),
                tier: t(`tier.${tiers.selected}` as MessageKey),
              })}
              <small> — {t(`pdf.estimateBasis.${estimate.basis}` as MessageKey)}</small>
            </p>
          )}
          <button
            type="button"
            className="jma-btn jma-btn--primary"
            onClick={() => void pdf.solve(tiers.selected)}
          >
            {t('pdf.solve', { n: pdf.questions.length })}
          </button>
        </div>
      </header>
      <p className="jma-note">{t('pdf.reviewHint')}</p>
      <div className="jma-pdf__list">
        {pdf.questions.map((q) => (
          <QuestionCard key={q.id} question={q} />
        ))}
      </div>
    </div>
  )
}

function Solving() {
  const { t, pdf } = useApp()
  const job = pdf.job
  const done = job?.completedQuestions ?? 0
  const total = job?.totalQuestions ?? pdf.questions.length
  const paused = job?.status === 'PAUSED'
  return (
    <div className="jma-pdf__solving">
      <header className="jma-pdf__bar">
        <div>
          <h3 className="jma-pdf__file">{pdf.filename}</h3>
          <p role="status">{t('pdf.progress', { done, total })}</p>
        </div>
        <div className="jma-pdf__controls">
          {paused ? (
            <button
              type="button"
              className="jma-btn jma-btn--small"
              onClick={() => void pdf.resume()}
            >
              {t('pdf.resume')}
            </button>
          ) : (
            <button
              type="button"
              className="jma-btn jma-btn--small"
              onClick={() => void pdf.pause()}
            >
              {t('pdf.pause')}
            </button>
          )}
          <button
            type="button"
            className="jma-btn jma-btn--small jma-btn--ghost"
            onClick={() => void pdf.cancel()}
          >
            {t('pdf.cancel')}
          </button>
        </div>
      </header>
      <div
        className="jma-bar jma-bar--big"
        role="progressbar"
        aria-label={t('pdf.solving')}
        aria-valuemin={0}
        aria-valuemax={total}
        aria-valuenow={done}
      >
        <span style={{ width: `${total ? (done / total) * 100 : 0}%` }} />
      </div>
      <div className="jma-pdf__list">
        {pdf.questions.map((q) => (
          <QuestionCard key={q.id} question={q} />
        ))}
      </div>
    </div>
  )
}

const FORMATS = [
  ['md', 'Markdown', '.md', 'export.md.desc'],
  ['pdf', 'PDF', '.pdf', 'export.pdf.desc'],
  ['docx', 'Word', '.docx', 'export.docx.desc'],
] as const

/** "Baixar gabarito": Markdown, PDF ou Word, direto do endpoint de exportação do lote. */
function ExportMenu() {
  const { t, pdf } = useApp()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onDown = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('pointerdown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])
  if (!pdf.pdf) return null
  const pdfId = pdf.pdf.id
  return (
    <div className="jma-export" ref={root}>
      <button
        type="button"
        className="jma-btn jma-btn--primary"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <DownloadIcon />
        {t('pdf.export')}
        <ChevronIcon />
      </button>
      {open && (
        <div className="jma-export__pop jma-pop" role="menu">
          {FORMATS.map(([format, label, ext, desc]) => (
            <a
              key={format}
              role="menuitem"
              className="jma-export__item"
              href={exportPdfUrl(pdfId, format, pdf.job?.id)}
              download
              onClick={() => setOpen(false)}
            >
              <DownloadIcon />
              <span>
                <strong>
                  {label} <code>{ext}</code>
                </strong>
                <small>{t(desc)}</small>
              </span>
            </a>
          ))}
        </div>
      )}
    </div>
  )
}

function Results() {
  const { t, pdf } = useApp()
  const answers = pdf.questions.flatMap((q) => (q.latestAnswer ? [q.latestAnswer] : []))
  const failed = answers.filter((a) => a.status === 'FAILED').length
  const verified = answers.filter((a) => a.verifiedByExecution).length
  return (
    <div className="jma-pdf__results">
      <header className="jma-pdf__bar">
        <div>
          <h3 className="jma-pdf__file">
            {t('pdf.results')} · {pdf.filename}
          </h3>
          <p>{t('pdf.summary', { answered: answers.length - failed, failed, verified })}</p>
        </div>
        <div className="jma-pdf__controls">
          <ExportMenu />
          <button type="button" className="jma-btn jma-btn--ghost" onClick={pdf.reset}>
            {t('pdf.reset')}
          </button>
        </div>
      </header>
      <div className="jma-pdf__list">
        {pdf.questions.map((q) => (
          <QuestionCard key={q.id} question={q} />
        ))}
      </div>
    </div>
  )
}

/** Fluxo completo de PDF: enviar → ler → revisar → resolver → gabarito. O nível vem do cabeçalho do app. */
export function PdfPanel({ className = '' }: { className?: string }) {
  const { t, pdf } = useApp()
  return (
    <section className={`jma-pdf ${className}`} data-stage={pdf.stage} aria-label={t('nav.pdf')}>
      <header className="jma-pdf__head">
        <h2>{t('pdf.title')}</h2>
        <p>{t('pdf.subtitle')}</p>
      </header>
      {pdf.error && (
        <div className="jma-alert jma-alert--error" role="alert">
          {errorText(t, pdf.error.code, pdf.error.message)}
        </div>
      )}
      {pdf.stage === 'idle' && <Dropzone />}
      {(pdf.stage === 'uploading' || pdf.stage === 'processing') && <Processing />}
      {pdf.stage === 'review' && <Review />}
      {pdf.stage === 'solving' && <Solving />}
      {pdf.stage === 'results' && <Results />}
    </section>
  )
}
