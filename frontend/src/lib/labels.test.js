import { describe, expect, it } from 'vitest'
import { summariseSync } from './labels'

const ok = (name, newJobs) => ({ companyName: name, status: 'OK', newJobs })

describe('summariseSync', () => {
  it('counts new roles across companies', () => {
    expect(summariseSync([ok('Airbnb', 3), ok('Spotify', 1)])).toBe('4 new roles')
    expect(summariseSync([ok('Airbnb', 1)])).toBe('1 new role')
  })

  it('names the boards that need attention', () => {
    const results = [ok('Airbnb', 0), { companyName: 'Plaid', status: 'BOARD_NOT_FOUND', newJobs: 0 }]
    expect(summariseSync(results)).toBe('No new roles; check Plaid')
  })
})
