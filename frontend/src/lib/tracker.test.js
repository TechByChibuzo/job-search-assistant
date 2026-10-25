import { describe, expect, it } from 'vitest'
import { relativeDay, stageLabel, todayIso } from './tracker'

const now = new Date(2026, 9, 25, 15, 0)

describe('relativeDay', () => {
  it('reads naturally around today', () => {
    expect(relativeDay('2026-10-25', now)).toBe('today')
    expect(relativeDay('2026-10-26', now)).toBe('tomorrow')
    expect(relativeDay('2026-10-24', now)).toBe('yesterday')
    expect(relativeDay('2026-10-30', now)).toBe('in 5 days')
    expect(relativeDay('2026-10-20', now)).toBe('5 days ago')
    expect(relativeDay(null, now)).toBeNull()
  })
})

describe('labels', () => {
  it('names stages and formats today for date inputs', () => {
    expect(stageLabel('REACHING_OUT')).toBe('Reaching out')
    expect(todayIso(now)).toBe('2026-10-25')
  })
})
