import { FileText } from 'lucide-react'
import { useAi } from '../api/hooks'
import { timeAgo } from '../lib/dates'

/** Whether Claude can score roles and draft messages: an API key, and a resume in the private folder. */
export default function AiCard() {
  const ai = useAi()
  if (ai.isPending) return <div className="skeleton h-24 w-full" />
  if (ai.error) return <p className="text-sm text-danger">{ai.error.message}</p>
  const a = ai.data

  return (
    <section aria-labelledby="ai-heading" className="rounded-xl border border-rule bg-surface p-5 shadow-card">
      <div className="flex items-start gap-4">
        <span className="flex size-9 items-center justify-center rounded-lg bg-sunken">
          <FileText className="size-4 text-slate" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="ai-heading" className="font-semibold">
            Resume and AI
          </h2>
          <p className="mt-1 max-w-[60ch] text-sm text-slate">
            Claude scores each new role against your resume and drafts messages you edit and send. It only uses what your
            resume says.
          </p>
          <dl className="mt-3 grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1.5 text-sm">
            <dt className="text-faint">Resume</dt>
            <dd>
              {a.resume ? (
                <>
                  {a.resume.fileName} <span className="text-faint">(updated {timeAgo(a.resume.modifiedAt)})</span>
                </>
              ) : (
                <span className="text-warn">{a.resumeHint}</span>
              )}
            </dd>
            <dt className="text-faint">Claude</dt>
            <dd>
              {a.configured ? (
                <>
                  {a.model}. Scores up to {a.dailyLimit} new roles each morning.
                </>
              ) : (
                <span className="text-warn">Add ANTHROPIC_API_KEY to .env, then restart the app.</span>
              )}
            </dd>
          </dl>
        </div>
      </div>
    </section>
  )
}
