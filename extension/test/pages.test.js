import { describe, expect, it } from 'vitest'
import { classify, knownSystem } from '../lib/pages.js'

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
