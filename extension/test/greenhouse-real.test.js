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
  country: 'United States',
  currentCompany: 'Oracle',
  currentTitle: 'Software Engineer',
  school: 'Purdue University',
}

describe('a real Greenhouse form, as Stripe embeds it', () => {
  it('fills the text fields, attaches the resume, and leaves pick-lists and personal questions for the owner', async () => {
    document.documentElement.innerHTML = readFileSync(join(process.cwd(), 'test/fixtures/greenhouse-stripe-apply.html'), 'utf8')
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

    // Saved without scripts, the form's pick-lists never show suggestions: nothing is chosen, and they're listed
    const report = await globalThis.JSA_FILL.fill(
      document,
      profile,
      { base64: btoa('%PDF'), fileName: 'r.pdf', type: 'application/pdf' },
      {},
      { waitMs: 50 },
    )

    const byId = (id) => document.getElementById(id).value
    expect(byId('first_name')).toBe('Jane')
    expect(byId('last_name')).toBe('Doe')
    expect(byId('email')).toBe('jane@example.com')
    expect(byId('phone')).toBe('555-0100')
    const byLabel = (label) => document.querySelector(`[aria-label="${label}"]`).value
    expect(byLabel('Who is your current or previous employer?')).toBe('Oracle')
    expect(byLabel('What is your current or previous job title?')).toBe('Software Engineer')
    expect(byLabel('If located in the US, in what city and state do you reside?')).toBe('Austin, TX')
    expect(report.resume).toBe('attached')
    expect(document.getElementById('cover_letter').files).toHaveLength(0)

    // With no suggestions to pick from, the lists are left empty and listed
    expect(byId('country')).toBe('')
    expect(byId('candidate-location')).toBe('')
    expect(report.toChoose).toEqual(expect.arrayContaining(['Country', 'Location (City)']))
    expect(report.needsYou).toEqual(expect.arrayContaining(['Gender', 'Are you Hispanic/Latino?', 'Veteran Status']))
    expect(report.toChoose).not.toContain('Gender')
  })
})
