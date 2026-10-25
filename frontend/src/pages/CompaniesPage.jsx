import { useState } from 'react'
import { Plus } from 'lucide-react'
import AddCompanyForm from '../components/AddCompanyForm'
import AiCard from '../components/AiCard'
import AlertEmailsCard from '../components/AlertEmailsCard'
import CompanyRow from '../components/CompanyRow'
import Dialog from '../components/Dialog'
import { useCompanies, useSummary } from '../api/hooks'
import { primaryButton } from '../lib/ui'

function RowsSkeleton() {
  return (
    <ul className="divide-y divide-rule" aria-label="Loading companies">
      {Array.from({ length: 4 }, (_, i) => (
        <li key={i} className="flex gap-4 p-5">
          <span className="skeleton size-9" />
          <span className="flex-1 space-y-2">
            <span className="skeleton block h-4 w-40" />
            <span className="skeleton block h-3 w-64" />
          </span>
        </li>
      ))}
    </ul>
  )
}

export default function CompaniesPage() {
  const companies = useCompanies()
  const rules = useSummary().data
  const [adding, setAdding] = useState(false)
  const all = companies.data ?? []
  const synced = all.filter((c) => c.ats !== 'MANUAL')
  const byHand = all.filter((c) => c.ats === 'MANUAL')

  return (
    <div className="max-w-6xl pt-6 lg:pt-10">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold tracking-tight">Companies</h1>
          <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
            Checked each morning at 7:00. Only roles{rules?.usOnly !== false && ' in the US,'} posted in the last{' '}
            {rules?.maxAgeDays ?? 30} days and matching a company&rsquo;s search phrases are kept.
          </p>
        </div>
        <button type="button" onClick={() => setAdding(true)} className={primaryButton}>
          <Plus className="size-4" aria-hidden />
          Add a company
        </button>
      </div>

      <section className="mt-8" aria-labelledby="synced">
        <h2 id="synced" className="text-sm font-semibold text-slate">
          Synced every morning <span className="font-normal text-faint">{synced.length}</span>
        </h2>
        <div className="mt-3 overflow-hidden rounded-xl border border-rule bg-surface shadow-card">
          {companies.isPending && <RowsSkeleton />}
          {companies.error && <p className="p-5 text-sm text-danger">{companies.error.message}</p>}
          {companies.isSuccess && synced.length === 0 && (
            <div className="p-10 text-center text-sm text-slate">
              <p className="font-medium text-ink">No companies yet</p>
              <p className="mt-1">Add one by pasting its careers link.</p>
            </div>
          )}
          {synced.length > 0 && (
            <ul className="divide-y divide-rule">
              {synced.map((c) => (
                <CompanyRow key={c.id} company={c} />
              ))}
            </ul>
          )}
        </div>
      </section>

      {byHand.length > 0 && (
        <section className="mt-8" aria-labelledby="by-hand">
          <h2 id="by-hand" className="text-sm font-semibold text-slate">
            From links and alerts <span className="font-normal text-faint">{byHand.length}</span>
          </h2>
          <div className="mt-3 overflow-hidden rounded-xl border border-rule bg-surface shadow-card">
            <ul className="divide-y divide-rule">
              {byHand.map((c) => (
                <CompanyRow key={c.id} company={c} />
              ))}
            </ul>
          </div>
        </section>
      )}

      <section className="mt-10" aria-labelledby="sources">
        <h2 id="sources" className="text-sm font-semibold text-slate">
          Other sources
        </h2>
        <div className="mt-3 grid items-start gap-6 xl:grid-cols-2">
          <AlertEmailsCard />
          <AiCard />
        </div>
      </section>

      <Dialog open={adding} onClose={() => setAdding(false)} title="Add a company" wide>
        <AddCompanyForm onAdded={() => setAdding(false)} />
      </Dialog>
    </div>
  )
}
