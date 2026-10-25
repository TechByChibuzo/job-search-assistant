import { useState } from 'react'
import { Sparkles } from 'lucide-react'
import { useAi, useContacts, useCreateContact, useDraft, useLogOutreach } from '../api/hooks'
import { CHANNELS, todayIso } from '../lib/tracker'
import { inputClass, primaryButton, secondaryButton, textareaClass } from '../lib/ui'
import Dialog from './Dialog'
import { Field } from './Field'
import PersonFields from './PersonFields'
import { useToast } from './Toasts'

const NEW = 'new'

/**
 * Records a message the owner sent (the app never sends anything). Pick someone already saved at the
 * company, or add them on the spot.
 */
export default function LogMessageDialog({ open, onClose, companyId, companyName, jobId }) {
  const toast = useToast()
  const contacts = useContacts(companyId)
  const createContact = useCreateContact()
  const log = useLogOutreach()
  const people = contacts.data ?? []
  const ai = useAi()
  const draft = useDraft()
  const canDraft = ai.data?.configured && ai.data?.resume

  const [personId, setPersonId] = useState('')
  const [person, setPerson] = useState({ kind: 'ENGINEER' })
  const [channel, setChannel] = useState('LINKEDIN')
  const [sentOn, setSentOn] = useState(todayIso())
  const [message, setMessage] = useState('')
  const [draftKind, setDraftKind] = useState('CONNECTION_NOTE')
  const [error, setError] = useState(null)
  // Whether the message started as Claude's draft, for the insights page; edits keep it a draft
  const [drafted, setDrafted] = useState(false)

  const chosen = personId || (people.length === 0 ? NEW : '')
  const limit = channel === 'LINKEDIN' && draftKind === 'CONNECTION_NOTE' ? 300 : null

  function generate() {
    setError(null)
    const who =
      chosen === NEW
        ? { personName: person.name, personTitle: person.title, personKind: person.kind }
        : { contactId: Number(chosen) }
    if (chosen === NEW && !person.name?.trim()) {
      setError('Add the person’s name first, so the draft can address them.')
      return
    }
    draft.mutate(
      { kind: channel === 'LINKEDIN' ? draftKind : 'MESSAGE', channel, jobId, ...who },
      {
        onSuccess: (d) => {
          setMessage(d.subject ? `Subject: ${d.subject}\n\n${d.message}` : d.message)
          setDrafted(true)
        },
        onError: (e) => setError(e.message),
      },
    )
  }
  const busy = createContact.isPending || log.isPending

  async function submit(e) {
    e.preventDefault()
    setError(null)
    try {
      let contactId = Number(chosen)
      if (chosen === NEW) {
        const created = await createContact.mutateAsync({ companyId, ...person })
        contactId = created.id
      }
      const sent = await log.mutateAsync({ contactId, jobId, channel, sentOn, message: message.trim() || undefined, drafted })
      toast(`Recorded your message to ${sent.person.name}. A follow-up reminder is set for ${sent.followUpOn}.`)
      setPersonId('')
      setPerson({ kind: 'ENGINEER' })
      setMessage('')
      onClose()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title="Record a message you sent" wide>
      <form onSubmit={submit} className="space-y-5">
        <Field id="log-person" label={`Who at ${companyName}?`}>
          <select
            id="log-person"
            required
            value={chosen}
            onChange={(e) => setPersonId(e.target.value)}
            className={inputClass}
          >
            {people.length > 0 && (
              <option value="" disabled>
                Choose a person
              </option>
            )}
            {people.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
                {p.title ? `, ${p.title}` : ''}
              </option>
            ))}
            <option value={NEW}>Someone new…</option>
          </select>
        </Field>
        {chosen === NEW && (
          <div className="rounded-xl border border-rule bg-paper/60 p-4">
            <PersonFields value={person} onChange={(patch) => setPerson((p) => ({ ...p, ...patch }))} idPrefix="log-new" />
          </div>
        )}
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="log-channel" label="Sent on">
            <select id="log-channel" value={channel} onChange={(e) => setChannel(e.target.value)} className={inputClass}>
              {CHANNELS.map((c) => (
                <option key={c.value} value={c.value}>
                  {c.label}
                </option>
              ))}
            </select>
          </Field>
          <Field id="log-date" label="Date">
            <input
              id="log-date"
              type="date"
              required
              max={todayIso()}
              value={sentOn}
              onChange={(e) => setSentOn(e.target.value)}
              className={inputClass}
            />
          </Field>
        </div>
        <div>
          <div className="flex flex-wrap items-end justify-between gap-2">
            <label htmlFor="log-message" className="text-sm font-medium">
              What you wrote <span className="font-normal text-faint">(optional)</span>
            </label>
            {canDraft && chosen && (
              <div className="flex items-center gap-2">
                {channel === 'LINKEDIN' && (
                  <select
                    aria-label="Kind of message"
                    value={draftKind}
                    onChange={(e) => setDraftKind(e.target.value)}
                    className="h-8 rounded-lg border border-rule bg-surface px-2 text-xs"
                  >
                    <option value="CONNECTION_NOTE">Connection note</option>
                    <option value="MESSAGE">Message</option>
                  </select>
                )}
                <button
                  type="button"
                  onClick={generate}
                  disabled={draft.isPending}
                  className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-rule bg-surface px-2.5 text-xs font-medium hover:border-slate disabled:opacity-50"
                >
                  <Sparkles className="size-3.5" aria-hidden />
                  {draft.isPending ? 'Drafting…' : message ? 'Draft again' : 'Draft with Claude'}
                </button>
              </div>
            )}
          </div>
          <textarea
            id="log-message"
            rows={5}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            className={textareaClass}
            aria-describedby="log-message-hint"
          />
          <p id="log-message-hint" className="mt-1.5 flex justify-between gap-4 text-xs text-faint">
            <span>
              {canDraft
                ? 'Draft it here, edit it, send it yourself on LinkedIn or by email, then record it.'
                : 'Kept with the conversation, and later used to see which messages get replies.'}
            </span>
            {limit && message && (
              <span className={message.length > limit ? 'font-medium text-danger' : ''}>
                {message.length}/{limit}
              </span>
            )}
          </p>
        </div>
        {error && <p className="text-sm text-danger">{error}</p>}
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className={secondaryButton}>
            Cancel
          </button>
          <button type="submit" disabled={busy || !chosen} className={primaryButton}>
            {busy ? 'Saving…' : 'Record message'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
