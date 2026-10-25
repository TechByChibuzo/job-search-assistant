import { useState } from 'react'
import { NavLink } from 'react-router-dom'
import { useCompanies, useSyncAll } from '../api/hooks'
import { summariseSync } from '../lib/labels'
import { timeAgo } from '../lib/dates'

function Tab({ to, children }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        `rounded-md px-3 py-1.5 text-sm font-medium transition-colors ${
          isActive ? 'bg-ink text-paper' : 'text-slate hover:text-ink'
        }`
      }
    >
      {children}
    </NavLink>
  )
}

export default function Header() {
  const companies = useCompanies()
  const sync = useSyncAll()
  const [message, setMessage] = useState(null)

  const lastSynced = (companies.data ?? [])
    .map((c) => c.lastSyncedAt)
    .filter(Boolean)
    .sort()
    .at(-1)

  function syncNow() {
    setMessage(null)
    sync.mutate(undefined, {
      onSuccess: (results) => setMessage(summariseSync(results)),
      onError: (e) => setMessage(e.message),
    })
  }

  return (
    <header className="sticky top-0 z-20 border-b border-rule bg-paper/90 backdrop-blur">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6">
        <span className="flex items-center gap-2 text-[15px] font-semibold tracking-tight">
          <img src="/favicon.svg" alt="" className="size-5" />
          Job Search Assistant
        </span>
        <nav className="flex gap-1" aria-label="Main">
          <Tab to="/jobs">Jobs</Tab>
          <Tab to="/companies">Companies</Tab>
        </nav>
        <div className="ml-auto flex items-center gap-3 text-sm">
          <span className="text-slate" aria-live="polite">
            {sync.isPending ? 'Checking every board…' : message ?? (lastSynced && `Synced ${timeAgo(lastSynced)}`)}
          </span>
          <button
            type="button"
            onClick={syncNow}
            disabled={sync.isPending}
            className="rounded-md border border-rule bg-surface px-3 py-1.5 font-medium hover:border-ink disabled:opacity-60"
          >
            Sync now
          </button>
        </div>
      </div>
    </header>
  )
}
