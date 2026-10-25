import { RefreshCw } from 'lucide-react'
import { useCompanies, useStartSync, useSyncProgress } from '../api/hooks'
import { summariseSync } from '../lib/labels'
import { timeAgo } from '../lib/dates'
import { useToast } from './Toasts'

/**
 * Live state of the background sync, and the button that starts one. The sidebar copy announces results;
 * the compact copy in the mobile header stays quiet so a run isn't announced twice.
 */
export default function SyncPanel({ compact = false }) {
  const toast = useToast()
  const companies = useCompanies()
  const progress = useSyncProgress((run) => {
    if (compact) return
    if (run.error) toast(`Sync stopped: ${run.error}`, { tone: 'error' })
    else if (run.results.length > 0) toast(`Sync finished. ${summariseSync(run.results)}.`)
  })
  const start = useStartSync()
  const run = progress.data
  const running = run?.running

  const lastSynced = (companies.data ?? [])
    .map((c) => c.lastSyncedAt)
    .filter(Boolean)
    .sort()
    .at(-1)

  function syncNow() {
    start.mutate(undefined, { onError: (e) => toast(e.message, { tone: 'error' }) })
  }

  const pct = running && run.total > 0 ? Math.round((run.done / run.total) * 100) : 0
  const status = running
    ? run.current
      ? `Syncing ${run.current}`
      : 'Starting sync'
    : lastSynced
      ? `Synced ${timeAgo(lastSynced)}`
      : 'Not synced yet'

  if (compact) {
    return (
      <button
        type="button"
        onClick={syncNow}
        disabled={running || start.isPending}
        className="inline-flex items-center gap-2 rounded-md border border-rule bg-surface px-3 py-1.5 text-sm font-medium disabled:opacity-60"
        aria-label={running ? status : 'Sync now'}
      >
        <RefreshCw className={`size-4 ${running ? 'motion-safe:animate-spin' : ''}`} aria-hidden />
        {running ? `${run.done}/${run.total || '…'}` : 'Sync'}
      </button>
    )
  }

  return (
    <div className="rounded-xl border border-rail-rule p-3">
      <p className="text-xs text-rail-dim" aria-live="polite">
        {status}
        {running && run.total > 0 && (
          <span className="text-rail-dim/80">
            {' '}
            ({run.done + 1} of {run.total})
          </span>
        )}
      </p>
      <div className="mt-2 h-1 overflow-hidden rounded-full bg-rail-rule" aria-hidden>
        <div
          className="h-full rounded-full bg-highlight transition-[width] duration-500"
          style={{ width: running ? `${Math.max(pct, 6)}%` : '0%' }}
        />
      </div>
      <button
        type="button"
        onClick={syncNow}
        disabled={running || start.isPending}
        className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-rail-ink px-3 py-2 text-sm font-semibold text-rail hover:opacity-90 disabled:opacity-50"
      >
        <RefreshCw className={`size-4 ${running ? 'motion-safe:animate-spin' : ''}`} aria-hidden />
        {running ? 'Syncing…' : 'Sync now'}
      </button>
    </div>
  )
}
