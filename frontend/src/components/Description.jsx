import { ExternalLink, FileText } from 'lucide-react'
import { parseDescription } from '../lib/description'

/** Why there's no description, and the way to the full posting. */
function NoDescription({ job, url }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed border-rule px-5 py-6 sm:flex-row sm:items-center">
      <FileText className="size-5 shrink-0 text-faint" aria-hidden />
      <div className="flex-1">
        <p className="font-medium text-ink">The full posting is on {job ? `${job.companyName}’s` : 'the company’s'} site</p>
        <p className="mt-0.5 text-sm text-slate">
          {job?.manual
            ? 'You added this role by link, so the app has its title and location but not the description.'
            : 'This company’s job feed lists roles without their descriptions.'}
        </p>
      </div>
      {url && (
        <a href={url} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-accent hover:underline">
          Read it there
          <ExternalLink className="size-3.5" aria-hidden />
        </a>
      )}
    </div>
  )
}

export default function Description({ text, job, url, fetching = false }) {
  if (!text && fetching) {
    return (
      <div className="space-y-2" aria-busy="true">
        <p className="text-sm text-slate">Getting the description from {job ? `${job.companyName}’s` : 'the company’s'} site…</p>
        <div className="skeleton h-4 w-11/12" />
        <div className="skeleton h-4 w-4/5" />
        <div className="skeleton h-4 w-2/3" />
      </div>
    )
  }
  if (!text) return <NoDescription job={job} url={url} />
  return (
    <div className="space-y-3">
      {parseDescription(text).map((b, i) => {
        if (b.type === 'heading') {
          return (
            <h3 key={i} className="pt-3 text-[15px] font-semibold first:pt-0">
              {b.text}
            </h3>
          )
        }
        if (b.type === 'list') {
          return (
            <ul key={i} className="list-disc space-y-1.5 pl-5 marker:text-faint">
              {b.items.map((item, j) => (
                <li key={j}>{item}</li>
              ))}
            </ul>
          )
        }
        return <p key={i}>{b.text}</p>
      })}
    </div>
  )
}
