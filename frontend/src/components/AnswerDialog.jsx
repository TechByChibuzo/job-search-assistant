import { useState } from 'react'
import { Sparkles } from 'lucide-react'
import { useAi, useDraftAnswer, useSaveAnswer } from '../api/hooks'
import { inputClass, primaryButton, secondaryButton, textareaClass } from '../lib/ui'
import Dialog from './Dialog'
import { Field } from './Field'
import { useToast } from './Toasts'

/**
 * Write or edit an answer to an application question. Claude can draft it from the resume (and the role,
 * when opened from one); the owner edits and saves it to the bank.
 */
export default function AnswerDialog({ open, onClose, answer, jobId, jobTitle }) {
  const toast = useToast()
  const ai = useAi()
  const save = useSaveAnswer()
  const draft = useDraftAnswer()
  const [question, setQuestion] = useState(answer?.question ?? '')
  const [text, setText] = useState(answer?.answer ?? '')
  const canDraft = ai.data?.configured && ai.data?.resume

  function generate() {
    draft.mutate(
      { question, jobId },
      { onSuccess: (d) => setText(d.answer), onError: (e) => toast(e.message, { tone: 'error' }) },
    )
  }

  function submit(e) {
    e.preventDefault()
    save.mutate(
      { id: answer?.id, question: question.trim(), answer: text.trim() },
      {
        onSuccess: () => {
          toast(answer ? 'Answer saved.' : 'Added to your answer bank.')
          onClose()
        },
        onError: (err) => toast(err.message, { tone: 'error' }),
      },
    )
  }

  return (
    <Dialog open={open} onClose={onClose} title={answer ? 'Edit answer' : 'New answer'} wide>
      <form onSubmit={submit} className="space-y-4">
        <Field id="answer-question" label="Question" hint="As forms ask it, e.g. “Why do you want to work here?”">
          <input id="answer-question" required value={question} onChange={(e) => setQuestion(e.target.value)} className={inputClass} />
        </Field>
        <div>
          <div className="flex items-end justify-between gap-2">
            <label htmlFor="answer-text" className="text-sm font-medium">
              Your answer
            </label>
            {canDraft && (
              <button
                type="button"
                onClick={generate}
                disabled={!question.trim() || draft.isPending}
                className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-rule bg-surface px-2.5 text-xs font-medium hover:border-slate disabled:opacity-50"
              >
                <Sparkles className="size-3.5" aria-hidden />
                {draft.isPending ? 'Drafting…' : text ? 'Draft again' : jobTitle ? `Draft for ${jobTitle}` : 'Draft with Claude'}
              </button>
            )}
          </div>
          <textarea id="answer-text" required rows={8} value={text} onChange={(e) => setText(e.target.value)} className={textareaClass} />
          <p className="mt-1.5 text-xs text-faint">
            {text.trim().split(/\s+/).filter(Boolean).length} words. The extension fills it into forms that ask a similar question, for you
            to check.
          </p>
        </div>
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className={secondaryButton}>
            Cancel
          </button>
          <button type="submit" disabled={save.isPending} className={primaryButton}>
            {answer ? 'Save answer' : 'Add to answer bank'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
