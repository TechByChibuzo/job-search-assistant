import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Inbox, Link2 } from 'lucide-react'
import { useJobs, useSummary } from '../api/hooks'
import AddRoleDialog from '../components/AddRoleDialog'
import Filters from '../components/Filters'
import JobDetail from '../components/JobDetail'
import JobList, { JobListSkeleton } from '../components/JobList'
import TodayStrip from '../components/TodayStrip'
import { markSeen } from '../lib/seen'
import { useKeyboardNav } from '../lib/useKeyboardNav'
import { setNeedsSponsorship, useNeedsSponsorship } from '../lib/visa'
import { secondaryButton } from '../lib/ui'

/** Filters and the open role live in the URL, so a view can be bookmarked and Back works. */
function readFilters(params) {
  return {
    q: params.get('q') ?? '',
    location: params.get('location') ?? '',
    companyId: params.get('companyId'),
    workplace: params.getAll('workplace'),
    newOnly: params.get('newOnly') === 'true',
    sort: params.get('sort') === 'fit' ? 'fit' : 'newest',
  }
}

function writeFilters(filters, selected) {
  const next = new URLSearchParams()
  if (filters.q) next.set('q', filters.q)
  if (filters.location) next.set('location', filters.location)
  if (filters.companyId) next.set('companyId', filters.companyId)
  filters.workplace.forEach((w) => next.append('workplace', w))
  if (filters.newOnly) next.set('newOnly', 'true')
  if (filters.sort === 'fit') next.set('sort', 'fit')
  if (selected) next.set('job', selected)
  return next
}

const NO_FILTERS = readFilters(new URLSearchParams())

function Empty({ hasFilters, onClear }) {
  return (
    <div className="flex flex-col items-center px-6 py-16 text-center">
      <span className="flex size-12 items-center justify-center rounded-full bg-sunken">
        <Inbox className="size-5 text-slate" aria-hidden />
      </span>
      {hasFilters ? (
        <>
          <p className="mt-4 font-medium">No open roles match these filters</p>
          <button type="button" onClick={onClear} className="mt-2 text-sm font-medium text-accent hover:underline">
            Clear the filters
          </button>
        </>
      ) : (
        <>
          <p className="mt-4 font-medium">No roles yet</p>
          <p className="mt-1 max-w-xs text-sm text-slate">
            <Link to="/companies" className="font-medium text-accent hover:underline">
              Add a company
            </Link>{' '}
            and sync it. Its open roles will show up here.
          </p>
        </>
      )}
    </div>
  )
}

