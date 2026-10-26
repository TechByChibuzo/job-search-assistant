import { useSyncExternalStore } from 'react'

/**
 * A standing on/off preference kept in this browser's storage, not the URL, so it survives clearing filters and
 * moving between pages. Every component using it re-renders when it changes.
 */
export function booleanPreference(key) {
  const listeners = new Set()
  const read = () => {
    try {
      return localStorage.getItem(key) === 'true'
    } catch {
      return false
    }
  }
  const subscribe = (listener) => {
    listeners.add(listener)
    window.addEventListener('storage', listener)
    return () => {
      listeners.delete(listener)
      window.removeEventListener('storage', listener)
    }
  }
  return {
    use: () => useSyncExternalStore(subscribe, read, () => false),
    set: (on) => {
      try {
        localStorage.setItem(key, String(on))
      } catch {
        // Storage blocked: the choice lasts until the page reloads
      }
      listeners.forEach((l) => l())
    },
  }
}
