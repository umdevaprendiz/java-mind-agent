import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { ChevronIcon } from '@/app/icons'
import { Cup } from '@/app/Cup'
import { useApp } from '@/app/session'
import { tierDesc, tierName, tierNote } from './tierText'

/** Botão do nível no topo (e no fluxo de provas): mostra a xícara atual e abre a lista para trocar. */
export function LevelPill() {
  const { t, tiers } = useApp()
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const listId = useId()
  const current = tiers.selectedItem

  useEffect(() => {
    if (!open) return
    const onDown = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onDown)
    return () => document.removeEventListener('pointerdown', onDown)
  }, [open])

  const onKeyDown = (event: KeyboardEvent) => {
    if (event.key === 'Escape') setOpen(false)
  }

  if (tiers.loading || tiers.items.length === 0) {
    return (
      <span className="jma-level" aria-busy="true">
        {t('tier.label')}…
      </span>
    )
  }

  return (
    <div className="jma-levelmenu" ref={root} onKeyDown={onKeyDown}>
      <button
        type="button"
        className="jma-level"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={`${t('tier.label')}: ${current ? tierName(t, current.id) : ''}`}
        onClick={() => setOpen((o) => !o)}
      >
        <Cup tier={current?.id ?? tiers.selected} size={24} />
        <span>{tierName(t, current?.id ?? tiers.selected)}</span>
        <ChevronIcon />
      </button>
      {open && (
        <div className="jma-levelmenu__pop jma-pop">
          <ul id={listId} role="listbox" aria-label={t('tier.label')}>
            {tiers.items.map((item) => (
              <li
                key={item.id}
                role="option"
                aria-selected={item.id === tiers.selected}
                aria-disabled={!item.selectable}
                className="jma-levelmenu__option"
                data-selected={item.id === tiers.selected}
                data-selectable={item.selectable}
                tabIndex={item.selectable ? 0 : -1}
                onClick={() => {
                  if (!item.selectable) return
                  tiers.select(item.id)
                  setOpen(false)
                }}
                onKeyDown={(e) => {
                  if ((e.key === 'Enter' || e.key === ' ') && item.selectable) {
                    e.preventDefault()
                    tiers.select(item.id)
                    setOpen(false)
                  }
                }}
              >
                <Cup tier={item.id} size={32} dashed={item.status === 'no-fit'} />
                <span className="jma-levelmenu__text">
                  <strong>{tierName(t, item.id)}</strong>
                  <small>{item.selectable ? tierDesc(t, item.id) : tierNote(t, item)}</small>
                </span>
              </li>
            ))}
          </ul>
          <Link className="jma-levelmenu__more" to="/niveis" onClick={() => setOpen(false)}>
            {t('tier.screen.link')}
          </Link>
        </div>
      )}
    </div>
  )
}
