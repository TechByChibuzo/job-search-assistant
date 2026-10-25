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
