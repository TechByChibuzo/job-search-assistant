import { describe, expect, it } from 'vitest'
import { boardLabel, boardUrl, splitSearch, summariseSync } from './labels'

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

describe('boards', () => {
  it('links each source to its public careers page', () => {
    expect(boardUrl('WORKDAY', 'nvidia.wd5/NVIDIAExternalCareerSite')).toBe(
      'https://nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite',
    )
    expect(boardUrl('AVATURE', 'bloomberg/careers')).toBe('https://bloomberg.avature.net/careers')
    expect(boardUrl('LEVER', 'palantir')).toBe('https://jobs.lever.co/palantir')
  })

  it('labels boards the way people recognise them', () => {
    expect(boardLabel('WORKDAY', 'nvidia.wd5/NVIDIAExternalCareerSite')).toBe('NVIDIAExternalCareerSite')
    expect(boardLabel('AMAZON', 'amazon')).toBeNull()
    expect(boardLabel('LEVER', 'palantir')).toBe('palantir')
  })
})

describe('splitSearch', () => {
  it('reads comma-separated phrases', () => {
    expect(splitSearch(' software engineer, backend ,, ')).toEqual(['software engineer', 'backend'])
    expect(splitSearch(null)).toEqual([])
  })
})
