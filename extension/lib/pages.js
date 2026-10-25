/** What kind of page the owner is on, from its address alone. */
export function classify(url) {
  if (!url || !/^https?:/i.test(url)) return 'unsupported'
  if (/linkedin\.com\/in\//i.test(url)) return 'linkedin-profile'
  if (/linkedin\.com\/jobs\/(view\/\d+|.*[?&]currentJobId=\d+)/i.test(url)) return 'linkedin-job'
  if (/linkedin\.com/i.test(url)) return 'linkedin-other'
  return 'application'
}

/** Hiring systems the filler knows well; others are tried by label, best effort. */
export function knownSystem(url) {
  if (/greenhouse\.io/i.test(url)) return 'Greenhouse'
  if (/lever\.co/i.test(url)) return 'Lever'
  if (/ashbyhq\.com/i.test(url)) return 'Ashby'
  if (/myworkdayjobs\.com|myworkdaysite\.com/i.test(url)) return 'Workday'
  return null
}
