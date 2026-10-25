import { useSyncExternalStore } from 'react'

/*
 * Whether the owner needs visa sponsorship, so the inbox hides roles closed to them. It's a standing preference
 * rather than a one-off filter, so it lives in this browser's storage, not the URL, and survives clearing filters.
 */
const KEY = 'needsSponsorship'
const listeners = new Set()

function read() {
  try {
    return localStorage.getItem(KEY) === 'true'
  } catch {
    return false
  }
}

function subscribe(listener) {
  listeners.add(listener)
  window.addEventListener('storage', listener)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', listener)
  }
}

export function useNeedsSponsorship() {
  return useSyncExternalStore(subscribe, read, () => false)
}

export function setNeedsSponsorship(on) {
  try {
    localStorage.setItem(KEY, String(on))
  } catch {
    // Storage blocked: the choice lasts until the page reloads
  }
  listeners.forEach((l) => l())
}

const RESTRICTIONS = {
  CITIZENS_ONLY: { label: 'Citizens or green card only', excludes: true },
  CLEARANCE: { label: 'Clearance required', excludes: true },
  NO_SPONSORSHIP: { label: 'No sponsorship', excludes: true },
  MAY_BE_RESTRICTED: { label: 'Check visa terms', excludes: false },
}

/** What to show about a role's visa terms, or null when nothing is known against it. */
export function visaNote(job) {
  if (job.visaRestriction && RESTRICTIONS[job.visaRestriction].excludes) return RESTRICTIONS[job.visaRestriction]
  if (job.companyNoSponsorship) return { label: 'Company doesn’t sponsor', excludes: true }
  return job.visaRestriction ? RESTRICTIONS[job.visaRestriction] : null
}
