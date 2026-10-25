import { useState } from 'react'
import { BookmarkPlus, MessageSquarePlus } from 'lucide-react'
import { useSummary, useTrack, useTracking, useUntrack, useUpdatePursuit } from '../api/hooks'
import { STAGES } from '../lib/tracker'
import { primaryButton, secondaryButton } from '../lib/ui'
import Conversation from './Conversation'
import LogMessageDialog from './LogMessageDialog'
import { useToast } from './Toasts'

/** The tracker inside a role's detail: its stage, and the people asked about it. */
export default function TrackPanel({ job }) {
  const toast = useToast()
  const tracking = useTracking(job.id)
  const track = useTrack()
  const update = useUpdatePursuit()
  const untrack = useUntrack()
  const [logging, setLogging] = useState(false)
  const followUpDays = useSummary().data?.followUpDays ?? 5

  if (tracking.isPending) return <div className="skeleton h-24 w-full" />
  if (tracking.error) return <p className="text-sm text-danger">{tracking.error.message}</p>

  const { pursuit, outreach } = tracking.data

  return (
    <section aria-labelledby="tracking-heading" className="rounded-xl border border-rule bg-sunken/40 p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-3">
        <h2 id="tracking-heading" className="font-semibold">
          {pursuit ? 'Tracking this role' : 'Pursue this role'}
        </h2>
        {pursuit && (
          <select
            aria-label="Stage"
            value={pursuit.stage}
            onChange={(e) =>
              update.mutate(
                { id: pursuit.id, stage: e.target.value },
                { onSuccess: (p) => toast(`Moved to ${STAGES.find((s) => s.value === p.stage)?.label}.`) },
              )
            }
            className="h-8 rounded-lg border border-rule bg-surface px-2 text-sm font-medium focus:border-accent focus:outline-none"
          >
            {STAGES.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </select>
        )}
        {pursuit?.appliedOn && <span className="text-xs text-slate">Applied {pursuit.appliedOn}</span>}
        <div className="ml-auto flex gap-2">
          {!pursuit && (
            <button
              type="button"
              onClick={() => track.mutate(job.id, { onSuccess: () => toast('Tracking this role.') })}
              disabled={track.isPending}
              className={secondaryButton}
            >
              <BookmarkPlus className="size-4" aria-hidden />
              Track
            </button>
          )}
          <button type="button" onClick={() => setLogging(true)} className={primaryButton}>
            <MessageSquarePlus className="size-4" aria-hidden />
            Record a message
          </button>
        </div>
      </div>

      {outreach.length === 0 ? (
        <p className="mt-3 text-sm text-slate">
          Find someone at {job.companyName} on LinkedIn, such as an engineer on the team or a recruiter, and message them
          yourself. Then record it here, and the app reminds you to follow up after {followUpDays} days of silence.
        </p>
      ) : (
        <ul className="mt-4 space-y-3">
          {outreach.map((o) => (
            <Conversation key={o.id} outreach={o} />
          ))}
        </ul>
      )}

      {pursuit && (
        <button
          type="button"
          onClick={() => window.confirm('Stop tracking this role? Your conversations stay with the people.') && untrack.mutate(pursuit.id)}
          className="mt-4 text-xs text-faint hover:text-danger"
        >
          Stop tracking
        </button>
      )}

      <LogMessageDialog
        open={logging}
        onClose={() => setLogging(false)}
        companyId={job.companyId}
        companyName={job.companyName}
        jobId={job.id}
      />
    </section>
  )
}
