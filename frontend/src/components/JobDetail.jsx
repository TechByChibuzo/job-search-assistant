import { useJob } from '../api/hooks'
import { formatDate, timeAgo } from '../lib/dates'
import { workplaceLabel } from '../lib/labels'

export default function JobDetail({ id, onClose }) {
  const { data, isPending, error } = useJob(id)

  if (isPending) return <p className="p-6 text-sm text-slate">Loading the role…</p>
  if (error) return <p className="p-6 text-sm text-danger">{error.message}</p>

  const { summary: job, description, closedAt } = data
  const facts = [
    ['Team', job.team],
    ['Location', job.location],
    ['Workplace', workplaceLabel(job.workplace)],
    ['Posted', formatDate(job.postedAt)],
    ['First seen', timeAgo(job.firstSeenAt)],
    ['Closed', closedAt && formatDate(closedAt)],
  ].filter(([, v]) => v)

  return (
    <article className="flex h-full flex-col">
      <header className="border-b border-rule p-6">
        <button type="button" onClick={onClose} className="mb-4 text-sm text-slate hover:text-ink lg:hidden">
          Back to the list
        </button>
        <p className="text-sm font-medium text-slate">{job.companyName}</p>
        <h1 className="mt-1 text-2xl font-semibold leading-tight tracking-tight text-balance">
          <span className={job.isNew ? 'highlight' : undefined}>{job.title}</span>
        </h1>
        <dl className="mt-4 grid grid-cols-[max-content_1fr] gap-x-6 gap-y-1 text-sm">
          {facts.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-slate">{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
        <a
          href={job.url}
          target="_blank"
          rel="noreferrer"
          className="mt-5 inline-block rounded-md bg-ink px-4 py-2 text-sm font-medium text-paper hover:opacity-90"
        >
          Open the posting
        </a>
      </header>
      <div className="max-w-[68ch] overflow-y-auto p-6 text-[15px] leading-relaxed whitespace-pre-line">
        {description ?? <span className="text-slate">This posting has no description in its feed.</span>}
      </div>
    </article>
  )
}
