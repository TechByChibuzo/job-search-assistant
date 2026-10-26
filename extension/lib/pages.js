/** What kind of page the owner is on, from its address alone. */
export function classify(url) {
  if (!url || !/^https?:/i.test(url)) return 'unsupported'
  if (/linkedin\.com\/in\//i.test(url)) return 'linkedin-profile'
  if (/linkedin\.com\/jobs\/(view\/\d+|.*[?&]currentJobId=\d+)/i.test(url)) return 'linkedin-job'
  if (/linkedin\.com/i.test(url)) return 'linkedin-other'
  return 'application'
}

/** Hiring systems the filler knows well, by the site's own name; others are tried by label, best effort. */
const SYSTEMS = [
  ['Greenhouse', 'greenhouse.io'],
  ['Lever', 'lever.co'],
  ['Ashby', 'ashbyhq.com'],
  ['Workday', 'myworkdayjobs.com'],
  ['Workday', 'myworkdaysite.com'],
]

/**
 * Matched on the parsed host, exactly or as a subdomain, over https: "evil.example/greenhouse.io" or
 * "greenhouse.io.evil.example" isn't Greenhouse, and the resume is attached automatically only on real ones.
 */
export function knownSystem(url) {
  let parsed
  try {
    parsed = new URL(url)
  } catch {
    return null
  }
  if (parsed.protocol !== 'https:') return null
  const host = parsed.hostname.toLowerCase()
  const match = SYSTEMS.find(([, domain]) => host === domain || host.endsWith(`.${domain}`))
  return match ? match[0] : null
}

/**
 * A hiring system's form embedded in the page (Stripe and many others show a Greenhouse form in a frame).
 * The extension may only read the page the owner clicked it on, not a frame from another site, so it offers
 * to open the form on its own. Returns { system, url } for the first known one, or null.
 */
export function embeddedForm(frameUrls) {
  for (const url of frameUrls ?? []) {
    const system = knownSystem(url)
    if (system) return { system, url }
  }
  return null
}
