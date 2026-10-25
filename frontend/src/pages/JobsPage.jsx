import { useSearchParams, Link } from 'react-router-dom'
import { useJobs } from '../api/hooks'
import Filters from '../components/Filters'
import JobList from '../components/JobList'
import JobDetail from '../components/JobDetail'

/** Filters and the open job live in the URL, so a view can be bookmarked and Back works. */
function readFilters(params) {
  return {
    q: params.get('q') ?? '',
    location: params.get('location') ?? '',
    companyId: params.get('companyId'),
    workplace: params.getAll('workplace'),
    newOnly: params.get('newOnly') === 'true',
  }
}

function writeFilters(filters, selected) {
  const next = new URLSearchParams()
  if (filters.q) next.set('q', filters.q)
  if (filters.location) next.set('location', filters.location)
  if (filters.companyId) next.set('companyId', filters.companyId)
  filters.workplace.forEach((w) => next.append('workplace', w))
  if (filters.newOnly) next.set('newOnly', 'true')
  if (selected) next.set('job', selected)
  return next
}

function Empty({ hasFilters, onClear }) {
  if (hasFilters) {
    return (
      <div className="px-4 py-12 text-sm">
        <p>No open roles match these filters.</p>
        <button type="button" onClick={onClear} className="mt-2 font-medium underline underline-offset-4">
          Clear the filters
        </button>
      </div>
    )
  }
  return (
    <div className="px-4 py-12 text-sm">
      <p className="font-medium">No roles yet.</p>
      <p className="mt-1 text-slate">
        <Link to="/companies" className="font-medium text-ink underline underline-offset-4">
          Add a company
        </Link>{' '}
        by pasting its careers link, and its open roles show up here after the next sync.
      </p>
    </div>
  )
}

export default function JobsPage() {
  const [params, setParams] = useSearchParams()
  const filters = readFilters(params)
  const selected = params.get('job') ? Number(params.get('job')) : null
  const jobs = useJobs(filters)

  const all = jobs.data?.pages.flatMap((p) => p.content) ?? []
  const total = jobs.data?.pages[0]?.totalElements ?? 0
  const hasFilters = Boolean(filters.q || filters.location || filters.companyId || filters.workplace.length || filters.newOnly)

  const setFilters = (f) => setParams(writeFilters(f, selected), { replace: true })
  const select = (id) => setParams(writeFilters(filters, id))

  return (
    <div className="pt-5">
      <Filters filters={filters} onChange={setFilters} />
      <p className="mt-4 text-sm text-slate" aria-live="polite">
        {jobs.isSuccess && `${total.toLocaleString()} open ${total === 1 ? 'role' : 'roles'}`}
      </p>

      <div className="mt-2 grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <div className={`border-t border-rule ${selected ? 'hidden lg:block' : ''}`}>
          {jobs.isPending && <p className="px-4 py-12 text-sm text-slate">Loading roles…</p>}
          {jobs.error && <p className="px-4 py-12 text-sm text-danger">{jobs.error.message}</p>}
          {jobs.isSuccess && all.length === 0 && <Empty hasFilters={hasFilters} onClear={() => setFilters(readFilters(new URLSearchParams()))} />}
          <JobList jobs={all} selectedId={selected} onSelect={select} />
          {jobs.hasNextPage && (
            <button
              type="button"
              onClick={() => jobs.fetchNextPage()}
              disabled={jobs.isFetchingNextPage}
              className="mt-4 w-full rounded-md border border-rule bg-surface py-2 text-sm font-medium hover:border-ink"
            >
              {jobs.isFetchingNextPage ? 'Loading…' : `Show more (${(total - all.length).toLocaleString()} left)`}
            </button>
          )}
        </div>

        <div className={`${selected ? '' : 'hidden lg:block'}`}>
          <div className="rounded-lg border border-rule bg-surface lg:sticky lg:top-[72px] lg:max-h-[calc(100dvh-88px)] lg:overflow-y-auto">
            {selected ? (
              <JobDetail key={selected} id={selected} onClose={() => select(null)} />
            ) : (
              <p className="p-6 text-sm text-slate">Pick a role to read it here.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
