import { useApp } from '@/app/session'
import { UploadIcon } from '@/app/icons'
import { useRecentPdfs } from '@/features/queries'
import { PdfPanel } from '@/ui/PdfPanel'

/** Provas: lista das já enviadas à esquerda e o fluxo (enviar, revisar, resolver, baixar gabarito) à direita. */
export function ExamsScreen() {
  const { t, pdf } = useApp()
  const recents = useRecentPdfs()
  const items = recents.data?.items ?? []
  return (
    <div className="jma-exams">
      <aside className="jma-exams__list jma-panel" aria-label={t('pdf.recent')}>
        <div className="jma-exams__head">
          <h2>{t('exams.list')}</h2>
          <button type="button" className="jma-btn jma-btn--small" onClick={pdf.reset}>
            <UploadIcon />
            {t('exams.new')}
          </button>
        </div>
        {items.length === 0 ? (
          <p className="jma-mute">{t('exams.empty')}</p>
        ) : (
          <ul>
            {items.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className="jma-exams__item"
                  aria-current={pdf.pdf?.id === item.id ? 'true' : undefined}
                  onClick={() => void pdf.open(item)}
                >
                  <strong>{item.filename}</strong>
                  <small className="jma-mute">
                    {t('home.questions', { n: item.questionCount })}
                    {item.needsReviewCount > 0 &&
                      ` · ${t('pdf.flagged', { n: item.needsReviewCount })}`}
                  </small>
                </button>
              </li>
            ))}
          </ul>
        )}
      </aside>
      <div className="jma-exams__main">
        <PdfPanel />
      </div>
    </div>
  )
}
