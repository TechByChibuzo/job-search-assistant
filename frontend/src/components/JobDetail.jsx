import { ArrowLeft, Check, Copy, ExternalLink, Stamp } from 'lucide-react'
import { useState } from 'react'
import { useJob, useJobDescription, useSetJobClosed } from '../api/hooks'
import { formatDate, timeAgo } from '../lib/dates'
import { workplaceLabel } from '../lib/labels'
import { safeHref } from '../lib/links'
import { visaNote } from '../lib/visa'
import Description from './Description'
import CompanyLogo from './CompanyLogo'
import DocumentsPanel from './DocumentsPanel'
import FitPanel from './FitPanel'
import TrackPanel from './TrackPanel'

function CopyLink({ url }) {
  const [copied, setCopied] = useState(false)
  async function copy() {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 1800)
    } catch {
      // Clipboard can be blocked; the link is still one click away
    }
  }
  return (
    <button
      type="button"
      onClick={copy}
      className="inline-flex h-10 items-center gap-2 rounded-lg border border-rule bg-surface px-3.5 text-sm font-medium hover:border-slate"
    >
      {copied ? <Check className="size-4 text-ok" aria-hidden /> : <Copy className="size-4" aria-hidden />}
      {copied ? 'Copied' : 'Copy link'}
    </button>
  )
}

function DetailSkeleton() {
  return (
    <div className="space-y-4 p-6 sm:p-8" aria-label="Loading the role">
      <span className="skeleton block size-11" />
      <span className="skeleton block h-7 w-2/3" />
      <span className="skeleton block h-4 w-1/2" />
      <span className="skeleton mt-8 block h-40 w-full" />
    </div>
  )
}

export default function JobDetail({ id, onClose }) {
  const { data, isPending, error } = useJob(id)
  const describing = useJobDescription(id, Boolean(data) && !data.description)
  const setClosed = useSetJobClosed()

  if (isPending) return <DetailSkeleton />
  if (error) return <p className="p-8 text-sm text-danger">{error.message}</p>

  const { summary: job, description, closedAt, visaEvidence } = data
  const visa = visaNote(job)
  const postingUrl = safeHref(job.url)
  const facts = [
    ['Team', job.team],
    ['Location', job.location],
    ['Workplace', workplaceLabel(job.workplace)],
    ['Posted', job.postedAt && `${formatDate(job.postedAt)} (${timeAgo(job.postedAt)})`],
    ['First seen', timeAgo(job.firstSeenAt)],
    ['Closed', closedAt && formatDate(closedAt)],
  ].filter(([, v]) => v)

  return (
    <article>
      <header className="border-b border-rule p-6 sm:p-8">
        <button
          type="button"
          onClick={onClose}
          className="mb-5 inline-flex items-center gap-1.5 text-sm text-slate hover:text-ink lg:hidden"
        >
          <ArrowLeft className="size-4" aria-hidden />
          Back to the list
        </button>
        <div className="flex items-center gap-3">
          <CompanyLogo companyId={job.companyId} name={job.companyName} size="lg" />
          <div>
            <p className="font-medium">{job.companyName}</p>
            {job.isNew && <p className="text-xs text-slate">New since yesterday</p>}
          </div>
        </div>
        <h1 className="mt-5 text-[26px] leading-[1.15] font-semibold tracking-tight text-balance">
          <span className={job.isNew ? 'highlight' : undefined}>{job.title}</span>
        </h1>
        <dl className="mt-6 grid grid-cols-[max-content_1fr] gap-x-8 gap-y-2 text-sm">
          {facts.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-faint">{label}</dt>
              <dd className="text-ink">{value}</dd>
            </div>
          ))}
        </dl>
        {visa && (
          <div
            className={`mt-5 flex gap-3 rounded-lg px-4 py-3 text-sm ${visa.excludes ? 'bg-warn-wash text-warn' : 'bg-sunken text-slate'}`}
          >
            <Stamp className="mt-0.5 size-4 shrink-0" aria-hidden />
            <div>
              <p className="font-semibold">{visa.label}</p>
              {visaEvidence && (
                <p className="mt-0.5">
                  The posting says: &ldquo;{visaEvidence}&rdquo;
                </p>
              )}
              {job.companyNoSponsorship && (
                <p className="mt-0.5">You marked {job.companyName} as not sponsoring visas, on the Companies page.</p>
              )}
            </div>
          </div>
        )}
        <div className="mt-7 flex flex-wrap gap-2">
          {postingUrl && (
            <>
              <a
                href={postingUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-10 items-center gap-2 rounded-lg bg-ink px-4 text-sm font-semibold text-paper hover:opacity-90"
              >
                Open the posting
                <ExternalLink className="size-4" aria-hidden />
              </a>
              <CopyLink url={postingUrl} />
            </>
          )}
          {job.manual && (
            <button
              type="button"
              onClick={() => setClosed.mutate({ id: job.id, closed: job.open })}
              className="inline-flex h-10 items-center rounded-lg px-3 text-sm font-medium text-slate hover:text-ink"
            >
              {job.open ? 'Mark as closed' : 'Reopen'}
            </button>
          )}
        </div>
        {job.manual && <p className="mt-3 text-xs text-faint">Added by hand. Syncs leave it alone; close it when the role is gone.</p>}
      </header>
      <div className="space-y-4 border-b border-rule p-6 sm:p-8">
        <FitPanel job={job} detail={data} />
        <TrackPanel job={job} />
        <DocumentsPanel job={job} />
      </div>
      <div className="max-w-[70ch] p-6 text-[15px] leading-relaxed text-ink/90 sm:p-8">
        <Description text={description} job={job} url={postingUrl} fetching={describing.isFetching} />
      </div>
    </article>
  )
}
