/** Text without the **bold** marks, which are layout rather than wording. */
export function plain(text) {
  return String(text ?? '').replaceAll('**', '')
}

/**
 * A word-by-word diff of two sentences, as runs of words that stayed, were added or were removed, in reading
 * order (removals before additions where both happen). Longest common subsequence: bullets are short.
 */
export function diffWords(before, after) {
  const a = String(before ?? '').split(/\s+/).filter(Boolean)
  const b = String(after ?? '').split(/\s+/).filter(Boolean)
  const lcs = Array.from({ length: a.length + 1 }, () => new Array(b.length + 1).fill(0))
  for (let i = a.length - 1; i >= 0; i--) {
    for (let j = b.length - 1; j >= 0; j--) {
      lcs[i][j] = a[i] === b[j] ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1])
    }
  }
  const ops = []
  const push = (type, word) => {
    const last = ops[ops.length - 1]
    if (last?.type === type) last.text += ` ${word}`
    else ops.push({ type, text: word })
  }
  let i = 0
  let j = 0
  while (i < a.length || j < b.length) {
    if (i < a.length && j < b.length && a[i] === b[j]) {
      push('same', a[i++])
      j++
    } else if (i < a.length && (j === b.length || lcs[i + 1][j] >= lcs[i][j + 1])) {
      push('del', a[i++])
    } else {
      push('add', b[j++])
    }
  }
  return ops
}
