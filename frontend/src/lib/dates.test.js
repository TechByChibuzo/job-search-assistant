import { describe, expect, it } from 'vitest'
import { groupByDay, timeAgo } from './dates'

const now = new Date(2026, 9, 25, 15, 0) // Oct 25 2026, 3pm local

function seen(year, month, day, hour = 9) {
  return { listedAt: new Date(year, month, day, hour).toISOString() }
}

describe('groupByDay', () => {
  it('buckets by local calendar day and drops empty groups', () => {
    const jobs = [seen(2026, 9, 25, 1), seen(2026, 9, 24, 23), seen(2026, 9, 20), seen(2026, 8, 1)]

    const groups = groupByDay(jobs, now)

    expect(groups.map((g) => [g.label, g.jobs.length])).toEqual([
      ['Today', 1],
      ['Yesterday', 1],
      ['This week', 1],
      ['Earlier', 1],
    ])
  })

  it('puts roles of unknown age last', () => {
    const groups = groupByDay([{ listedAt: null }, seen(2026, 9, 25)], now)

    expect(groups.map((g) => g.label)).toEqual(['Today', 'Date unknown'])
  })

  it('keeps the order jobs arrived in within a group', () => {
    const a = { ...seen(2026, 9, 25, 8), id: 'a' }
    const b = { ...seen(2026, 9, 25, 10), id: 'b' }

    expect(groupByDay([b, a], now)[0].jobs.map((j) => j.id)).toEqual(['b', 'a'])
  })
})

describe('timeAgo', () => {
  it('reads naturally at each scale', () => {
    expect(timeAgo(new Date(now.getTime() - 20_000).toISOString(), now)).toBe('just now')
    expect(timeAgo(new Date(now.getTime() - 5 * 60_000).toISOString(), now)).toBe('5 min ago')
    expect(timeAgo(new Date(now.getTime() - 3 * 3_600_000).toISOString(), now)).toBe('3 h ago')
    expect(timeAgo(new Date(now.getTime() - 26 * 3_600_000).toISOString(), now)).toBe('1 day ago')
  })

  it('handles a missing date', () => {
    expect(timeAgo(null, now)).toBeNull()
  })
})

describe('shortAge', () => {
  it('stays compact', async () => {
    const { shortAge } = await import('./dates')
    expect(shortAge(new Date(now.getTime() - 30 * 60_000).toISOString(), now)).toBe('now')
    expect(shortAge(new Date(now.getTime() - 5 * 3_600_000).toISOString(), now)).toBe('5h')
    expect(shortAge(new Date(now.getTime() - 3 * 86_400_000).toISOString(), now)).toBe('3d')
    expect(shortAge(new Date(now.getTime() - 21 * 86_400_000).toISOString(), now)).toBe('3w')
    expect(shortAge(null, now)).toBeNull()
  })
})
