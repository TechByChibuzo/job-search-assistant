import { describe, expect, it } from 'vitest'
import { parseDescription } from './description'

describe('parseDescription', () => {
  it('finds headings, paragraphs and lists', () => {
    const text = "We build things.\n\nWhat You'll Do\n• Design systems\n• Ship them\n\nBasic qualifications:\n- 3+ years of Java"

    expect(parseDescription(text)).toEqual([
      { type: 'p', text: 'We build things.' },
      { type: 'heading', text: "What You'll Do" },
      { type: 'list', items: ['Design systems', 'Ship them'] },
      { type: 'heading', text: 'Basic qualifications' },
      { type: 'list', items: ['3+ years of Java'] },
    ])
  })

  it('does not turn a short closing line into a heading', () => {
    expect(parseDescription('Join us\n\nApply today')).toEqual([
      { type: 'p', text: 'Join us' },
      { type: 'p', text: 'Apply today' },
    ])
  })
})
