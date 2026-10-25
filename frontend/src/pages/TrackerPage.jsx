import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { BellRing, ChevronDown, KanbanSquare } from 'lucide-react'
import { useDue, usePursuits, useRecordEvent, useSummary } from '../api/hooks'
import Drawer from '../components/Drawer'
import JobDetail from '../components/JobDetail'
import Monogram from '../components/Monogram'
import { Pill } from '../components/StatusPill'
import { useToast } from '../components/Toasts'
import { timeAgo } from '../lib/dates'
import { STAGES, kindLabel, relativeDay } from '../lib/tracker'
import { safeHref } from '../lib/links'

function DueList() {
  const toast = useToast()
  const due = useDue()
  const record = useRecordEvent()
  const followUpDays = useSummary().data?.followUpDays ?? 5
  const items = due.data ?? []

  if (due.isPending) return <div className="skeleton h-20 w-full" />
  if (items.length === 0) {
    return (
      <p className="rounded-xl border border-dashed border-rule px-5 py-4 text-sm text-slate">
        No follow-ups due. When a message goes {followUpDays} days without a reply, it shows up here.
      </p>
    )
  }

  const act = (o, type, done) =>
    record.mutate({ id: o.id, type }, { onSuccess: () => toast(done), onError: (e) => toast(e.message, { tone: 'error' }) })

  return (
    <ul className="divide-y divide-rule overflow-hidden rounded-xl border border-rule bg-surface shadow-card">
      {items.map((o) => (
        <li key={o.id} className="flex flex-wrap items-center gap-x-4 gap-y-3 p-4">
          <Monogram name={o.person.companyName} />
          <div className="min-w-0 flex-1">
            <p className="text-[15px]">
              Follow up with{' '}
              {safeHref(o.person.linkedinUrl) ? (
                <a href={safeHref(o.person.linkedinUrl)} target="_blank" rel="noreferrer" className="font-semibold hover:underline">
                  {o.person.name}
                </a>
              ) : (
                <span className="font-semibold">{o.person.name}</span>
              )}
              {o.role && <span className="text-slate"> about {o.role.title}</span>}
            </p>
            <p className="mt-0.5 text-sm text-slate">
              {kindLabel(o.person.kind)} at {o.person.companyName}. Last message {timeAgo(o.lastContactedAt)}
              {o.followUps > 0 && `, ${o.followUps} follow-up${o.followUps === 1 ? '' : 's'} so far`}.
            </p>
          </div>
          <div className="flex flex-wrap gap-1.5">
            <button
              type="button"
              onClick={() => act(o, 'FOLLOW_UP', `Follow-up to ${o.person.name} recorded.`)}
              className="rounded-md bg-ink px-3 py-1.5 text-xs font-semibold text-paper hover:opacity-90"
            >
              I followed up
            </button>
            <button
              type="button"
              onClick={() => act(o, 'REPLIED', 'Reply recorded.')}
              className="rounded-md border border-rule px-3 py-1.5 text-xs font-medium text-slate hover:text-ink"
            >
              They replied
            </button>
            <button
              type="button"
              onClick={() => act(o, 'NO_REPLY', `Stopped following up with ${o.person.name}.`)}
              className="rounded-md border border-rule px-3 py-1.5 text-xs font-medium text-slate hover:text-ink"
            >
              Stop, no reply
            </button>
          </div>
        </li>
      ))}
    </ul>
  )
}

function Card({ pursuit: p, onOpen }) {
  return (
    <li>
      <button
        type="button"
        onClick={() => onOpen(p.role.id)}
        className="w-full rounded-xl border border-rule bg-surface p-3.5 text-left shadow-card transition-colors hover:border-slate"
      >
        <span className="flex items-center gap-2.5">
          <Monogram name={p.role.companyName} size="sm" />
          <span className="truncate text-sm text-slate">{p.role.companyName}</span>
        </span>
        <span className="mt-2 block text-sm leading-snug font-medium">{p.role.title}</span>
        <span className="mt-2.5 flex flex-wrap gap-1.5">
          {p.followUpDue && <Pill tone="warn">Follow-up due</Pill>}
          {!p.followUpDue && p.nextFollowUpOn && <Pill>Follow up {relativeDay(p.nextFollowUpOn)}</Pill>}
          {p.people > 0 && (
            <Pill>
              {p.people} {p.people === 1 ? 'person' : 'people'}
            </Pill>
          )}
          {!p.role.open && <Pill tone="muted">Role closed</Pill>}
        </span>
      </button>
    </li>
  )
}

