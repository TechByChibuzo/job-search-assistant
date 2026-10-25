import { describe, expect, it } from 'vitest'
import { safeHref } from './links'

describe('safeHref', () => {
  it('keeps web links', () => {
    expect(safeHref('https://www.linkedin.com/in/ada')).toBe('https://www.linkedin.com/in/ada')
    expect(safeHref('HTTP://example.com')).toBe('HTTP://example.com')
  })

  it('drops anything that could run code or is not absolute', () => {
    expect(safeHref('javascript:alert(1)')).toBeNull()
    expect(safeHref(' JavaScript:alert(1)')).toBeNull()
    expect(safeHref('data:text/html,hi')).toBeNull()
    expect(safeHref('www.linkedin.com/in/ada')).toBeNull()
    expect(safeHref(null)).toBeNull()
  })
})