export default function JobsPage() {
  const [params, setParams] = useSearchParams()
  const filters = useMemo(() => readFilters(params), [params])
  const selected = params.get('job') ? Number(params.get('job')) : null
  const needsSponsorship = useNeedsSponsorship()
  const jobs = useJobs({ ...filters, needsSponsorship })
  const maxAgeDays = useSummary().data?.maxAgeDays
  const [adding, setAdding] = useState(false)

  const all = useMemo(() => jobs.data?.pages.flatMap((p) => p.content) ?? [], [jobs.data])
  const ids = useMemo(() => all.map((j) => j.id), [all])
  const total = jobs.data?.pages[0]?.totalElements ?? 0
  const hasFilters = Boolean(filters.q || filters.location || filters.companyId || filters.workplace.length || filters.newOnly)

  const setFilters = (f) => setParams(writeFilters(f, selected), { replace: true })
  const select = useCallback((id) => setParams(writeFilters(filters, id)), [filters, setParams])
  const close = useCallback(() => select(null), [select])
  useKeyboardNav({ ids, selected, onSelect: select, onClose: close })
  // Opening a role clears its highlight in the list
  useEffect(() => markSeen(selected), [selected])

  return (
    <div className="pt-6 lg:pt-10">
      {/* On a phone an open role takes the screen; the header and filters come back with the list */}
      <header className={`flex-col gap-5 ${selected ? 'hidden lg:flex' : 'flex'}`}>
        <div>
          <div className="flex items-center justify-between gap-4">
            <h1 className="text-3xl font-semibold tracking-tight">Inbox</h1>
            <button type="button" onClick={() => setAdding(true)} className={secondaryButton}>
              <Link2 className="size-4" aria-hidden />
              Add a role by link
            </button>
          </div>
          <div className="mt-2">
            <TodayStrip
              activeCompanyId={filters.newOnly ? filters.companyId : null}
              onPick={(companyId) => {
                const same = filters.newOnly && String(filters.companyId) === String(companyId)
                setFilters(same ? NO_FILTERS : { ...NO_FILTERS, companyId: String(companyId), newOnly: true })
              }}
            />
          </div>
        </div>
        <Filters
          filters={filters}
          onChange={setFilters}
          needsSponsorship={needsSponsorship}
          onNeedsSponsorship={setNeedsSponsorship}
        />
      </header>
      <AddRoleDialog open={adding} onClose={() => setAdding(false)} onAdded={(id) => select(id)} />

      <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
        <div className={selected ? 'hidden lg:block' : ''}>
          <div className="flex items-baseline justify-between px-1 pb-2 text-sm text-slate">
            <span aria-live="polite">
              {jobs.isSuccess &&
                `${total.toLocaleString()} open ${total === 1 ? 'role' : 'roles'}${needsSponsorship ? ' that may sponsor' : ''}${
                  maxAgeDays ? `, last ${maxAgeDays} days` : ''
                }`}
            </span>
            <span className="flex items-center gap-3">
              <span className="hidden text-xs text-faint xl:inline">
                <kbd className="font-sans">j</kbd> / <kbd className="font-sans">k</kbd> to move
              </span>
              <select
                aria-label="Sort"
                value={filters.sort}
                onChange={(e) => setFilters({ ...filters, sort: e.target.value })}
                className="-my-1 h-8 rounded-md border border-transparent bg-transparent pr-1 pl-2 text-sm font-medium text-ink hover:border-rule focus:border-accent focus:outline-none"
              >
                <option value="newest">Newest first</option>
                <option value="fit">Best fit first</option>
              </select>
            </span>
          </div>
          <div className="scroll-quiet overflow-hidden rounded-xl border border-rule bg-paper lg:sticky lg:top-6 lg:max-h-[calc(100dvh-3rem)] lg:overflow-y-auto">
            {jobs.isPending && <JobListSkeleton />}
            {jobs.error && <p className="px-4 py-12 text-sm text-danger">{jobs.error.message}</p>}
            {jobs.isSuccess && all.length === 0 && <Empty hasFilters={hasFilters} onClear={() => setFilters(NO_FILTERS)} />}
            <JobList jobs={all} selectedId={selected} onSelect={select} />
            {jobs.hasNextPage && (
              <div className="border-t border-rule p-3">
                <button
                  type="button"
                  onClick={() => jobs.fetchNextPage()}
                  disabled={jobs.isFetchingNextPage}
                  className="w-full rounded-lg py-2 text-sm font-medium text-slate hover:bg-surface hover:text-ink"
                >
                  {jobs.isFetchingNextPage ? 'Loading…' : `Show more (${(total - all.length).toLocaleString()} left)`}
                </button>
              </div>
            )}
          </div>
        </div>

        <div className={selected ? '' : 'hidden lg:block'}>
          <div className="scroll-quiet rounded-xl border border-rule bg-surface shadow-card lg:sticky lg:top-6 lg:max-h-[calc(100dvh-3rem)] lg:overflow-y-auto">
            {selected ? (
              <JobDetail key={selected} id={selected} onClose={close} />
            ) : (
              <div className="flex flex-col items-center px-6 py-24 text-center">
                <p className="font-medium">Pick a role to read it here</p>
                <p className="mt-1 text-sm text-slate">Or press j to start at the top.</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
