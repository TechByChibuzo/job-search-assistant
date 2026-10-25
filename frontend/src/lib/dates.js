const DAY = 24 * 60 * 60 * 1000

function startOfDay(date) {
  const d = new Date(date)
  d.setHours(0, 0, 0, 0)
  return d
}

/**
 * Groups jobs by the local day they were listed: Today, Yesterday, This week, Earlier, and Date unknown for
 * roles whose feed has no dates and that were already there when the company was added. The API returns
 * them in that order.
 */
export function groupByDay(jobs, now = new Date()) {
  const today = startOfDay(now).getTime()
  const groups = [
    { label: 'Today', from: today, jobs: [] },
    { label: 'Yesterday', from: today - DAY, jobs: [] },
    { label: 'This week', from: today - 6 * DAY, jobs: [] },
    { label: 'Earlier', from: -Infinity, jobs: [] },
  ]
  const unknown = { label: 'Date unknown', jobs: [] }
  for (const job of jobs) {
    if (!job.listedAt) {
      unknown.jobs.push(job)
      continue
    }
    const day = new Date(job.listedAt).getTime()
    groups.find((g) => day >= g.from).jobs.push(job)
  }
  return [...groups, unknown].filter((g) => g.jobs.length > 0)
}

/** "just now", "5 min ago", "3 h ago", "2 days ago", then a date. */
export function timeAgo(iso, now = new Date()) {
  if (!iso) return null
  const ms = now.getTime() - new Date(iso).getTime()
  const minutes = Math.round(ms / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  const days = Math.round(hours / 24)
  if (days < 14) return `${days} ${days === 1 ? 'day' : 'days'} ago`
  return formatDate(iso)
}

/** A compact age for list rows: "3h", "2d", "5w". */
export function shortAge(iso, now = new Date()) {
  if (!iso) return null
  const hours = Math.max(0, (now.getTime() - new Date(iso).getTime()) / 3_600_000)
  if (hours < 1) return 'now'
  if (hours < 24) return `${Math.floor(hours)}h`
  const days = Math.floor(hours / 24)
  if (days < 14) return `${days}d`
  return `${Math.floor(days / 7)}w`
}

export function formatDate(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}
