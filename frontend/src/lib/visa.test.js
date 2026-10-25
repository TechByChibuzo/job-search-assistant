import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { setNeedsSponsorship, useNeedsSponsorship, visaNote } from './visa'

// A fresh in-memory store per test; the test environment's own localStorage isn't reliable
beforeEach(() => {
  const data = new Map()
  vi.stubGlobal('localStorage', {
    getItem: (k) => (data.has(k) ? data.get(k) : null),
    setItem: (k, v) => data.set(k, String(v)),
  })
})

describe('visaNote', () => {
  it('names what the posting says, strictest first, and only definite restrictions exclude', () => {
    expect(visaNote({ visaRestriction: 'CITIZENS_ONLY' })).toEqual({ label: 'Citizens or green card only', excludes: true })
    expect(visaNote({ visaRestriction: 'MAY_BE_RESTRICTED' })).toEqual({ label: 'Check visa terms', excludes: false })
    expect(visaNote({ visaRestriction: null })).toBeNull()
  })

  it('falls back to the owner marking the company, which outranks a mere maybe', () => {
    expect(visaNote({ visaRestriction: null, companyNoSponsorship: true }).label).toBe('Company doesn’t sponsor')
    expect(visaNote({ visaRestriction: 'MAY_BE_RESTRICTED', companyNoSponsorship: true }).excludes).toBe(true)
    expect(visaNote({ visaRestriction: 'CLEARANCE', companyNoSponsorship: true }).label).toBe('Clearance required')
  })
})

describe('the sponsorship preference', () => {
  it('is off by default, remembered, and shared by every component using it', () => {
    const a = renderHook(() => useNeedsSponsorship())
    const b = renderHook(() => useNeedsSponsorship())
    expect(a.result.current).toBe(false)

    act(() => setNeedsSponsorship(true))
    expect(a.result.current).toBe(true)
    expect(b.result.current).toBe(true)
    expect(localStorage.getItem('needsSponsorship')).toBe('true')
  })
})
