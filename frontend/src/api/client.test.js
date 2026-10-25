import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, toQuery } from './client'

describe('toQuery', () => {
  it('skips empty values and repeats arrays', () => {
    expect(toQuery({ q: 'backend', location: '', newOnly: false, workplace: ['REMOTE', 'HYBRID'], page: 0 })).toBe(
      '?q=backend&workplace=REMOTE&workplace=HYBRID&page=0',
    )
  })

  it('returns nothing when there is nothing to send', () => {
    expect(toQuery({ q: '', companyId: null })).toBe('')
  })
})

describe('api', () => {
  afterEach(() => vi.restoreAllMocks())

  it('marks every change as coming from the app, and leaves reads plain', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 200 }))

    await api('/sync', { method: 'POST' })
    await api('/jobs')

    expect(fetch.mock.calls[0][1].headers).toEqual({ 'X-Requested-With': 'job-search-assistant' })
    expect(fetch.mock.calls[1][1].headers).toEqual({})
  })
})
