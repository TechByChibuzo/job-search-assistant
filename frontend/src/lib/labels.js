export const WORKPLACES = [
  { value: 'REMOTE', label: 'Remote' },
  { value: 'HYBRID', label: 'Hybrid' },
  { value: 'ONSITE', label: 'On-site' },
]

export function workplaceLabel(value) {
  return WORKPLACES.find((w) => w.value === value)?.label ?? null
}

/** How each source is named in the interface. */
export const SOURCES = {
  GREENHOUSE: { label: 'Greenhouse', url: (b) => `https://boards.greenhouse.io/${b}` },
  LEVER: { label: 'Lever', url: (b) => `https://jobs.lever.co/${b}` },
  ASHBY: { label: 'Ashby', url: (b) => `https://jobs.ashbyhq.com/${b}` },
  WORKDAY: {
    label: 'Workday',
    url: (b) => {
      const [host, site] = b.split('/')
      return `https://${host}.myworkdayjobs.com/${site}`
    },
  },
  AVATURE: {
    label: 'Avature',
    url: (b) => {
      const [sub, portal] = b.split('/')
      return `https://${sub}.avature.net/${portal}`
    },
  },
  AMAZON: { label: 'amazon.jobs', url: () => 'https://www.amazon.jobs/en/' },
  GOOGLE: { label: 'Google Careers', url: () => 'https://www.google.com/about/careers/applications/jobs/results' },
  GOLDMAN: { label: 'Goldman Sachs careers', url: () => 'https://higher.gs.com/results' },
}

export function sourceLabel(ats) {
  return SOURCES[ats]?.label ?? ats
}

export function boardUrl(ats, board) {
  return SOURCES[ats]?.url(board)
}

/** The board as people would recognise it: the site name for Workday and Avature, nothing for single-company sources. */
export function boardLabel(ats, board) {
  if (ats === 'AMAZON' || ats === 'GOOGLE' || ats === 'GOLDMAN') return null
  if (ats === 'WORKDAY' || ats === 'AVATURE') return board.split('/')[1]
  return board
}

export function splitSearch(search) {
  return (search ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
}

/** One line after a sync: what's new, and which boards need attention. */
export function summariseSync(results) {
  const fresh = results.reduce((n, r) => n + r.newJobs, 0)
  const problems = results.filter((r) => r.status !== 'OK').map((r) => r.companyName)
  const parts = [fresh === 0 ? 'No new roles' : `${fresh.toLocaleString()} new ${fresh === 1 ? 'role' : 'roles'}`]
  if (problems.length > 0) parts.push(`check ${problems.join(', ')}`)
  return parts.join('; ')
}
