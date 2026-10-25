export const WORKPLACES = [
  { value: 'REMOTE', label: 'Remote' },
  { value: 'HYBRID', label: 'Hybrid' },
  { value: 'ONSITE', label: 'On-site' },
]

export function workplaceLabel(value) {
  return WORKPLACES.find((w) => w.value === value)?.label ?? null
}

export const ATS_LABELS = { GREENHOUSE: 'Greenhouse', LEVER: 'Lever', ASHBY: 'Ashby' }

const BOARD_URLS = {
  GREENHOUSE: (b) => `https://boards.greenhouse.io/${b}`,
  LEVER: (b) => `https://jobs.lever.co/${b}`,
  ASHBY: (b) => `https://jobs.ashbyhq.com/${b}`,
}

export function boardUrl(ats, board) {
  return BOARD_URLS[ats]?.(board)
}

/** One line for the header after a sync: what's new, and which boards need attention. */
export function summariseSync(results) {
  const fresh = results.reduce((n, r) => n + r.newJobs, 0)
  const problems = results.filter((r) => r.status !== 'OK').map((r) => r.companyName)
  const parts = [fresh === 0 ? 'No new roles' : `${fresh} new ${fresh === 1 ? 'role' : 'roles'}`]
  if (problems.length > 0) parts.push(`check ${problems.join(', ')}`)
  return parts.join('; ')
}
