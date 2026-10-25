import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useDeleteCompany, useSyncCompany, useUpdateCompany } from '../api/hooks'
import { timeAgo } from '../lib/dates'
import { ATS_LABELS, boardUrl } from '../lib/labels'

function SyncState({ company }) {
  if (!company.lastSyncStatus) return <span className="text-slate">Not synced yet</span>
  if (company.lastSyncStatus === 'OK') return <span className="text-slate">Synced {timeAgo(company.lastSyncedAt)}</span>
  if (company.lastSyncStatus === 'BOARD_NOT_FOUND') {
    return <span className="text-danger">Board not found. The company may have moved; paste its new careers link.</span>
  }
  return <span className="text-danger">Last sync failed: {company.lastSyncError}</span>
}

function Relink({ company, focus, onDone }) {
  const [url, setUrl] = useState('')
  const update = useUpdateCompany()
  return (
    <form
      className="mt-3 flex flex-col gap-2 sm:flex-row"
      onSubmit={(e) => {
        e.preventDefault()
        update.mutate({ id: company.id, careersUrl: url.trim() }, { onSuccess: onDone })
      }}
    >
      <input
        type="text"
        inputMode="url"
        required
        autoFocus={focus}
        value={url}
        onChange={(e) => setUrl(e.target.value)}
        aria-label={`New careers link for ${company.name}`}
        placeholder="New careers link"
        className="flex-1 rounded-md border border-rule bg-paper px-3 py-1.5 text-sm focus:border-ink focus:outline-none"
      />
      <button type="submit" disabled={update.isPending} className="rounded-md bg-ink px-3 py-1.5 text-sm font-medium text-paper">
        Save link
      </button>
      {update.error && <p className="text-sm text-danger sm:self-center">{update.error.message}</p>}
    </form>
  )
}

const action = 'rounded-md px-2.5 py-1.5 text-sm font-medium text-slate hover:bg-paper hover:text-ink disabled:opacity-50'

export default function CompanyRow({ company }) {
  const sync = useSyncCompany()
  const update = useUpdateCompany()
  const remove = useDeleteCompany()
  // A missing board opens the relink form straight away, but only a click moves focus into it
  const [relinking, setRelinking] = useState(company.lastSyncStatus === 'BOARD_NOT_FOUND')
  const [clicked, setClicked] = useState(false)

  function confirmRemove() {
    if (window.confirm(`Remove ${company.name} and its ${company.openJobs} saved roles?`)) remove.mutate(company.id)
  }

  return (
    <li className={`py-4 ${company.active ? '' : 'opacity-60'}`}>
      <div className="flex flex-wrap items-start gap-x-4 gap-y-2">
        <div className="min-w-0 flex-1">
          <p className="font-medium">
            {company.name}
            {!company.active && <span className="ml-2 text-sm font-normal text-slate">Paused</span>}
          </p>
          <p className="mt-0.5 text-sm text-slate">
            <a href={boardUrl(company.ats, company.board)} target="_blank" rel="noreferrer" className="hover:text-ink hover:underline">
              {ATS_LABELS[company.ats]} / {company.board}
            </a>
            {company.openJobs > 0 && (
              <>
                {', '}
                <Link to={`/jobs?companyId=${company.id}`} className="text-ink underline-offset-4 hover:underline">
                  {company.openJobs.toLocaleString()} open {company.openJobs === 1 ? 'role' : 'roles'}
                </Link>
              </>
            )}
          </p>
          <p className="mt-1 text-sm">
            <SyncState company={company} />
          </p>
        </div>
        <div className="flex flex-wrap gap-1">
          <button type="button" className={action} disabled={sync.isPending} onClick={() => sync.mutate(company.id)}>
            {sync.isPending ? 'Syncing…' : 'Sync'}
          </button>
          <button type="button" className={action} onClick={() => {
              setClicked(true)
              setRelinking((r) => !r)
            }}>
            Change link
          </button>
          <button
            type="button"
            className={action}
            disabled={update.isPending}
            onClick={() => update.mutate({ id: company.id, active: !company.active })}
          >
            {company.active ? 'Pause' : 'Resume'}
          </button>
          <button type="button" className={`${action} hover:text-danger`} disabled={remove.isPending} onClick={confirmRemove}>
            Remove
          </button>
        </div>
      </div>
      {sync.error && <p className="mt-2 text-sm text-danger">{sync.error.message}</p>}
      {relinking && <Relink company={company} focus={clicked} onDone={() => setRelinking(false)} />}
    </li>
  )
}
