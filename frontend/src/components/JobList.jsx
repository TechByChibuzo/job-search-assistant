import { groupByDay, shortAge } from '../lib/dates'
import { workplaceLabel } from '../lib/labels'
import { seniorityNote } from '../lib/experience'
import { useSeenJobs } from '../lib/seen'
import { stageLabel } from '../lib/tracker'
import { visaNote } from '../lib/visa'
import FitScore from './FitScore'
import CompanyLogo from './CompanyLogo'

function JobRow({ job, selected, unread, onSelect }) {
  const workplace = workplaceLabel(job.workplace)
  const visa = visaNote(job)
  const senior = seniorityNote(job)
  return (
    <li>
      <button
        id={`job-${job.id}`}
        type="button"
        onClick={() => onSelect(job.id)}
        aria-current={selected ? 'true' : undefined}
        className={`relative flex w-full gap-3 px-4 py-3.5 text-left transition-colors ${
          selected ? 'bg-surface shadow-card' : 'hover:bg-surface/60'
        }`}
      >
        {selected && <span className="absolute inset-y-2 left-0 w-0.5 rounded-full bg-ink" aria-hidden />}
        <CompanyLogo companyId={job.companyId} name={job.companyName} />
        <span className="min-w-0 flex-1">
          <span className="flex items-baseline gap-3">
            <span className="min-w-0 flex-1 text-[15px] leading-snug font-medium">
              <span className={unread ? 'highlight-soft highlight-in' : undefined}>{job.title}</span>
              {unread && <span className="sr-only"> (new, not opened yet)</span>}
            </span>
            <span className="flex shrink-0 items-center gap-2">
              {job.listedAt && (
                <span className="text-xs text-faint" title={job.postedAt ? 'Posted' : 'First seen'}>
                  {shortAge(job.listedAt)}
                </span>
              )}
              <FitScore score={job.matchScore} />
            </span>
          </span>
          <span className="mt-0.5 block truncate text-sm text-slate">
            {job.companyName}
            {job.team && <span className="text-faint"> / {job.team}</span>}
          </span>
          {(job.location || workplace || !job.open || visa || senior || job.trackedStage) && (
            <span className="mt-1.5 flex flex-wrap items-center gap-1.5 text-xs text-slate">
              {job.location && <span className="max-w-full truncate">{job.location}</span>}
              {workplace && <span className="rounded bg-sunken px-1.5 py-0.5 font-medium">{workplace}</span>}
              {job.trackedStage && (
                <span className="rounded bg-ink px-1.5 py-0.5 font-medium text-paper">{stageLabel(job.trackedStage)}</span>
              )}
              {senior && <span className="rounded bg-sunken px-1.5 py-0.5 font-medium text-slate">{senior.label}</span>}
              {visa && (
                <span className={`rounded px-1.5 py-0.5 font-medium ${visa.excludes ? 'bg-warn-wash text-warn' : 'bg-sunken text-slate'}`}>
                  {visa.label}
                </span>
              )}
              {!job.open && <span className="rounded bg-danger-wash px-1.5 py-0.5 font-medium text-danger">Closed</span>}
            </span>
          )}
        </span>
      </button>
    </li>
  )
}

export default function JobList({ jobs, selectedId, onSelect }) {
  const seen = useSeenJobs()
  return groupByDay(jobs).map((group) => (
    <section key={group.label} aria-label={group.label}>
      <h2 className="sticky top-0 z-10 flex items-baseline gap-2 border-b border-rule bg-paper/95 px-4 py-2 text-xs font-semibold text-slate backdrop-blur">
        {group.label}
        <span className="font-normal text-faint">{group.jobs.length}</span>
      </h2>
      <ul className="divide-y divide-rule/70">
        {group.jobs.map((job) => (
          <JobRow
            key={job.id}
            job={job}
            selected={job.id === selectedId}
            unread={job.isNew && !seen.has(job.id)}
            onSelect={onSelect}
          />
        ))}
      </ul>
    </section>
  ))
}

export function JobListSkeleton() {
  return (
    <ul className="divide-y divide-rule/70" aria-label="Loading roles">
      {Array.from({ length: 7 }, (_, i) => (
        <li key={i} className="flex gap-3 px-4 py-4">
          <span className="skeleton size-9 shrink-0" />
          <span className="flex-1 space-y-2">
            <span className="skeleton block h-4 w-3/4" />
            <span className="skeleton block h-3 w-1/3" />
          </span>
        </li>
      ))}
    </ul>
  )
}
