import { useState } from 'react'
import { MessageSquareQuote, Pencil, Plus, Trash2 } from 'lucide-react'
import { useAnswers, useDeleteAnswer } from '../api/hooks'
import AnswerDialog from '../components/AnswerDialog'
import { useToast } from '../components/Toasts'
import { timeAgo } from '../lib/dates'
import { primaryButton } from '../lib/ui'
import { useDebounced } from '../lib/useDebounced'

export default function AnswersPage() {
  const toast = useToast()
  const [q, setQ] = useState('')
  const answers = useAnswers(useDebounced(q))
  const remove = useDeleteAnswer()
  const [editing, setEditing] = useState(null)
  const all = answers.data ?? []

  return (
    <div className="max-w-4xl pt-6 lg:pt-10">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Answers</h1>
          <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
            Your answers to the questions application forms keep asking. The extension fills one in when a form asks something
            similar, outlined in blue for you to check.
          </p>
        </div>
        <button type="button" onClick={() => setEditing({})} className={primaryButton}>
          <Plus className="size-4" aria-hidden />
          New answer
        </button>
      </div>

      {(all.length > 0 || q) && (
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search questions and answers"
          aria-label="Search questions and answers"
          className="mt-6 h-10 w-full max-w-sm rounded-lg border border-rule bg-surface px-3 text-sm placeholder:text-faint focus:border-accent focus:outline-none"
        />
      )}

      {answers.isPending && <div className="skeleton mt-6 h-40 w-full" />}
      {answers.isSuccess && all.length === 0 && !q && (
        <div className="mt-8 rounded-xl border border-dashed border-rule px-6 py-10 text-center">
          <MessageSquareQuote className="mx-auto size-6 text-faint" aria-hidden />
          <p className="mt-3 font-medium">No answers yet</p>
          <p className="mx-auto mt-1 max-w-sm text-sm text-slate">
            Start with the ones every form asks: why this company, a project you&rsquo;re proud of, what you&rsquo;re looking for.
          </p>
        </div>
      )}

      <ul className="mt-6 space-y-3">
        {all.map((a) => (
          <li key={a.id} className="rounded-xl border border-rule bg-surface p-5 shadow-card">
            <div className="flex items-start gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{a.question}</p>
                <p className="mt-2 text-[15px] whitespace-pre-line text-ink/90">{a.answer}</p>
                <p className="mt-3 text-xs text-faint">
                  {a.uses > 0 ? `Filled into ${a.uses} form${a.uses === 1 ? '' : 's'}. ` : ''}Updated {timeAgo(a.updatedAt)}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setEditing(a)}
                aria-label={`Edit the answer to ${a.question}`}
                title="Edit"
                className="inline-flex size-9 items-center justify-center rounded-lg text-slate hover:bg-sunken hover:text-ink"
              >
                <Pencil className="size-4" />
              </button>
              <button
                type="button"
                onClick={() =>
                  window.confirm('Delete this answer?') && remove.mutate(a.id, { onSuccess: () => toast('Answer deleted.') })
                }
                aria-label={`Delete the answer to ${a.question}`}
                title="Delete"
                className="inline-flex size-9 items-center justify-center rounded-lg text-slate hover:bg-sunken hover:text-danger"
              >
                <Trash2 className="size-4" />
              </button>
            </div>
          </li>
        ))}
      </ul>

      {editing && <AnswerDialog key={editing.id ?? 'new'} open onClose={() => setEditing(null)} answer={editing.id ? editing : null} />}
    </div>
  )
}
