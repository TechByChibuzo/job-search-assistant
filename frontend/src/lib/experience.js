import { booleanPreference } from './preferences'

/** The most years of experience a role may ask for and still show, when the owner hides senior roles. */
export const MAX_YEARS = 4

/** Whether the owner hides roles asking for 5+ years of experience, or with a senior title. */
const hideSenior = booleanPreference('hideSenior')
export const useHideSenior = hideSenior.use
export const setHideSenior = hideSenior.set

/** What to show about how senior a role is, or null when nothing says it's senior. */
export function seniorityNote(job) {
  if (job.minYears != null && job.minYears > MAX_YEARS) return { label: `${job.minYears}+ years` }
  if (job.seniorTitle) return { label: 'Senior level' }
  return null
}
