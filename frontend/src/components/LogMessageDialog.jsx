import { useState } from 'react'
import { useContacts, useCreateContact, useLogOutreach } from '../api/hooks'
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

  const [personId, setPersonId] = useState('')
  const [person, setPerson] = useState({ kind: 'ENGINEER' })
  const [channel, setChannel] = useState('LINKEDIN')
  const [sentOn, setSentOn] = useState(todayIso())
  const [message, setMessage] = useState('')
  const [error, setError] = useState(null)

  const chosen = personId || (people.length === 0 ? NEW : '')
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
      const sent = await log.mutateAsync({ contactId, jobId, channel, sentOn, message: message.trim() || undefined })
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
        <Field id="log-message" label="What you wrote" optional hint="Kept with the conversation, and later used to see which messages get replies.">
          <textarea
            id="log-message"
            rows={4}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            className={textareaClass}
          />
        </Field>
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
