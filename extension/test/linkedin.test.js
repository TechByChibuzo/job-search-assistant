import { beforeAll, describe, expect, it } from 'vitest'
import { loadContentScript } from './setup.js'

beforeAll(() => loadContentScript('linkedin.js'))

const page = (html, title = '') => {
  const doc = document.implementation.createHTMLDocument(title)
  doc.body.innerHTML = html
  return doc
}

describe('reading a LinkedIn profile', () => {
  it('takes the name, headline and current company from the top card', () => {
    const doc = page(`
      <main><section>
        <h1>Ada Lovelace</h1>
        <div class="text-body-medium break-words">Senior Software Engineer at Microsoft | Purdue '19</div>
        <span class="text-body-small inline t-black--light break-words">Seattle, Washington, United States</span>
        <button aria-label="Current company: Microsoft. Click to skip to experience card">Microsoft</button>
      </section></main>`)

    const p = globalThis.JSA_LINKEDIN.profile(doc, 'https://www.linkedin.com/in/ada-lovelace-123/details/experience/')

    expect(p).toEqual({
      name: 'Ada Lovelace',
      title: 'Senior Software Engineer',
      company: 'Microsoft',
      location: 'Seattle, Washington, United States',
      linkedinUrl: 'https://www.linkedin.com/in/ada-lovelace-123/',
    })
  })

  it('falls back to the tab title and the headline when the top card differs', () => {
    const doc = page('<div class="text-body-medium">Engineer @ Stripe</div>', '(3) Grace Hopper | LinkedIn')

    const p = globalThis.JSA_LINKEDIN.profile(doc, 'https://www.linkedin.com/in/grace/')

    expect(p.name).toBe('Grace Hopper')
    expect(p.title).toBe('Engineer')
    expect(p.company).toBe('Stripe')
  })
})

describe('reading a LinkedIn job', () => {
  it('takes the title, company and location, and links to the job itself', () => {
    const doc = page(`
      <div class="job-details-jobs-unified-top-card__job-title"><h1>Software Engineer II</h1></div>
      <div class="job-details-jobs-unified-top-card__company-name"><a>Microsoft</a></div>
      <div class="job-details-jobs-unified-top-card__primary-description-container">Redmond, WA · 2 days ago · Over 100 applicants</div>`)

    const job = globalThis.JSA_LINKEDIN.job(doc, 'https://www.linkedin.com/jobs/search/?keywords=swe&currentJobId=4012345678')

    expect(job).toEqual({
      title: 'Software Engineer II',
      company: 'Microsoft',
      location: 'Redmond, WA',
      url: 'https://www.linkedin.com/jobs/view/4012345678/',
    })
  })

  it('finds the location in LinkedIn\'s newer layout, without the old class names', () => {
    const doc = page(`
      <main>
        <div class="t-24"><h1>Software Engineer, New Grad</h1></div>
        <div class="xyz"><span>San Francisco, CA</span><span> · </span><span>Reposted 2 days ago</span><span> · </span><span>Over 100 people clicked apply</span></div>
        <p>Promoted by hirer · Responses managed off LinkedIn</p>
      </main>`, 'Software Engineer, New Grad | Nooks | LinkedIn')

    const job = globalThis.JSA_LINKEDIN.job(doc, 'https://www.linkedin.com/jobs/search-results/?currentJobId=4446745306&eBP=x')

    expect(job).toEqual({
      title: 'Software Engineer, New Grad',
      company: 'Nooks',
      location: 'San Francisco, CA',
      url: 'https://www.linkedin.com/jobs/view/4446745306/',
    })
  })

  it('falls back to the tab title', () => {
    const doc = page('', 'Backend Engineer | Plaid | LinkedIn')

    const job = globalThis.JSA_LINKEDIN.job(doc, 'https://www.linkedin.com/jobs/view/42/')

    expect(job.title).toBe('Backend Engineer')
    expect(job.company).toBe('Plaid')
  })
})
