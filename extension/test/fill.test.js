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

function form(html, owner = profile) {
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
  return globalThis.JSA_FILL.fill(document, owner, resume)
}

describe('filling a Greenhouse-style form', () => {
  it('fills contact fields and links, attaches the resume, and leaves sensitive questions alone', async () => {
    const report = await form(`
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
  it('uses the full name and the company and link fields', async () => {
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
  it('reads questions from data-automation-id and fires the events frameworks listen for', async () => {
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

    const report = await globalThis.JSA_FILL.fill(document, profile, null)

    expect(document.querySelector('[data-automation-id=legalNameSection_firstName]').value).toBe('Jane')
    expect(document.querySelector('[data-automation-id=addressSection_city]').value).toBe('Austin')
    expect(events).toEqual(['input'])
    expect(report.resume).toBe('no-upload-field')
  })
})

describe('what it never does', () => {
  it('never overwrites an answer, never fills choices, and never touches EEO questions', async () => {
    const report = await form(`
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

  it('lists a choice question once, by its question, even when its options look harmless', async () => {
    const report = await form(`
      <ul><li class="application-question">
        <div class="application-label multiple-select">Pronouns</div>
        <div class="application-field"><ul>
          <li><label><input type="checkbox" name="pronouns" value="She/her"><span>She/her</span></label></li>
          <li><label><input type="checkbox" name="pronouns" value="They/them"><span>They/them</span></label></li>
        </ul></div>
      </li></ul>`)

    expect(report.needsYou).toEqual(['Pronouns'])
  })

  it('never fills fields the owner cannot see', async () => {
    const report = await form(`
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

  it('leaves the resume to the owner when told not to attach it', async () => {
    document.body.innerHTML = '<label for="r">Resume</label><input id="r" type="file">'

    const report = await globalThis.JSA_FILL.fill(document, profile, null)

    expect(report.resume).toBe('not-attached')
  })

  it('does not mistake a cover letter upload for the resume', async () => {
    const report = await form('<label for="cl">Cover letter</label><input id="cl" type="file">')

    expect(report.resume).toBe('not-found')
    expect(document.getElementById('cl').files).toHaveLength(0)
  })
})

describe('answers from the bank', () => {
  it('lists the long-answer questions it may fill, and leaves sensitive ones out', async () => {
    form(`
      <label for="why">Why do you want to work here?</label><textarea id="why"></textarea>
      <label for="auth">Describe your work authorization status</label><textarea id="auth"></textarea>
      <label for="done">Anything else?</label><textarea id="done">Already written</textarea>`)
    expect(globalThis.JSA_FILL.questions(document)).toEqual(['Why do you want to work here?'])
  })

  it('fills a matched answer, outlined in blue, and never one into a sensitive or written box', async () => {
    form(`
      <label for="why">Why do you want to work here?</label><textarea id="why"></textarea>
      <label for="auth">Describe your work authorization status</label><textarea id="auth"></textarea>
      <label for="other">Tell us about a project</label><textarea id="other"></textarea>`)
    const answers = {
      'Why do you want to work here?': { answerId: 4, answer: 'I like the product.' },
      'Describe your work authorization status': { answerId: 5, answer: 'Should never be typed' },
    }
    const report = await globalThis.JSA_FILL.fill(document, profile, null, answers)

    expect(document.getElementById('why').value).toBe('I like the product.')
    expect(document.getElementById('why').style.outline).toContain('solid')
    expect(document.getElementById('auth').value).toBe('')
    expect(document.getElementById('other').value).toBe('')
    expect(report.answered).toEqual([{ question: 'Why do you want to work here?', answerId: 4 }])
    expect(report.needsYou).toContain('Describe your work authorization status')
  })
})

describe('putting a drafted answer in the form', () => {
  it('types it into the matching empty box, outlined in blue', async () => {
    form(`<label for="why">Why are you interested in Bridge?</label><textarea id="why"></textarea>`)
    expect(globalThis.JSA_FILL.insertAnswer(document, 'Why are you interested in Bridge?', 'Stablecoin rails.')).toBe('inserted')
    expect(document.getElementById('why').value).toBe('Stablecoin rails.')
    expect(document.getElementById('why').style.outline).toContain('solid')
  })

  it('never overwrites what the owner wrote, and never answers a sensitive question', async () => {
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

/**
 * A search-and-pick list in the style of react-select (Greenhouse's): typing shows suggestions after a moment,
 * as if loaded from the network, and clicking one shows it as the choice.
 */
function pickList(id, label, suggestions) {
  const box = document.createElement('div')
  box.innerHTML = `
    <label id="${id}-label" for="${id}">${label}</label>
    <div class="select__control">
      <div class="select__value-container"></div>
      <input id="${id}" role="combobox" aria-autocomplete="list" aria-labelledby="${id}-label" aria-controls="${id}-listbox">
    </div>
    <div id="${id}-listbox" role="listbox"></div>`
  document.body.append(box)
  const input = box.querySelector('input')
  input.addEventListener('input', () => {
    setTimeout(() => {
      const list = box.querySelector('[role=listbox]')
      list.innerHTML = ''
      for (const s of suggestions(input.value)) {
        const option = document.createElement('div')
        option.setAttribute('role', 'option')
        option.textContent = s
        option.addEventListener('click', () => {
          box.querySelector('.select__value-container').innerHTML = `<div class="select__single-value">${s}</div>`
          list.innerHTML = ''
          input.value = ''
        })
        list.append(option)
      }
    }, 60)
  })
  return box
}

const chosen = (id) => document.getElementById(id).closest('.select__control').querySelector('.select__single-value')?.textContent

describe('search-and-pick lists', () => {
  const education = { ...profile, city: 'Austin', state: 'Texas', school: 'Texas A&M University - College Station',
    degree: 'Master of Computer Science', previousSchool: 'Purdue University (West Lafayette)',
    previousDegree: 'Bachelor of Science in Computer Engineering' }
  // As Greenhouse lists them: by campus, and a named degree before the plain one
  const campuses = (q) => [`${q} - College Station`, `${q} - Commerce`, `${q} - West Lafayette`, `West ${q}`]
  const degrees = () => ['Master of Business Administration (M.B.A.)', "Master's Degree", "Bachelor's Degree"]

  it('chooses the matching suggestion for country, city, each school and each degree', async () => {
    document.body.innerHTML = ''
    pickList('country', 'Country', () => ['United States Minor Outlying Islands +1', 'United States +1'])
    pickList('candidate-location', 'Location (City)', (q) => [`${q}, Minnesota, United States`, `${q}, Texas, United States`])
    pickList('school--0', 'School', campuses)
    pickList('degree--0', 'Degree', degrees)
    pickList('school--1', 'School', campuses)
    pickList('degree--1', 'Degree', degrees)

    const report = await globalThis.JSA_FILL.fill(document, education, null)

    expect(chosen('country')).toBe('United States +1')
    expect(chosen('candidate-location')).toBe('Austin, Texas, United States')
    expect(chosen('school--0')).toBe('Texas A&M University - College Station')
    expect(chosen('degree--0')).toBe("Master's Degree")
    expect(chosen('school--1')).toBe('Purdue University - West Lafayette')
    expect(chosen('degree--1')).toBe("Bachelor's Degree")
    expect(report.toChoose).toEqual([])
  })

  it('never guesses a campus: a school named without one is left, with the reason', async () => {
    document.body.innerHTML = ''
    pickList('school--0', 'School', campuses)

    const report = await globalThis.JSA_FILL.fill(document, { ...education, school: 'Texas A&M University' }, null, {}, { waitMs: 400 })

    expect(chosen('school--0')).toBeUndefined()
    expect(report.toChoose).toEqual(['School: Several campuses match: choose yours, or add the campus to the school in your Profile'])
  })

  it('keeps a choice already made, and lists a list with no matching suggestion', async () => {
    document.body.innerHTML = ''
    const country = pickList('country', 'Country', () => ['United States +1'])
    country.querySelector('.select__value-container').innerHTML = '<div class="select__single-value">Canada +1</div>'
    pickList('school--0', 'School', () => ['Rice University'])
    pickList('how', 'How did you hear about us?', () => ['LinkedIn', 'A friend'])

    const report = await globalThis.JSA_FILL.fill(document, education, null, {}, { waitMs: 400 })

    expect(chosen('country')).toBe('Canada +1')
    expect(chosen('school--0')).toBeUndefined()
    expect(document.getElementById('school--0').value).toBe('')
    expect(chosen('how')).toBeUndefined()
    expect(report.toChoose).toEqual(['School', 'How did you hear about us?'])
  })
})

describe('equal-opportunity answers the owner saved', () => {
  const saved = { ...profile, eeoGender: 'Male', eeoHispanicLatino: 'No', eeoVeteran: 'Not a protected veteran' }

  it('chooses them in plain dropdowns and radio buttons, and still never answers authorization or sponsorship', async () => {
    const report = await form(`
      <label for="g">Gender</label>
      <select id="g"><option value="">Select</option><option value="f">Female</option><option value="m">Male</option><option value="d">Decline to self-identify</option></select>
      <fieldset><legend>Are you Hispanic/Latino?</legend>
        <label><input type="radio" name="hisp" value="y"> Yes</label>
        <label><input type="radio" name="hisp" value="n"> No</label></fieldset>
      <label for="v">Veteran status</label>
      <select id="v"><option></option><option value="1">I identify as one or more of the classifications of protected veteran</option><option value="2">I am not a protected veteran</option><option value="3">I don't wish to answer</option></select>
      <label for="gi">Gender identity</label><select id="gi"><option></option><option>Man</option></select>
      <label for="auth">Are you legally authorized to work in the United States?</label>
      <select id="auth"><option></option><option>Yes</option><option>No</option></select>
      <label for="sp">Will you require sponsorship?</label><input id="sp">`,
      saved,
    )
    expect(document.getElementById('g').value).toBe('m')
    expect(document.querySelector('input[name=hisp][value=n]').checked).toBe(true)
    expect(document.querySelector('input[name=hisp][value=y]').checked).toBe(false)
    expect(document.getElementById('v').value).toBe('2')
    expect(document.getElementById('gi').value).toBe('')
    expect(document.getElementById('auth').value).toBe('')
    expect(document.getElementById('sp').value).toBe('')
    expect(report.needsYou).toEqual(expect.arrayContaining(['Gender identity', 'Will you require sponsorship?']))
    expect(report.needsYou.some((q) => /authorized/.test(q))).toBe(true)
    expect(report.needsYou).not.toContain('Gender')
  })

  it('picks them in search dropdowns, and leaves unsaved ones for the owner', async () => {
    document.body.innerHTML = ''
    pickList('gender', 'Gender', (q) => ['Male', 'Female', 'Decline To Self Identify'].filter((o) => o.toLowerCase().includes(q.toLowerCase())))
    pickList('veteran_status', 'Veteran Status', (q) =>
      ['I am not a protected veteran', 'I identify as one or more of the classifications of protected veteran', "I don't wish to answer"].filter((o) =>
        o.toLowerCase().includes(q.toLowerCase()),
      ),
    )
    pickList('hispanic_ethnicity', 'Are you Hispanic/Latino?', () => ['Yes', 'No'])

    const report = await globalThis.JSA_FILL.fill(document, { ...saved, eeoHispanicLatino: null }, null, {}, { waitMs: 400 })

    expect(chosen('gender')).toBe('Male')
    expect(chosen('veteran_status')).toBe('I am not a protected veteran')
    expect(chosen('hispanic_ethnicity')).toBeUndefined()
    expect(report.needsYou).toEqual(['Are you Hispanic/Latino?'])
  })
})
