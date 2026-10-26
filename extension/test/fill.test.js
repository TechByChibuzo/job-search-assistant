import { beforeAll, beforeEach, describe, expect, it } from 'vitest'
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
  school: 'Purdue University',
}
const resume = { base64: btoa('%PDF-1.4'), fileName: 'Jane_Doe_Resume.pdf', type: 'application/pdf' }

// jsdom has no DataTransfer; a minimal one, enough for the filler to hand a File to an input
class FakeDataTransfer {
  constructor() {
    const list = []
    this.items = { add: (f) => list.push(f) }
    this.files = list
  }
}

beforeEach(() => {
  globalThis.DataTransfer = FakeDataTransfer
  document.body.innerHTML = ''
})

function form(html) {
  document.body.innerHTML = html
  // jsdom's HTMLInputElement.files is read-only; let the filler assign it as Chrome allows
  for (const input of document.querySelectorAll('input[type=file]')) {
    let files = []
    Object.defineProperty(input, 'files', {
      get: () => files,
      set: (v) => {
        files = v
      },
    })
  }
  return globalThis.JSA_FILL.fill(document, profile, resume)
}

describe('filling a Greenhouse-style form', () => {
  it('fills contact fields and links, attaches the resume, and leaves sensitive questions alone', () => {
    const report = form(`
      <label for="first_name">First Name *</label><input id="first_name" name="first_name">
      <label for="last_name">Last Name *</label><input id="last_name" name="last_name">
      <label for="email">Email *</label><input id="email" type="text" name="email">
      <label for="phone">Phone</label><input id="phone" name="phone">
      <label for="q1">LinkedIn Profile</label><input id="q1" name="job_application[answers][0][text_value]">
      <label for="resume">Resume/CV</label><input id="resume" type="file">
      <label for="q2">Are you legally authorized to work in the United States?</label>
      <select id="q2"><option>Yes</option><option>No</option></select>
      <label for="q3">Will you now or in the future require sponsorship?</label><input id="q3">
      <label for="cover">Cover Letter</label><textarea id="cover"></textarea>`)

    expect(document.getElementById('first_name').value).toBe('Jane')
    expect(document.getElementById('last_name').value).toBe('Doe')
    expect(document.getElementById('email').value).toBe('jane@example.com')
    expect(document.getElementById('q1').value).toBe('https://www.linkedin.com/in/jane/')
    expect(document.getElementById('q3').value).toBe('')
    expect(document.getElementById('cover').value).toBe('')
    expect(document.getElementById('resume').files[0].name).toBe('Jane_Doe_Resume.pdf')
    expect(report.resume).toBe('attached')
    expect(report.filled).toHaveLength(5)
    expect(report.needsYou).toEqual([
      'Are you legally authorized to work in the United States?',
      'Will you now or in the future require sponsorship?',
    ])
  })
})

describe('filling a Lever-style form', () => {
  it('uses the full name and the company and link fields', () => {
    form(`
      <label>Full name<input name="name"></label>
      <label>Current company<input name="org"></label>
      <label>GitHub URL<input name="urls[GitHub]"></label>
      <label>Gender<select name="eeo[gender]"><option></option></select></label>`)

    expect(document.querySelector('[name=name]').value).toBe('Jane Doe')
    expect(document.querySelector('[name=org]').value).toBe('Oracle')
    expect(document.querySelector('[name="urls[GitHub]"]').value).toBe('https://github.com/jane')
  })
})

describe('filling a Workday-style form', () => {
  it('reads questions from data-automation-id and fires the events frameworks listen for', () => {
    document.body.innerHTML = `
      <div data-automation-id="formField-legalNameSection_firstName">
        <label data-automation-id="formLabel">Given Name(s)</label>
        <input data-automation-id="legalNameSection_firstName">
      </div>
      <div data-automation-id="formField-addressSection_city">
        <label data-automation-id="formLabel">City</label>
        <input data-automation-id="addressSection_city">
      </div>`
    const events = []
    document.querySelector('[data-automation-id=legalNameSection_firstName]').addEventListener('input', () => events.push('input'))

    const report = globalThis.JSA_FILL.fill(document, profile, null)

    expect(document.querySelector('[data-automation-id=legalNameSection_firstName]').value).toBe('Jane')
    expect(document.querySelector('[data-automation-id=addressSection_city]').value).toBe('Austin')
    expect(events).toEqual(['input'])
    expect(report.resume).toBe('no-upload-field')
  })
})

