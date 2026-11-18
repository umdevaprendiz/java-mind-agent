import { Fragment, useState, type ReactNode } from 'react'
import { useApp } from '@/app/session'

/**
 * Markdown mínimo para as respostas do agente (parágrafos, listas, `código`, **negrito**, *itálico*,
 * links http(s) e blocos ```cercados```). Só gera elementos React: nada de HTML cru. A renderização
 * completa (tabelas, destaque de sintaxe) é da F35.
 */
type Block =
  | { kind: 'code'; lang: string; code: string }
  | { kind: 'p'; text: string }
  | { kind: 'ul' | 'ol'; items: string[] }

function parseBlocks(source: string): Block[] {
  const blocks: Block[] = []
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    const fence = /^```(\w*)\s*$/.exec(line)
    if (fence) {
      const code: string[] = []
      i++
      // Bloco ainda sem a cerca final (resposta em streaming): vale até o fim do texto.
      while (i < lines.length && !/^```\s*$/.test(lines[i])) code.push(lines[i++])
      i++
      blocks.push({ kind: 'code', lang: fence[1], code: code.join('\n') })
      continue
    }
    if (/^\s*[-*]\s+/.test(line) || /^\s*\d+\.\s+/.test(line)) {
      const ordered = /^\s*\d+\.\s+/.test(line)
      const pattern = ordered ? /^\s*\d+\.\s+(.*)$/ : /^\s*[-*]\s+(.*)$/
      const items: string[] = []
      while (i < lines.length && pattern.test(lines[i])) items.push(pattern.exec(lines[i++])![1])
      blocks.push({ kind: ordered ? 'ol' : 'ul', items })
      continue
    }
    if (line.trim() === '') {
      i++
      continue
    }
    const paragraph: string[] = []
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      !/^```/.test(lines[i]) &&
      !/^\s*[-*]\s+/.test(lines[i]) &&
      !/^\s*\d+\.\s+/.test(lines[i])
    ) {
      paragraph.push(lines[i++])
    }
    blocks.push({ kind: 'p', text: paragraph.join(' ') })
  }
  return blocks
}

const INLINE = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*\s][^*]*\*|\[[^\]]+\]\([^)\s]+\))/g

function renderInline(text: string): ReactNode[] {
  return text.split(INLINE).map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={index}>{renderInline(part.slice(2, -2))}</strong>
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return <code key={index}>{part.slice(1, -1)}</code>
    }
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
      return <em key={index}>{part.slice(1, -1)}</em>
    }
    const link = /^\[([^\]]+)\]\(([^)\s]+)\)$/.exec(part)
    if (link) {
      return /^https?:\/\//.test(link[2]) ? (
        <a key={index} href={link[2]} target="_blank" rel="noreferrer noopener">
          {link[1]}
        </a>
      ) : (
        <Fragment key={index}>{link[1]}</Fragment>
      )
    }
    return <Fragment key={index}>{part}</Fragment>
  })
}

export function CodeBlock({ code, lang }: { code: string; lang?: string }) {
  const { t } = useApp()
  const [copied, setCopied] = useState(false)
  const copy = () => {
    void navigator.clipboard?.writeText(code).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }
  return (
    <figure className="jma-code" data-lang={lang || 'java'}>
      <figcaption className="jma-code__bar">
        <span className="jma-code__lang">{lang || 'java'}</span>
        <button type="button" className="jma-code__copy" onClick={copy}>
          {copied ? t('chat.copied') : t('chat.copy')}
        </button>
      </figcaption>
      <pre>
        <code>{code}</code>
      </pre>
    </figure>
  )
}

export function Markdown({ source }: { source: string }) {
  return (
    <div className="jma-md">
      {parseBlocks(source).map((block, index) => {
        if (block.kind === 'code')
          return <CodeBlock key={index} code={block.code} lang={block.lang} />
        if (block.kind === 'p') return <p key={index}>{renderInline(block.text)}</p>
        const List = block.kind
        return (
          <List key={index}>
            {block.items.map((item, i) => (
              <li key={i}>{renderInline(item)}</li>
            ))}
          </List>
        )
      })}
    </div>
  )
}
