/** The stages a tracked role moves through, in order, with how the interface names them. */
export const STAGES = [
  { value: 'SAVED', label: 'Saved', hint: 'Worth pursuing' },
  { value: 'REACHING_OUT', label: 'Reaching out', hint: 'Messages sent' },
  { value: 'REFERRED', label: 'Referred', hint: 'Someone vouched for you' },
  { value: 'APPLIED', label: 'Applied', hint: 'Application in' },
  { value: 'INTERVIEWING', label: 'Interviewing', hint: 'In the process' },
  { value: 'OFFER', label: 'Offer', hint: '' },
  { value: 'CLOSED', label: 'Closed', hint: 'No longer pursuing' },
]

export function stageLabel(value) {
  return STAGES.find((s) => s.value === value)?.label ?? value
}

export const OUTREACH_STATUS = {
  AWAITING_REPLY: { label: 'Waiting for a reply', tone: 'neutral' },
  REPLIED: { label: 'Replied', tone: 'ok' },
  REFERRED: { label: 'Referred you', tone: 'ok' },
  NO_REPLY: { label: 'No reply', tone: 'muted' },
  DECLINED: { label: 'Declined', tone: 'muted' },
}

export const EVENT_LABELS = {
  SENT: 'Sent a message',
  FOLLOW_UP: 'Followed up',
  REPLIED: 'They replied',
  REFERRED: 'They referred you',
  NO_REPLY: 'Marked no reply',
  DECLINED: 'They declined',
  NOTE: 'Note',
}

export const KINDS = [
  { value: 'RECRUITER', label: 'Recruiter' },
  { value: 'HIRING_MANAGER', label: 'Hiring manager' },
  { value: 'ENGINEER', label: 'Engineer on the team' },
  { value: 'ALUMNI', label: 'Alumni' },
  { value: 'OTHER', label: 'Other' },
]

export function kindLabel(value) {
  return KINDS.find((k) => k.value === value)?.label ?? value
}

export const CHANNELS = [
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'EMAIL', label: 'Email' },
  { value: 'OTHER', label: 'Other' },
]

/** "today", "tomorrow", "in 3 days", "2 days ago" for a yyyy-mm-dd date. */
export function relativeDay(isoDate, now = new Date()) {
  if (!isoDate) return null
  const [y, m, d] = isoDate.split('-').map(Number)
  const target = new Date(y, m - 1, d)
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const days = Math.round((target - today) / 86_400_000)
  if (days === 0) return 'today'
  if (days === 1) return 'tomorrow'
  if (days === -1) return 'yesterday'
  return days > 0 ? `in ${days} days` : `${-days} days ago`
}

/** Today as yyyy-mm-dd in local time, for date inputs. */
export function todayIso(now = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}