describe('what it never does', () => {
  it('never overwrites an answer, never fills choices, and never touches EEO questions', () => {
    const report = form(`
      <label for="email">Email</label><input id="email" value="mine@example.com">
      <label for="race">Race / Ethnicity</label><input id="race">
      <label for="vet">Veteran status</label><select id="vet"><option>Prefer not to say</option></select>
      <label><input type="checkbox" name="consent"> I consent to the privacy policy</label>
      <label for="hear">How did you hear about us?</label><select id="hear"><option></option></select>`)

    expect(document.getElementById('email').value).toBe('mine@example.com')
    expect(document.getElementById('race').value).toBe('')
    expect(report.filled).toEqual([])
    expect(report.needsYou).toEqual(['Race / Ethnicity', 'Veteran status', 'I consent to the privacy policy'])
  })

  it('lists a choice question once, by its question, even when its options look harmless', () => {
    const report = form(`
      <ul><li class="application-question">
        <div class="application-label multiple-select">Pronouns</div>
        <div class="application-field"><ul>
          <li><label><input type="checkbox" name="pronouns" value="She/her"><span>She/her</span></label></li>
          <li><label><input type="checkbox" name="pronouns" value="They/them"><span>They/them</span></label></li>
        </ul></div>
      </li></ul>`)

    expect(report.needsYou).toEqual(['Pronouns'])
  })

  it('never fills fields the owner cannot see', () => {
    const report = form(`
      <label for="email">Email</label><input id="email">
      <label for="e2">Email</label><input id="e2" style="opacity: 0">
      <div style="opacity: 0"><label for="e3">Phone</label><input id="e3"></div>
      <label for="e4">Phone</label><input id="e4" data-rect="10,10,1,1">
      <label for="e5">Phone</label><input id="e5" data-rect="-5000,10,200,24">
      <div style="visibility: hidden"><label for="e6">LinkedIn</label><input id="e6"></div>`)

    expect(document.getElementById('email').value).toBe('jane@example.com')
    for (const id of ['e2', 'e3', 'e4', 'e5', 'e6']) expect(document.getElementById(id).value).toBe('')
    expect(report.filled).toEqual(['Email'])
  })

  it('leaves the resume to the owner when told not to attach it', () => {
    document.body.innerHTML = '<label for="r">Resume</label><input id="r" type="file">'

    const report = globalThis.JSA_FILL.fill(document, profile, null)

    expect(report.resume).toBe('not-attached')
  })

  it('does not mistake a cover letter upload for the resume', () => {
    const report = form('<label for="cl">Cover letter</label><input id="cl" type="file">')

    expect(report.resume).toBe('not-found')
    expect(document.getElementById('cl').files).toHaveLength(0)
  })
})

describe('answers from the bank', () => {
  it('lists the long-answer questions it may fill, and leaves sensitive ones out', () => {
    form(`
      <label for="why">Why do you want to work here?</label><textarea id="why"></textarea>
      <label for="auth">Describe your work authorization status</label><textarea id="auth"></textarea>
      <label for="done">Anything else?</label><textarea id="done">Already written</textarea>`)
    expect(globalThis.JSA_FILL.questions(document)).toEqual(['Why do you want to work here?'])
  })

  it('fills a matched answer, outlined in blue, and never one into a sensitive or written box', () => {
    form(`
      <label for="why">Why do you want to work here?</label><textarea id="why"></textarea>
      <label for="auth">Describe your work authorization status</label><textarea id="auth"></textarea>
      <label for="other">Tell us about a project</label><textarea id="other"></textarea>`)
    const answers = {
      'Why do you want to work here?': { answerId: 4, answer: 'I like the product.' },
      'Describe your work authorization status': { answerId: 5, answer: 'Should never be typed' },
    }
    const report = globalThis.JSA_FILL.fill(document, profile, null, answers)

    expect(document.getElementById('why').value).toBe('I like the product.')
    expect(document.getElementById('why').style.outline).toContain('solid')
    expect(document.getElementById('auth').value).toBe('')
    expect(document.getElementById('other').value).toBe('')
    expect(report.answered).toEqual([{ question: 'Why do you want to work here?', answerId: 4 }])
    expect(report.needsYou).toContain('Describe your work authorization status')
  })
})

describe('putting a drafted answer in the form', () => {
  it('types it into the matching empty box, outlined in blue', () => {
    form(`<label for="why">Why are you interested in Bridge?</label><textarea id="why"></textarea>`)
    expect(globalThis.JSA_FILL.insertAnswer(document, 'Why are you interested in Bridge?', 'Stablecoin rails.')).toBe('inserted')
    expect(document.getElementById('why').value).toBe('Stablecoin rails.')
    expect(document.getElementById('why').style.outline).toContain('solid')
  })

  it('never overwrites what the owner wrote, and never answers a sensitive question', () => {
    form(`
      <label for="why">Why us?</label><textarea id="why">My own words</textarea>
      <label for="auth">Describe your visa sponsorship needs</label><textarea id="auth"></textarea>`)
    expect(globalThis.JSA_FILL.insertAnswer(document, 'Why us?', 'Draft')).toBe('not-empty')
    expect(document.getElementById('why').value).toBe('My own words')
    expect(globalThis.JSA_FILL.insertAnswer(document, 'Describe your visa sponsorship needs', 'Draft')).toBe('not-found')
    expect(document.getElementById('auth').value).toBe('')
    expect(globalThis.JSA_FILL.insertAnswer(document, 'A question not on the page', 'Draft')).toBe('not-found')
  })
})
