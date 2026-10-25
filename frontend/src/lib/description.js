/**
 * Renders a plain-text job description with structure: short lines without end punctuation that start a
 * block ("Responsibilities", "What You'll Do") become headings, and "•" or "-" lines become a list.
 */
export function parseDescription(text) {
  const blocks = []
  for (const raw of text.split('\n')) {
    const line = raw.trim()
    if (!line) {
      blocks.push({ type: 'gap' })
      continue
    }
    const bullet = line.match(/^[•\-*·]\s+(.*)$/)
    if (bullet) {
      const last = blocks.at(-1)
      if (last?.type === 'list') last.items.push(bullet[1])
      else blocks.push({ type: 'list', items: [bullet[1]] })
      continue
    }
    const looksLikeHeading = line.length <= 60 && !/[.,;]$/.test(line) && line.split(' ').length <= 8
    blocks.push({ type: looksLikeHeading ? 'heading' : 'p', text: line.replace(/:$/, '') })
  }
  // A heading needs content after it; otherwise it was a short sentence
  return blocks
    .map((b, i) => {
      if (b.type !== 'heading') return b
      const next = blocks.slice(i + 1).find((n) => n.type !== 'gap')
      return next && next.type !== 'heading' ? b : { type: 'p', text: b.text }
    })
    .filter((b) => b.type !== 'gap')
}
