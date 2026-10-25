import { useState } from 'react'
import { ChevronDown, ExternalLink, Trash2 } from 'lucide-react'
import { useDeleteOutreach, useRecordEvent } from '../api/hooks'
import { timeAgo } from '../lib/dates'
import { EVENT_LABELS, kindLabel, relativeDay } from '../lib/tracker'
import { OutreachPill } from './StatusPill'
import { useToast } from './Toasts'

const ACTIONS = [
  { type: 'FOLLOW_UP', label: 'I followed up', when: (o) => o.status === 'AWAITING_REPLY', done: 'Follow-up recorded' },
  { type: 'REPLIED', label: 'They replied', when: (o) => o.status === 'AWAITING_REPLY' || o.status === 'NO_REPLY', done: 'Reply recorded' },
  { type: 'REFERRED', label: 'They referred me', when: (o) => o.status !== 'REFERRED', done: 'Referral recorded' },
  { type: 'NO_REPLY', label: 'No reply', when: (o) => o.status === 'AWAITING_REPLY', done: 'Marked as no reply' },
]

/** One conversation with one person: where it stands, what to do next, and its history. */
export default function Conversation({ outreach: o, showRole = false }) {
  const toast = useToast()
  const record = useRecordEvent()
  const remove = useDeleteOutreach()
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')

  function act(type, done) {
    record.mutate({ id: o.id, type }, { onSuccess: () => toast(`${done}.`), onError: (e) => toast(e.message, { tone: 'error' }) })
  }

  function addNote(e) {
    e.preventDefault()
    if (!note.trim()) return
    record.mutate({ id: o.id, type: 'NOTE', note: note.trim() }, { onSuccess: () => setNote('') })
  }

  return (
    <li className="rounded-xl border border-rule bg-paper/50 p-4">
      <div className="flex flex-wrap items-start gap-x-3 gap-y-1">
        <div className="min-w-0 flex-1">
          <p className="font-medium">
            {o.person.linkedinUrl ? (
              <a href={o.person.linkedinUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:underline">
                {o.person.name}
                <ExternalLink className="size-3 text-faint" aria-hidden />
              </a>
            ) : (
              o.person.name
            )}
          </p>
          <p className="text-sm text-slate">
            {[o.person.title, kindLabel(o.person.kind)].filter(Boolean).join(', ')}
            {showRole && o.role && <span className="text-faint"> about {o.role.title}</span>}
          </p>
        </div>
        <OutreachPill status={o.status} due={o.followUpDue} />
      </div>
      <p className="mt-2 text-xs text-faint">
        First message {timeAgo(o.firstSentAt)}
        {o.followUps > 0 && `, followed up ${o.followUps} ${o.followUps === 1 ? 'time' : 'times'}`}
        {o.status === 'AWAITING_REPLY' && o.followUpOn && !o.followUpDue && `. Follow up ${relativeDay(o.followUpOn)}`}
      </p>
      <div className="mt-3 flex flex-wrap gap-1.5">
        {ACTIONS.filter((a) => a.when(o)).map((a) => (
          <button
            key={a.type}
            type="button"
            disabled={record.isPending}
            onClick={() => act(a.type, a.done)}
            className={`rounded-md border px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50 ${
              a.type === 'FOLLOW_UP' && o.followUpDue
                ? 'border-ink bg-ink text-paper'
                : 'border-rule bg-surface text-slate hover:border-slate hover:text-ink'
            }`}
          >
            {a.label}
          </button>
        ))}
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="ml-auto inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-slate hover:text-ink"
        >
          History
          <ChevronDown className={`size-3.5 transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden />
        </button>
      </div>
      {open && (
        <div className="mt-3 border-t border-rule pt-3">
          {o.message && (
            <blockquote className="mb-3 rounded-lg bg-sunken px-3 py-2 text-sm whitespace-pre-line text-slate">{o.message}</blockquote>
          )}
          <ol className="space-y-1.5 text-sm">
            {o.events.map((e) => (
              <li key={e.id} className="flex gap-3">
                <span className="w-24 shrink-0 text-xs text-faint">{timeAgo(e.at)}</span>
                <span>
                  {EVENT_LABELS[e.type]}
                  {e.note && <span className="text-slate">: {e.note}</span>}
                </span>
              </li>
            ))}
          </ol>
          <form onSubmit={addNote} className="mt-3 flex gap-2">
            <input
              value={note}
              onChange={(e) => setNote(e.target.value)}
              placeholder="Add a note"
              aria-label="Add a note"
              className="h-9 flex-1 rounded-lg border border-rule bg-surface px-3 text-sm focus:border-accent focus:outline-none"
            />
            <button type="submit" disabled={!note.trim()} className="rounded-lg px-3 text-sm font-medium text-slate hover:text-ink disabled:opacity-40">
              Add
            </button>
            <button
              type="button"
              title="Delete this conversation"
              aria-label="Delete this conversation"
              onClick={() => window.confirm(`Delete your conversation with ${o.person.name}?`) && remove.mutate(o.id)}
              className="rounded-lg px-2 text-faint hover:text-danger"
            >
              <Trash2 className="size-4" />
            </button>
          </form>
        </div>
      )}
    </li>
  )
}
