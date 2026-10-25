import { useEffect } from 'react'

/** j / k move through the list, Escape closes the open role. Ignored while typing in a field. */
export function useKeyboardNav({ ids, selected, onSelect, onClose }) {
  useEffect(() => {
    function onKey(e) {
      const tag = e.target.tagName
      if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || e.metaKey || e.ctrlKey || e.altKey) return
      if (e.key === 'Escape' && selected != null) {
        onClose()
        return
      }
      if (e.key !== 'j' && e.key !== 'k') return
      if (ids.length === 0) return
      const i = ids.indexOf(selected)
      const next = e.key === 'j' ? Math.min(ids.length - 1, i + 1) : Math.max(0, i === -1 ? 0 : i - 1)
      onSelect(ids[next])
      document.getElementById(`job-${ids[next]}`)?.scrollIntoView({ block: 'nearest' })
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [ids, selected, onSelect, onClose])
}
