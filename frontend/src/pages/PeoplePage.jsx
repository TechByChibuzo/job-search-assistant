import { useMemo, useState } from 'react'
import { ExternalLink, Pencil, Trash2, UserPlus } from 'lucide-react'
import { useCompanies, useContacts, useCreateContact, useDeleteContact, useUpdateContact } from '../api/hooks'
import Dialog from '../components/Dialog'
import { Field } from '../components/Field'
import Monogram from '../components/Monogram'
import PersonFields from '../components/PersonFields'
import { OutreachPill } from '../components/StatusPill'
import { useToast } from '../components/Toasts'
import { timeAgo } from '../lib/dates'
import { kindLabel } from '../lib/tracker'
import { inputClass, primaryButton, secondaryButton, textareaClass } from '../lib/ui'

function PersonDialog({ open, onClose, person }) {
  const toast = useToast()
  const companies = useCompanies()
  const create = useCreateContact()
  const update = useUpdateContact()
  const [value, setValue] = useState(() => person ?? { kind: 'ENGINEER' })
  const [company, setCompany] = useState('')
  const busy = create.isPending || update.isPending
  const error = create.error ?? update.error

  function submit(e) {
    e.preventDefault()
    const fields = {
      name: value.name,
      title: value.title,
      kind: value.kind,
      linkedinUrl: value.linkedinUrl,
      email: value.email,
      notes: value.notes,
    }
    const done = (p) => {
      toast(person ? `Saved ${p.name}.` : `Added ${p.name}.`)
      onClose()
    }
    if (person) update.mutate({ id: person.id, ...fields }, { onSuccess: done })
    else {
      const known = (companies.data ?? []).find((c) => c.name.toLowerCase() === company.trim().toLowerCase())
      create.mutate(known ? { companyId: known.id, ...fields } : { companyName: company.trim(), ...fields }, { onSuccess: done })
    }
  }

  return (
    <Dialog open={open} onClose={onClose} title={person ? `Edit ${person.name}` : 'Add a person'} wide>
      <form onSubmit={submit} className="space-y-4">
        {!person && (
          <Field id="person-company" label="Company" hint="A company you track, or any other name.">
            <input
              id="person-company"
              required
              list="known-companies"
              value={company}
              onChange={(e) => setCompany(e.target.value)}
              className={inputClass}
            />
            <datalist id="known-companies">
              {(companies.data ?? []).map((c) => (
                <option key={c.id} value={c.name} />
              ))}
            </datalist>
          </Field>
        )}
        <PersonFields value={value} onChange={(patch) => setValue((v) => ({ ...v, ...patch }))} idPrefix="person" />
        <Field id="person-email" label="Email" optional>
          <input
            id="person-email"
            type="email"
            value={value.email ?? ''}
            onChange={(e) => setValue((v) => ({ ...v, email: e.target.value }))}
            className={inputClass}
          />
        </Field>
        <Field id="person-notes" label="Notes" optional hint="How you know them, what you have in common.">
          <textarea
            id="person-notes"
            rows={3}
            value={value.notes ?? ''}
            onChange={(e) => setValue((v) => ({ ...v, notes: e.target.value }))}
            className={textareaClass}
          />
        </Field>
        {error && <p className="text-sm text-danger">{error.message}</p>}
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onClose} className={secondaryButton}>
            Cancel
          </button>
          <button type="submit" disabled={busy} className={primaryButton}>
            {person ? 'Save changes' : 'Add person'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}

export default function PeoplePage() {
  const toast = useToast()
  const contacts = useContacts()
  const remove = useDeleteContact()
  const [editing, setEditing] = useState(null)
  const [query, setQuery] = useState('')

  const groups = useMemo(() => {
    const q = query.trim().toLowerCase()
    const byCompany = new Map()
    for (const p of contacts.data ?? []) {
      const text = `${p.name} ${p.title ?? ''} ${p.companyName}`.toLowerCase()
      if (q && !text.includes(q)) continue
      if (!byCompany.has(p.companyName)) byCompany.set(p.companyName, [])
      byCompany.get(p.companyName).push(p)
    }
    return [...byCompany.entries()]
  }, [contacts.data, query])

  const total = contacts.data?.length ?? 0

  return (
    <div className="max-w-5xl pt-6 lg:pt-10">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-3xl font-semibold tracking-tight">People</h1>
        <button type="button" onClick={() => setEditing({})} className={primaryButton}>
          <UserPlus className="size-4" aria-hidden />
          Add a person
        </button>
      </div>
      <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
        Recruiters, engineers on the teams you want, alumni: everyone who might refer you, and where each conversation stands.
      </p>

      {total > 0 && (
        <input
          type="search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search people and companies"
          aria-label="Search people and companies"
          className="mt-6 h-10 w-full max-w-sm rounded-lg border border-rule bg-surface px-3 text-sm placeholder:text-faint focus:border-accent focus:outline-none"
        />
      )}

      {contacts.isPending && <div className="skeleton mt-6 h-40 w-full" />}
      {contacts.isSuccess && total === 0 && (
        <p className="mt-8 rounded-xl border border-dashed border-rule px-5 py-10 text-center text-sm text-slate">
          No one yet. Add people here, or straight from a role when you record a message to them.
        </p>
      )}

      <div className="mt-6 space-y-6">
        {groups.map(([company, people]) => (
          <section key={company} aria-label={company}>
            <h2 className="flex items-center gap-2 text-sm font-semibold">
              <Monogram name={company} size="sm" />
              {company}
            </h2>
            <ul className="mt-2 divide-y divide-rule overflow-hidden rounded-xl border border-rule bg-surface shadow-card">
              {people.map((p) => (
                <li key={p.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">
                      {p.linkedinUrl ? (
                        <a href={p.linkedinUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 hover:underline">
                          {p.name}
                          <ExternalLink className="size-3 text-faint" aria-hidden />
                        </a>
                      ) : (
                        p.name
                      )}
                    </p>
                    <p className="text-sm text-slate">{[p.title, kindLabel(p.kind)].filter(Boolean).join(', ')}</p>
                  </div>
                  <div className="text-right">
                    {p.lastStatus ? (
                      <>
                        <OutreachPill status={p.lastStatus} />
                        <p className="mt-1 text-xs text-faint">Last contact {timeAgo(p.lastContactedAt)}</p>
                      </>
                    ) : (
                      <span className="text-xs text-faint">Not contacted yet</span>
                    )}
                  </div>
                  <div className="flex gap-0.5">
                    <button
                      type="button"
                      onClick={() => setEditing(p)}
                      title="Edit"
                      aria-label={`Edit ${p.name}`}
                      className="inline-flex size-9 items-center justify-center rounded-lg text-slate hover:bg-sunken hover:text-ink"
                    >
                      <Pencil className="size-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() =>
                        window.confirm(`Remove ${p.name} and your conversations with them?`) &&
                        remove.mutate(p.id, { onSuccess: () => toast(`Removed ${p.name}.`) })
                      }
                      title="Remove"
                      aria-label={`Remove ${p.name}`}
                      className="inline-flex size-9 items-center justify-center rounded-lg text-slate hover:bg-sunken hover:text-danger"
                    >
                      <Trash2 className="size-4" />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>

      {editing && (
        <PersonDialog
          key={editing.id ?? 'new'}
          open
          onClose={() => setEditing(null)}
          person={editing.id ? editing : null}
        />
      )}
    </div>
  )
}
