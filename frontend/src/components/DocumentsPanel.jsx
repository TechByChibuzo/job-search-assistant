import { useState } from 'react'
import { Link } from 'react-router-dom'
import { FileText, MessageSquareQuote, Sparkles } from 'lucide-react'
import { useAi, useDocuments, useGenerateDocument } from '../api/hooks'
import { timeAgo } from '../lib/dates'
import { secondaryButton } from '../lib/ui'
import AnswerDialog from './AnswerDialog'
import { useToast } from './Toasts'

const KIND = { RESUME: 'Tailored resume', COVER_LETTER: 'Cover letter' }

/** Tailored documents and answers for one role: generate, open, print. */
export default function DocumentsPanel({ job }) {
  const toast = useToast()
  const ai = useAi()
  const docs = useDocuments(job.id)
  const generate = useGenerateDocument(job.id)
  const [answering, setAnswering] = useState(false)
  const ready = ai.data?.configured && ai.data?.resume
  const working = generate.isPending ? generate.variables : null

  function make(kind) {
    generate.mutate(kind, {
      onSuccess: (d) =>
        toast(
          d.content.flagged > 0
            ? `${KIND[kind]} ready. ${d.content.flagged} part${d.content.flagged === 1 ? '' : 's'} need your check.`
            : `${KIND[kind]} ready.`,
        ),
      onError: (e) => toast(e.message, { tone: 'error' }),
    })
  }

  return (
    <section aria-labelledby="docs-heading" className="rounded-xl border border-rule bg-sunken/40 p-4 sm:p-5">
      <h2 id="docs-heading" className="font-semibold">
        Apply with care
      </h2>
      <p className="mt-0.5 text-sm text-slate">
        A resume and cover letter shaped for this role from your real resume. Anything Claude can&rsquo;t back with your
        resume is flagged for you.
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <button type="button" onClick={() => make('RESUME')} disabled={!ready || generate.isPending} className={secondaryButton}>
          <Sparkles className="size-4" aria-hidden />
          {working === 'RESUME' ? 'Tailoring… about 30 seconds' : 'Tailor my resume'}
        </button>
        <button type="button" onClick={() => make('COVER_LETTER')} disabled={!ready || generate.isPending} className={secondaryButton}>
          <FileText className="size-4" aria-hidden />
          {working === 'COVER_LETTER' ? 'Writing…' : 'Write a cover letter'}
        </button>
        <button type="button" onClick={() => setAnswering(true)} className={secondaryButton}>
          <MessageSquareQuote className="size-4" aria-hidden />
          Answer a question
        </button>
      </div>
      {docs.data?.length > 0 && (
        <ul className="mt-4 divide-y divide-rule rounded-lg border border-rule bg-surface">
          {docs.data.map((d) => (
            <li key={d.id} className="flex items-center gap-3 px-3 py-2.5 text-sm">
              <FileText className="size-4 text-faint" aria-hidden />
              <Link to={`/documents/${d.id}`} className="font-medium hover:underline">
                {KIND[d.kind]}
              </Link>
              <span className="text-faint">{timeAgo(d.createdAt)}</span>
              {d.content.flagged > 0 && (
                <span className="ml-auto rounded-full bg-warn-wash px-2 py-0.5 text-xs font-medium text-warn">
                  {d.content.flagged} to check
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      {answering && <AnswerDialog open onClose={() => setAnswering(false)} jobId={job.id} jobTitle={job.title} />}
    </section>
  )
}
