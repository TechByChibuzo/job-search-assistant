import { useEffect, useState } from 'react'
import { Search, MapPin, Sparkles, Stamp } from 'lucide-react'
import { useCompanies } from '../api/hooks'
import { WORKPLACES } from '../lib/labels'
import { useDebounced } from '../lib/useDebounced'

const field =
  'h-10 rounded-lg border border-rule bg-surface text-sm text-ink placeholder:text-faint shadow-[0_1px_0_rgb(0_0_0/0.02)] focus:border-accent focus:outline-none'

/** Text inputs update the filters after a short pause, so typing doesn't fire a request per key. */
function DebouncedInput({ value, onChange, icon: Icon, className = '', ...props }) {
  const [draft, setDraft] = useState(value)
  const [lastValue, setLastValue] = useState(value)
  // Clearing the filters from outside empties the box
  if (value !== lastValue) {
    setLastValue(value)
    if (value === '') setDraft('')
  }
  const debounced = useDebounced(draft)
  useEffect(() => {
    if (debounced !== value) onChange(debounced)
    // Only the settled draft should trigger a change
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])
  return (
    <div className={`relative ${className}`}>
      <Icon className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-faint" aria-hidden />
      <input {...props} value={draft} onChange={(e) => setDraft(e.target.value)} className={`${field} w-full pr-3 pl-9`} />
    </div>
  )
}

export default function Filters({ filters, onChange, needsSponsorship = false, onNeedsSponsorship }) {
  const companies = useCompanies()
  const set = (patch) => onChange({ ...filters, ...patch })

  function toggleWorkplace(value) {
    const current = filters.workplace
    set({ workplace: current.includes(value) ? current.filter((w) => w !== value) : [...current, value] })
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <DebouncedInput
        icon={Search}
        type="search"
        aria-label="Search titles and teams"
        placeholder="Search titles and teams"
        className="w-full sm:w-72"
        value={filters.q}
        onChange={(q) => set({ q })}
      />
      <DebouncedInput
        icon={MapPin}
        type="search"
        aria-label="Location"
        placeholder="City or state"
        className="w-[calc(50%-0.25rem)] sm:w-44"
        value={filters.location}
        onChange={(location) => set({ location })}
      />
      <select
        aria-label="Company"
        className={`${field} w-[calc(50%-0.25rem)] px-3 sm:w-48`}
        value={filters.companyId ?? ''}
        onChange={(e) => set({ companyId: e.target.value || null })}
      >
        <option value="">All companies</option>
        {(companies.data ?? []).map((c) => (
          <option key={c.id} value={c.id}>
            {c.name}
          </option>
        ))}
      </select>
      <div className="flex h-10 rounded-lg border border-rule bg-surface p-1" role="group" aria-label="Workplace">
        {WORKPLACES.map((w) => {
          const on = filters.workplace.includes(w.value)
          return (
            <button
              key={w.value}
              type="button"
              aria-pressed={on}
              onClick={() => toggleWorkplace(w.value)}
              className={`rounded-md px-3 text-sm font-medium transition-colors ${
                on ? 'bg-ink text-paper' : 'text-slate hover:text-ink'
              }`}
            >
              {w.label}
            </button>
          )
        })}
      </div>
      <select
        aria-label="Sort"
        value={filters.sort}
        onChange={(e) => set({ sort: e.target.value })}
        className={`${field} px-3`}
      >
        <option value="newest">Newest first</option>
        <option value="fit">Best fit first</option>
      </select>
      <button
        type="button"
        aria-pressed={filters.newOnly}
        onClick={() => set({ newOnly: !filters.newOnly })}
        className={`inline-flex h-10 items-center gap-2 rounded-lg border px-3 text-sm font-medium transition-colors ${
          filters.newOnly ? 'border-ink bg-ink text-paper' : 'border-rule bg-surface text-slate hover:text-ink'
        }`}
      >
        <Sparkles className="size-4" aria-hidden />
        New only
      </button>
      {onNeedsSponsorship && (
        <button
          type="button"
          aria-pressed={needsSponsorship}
          onClick={() => onNeedsSponsorship(!needsSponsorship)}
          title="Hide roles for U.S. citizens or green card holders only, roles needing a clearance, roles that say they won't sponsor, and companies you've marked as not sponsoring"
          className={`inline-flex h-10 items-center gap-2 rounded-lg border px-3 text-sm font-medium transition-colors ${
            needsSponsorship ? 'border-ink bg-ink text-paper' : 'border-rule bg-surface text-slate hover:text-ink'
          }`}
        >
          <Stamp className="size-4" aria-hidden />
          Hide no-sponsorship
        </button>
      )}
    </div>
  )
}
