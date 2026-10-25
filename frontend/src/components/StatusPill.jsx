import { OUTREACH_STATUS } from '../lib/tracker'

const TONES = {
  ok: 'bg-ok-wash text-ok',
  warn: 'bg-warn-wash text-warn',
  neutral: 'bg-sunken text-slate',
  muted: 'bg-sunken text-faint',
}

export function Pill({ tone = 'neutral', children }) {
  return <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${TONES[tone]}`}>{children}</span>
}

export function OutreachPill({ status, due }) {
  if (due) return <Pill tone="warn">Follow-up due</Pill>
  const s = OUTREACH_STATUS[status] ?? { label: status, tone: 'neutral' }
  return <Pill tone={s.tone}>{s.label}</Pill>
}
