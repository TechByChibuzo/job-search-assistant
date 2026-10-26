import { booleanPreference } from './preferences'

/** Whether the owner needs visa sponsorship, so the inbox hides roles closed to them. */
const needsSponsorship = booleanPreference('needsSponsorship')
export const useNeedsSponsorship = needsSponsorship.use
export const setNeedsSponsorship = needsSponsorship.set

const RESTRICTIONS = {
  CITIZENS_ONLY: { label: 'Citizens or green card only', excludes: true },
  CLEARANCE: { label: 'Clearance required', excludes: true },
  NO_SPONSORSHIP: { label: 'No sponsorship', excludes: true },
  MAY_BE_RESTRICTED: { label: 'Check visa terms', excludes: false },
}

/** What to show about a role's visa terms, or null when nothing is known against it. */
export function visaNote(job) {
  if (job.visaRestriction && RESTRICTIONS[job.visaRestriction].excludes) return RESTRICTIONS[job.visaRestriction]
  if (job.companyNoSponsorship) return { label: 'Company doesn’t sponsor', excludes: true }
  return job.visaRestriction ? RESTRICTIONS[job.visaRestriction] : null
}
