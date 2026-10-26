import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ExternalLink, Pause, Pencil, Play, RefreshCw, Trash2 } from 'lucide-react'
import { useDeleteCompany, useStartSync, useSyncProgress, useUpdateCompany } from '../api/hooks'
import { timeAgo } from '../lib/dates'
import { boardLabel, boardUrl, sourceLabel } from '../lib/labels'
import { safeHref } from '../lib/links'
import CompanyLogo from './CompanyLogo'
import SearchChips from './SearchChips'
import { useToast } from './Toasts'

function StatusPill({ company, syncing }) {
  if (company.ats === 'MANUAL') {
    return (
      <span className="inline-flex items-center rounded-full bg-sunken px-2.5 py-1 text-xs font-medium text-slate">
        Roles added by link
      </span>
    )
  }
  let tone = 'bg-sunken text-slate'
  let dot = 'bg-faint'
  let text = 'Not synced yet'
  if (syncing) {
    tone = 'bg-sunken text-ink'
    dot = 'bg-accent motion-safe:animate-pulse'
    text = 'Syncing now'
  } else if (company.lastSyncStatus === 'OK') {
    tone = 'bg-ok-wash text-ok'
    dot = 'bg-ok'
    text = `Synced ${timeAgo(company.lastSyncedAt)}`
  } else if (company.lastSyncStatus === 'BOARD_NOT_FOUND') {
    tone = 'bg-warn-wash text-warn'
    dot = 'bg-warn'
    text = 'Board not found'
  } else if (company.lastSyncStatus === 'FAILED') {
    tone = 'bg-danger-wash text-danger'
    dot = 'bg-danger'
    text = 'Last sync failed'
  }
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${tone}`}>
      <span className={`size-1.5 rounded-full ${dot}`} aria-hidden />
      {text}
    </span>
  )
}

function IconButton({ label, onClick, disabled, danger, children }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      title={label}
      aria-label={label}
      className={`inline-flex size-9 items-center justify-center rounded-lg text-slate transition-colors hover:bg-sunken disabled:opacity-40 ${
        danger ? 'hover:text-danger' : 'hover:text-ink'
      }`}
    >
      {children}
    </button>
  )
}

const input = 'mt-1.5 h-10 w-full rounded-lg border border-rule bg-paper px-3 text-sm focus:border-accent focus:outline-none'

function EditPanel({ company, focusLink, onDone }) {
  const toast = useToast()
  const update = useUpdateCompany()
  const [name, setName] = useState(company.name)
  const [search, setSearch] = useState(company.search ?? '')
  const [url, setUrl] = useState('')
  const [domain, setDomain] = useState(company.domain ?? '')
  const [noSponsorship, setNoSponsorship] = useState(company.noSponsorship)

  function save(e) {
    e.preventDefault()
    const body = { id: company.id, name: name.trim(), search: search.trim(), domain: domain.trim(), noSponsorship }
    if (url.trim()) body.careersUrl = url.trim()
    update.mutate(body, {
      onSuccess: () => {
        toast(`Saved ${name.trim() || company.name}.`)
        onDone()
      },
    })
  }

  return (
    <form onSubmit={save} className="mt-4 grid gap-4 rounded-lg border border-rule bg-paper p-4 md:grid-cols-2">
      <label className="text-sm font-medium">
        Name
        <input value={name} onChange={(e) => setName(e.target.value)} className={input} />
      </label>
      <label className="text-sm font-medium">
        Search phrases
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="software engineer, backend"
          className={input}
        />
      </label>
      <label className="text-sm font-medium md:col-span-2">
        Website <span className="font-normal text-faint">(for the logo; fix it if the logo is wrong)</span>
        <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder="airbnb.com" className={input} />
      </label>
      {company.ats !== 'MANUAL' && (
        <label className="text-sm font-medium md:col-span-2">
          New careers link <span className="font-normal text-faint">(when the company moves to another system)</span>
          <input
            value={url}
            autoFocus={focusLink}
            onChange={(e) => setUrl(e.target.value)}
            inputMode="url"
            placeholder={company.careersUrl ?? 'https://…'}
            className={input}
          />
        </label>
      )}
      <label className="flex items-start gap-2.5 text-sm md:col-span-2">
        <input
          type="checkbox"
          checked={noSponsorship}
          onChange={(e) => setNoSponsorship(e.target.checked)}
          className="mt-0.5 size-4 accent-ink"
        />
        <span>
          <span className="font-medium">Doesn&rsquo;t sponsor visas</span>
          <span className="block text-faint">
            Hides all its roles when you turn on Hide &ldquo;No sponsorship&rdquo; in the inbox, even ones whose posting doesn&rsquo;t
            say.
          </span>
        </span>
      </label>
      <div className="flex flex-wrap items-center gap-2 md:col-span-2">
        <button
          type="submit"
          disabled={update.isPending}
          className="h-9 rounded-lg bg-ink px-4 text-sm font-semibold text-paper hover:opacity-90 disabled:opacity-50"
        >
          Save changes
        </button>
        <button type="button" onClick={onDone} className="h-9 rounded-lg px-3 text-sm font-medium text-slate hover:text-ink">
          Cancel
        </button>
        {update.error && <p className="text-sm text-danger">{update.error.message}</p>}
      </div>
    </form>
  )
}

export default function CompanyRow({ company }) {
  const toast = useToast()
  const sync = useStartSync()
  const progress = useSyncProgress()
  const update = useUpdateCompany()
  const remove = useDeleteCompany()
  // A missing board opens the editor straight away, but only a click moves focus into it
  const [editing, setEditing] = useState(company.lastSyncStatus === 'BOARD_NOT_FOUND')
  const [clicked, setClicked] = useState(false)

  const running = progress.data?.running
  const syncingThis = running && progress.data?.current === company.name
  const board = boardLabel(company.ats, company.board)

  function confirmRemove() {
    if (window.confirm(`Remove ${company.name} and its ${company.openJobs} saved roles?`)) {
      remove.mutate(company.id, { onSuccess: () => toast(`Removed ${company.name}.`) })
    }
  }

  return (
    <li className={`p-5 ${company.active ? '' : 'opacity-60'}`}>
      <div className="flex flex-wrap items-start gap-4">
        <CompanyLogo companyId={company.id} name={company.name} />
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            <p className="font-semibold">{company.name}</p>
            <StatusPill company={company} syncing={syncingThis} />
            {!company.active && <span className="text-xs font-medium text-slate">Paused</span>}
            {company.noSponsorship && (
              <span className="rounded-full bg-warn-wash px-2.5 py-1 text-xs font-medium text-warn">Doesn&rsquo;t sponsor</span>
            )}
          </div>
          <p className="mt-1 text-sm text-slate">
            {company.ats === 'MANUAL' ? (
              <span>No feed the app can read. Add its roles by link, or set up job alerts.</span>
            ) : (
              <a
                href={safeHref(boardUrl(company.ats, company.board)) ?? undefined}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 hover:text-ink"
              >
                {sourceLabel(company.ats)}
                {board && <span className="text-faint">/ {board}</span>}
                <ExternalLink className="size-3" aria-hidden />
              </a>
            )}
          </p>
          <div className="mt-3 flex flex-wrap items-center gap-x-6 gap-y-2">
            <SearchChips search={company.search} />
            {company.openJobs > 0 && (
              <Link to={`/jobs?companyId=${company.id}`} className="text-sm font-medium text-accent hover:underline">
                {company.openJobs.toLocaleString()} open {company.openJobs === 1 ? 'role' : 'roles'}
              </Link>
            )}
          </div>
          {company.lastSyncStatus === 'BOARD_NOT_FOUND' && (
            <p className="mt-3 text-sm text-warn">
              This board no longer answers. The company has probably moved to another system: paste its new careers link
              below.
            </p>
          )}
          {company.lastSyncStatus === 'FAILED' && <p className="mt-3 text-sm text-danger">{company.lastSyncError}</p>}
        </div>
        <div className="flex gap-0.5">
          {company.ats !== 'MANUAL' && (
            <IconButton
              label="Sync now"
              disabled={running}
              onClick={() => sync.mutate(company.id, { onError: (e) => toast(e.message, { tone: 'error' }) })}
            >
              <RefreshCw className={`size-4 ${syncingThis ? 'motion-safe:animate-spin' : ''}`} />
            </IconButton>
          )}
          <IconButton
            label="Edit"
            onClick={() => {
              setClicked(true)
              setEditing((v) => !v)
            }}
          >
            <Pencil className="size-4" />
          </IconButton>
          {company.ats !== 'MANUAL' && (
            <IconButton
              label={company.active ? 'Pause daily sync' : 'Resume daily sync'}
              disabled={update.isPending}
              onClick={() => update.mutate({ id: company.id, active: !company.active })}
            >
              {company.active ? <Pause className="size-4" /> : <Play className="size-4" />}
            </IconButton>
          )}
          <IconButton label="Remove" danger disabled={remove.isPending} onClick={confirmRemove}>
            <Trash2 className="size-4" />
          </IconButton>
        </div>
      </div>
      {editing && (
        <EditPanel
          company={company}
          focusLink={clicked && company.lastSyncStatus === 'BOARD_NOT_FOUND'}
          onDone={() => setEditing(false)}
        />
      )}
    </li>
  )
}
