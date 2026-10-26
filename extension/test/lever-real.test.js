import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeAll, describe, expect, it } from 'vitest'
import { loadContentScript } from './setup.js'

beforeAll(() => loadContentScript('fill.js'))

const profile = {
  firstName: 'Jane',
  lastName: 'Doe',
  email: 'jane@example.com',
  phone: '555-0100',
  city: 'Austin',
  state: 'TX',
  linkedinUrl: 'https://www.linkedin.com/in/jane/',
  githubUrl: 'https://github.com/jane',
  currentCompany: 'Oracle',
}

describe('a real Lever application form', () => {
  it('fills every contact field, attaches the resume, and leaves each personal question for the owner', async () => {
    document.documentElement.innerHTML = readFileSync(join(process.cwd(), 'test/fixtures/lever-apply.html'), 'utf8')
    globalThis.DataTransfer = class {
      constructor() {
        const list = []
        this.items = { add: (f) => list.push(f) }
        this.files = list
      }
    }
    for (const input of document.querySelectorAll('input[type=file]')) {
      let files = []
      Object.defineProperty(input, 'files', {
        get: () => files,
        set: (v) => {
          files = v
        },
      })
    }

    const report = await globalThis.JSA_FILL.fill(document, profile, { base64: btoa('%PDF'), fileName: 'r.pdf', type: 'application/pdf' })

    const value = (name) => document.querySelector(`[name="${name}"]`).value
    expect(value('name')).toBe('Jane Doe')
    expect(value('email')).toBe('jane@example.com')
    expect(value('phone')).toBe('555-0100')
    expect(value('location')).toBe('Austin, TX')
    expect(value('org')).toBe('Oracle')
    expect(value('urls[LinkedIn]')).toBe('https://www.linkedin.com/in/jane/')
    expect(value('urls[GitHub]')).toBe('https://github.com/jane')
    expect(report.resume).toBe('attached')
    expect(report.filled).toEqual(['Full name', 'Email', 'Phone', 'Current location', 'Current company', 'LinkedIn URL', 'GitHub URL'])
    expect(report.needsYou).toContain('Pronouns')
    expect(report.needsYou).toContain('What is your race/ethnicity?:')
    expect(report.needsYou.some((q) => /consent/i.test(q))).toBe(true)
    // No checkbox or choice was ticked
    expect([...document.querySelectorAll('input[type=checkbox]:checked, input[type=radio]:checked')]).toHaveLength(0)
  })
})
