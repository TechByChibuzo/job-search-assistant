import AddCompanyForm from '../components/AddCompanyForm'
import AlertEmailsCard from '../components/AlertEmailsCard'
import CompanyRow from '../components/CompanyRow'
import { useCompanies, useSummary } from '../api/hooks'

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
  const count = companies.data?.length ?? 0
  const rules = useSummary().data

  return (
    <div className="max-w-5xl pt-6 lg:pt-10">
      <h1 className="text-3xl font-semibold tracking-tight">Companies</h1>
      <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
        Every company here is checked each morning at 7:00. Only roles
        {rules?.usOnly !== false && ' in the US,'} posted in the last {rules?.maxAgeDays ?? 30} days and matching a
        company&rsquo;s search phrases are kept.
      </p>

      <div className="mt-8">
        <AddCompanyForm />
      </div>

      <div className="mt-6">
        <AlertEmailsCard />
      </div>

      <section className="mt-10" aria-labelledby="tracked">
        <h2 id="tracked" className="text-sm font-semibold text-slate">
          Tracking {count} {count === 1 ? 'company' : 'companies'}
        </h2>
        <div className="mt-3 overflow-hidden rounded-xl border border-rule bg-surface shadow-card">
          {companies.isPending && <RowsSkeleton />}
          {companies.error && <p className="p-5 text-sm text-danger">{companies.error.message}</p>}
          {companies.isSuccess && count === 0 && (
            <p className="p-8 text-center text-sm text-slate">No companies yet. Paste a careers link above to add the first.</p>
          )}
          {companies.isSuccess && count > 0 && (
            <ul className="divide-y divide-rule">
              {companies.data.map((c) => (
                <CompanyRow key={c.id} company={c} />
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  )
}
