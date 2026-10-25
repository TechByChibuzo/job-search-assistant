import { useEffect, useState } from 'react'
import { useCompanies } from '../api/hooks'
import { WORKPLACES } from '../lib/labels'
import { useDebounced } from '../lib/useDebounced'

const input =
  'rounded-md border border-rule bg-surface px-3 py-2 text-sm placeholder:text-slate/80 focus:border-ink focus:outline-none'

/** Text inputs update the filters after a short pause, so typing doesn't fire a request per key. */
function DebouncedInput({ value, onChange, ...props }) {
  const [draft, setDraft] = useState(value)
  const debounced = useDebounced(draft)
  useEffect(() => {
    if (debounced !== value) onChange(debounced)
    // Only the settled draft should trigger a change
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])
  return <input {...props} value={draft} onChange={(e) => setDraft(e.target.value)} />
}

export default function Filters({ filters, onChange }) {
  const companies = useCompanies()
  const set = (patch) => onChange({ ...filters, ...patch })

  function toggleWorkplace(value) {
    const current = filters.workplace
    set({ workplace: current.includes(value) ? current.filter((w) => w !== value) : [...current, value] })
  }

  return (
    <div className="flex flex-wrap items-center gap-2">
      <DebouncedInput
        type="search"
        aria-label="Search titles and teams"
        placeholder="Search titles and teams"
        className={`${input} w-full sm:w-64`}
        value={filters.q}
        onChange={(q) => set({ q })}
      />
      <DebouncedInput
        type="search"
        aria-label="Location"
        placeholder="Location"
        className={`${input} w-[calc(50%-0.25rem)] sm:w-40`}
        value={filters.location}
        onChange={(location) => set({ location })}
      />
      <select
        aria-label="Company"
        className={`${input} w-[calc(50%-0.25rem)] sm:w-44`}
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
      <div className="flex gap-1" role="group" aria-label="Workplace">
        {WORKPLACES.map((w) => {
          const on = filters.workplace.includes(w.value)
          return (
            <button
              key={w.value}
              type="button"
              aria-pressed={on}
              onClick={() => toggleWorkplace(w.value)}
              className={`rounded-md border px-2.5 py-2 text-sm ${
                on ? 'border-ink bg-ink text-paper' : 'border-rule bg-surface text-slate hover:text-ink'
              }`}
            >
              {w.label}
            </button>
          )
        })}
      </div>
      <label className="flex cursor-pointer items-center gap-2 px-1 text-sm">
        <input
          type="checkbox"
          className="size-4 accent-[var(--ink)]"
          checked={filters.newOnly}
          onChange={(e) => set({ newOnly: e.target.checked })}
        />
        <span className="highlight">New only</span>
      </label>
    </div>
  )
}
