import { Sparkles } from 'lucide-react'
import { useAi, useMatch } from '../api/hooks'
import { timeAgo } from '../lib/dates'
import { secondaryButton } from '../lib/ui'
import FitScore from './FitScore'
import { useToast } from './Toasts'

/** How well the role fits the resume: the score, why, and what's missing. Scored on demand or each morning. */
export default function FitPanel({ job, detail }) {
  const toast = useToast()
  const ai = useAi()
  const match = useMatch()
  const scored = job.matchScore != null
  const ready = ai.data?.configured && ai.data?.resume

  function check() {
    match.mutate(job.id, { onError: (e) => toast(e.message, { tone: 'error' }) })
  }

  if (!scored && ai.isSuccess && !ready) {
    return (
      <p className="rounded-xl border border-dashed border-rule px-4 py-3 text-sm text-slate">
        Add your resume and an Anthropic API key to see how well each role fits you. See Companies → Resume and AI.
      </p>
    )
  }

  return (
    <section aria-labelledby="fit-heading" className="rounded-xl border border-rule bg-sunken/40 p-4 sm:p-5">
      <div className="flex items-start gap-4">
        {scored ? <FitScore score={job.matchScore} size="lg" /> : <Sparkles className="mt-0.5 size-5 text-faint" aria-hidden />}
        <div className="min-w-0 flex-1">
          <h2 id="fit-heading" className="font-semibold">
            {scored ? 'Your fit' : 'How well do you fit?'}
          </h2>
          <p className="mt-0.5 text-sm text-slate">
            {scored
              ? job.matchSummary
              : 'Claude reads the posting against your resume and scores the fit, with what matches and what’s missing.'}
          </p>
        </div>
        <button type="button" onClick={check} disabled={match.isPending || !ready} className={secondaryButton}>
          {match.isPending ? 'Reading…' : scored ? 'Re-check' : 'Check my fit'}
        </button>
      </div>
      {scored && (detail.matchStrengths?.length > 0 || detail.matchGaps?.length > 0) && (
        <div className="mt-4 grid gap-4 text-sm sm:grid-cols-2">
          {detail.matchStrengths?.length > 0 && (
            <div>
              <h3 className="text-xs font-semibold text-ok">Matches</h3>
              <ul className="mt-1.5 space-y-1">
                {detail.matchStrengths.map((s) => (
                  <li key={s}>{s}</li>
                ))}
              </ul>
            </div>
          )}
          {detail.matchGaps?.length > 0 && (
            <div>
              <h3 className="text-xs font-semibold text-warn">Gaps</h3>
              <ul className="mt-1.5 space-y-1">
                {detail.matchGaps.map((g) => (
                  <li key={g}>{g}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
      {scored && detail.matchedAt && <p className="mt-3 text-xs text-faint">Scored {timeAgo(detail.matchedAt)}</p>}
    </section>
  )
}
