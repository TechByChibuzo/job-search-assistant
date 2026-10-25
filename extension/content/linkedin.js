/*
 * Reads the LinkedIn page the owner is looking at, once, when they click the extension. It never
 * navigates, scrolls, clicks or loads anything: it is the same as copying what is on the screen.
 * Injected on demand (activeTab); defines globalThis.JSA_LINKEDIN for the popup to call.
 */
;(function () {
  const text = (el) => (el ? el.textContent.replace(/\s+/g, ' ').trim() : '')

  /** "(3) Ada Lovelace | LinkedIn" → "Ada Lovelace" */
  function nameFromTitle(title) {
    return title.replace(/^\(\d+\)\s*/, '').split('|')[0].trim()
  }

  /** "Senior Engineer at Acme" or "Engineer @ Acme | ex-Foo" → { title, company } */
  function splitHeadline(headline) {
    const first = headline.split('|')[0].trim()
    const m = first.match(/^(.*?)\s+(?:at|@)\s+(.+)$/i)
    return m ? { title: m[1].trim(), company: m[2].trim() } : { title: first, company: '' }
  }

  /** https://www.linkedin.com/in/ada-lovelace-123/details/ → https://www.linkedin.com/in/ada-lovelace-123/ */
  function profileUrl(url) {
    const m = url.match(/linkedin\.com\/in\/([^/?#]+)/i)
    return m ? `https://www.linkedin.com/in/${m[1]}/` : url
  }

  function profile(doc, url) {
    const h1 = doc.querySelector('main h1') || doc.querySelector('h1')
    const name = text(h1) || nameFromTitle(doc.title || '')
    const top = h1 ? h1.closest('section') || h1.parentElement : doc
    const headline = text(top.querySelector('.text-body-medium')) || text(doc.querySelector('.text-body-medium'))
    const fromHeadline = splitHeadline(headline)

    // The top card's "Current company" button names the employer
    const current = doc.querySelector('button[aria-label^="Current company"], [aria-label^="Current company"]')
    const currentCompany = current
      ? current.getAttribute('aria-label').replace(/^Current company:\s*/i, '').replace(/\.\s*Click.*$/i, '').trim()
      : ''

    const location = text(top.querySelector('.text-body-small.inline.t-black--light'))
    return {
      name,
      title: fromHeadline.title,
      company: currentCompany || fromHeadline.company,
      location,
      linkedinUrl: profileUrl(url),
    }
  }

  /** The job id from /jobs/view/123 or ?currentJobId=123 */
  function jobId(url) {
    const view = url.match(/\/jobs\/view\/(\d+)/)
    if (view) return view[1]
    const param = url.match(/[?&]currentJobId=(\d+)/)
    return param ? param[1] : null
  }

  function job(doc, url) {
    const id = jobId(url)
    const [titleFromTab, companyFromTab] = (doc.title || '').replace(/^\(\d+\)\s*/, '').split('|').map((s) => s.trim())
    const title =
      text(doc.querySelector('.job-details-jobs-unified-top-card__job-title')) ||
      text(doc.querySelector('.jobs-unified-top-card__job-title')) ||
      text(doc.querySelector('main h1')) ||
      titleFromTab ||
      ''
    const company =
      text(doc.querySelector('.job-details-jobs-unified-top-card__company-name')) ||
      text(doc.querySelector('.jobs-unified-top-card__company-name')) ||
      companyFromTab ||
      ''
    const description = doc.querySelector(
      '.job-details-jobs-unified-top-card__primary-description-container, .job-details-jobs-unified-top-card__tertiary-description-container',
    )
    // "Seattle, WA · 1 week ago · Over 100 people clicked apply" → "Seattle, WA"
    const location = (text(description) || metaLine(doc)).split('·')[0].trim()
    return {
      title,
      company,
      location,
      url: id ? `https://www.linkedin.com/jobs/view/${id}/` : url,
    }
  }

  /**
   * The job's "place · when · how many" line, when LinkedIn's class names have changed: the shortest
   * element whose text has that shape ("San Francisco, CA · Reposted 2 days ago · Over 100 people…").
   */
  function metaLine(doc) {
    const shape = /^[^·]{2,80}\s·\s.*\b(ago|applicants?|people|clicked|reposted|posted)\b/i
    let best = ''
    for (const el of doc.querySelectorAll('main div, main span, main p, div, span, p')) {
      const t = text(el)
      if (t.length < 240 && shape.test(t) && (!best || t.length < best.length)) best = t
    }
    return best
  }

  globalThis.JSA_LINKEDIN = { profile, job, splitHeadline, profileUrl, jobId }
})()
