import { useSyncExternalStore } from 'react'

/*
 * Roles the owner has opened, so the inbox highlights only new roles they haven't looked at yet, like unread
 * mail. Kept in this browser; capped so it can't grow without end.
 */
const KEY = 'seenJobs'
const LIMIT = 3000
const listeners = new Set()
let seen = null
let version = 0

function load() {
  if (seen) return seen
  try {
    seen = new Set(JSON.parse(localStorage.getItem(KEY) ?? '[]'))
  } catch {
    seen = new Set()
  }
  return seen
}

function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The set of opened role ids; components re-render when it changes. */
export function useSeenJobs() {
  useSyncExternalStore(subscribe, () => version, () => 0)
  return load()
}

export function markSeen(id) {
  const ids = load()
  if (id == null || ids.has(id)) return
  ids.add(id)
  if (ids.size > LIMIT) seen = new Set([...ids].slice(-LIMIT))
  try {
    localStorage.setItem(KEY, JSON.stringify([...seen]))
  } catch {
    // Storage blocked: remembered until the page reloads
  }
  version++
  listeners.forEach((l) => l())
}

/** For tests: forget everything, including what's cached in memory. */
export function resetSeen() {
  seen = null
  version++
}
