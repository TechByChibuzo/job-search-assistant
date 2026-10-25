import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ChevronDown, Mail } from 'lucide-react'
import { useGmail, useGmailDisconnect, useGmailImport, useSyncProgress } from '../api/hooks'
import { timeAgo } from '../lib/dates'
import { primaryButton, secondaryButton } from '../lib/ui'
import { useToast } from './Toasts'

const SETUP_GUIDE = 'https://github.com/TechByChibuzo/job-search-assistant/blob/main/docs/gmail-setup.md'

function FilterSteps({ label }) {
  return (
    <details className="group mt-4 rounded-lg border border-rule bg-paper/60 px-4 py-3 text-sm">
      <summary className="flex cursor-pointer list-none items-center justify-between font-medium">
        Keep alerts out of your inbox
        <ChevronDown className="size-4 text-faint transition-transform group-open:rotate-180" aria-hidden />
      </summary>
      <ol className="mt-3 list-decimal space-y-1.5 pl-5 text-slate">
        <li>Set up job alerts on LinkedIn and on the careers sites you can&rsquo;t sync (Microsoft, Meta, Apple).</li>
        <li>
          In Gmail, open an alert and choose <span className="font-medium text-ink">Filter messages like this</span>.
        </li>
        <li>
          Tick <span className="font-medium text-ink">Skip the Inbox</span>, <span className="font-medium text-ink">Mark as read</span>{' '}
          and <span className="font-medium text-ink">Apply the label &ldquo;{label}&rdquo;</span>.
        </li>
        <li>Gmail never notifies for archived mail, so the alerts stay silent, and the app reads them each morning.</li>
      </ol>
    </details>
  )
}

/**
 * Reading job-alert emails from Gmail, for companies whose sites can't be synced. Shows what to do next
 * at each step: set up, connect, then import.
 */
export default function AlertEmailsCard() {
  const toast = useToast()
  const [params, setParams] = useSearchParams()
  const gmail = useGmail()
  const importNow = useGmailImport()
  const disconnect = useGmailDisconnect()
  const sync = useSyncProgress()
  const running = sync.data?.running

  // Google sends the browser back with ?gmail=connected (or declined, or error)
  const outcome = params.get('gmail')
  useEffect(() => {
    if (!outcome) return
    if (outcome === 'connected') toast('Gmail connected. Import now to read your alerts.')
    else if (outcome === 'declined') toast('Gmail wasn’t connected: access was declined.', { tone: 'error' })
    else toast(params.get('reason') ?? 'Gmail couldn’t be connected.', { tone: 'error' })
    setParams({}, { replace: true })
  }, [outcome, params, setParams, toast])

  if (gmail.isPending) return <div className="skeleton h-28 w-full" />
  if (gmail.error) return <p className="text-sm text-danger">{gmail.error.message}</p>
  const g = gmail.data

  return (
    <section aria-labelledby="alerts-heading" className="rounded-xl border border-rule bg-surface p-5 shadow-card">
      <div className="flex flex-wrap items-start gap-4">
        <span className="flex size-9 items-center justify-center rounded-lg bg-sunken">
          <Mail className="size-4 text-slate" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <h2 id="alerts-heading" className="font-semibold">
            Job alert emails
          </h2>
          <p className="mt-1 max-w-[60ch] text-sm text-slate">
            For Microsoft, Meta, Apple and LinkedIn-only roles: the app reads alert emails from your Gmail label
            &ldquo;{g.label}&rdquo;, read-only, and adds their roles to the inbox each morning.
          </p>
        </div>
        <div className="flex gap-2">
          {g.configured && !g.connected && (
            <a href="/api/gmail/connect" className={primaryButton}>
              Connect Gmail
            </a>
          )}
          {g.connected && (
            <>
              <button
                type="button"
                disabled={running || importNow.isPending}
                onClick={() => importNow.mutate(undefined, { onError: (e) => toast(e.message, { tone: 'error' }) })}
                className={primaryButton}
              >
                {running ? 'Reading…' : 'Import now'}
              </button>
              <button
                type="button"
                onClick={() =>
                  window.confirm('Disconnect Gmail? The app’s access is revoked; roles already added stay.') &&
                  disconnect.mutate(undefined, { onSuccess: () => toast('Gmail disconnected.') })
                }
                className={secondaryButton}
              >
                Disconnect
              </button>
            </>
          )}
        </div>
      </div>

      {!g.configured && (
        <p className="mt-4 rounded-lg bg-sunken px-4 py-3 text-sm text-slate">
          This needs a free Google sign-in client, set up once.{' '}
          <a href={SETUP_GUIDE} target="_blank" rel="noreferrer" className="font-medium text-accent hover:underline">
            Follow the setup guide
          </a>
          , then restart the app.
        </p>
      )}

      {g.connected && (
        <div className="mt-4 text-sm">
          <p className="text-slate">
            Connected as <span className="font-medium text-ink">{g.email}</span>.{' '}
            {g.lastImportAt
              ? `Last read ${timeAgo(g.lastImportAt)}: ${g.lastEmailsRead} new ${g.lastEmailsRead === 1 ? 'email' : 'emails'}, ${g.lastRolesAdded} ${g.lastRolesAdded === 1 ? 'role' : 'roles'} added.`
              : 'Not read yet.'}
          </p>
          {g.lastImportError && <p className="mt-2 text-danger">{g.lastImportError}</p>}
          {!g.lastImportError && g.lastUnreadable > 0 && (
            <p className="mt-2 text-warn">
              {g.lastUnreadable} {g.lastUnreadable === 1 ? 'email had' : 'emails had'} no roles the app could read. A new
              alert format may need a parser.
            </p>
          )}
        </div>
      )}

      {(g.configured || g.connected) && <FilterSteps label={g.label} />}
    </section>
  )
}
