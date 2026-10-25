import { describe, expect, it } from 'vitest'
import { toQuery } from './client'

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
