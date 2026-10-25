import { useState } from 'react'
import { Check, Copy, Sparkles } from 'lucide-react'
import { useDraft } from '../api/hooks'
import { primaryButton, secondaryButton, textareaClass } from '../lib/ui'
import Dialog from './Dialog'
import { useToast } from './Toasts'

/**
 * A drafted follow-up for a conversation that's gone quiet. The owner edits it, copies it into LinkedIn or
 * email, sends it, then records the follow-up on the conversation.
 */
export default function DraftDialog({ open, onClose, outreach: o }) {
  const toast = useToast()
  const draft = useDraft()
  const [text, setText] = useState('')
  const [copied, setCopied] = useState(false)

  function generate() {
    const days = Math.max(1, Math.round((Date.now() - new Date(o.lastContactedAt).getTime()) / 86_400_000))
    draft.mutate(
      {
        kind: 'FOLLOW_UP',
        channel: o.channel,
        jobId: o.role?.id,
        contactId: o.person.id,
        previousMessage: o.message ?? undefined,
        daysSince: days,
      },
      { onSuccess: (d) => setText(d.message), onError: (e) => toast(e.message, { tone: 'error' }) },
    )
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      // Clipboard can be blocked; the text is still selectable
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title={`Follow up with ${o.person.name}`} wide>
      {!text && (
        <div className="text-sm text-slate">
          <p>
            Claude drafts a short, polite nudge from your resume
            {o.message ? ' and the message you sent' : ''}. You edit it and send it yourself.
          </p>
          <button type="button" onClick={generate} disabled={draft.isPending} className={`${primaryButton} mt-4`}>
            <Sparkles className="size-4" aria-hidden />
            {draft.isPending ? 'Drafting…' : 'Draft a follow-up'}
          </button>
        </div>
      )}
      {text && (
        <div>
          <textarea
            aria-label="Draft"
            rows={6}
            value={text}
            onChange={(e) => setText(e.target.value)}
            className={textareaClass}
          />
          <div className="mt-4 flex flex-wrap justify-end gap-2">
            <button type="button" onClick={generate} disabled={draft.isPending} className={secondaryButton}>
              {draft.isPending ? 'Drafting…' : 'Try again'}
            </button>
            <button type="button" onClick={copy} className={primaryButton}>
              {copied ? <Check className="size-4" aria-hidden /> : <Copy className="size-4" aria-hidden />}
              {copied ? 'Copied' : 'Copy'}
            </button>
          </div>
          <p className="mt-3 text-xs text-faint">
            After you send it, press &ldquo;I followed up&rdquo; on the conversation so the reminder moves on.
          </p>
        </div>
      )}
    </Dialog>
  )
}
