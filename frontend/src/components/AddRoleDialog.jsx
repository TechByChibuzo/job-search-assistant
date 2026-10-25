import { useState } from 'react'
import { useAddJob, useCreateCompany } from '../api/hooks'
import { sourceLabel } from '../lib/labels'
import { inputClass, primaryButton, secondaryButton } from '../lib/ui'
import Dialog from './Dialog'
import { Field } from './Field'
import { useToast } from './Toasts'

const EMPTY = { url: '', title: '', company: '', location: '' }

/**
 * Adds a role by pasting its link: from LinkedIn, from a site the app can't read, or anything else. When
 * the link turns out to be on a board the app can sync, it offers to track the whole company.
 */
export default function AddRoleDialog({ open, onClose, onAdded }) {
  const toast = useToast()
  const add = useAddJob()
  const trackCompany = useCreateCompany()
  const [form, setForm] = useState(EMPTY)
  const [offer, setOffer] = useState(null)
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  function close() {
    setForm(EMPTY)
    setOffer(null)
    onClose()
  }

  function submit(e) {
    e.preventDefault()
    add.mutate(
      { url: form.url.trim(), title: form.title.trim(), company: form.company.trim(), location: form.location.trim() || undefined },
      {
        onSuccess: (res) => {
          toast(res.alreadySaved ? 'You saved this role before; here it is.' : `Added ${res.job.title}.`)
          onAdded(res.job.id)
          if (res.trackableBoard) setOffer({ ...res.trackableBoard, careersUrl: form.url.trim(), company: form.company.trim() })
          else close()
        },
      },
    )
  }

  if (offer) {
    return (
      <Dialog open={open} onClose={close} title={`Track every role at ${offer.company}?`}>
        <p className="text-sm text-slate">
          That link is on {offer.company}&rsquo;s {sourceLabel(offer.ats)} board, which the app can read. Track the company and
          its new roles will show up every morning.
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <button type="button" onClick={close} className={secondaryButton}>
            Not now
          </button>
          <button
            type="button"
            disabled={trackCompany.isPending}
            onClick={() =>
              trackCompany.mutate(
                { careersUrl: offer.careersUrl, name: offer.company },
                {
                  onSuccess: (c) => {
                    toast(`Tracking ${c.name}. Sync it from Companies to pull its roles.`)
                    close()
                  },
                  onError: (err) => toast(err.message, { tone: 'error' }),
                },
              )
            }
            className={primaryButton}
          >
            Track {offer.company}
          </button>
        </div>
      </Dialog>
    )
  }

  return (
    <Dialog open={open} onClose={close} title="Add a role by link">
      <form onSubmit={submit} className="space-y-4">
        <Field id="role-url" label="Link to the posting" hint="From LinkedIn, a company's careers site, anywhere.">
          <input id="role-url" required inputMode="url" value={form.url} onChange={set('url')} placeholder="https://" className={inputClass} />
        </Field>
        <Field id="role-title" label="Job title">
          <input id="role-title" required value={form.title} onChange={set('title')} placeholder="Software Engineer II" className={inputClass} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="role-company" label="Company">
            <input id="role-company" required value={form.company} onChange={set('company')} placeholder="Microsoft" className={inputClass} />
          </Field>
          <Field id="role-location" label="Location" optional>
            <input id="role-location" value={form.location} onChange={set('location')} placeholder="Redmond, WA" className={inputClass} />
          </Field>
        </div>
        {add.error && <p className="text-sm text-danger">{add.error.message}</p>}
        <div className="flex justify-end gap-2 pt-1">
          <button type="button" onClick={close} className={secondaryButton}>
            Cancel
          </button>
          <button type="submit" disabled={add.isPending} className={primaryButton}>
            {add.isPending ? 'Adding…' : 'Add role'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
