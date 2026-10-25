import { groupByFirstSeen } from '../lib/dates'
import { workplaceLabel } from '../lib/labels'

function JobRow({ job, selected, onSelect }) {
  const where = [job.location, workplaceLabel(job.workplace)].filter(Boolean).join(', ')
  return (
    <li>
      <button
        type="button"
        onClick={() => onSelect(job.id)}
        aria-current={selected ? 'true' : undefined}
        className={`group block w-full border-l-2 px-4 py-3 text-left transition-colors ${
          selected ? 'border-ink bg-surface' : 'border-transparent hover:bg-surface/70'
        }`}
      >
        <span className="block text-[15px] font-medium leading-snug">
          <span className={job.isNew ? 'highlight highlight-in' : undefined}>{job.title}</span>
          {!job.open && <span className="ml-2 text-xs font-normal text-slate">Closed</span>}
        </span>
        <span className="mt-0.5 block text-sm text-slate">
          <span className="font-medium text-ink/80">{job.companyName}</span>
          {job.team && <> in {job.team}</>}
        </span>
        {where && <span className="mt-0.5 block text-sm text-slate">{where}</span>}
      </button>
    </li>
  )
}

export default function JobList({ jobs, selectedId, onSelect }) {
  return groupByFirstSeen(jobs).map((group) => (
    <section key={group.label} aria-label={`First seen ${group.label.toLowerCase()}`}>
      <h2 className="sticky top-[57px] z-10 border-b border-rule bg-paper px-4 py-2 text-sm font-semibold">
        {group.label} <span className="font-normal text-slate">{group.jobs.length}</span>
      </h2>
      <ul className="divide-y divide-rule">
        {group.jobs.map((job) => (
          <JobRow key={job.id} job={job} selected={job.id === selectedId} onSelect={onSelect} />
        ))}
      </ul>
    </section>
  ))
}
