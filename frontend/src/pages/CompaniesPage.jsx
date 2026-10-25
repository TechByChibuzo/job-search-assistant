import AddCompanyForm from '../components/AddCompanyForm'
import CompanyRow from '../components/CompanyRow'
import { useCompanies } from '../api/hooks'

export default function CompaniesPage() {
  const companies = useCompanies()

  return (
    <div className="max-w-4xl pt-6">
      <h1 className="text-2xl font-semibold tracking-tight">Companies</h1>
      <p className="mt-1 max-w-[60ch] text-sm text-slate">
        Paste a careers link from Greenhouse, Lever or Ashby. Every company here is checked once a day at 7:00, and you can
        sync any of them now.
      </p>

      <AddCompanyForm />

      {companies.isPending && <p className="mt-8 text-sm text-slate">Loading companies…</p>}
      {companies.error && <p className="mt-8 text-sm text-danger">{companies.error.message}</p>}
      {companies.isSuccess && companies.data.length === 0 && (
        <p className="mt-8 text-sm text-slate">No companies yet. Add the first one above.</p>
      )}
      {companies.isSuccess && companies.data.length > 0 && (
        <ul className="mt-8 divide-y divide-rule border-y border-rule">
          {companies.data.map((c) => (
            <CompanyRow key={c.id} company={c} />
          ))}
        </ul>
      )}
    </div>
  )
}
