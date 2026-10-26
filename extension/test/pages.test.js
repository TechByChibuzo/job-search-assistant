import { describe, expect, it } from 'vitest'
import { classify, embeddedForm, knownSystem, roleFromTitle } from '../lib/pages.js'

describe('classify', () => {
  it('tells profiles, jobs and applications apart', () => {
    expect(classify('https://www.linkedin.com/in/ada/')).toBe('linkedin-profile')
    expect(classify('https://www.linkedin.com/jobs/view/123/')).toBe('linkedin-job')
    expect(classify('https://www.linkedin.com/jobs/search/?currentJobId=123')).toBe('linkedin-job')
    expect(classify('https://www.linkedin.com/feed/')).toBe('linkedin-other')
    expect(classify('https://boards.greenhouse.io/airbnb/jobs/1')).toBe('application')
    expect(classify('chrome://extensions')).toBe('unsupported')
  })

  it('names the hiring systems it knows', () => {
    expect(knownSystem('https://jobs.lever.co/plaid/1/apply')).toBe('Lever')
    expect(knownSystem('https://nvidia.wd5.myworkdayjobs.com/x/job/y/apply')).toBe('Workday')
    expect(knownSystem('https://careers.example.com/apply')).toBeNull()
  })

  it('is not fooled by the name elsewhere in the address', () => {
    expect(knownSystem('https://evil.example/greenhouse.io/apply')).toBeNull()
    expect(knownSystem('https://greenhouse.io.evil.example/jobs/1')).toBeNull()
    expect(knownSystem('https://evil.example/?next=jobs.lever.co')).toBeNull()
    expect(knownSystem('https://notlever.co/apply')).toBeNull()
    expect(knownSystem('http://boards.greenhouse.io/airbnb/jobs/1')).toBeNull()
    expect(knownSystem('https://boards.greenhouse.io/airbnb/jobs/1')).toBe('Greenhouse')
  })
})

describe('embeddedForm', () => {
  it('finds a hiring system form embedded in a company page', () => {
    expect(
      embeddedForm(['https://www.youtube.com/embed/x', 'https://job-boards.greenhouse.io/embed/job_app?for=stripe&token=7277110']),
    ).toEqual({ system: 'Greenhouse', url: 'https://job-boards.greenhouse.io/embed/job_app?for=stripe&token=7277110' })
  })

  it('ignores other frames and lookalike hosts', () => {
    expect(embeddedForm(['https://greenhouse.io.evil.example/embed', 'http://boards.greenhouse.io/x'])).toBeNull()
    expect(embeddedForm([])).toBeNull()
  })
})

describe('roleFromTitle', () => {
  it('reads the role and company from the ways hiring systems title their pages', () => {
    expect(roleFromTitle('Job Application for Software Engineer, Bridge at Stripe')).toEqual({ title: 'Software Engineer, Bridge', company: 'Stripe' })
    expect(roleFromTitle('Backend Engineer @ Plaid')).toEqual({ title: 'Backend Engineer', company: 'Plaid' })
    expect(roleFromTitle('Spotify - Backend Engineer')).toEqual({ title: 'Backend Engineer', company: 'Spotify' })
    expect(roleFromTitle('Careers')).toEqual({ title: 'Careers', company: '' })
    expect(roleFromTitle(undefined)).toEqual({ title: '', company: '' })
  })
})