export default function TrackerPage() {
  const [params, setParams] = useSearchParams()
  const pursuits = usePursuits()
  const [showClosed, setShowClosed] = useState(false)
  const open = params.get('job') ? Number(params.get('job')) : null
  const all = pursuits.data ?? []
  const columns = STAGES.filter((s) => s.value !== 'CLOSED')
  const closed = all.filter((p) => p.stage === 'CLOSED')

  const openRole = (id) => setParams(id ? { job: String(id) } : {})

  return (
    <div className="pt-6 lg:pt-10">
      <h1 className="text-3xl font-semibold tracking-tight">Tracker</h1>
      <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
        The roles you&rsquo;re pursuing and the people you&rsquo;ve asked about them. Record each message you send, and the
        app tells you when to follow up.
      </p>

      <section className="mt-8" aria-labelledby="due-heading">
        <h2 id="due-heading" className="flex items-center gap-2 text-sm font-semibold text-slate">
          <BellRing className="size-4" aria-hidden />
          Follow-ups due
        </h2>
        <div className="mt-3">
          <DueList />
        </div>
      </section>

      <section className="mt-10" aria-labelledby="board-heading">
        <h2 id="board-heading" className="flex items-center gap-2 text-sm font-semibold text-slate">
          <KanbanSquare className="size-4" aria-hidden />
          Roles
        </h2>
        {pursuits.isPending && <div className="skeleton mt-3 h-48 w-full" />}
        {pursuits.isSuccess && all.length === 0 && (
          <p className="mt-3 rounded-xl border border-dashed border-rule px-5 py-8 text-center text-sm text-slate">
            Nothing tracked yet. Open a role in the inbox and press <span className="font-medium text-ink">Track</span>, or record a
            message to someone about it.
          </p>
        )}
        {all.length > 0 && (
          <div className="scroll-quiet -mx-4 mt-3 overflow-x-auto px-4 pb-2 sm:-mx-6 sm:px-6 lg:mx-0 lg:px-0">
            <div className="grid min-w-[1080px] grid-cols-6 gap-3">
              {columns.map((stage) => {
                const cards = all.filter((p) => p.stage === stage.value)
                return (
                  <div key={stage.value} className="rounded-xl bg-sunken/60 p-2.5">
                    <h3 className="flex items-baseline justify-between px-1 pb-2.5 text-sm font-semibold">
                      {stage.label}
                      <span className="text-xs font-normal text-faint">{cards.length}</span>
                    </h3>
                    <ul className="space-y-2">
                      {cards.map((p) => (
                        <Card key={p.id} pursuit={p} onOpen={openRole} />
                      ))}
                    </ul>
                  </div>
                )
              })}
            </div>
          </div>
        )}
        {closed.length > 0 && (
          <div className="mt-4">
            <button
              type="button"
              onClick={() => setShowClosed((v) => !v)}
              aria-expanded={showClosed}
              className="inline-flex items-center gap-1.5 text-sm font-medium text-slate hover:text-ink"
            >
              {closed.length} closed
              <ChevronDown className={`size-4 transition-transform ${showClosed ? 'rotate-180' : ''}`} aria-hidden />
            </button>
            {showClosed && (
              <ul className="mt-3 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
                {closed.map((p) => (
                  <Card key={p.id} pursuit={p} onOpen={openRole} />
                ))}
              </ul>
            )}
          </div>
        )}
      </section>

      <Drawer open={open != null} onClose={() => openRole(null)} label="Role">
        {open != null && <JobDetail key={open} id={open} onClose={() => openRole(null)} />}
      </Drawer>
    </div>
  )
}
