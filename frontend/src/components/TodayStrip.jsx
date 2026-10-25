import { useSummary } from '../api/hooks'
import CompanyLogo from './CompanyLogo'

/**
 * Today's digest: how many roles are new and at which companies. Each company is a shortcut to its new
 * roles, because a fresh role is the moment to ask for a referral.
 */
export default function TodayStrip({ onPick, activeCompanyId }) {
  const { data } = useSummary()
  if (!data) return <div className="skeleton mt-1 h-7 w-80" />

  if (data.newJobs === 0) {
    return <p className="text-[15px] text-slate">Nothing new in the last day. Everything below has been seen before.</p>
  }

  return (
    <div>
      <p className="text-[15px] text-slate">
        <span className="highlight font-semibold text-ink">
          {data.newJobs.toLocaleString()} new {data.newJobs === 1 ? 'role' : 'roles'}
        </span>{' '}
        since yesterday, at {data.byCompany.length} {data.byCompany.length === 1 ? 'company' : 'companies'}.
      </p>
      <ul className="mt-3 flex flex-wrap gap-2">
        {data.byCompany.map((c) => {
          const active = String(c.companyId) === String(activeCompanyId)
          return (
            <li key={c.companyId}>
              <button
                type="button"
                onClick={() => onPick(c.companyId)}
                aria-pressed={active}
                className={`inline-flex items-center gap-2 rounded-full border py-1 pr-3 pl-1 text-sm transition-colors ${
                  active ? 'border-ink bg-ink text-paper' : 'border-rule bg-surface hover:border-slate'
                }`}
              >
                <CompanyLogo companyId={c.companyId} name={c.companyName} size="sm" />
                <span className="font-medium">{c.companyName}</span>
                <span className={active ? 'text-paper/70' : 'text-faint'}>{c.newJobs}</span>
              </button>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
