import { describe, expect, it } from 'vitest'
import { seniorityNote } from './experience'

describe('seniorityNote', () => {
  it('names the years asked for when there are too many, else a senior title, else nothing', () => {
    expect(seniorityNote({ minYears: 8, seniorTitle: false })).toEqual({ label: '8+ years' })
    expect(seniorityNote({ minYears: 8, seniorTitle: true })).toEqual({ label: '8+ years' })
    expect(seniorityNote({ minYears: null, seniorTitle: true })).toEqual({ label: 'Senior level' })
    expect(seniorityNote({ minYears: 3, seniorTitle: false })).toBeNull()
    expect(seniorityNote({ minYears: null, seniorTitle: false })).toBeNull()
  })
})
