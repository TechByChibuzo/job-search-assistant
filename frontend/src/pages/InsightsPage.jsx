import { useState } from 'react'
import { BarChart3 } from 'lucide-react'
import { useInsights } from '../api/hooks'
import { STAGES } from '../lib/tracker'

function percent(rate) {
  return rate == null ? '–' : `${Math.round(rate * 100)}%`
}

function Tile({ label, value, note }) {
  return (
    <div className="rounded-xl border border-rule bg-surface p-4 shadow-card sm:p-5">
      <p className="text-sm text-slate">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight tabular-nums sm:text-3xl">{value}</p>
      {note && <p className="mt-1 text-xs text-faint">{note}</p>}
    </div>
  )
}

/**
 * One horizontal bar per group: the reply rate, labelled "replies of settled". A group too small to mean much
 * is drawn hatched and marked "early signal", so a 1-of-1 never reads like a trend.
 */
function Bars({ title, groups, rate = 'replyRate', count = 'replied' }) {
  const rows = groups.filter((g) => g.sent > 0)
  if (rows.length === 0) return null
  return (
    <figure className="rounded-xl border border-rule bg-surface p-5 shadow-card">
      <figcaption className="font-semibold">{title}</figcaption>
      <ul className="mt-4 space-y-3">
        {rows.map((g) => {
          const width = g[rate] == null ? 0 : Math.max(g[rate] * 100, g[count] > 0 ? 2 : 0)
          return (
            <li
              key={g.label}
              className="group"
              title={`${g.label}: ${g[count]} of ${g.settled} settled conversations (${percent(g[rate])}); ${g.sent} sent in all`}
            >
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span>{g.label}</span>
                <span className="text-slate tabular-nums">
                  {g.settled > 0 ? `${g[count]} of ${g.settled}` : 'Waiting on replies'}
                  {g.settled > 0 && <span className="ml-2 font-semibold text-ink">{percent(g[rate])}</span>}
                </span>
              </div>
              <div className="mt-1.5 h-3 rounded bg-sunken">
                <div
                  className={`h-3 rounded transition-[width] group-hover:opacity-85 ${g.enough ? 'bg-chart' : 'bar-early'}`}
                  style={{ width: `${width}%` }}
                />
              </div>
              {!g.enough && g.settled > 0 && <p className="mt-1 text-xs text-faint">Early signal: under 5 conversations</p>}
            </li>
          )
        })}
      </ul>
    </figure>
  )
}

function Table({ sections }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-rule bg-surface shadow-card">
      <table className="w-full text-sm">
        <thead className="text-left text-slate">
          <tr className="border-b border-rule">
            <th className="px-4 py-2.5 font-medium">Group</th>
            <th className="px-4 py-2.5 text-right font-medium">Sent</th>
            <th className="px-4 py-2.5 text-right font-medium">Settled</th>
            <th className="px-4 py-2.5 text-right font-medium">Replied</th>
            <th className="px-4 py-2.5 text-right font-medium">Referred</th>
            <th className="px-4 py-2.5 text-right font-medium">Reply rate</th>
          </tr>
        </thead>
        {sections.map(([title, groups]) => (
          <tbody key={title}>
            <tr>
              <th colSpan={6} className="bg-sunken/60 px-4 py-1.5 text-left text-xs font-semibold text-slate">
                {title}
              </th>
            </tr>
            {groups.map((g) => (
              <tr key={g.label} className="border-t border-rule tabular-nums">
                <td className="px-4 py-2">{g.label}</td>
                <td className="px-4 py-2 text-right">{g.sent}</td>
                <td className="px-4 py-2 text-right">{g.settled}</td>
                <td className="px-4 py-2 text-right">{g.replied}</td>
                <td className="px-4 py-2 text-right">{g.referred}</td>
                <td className="px-4 py-2 text-right">{percent(g.replyRate)}</td>
              </tr>
            ))}
          </tbody>
        ))}
      </table>
    </div>
  )
}

export default function InsightsPage() {
  const query = useInsights()
  const [table, setTable] = useState(false)

  if (query.isPending) return <div className="skeleton mt-10 h-96 w-full max-w-5xl" />
  if (query.error) return <p className="mt-10 text-sm text-danger">{query.error.message}</p>
  const i = query.data
  const o = i.overall

  const sections = [
    ['By channel', i.byChannel],
    ['By who you messaged', i.byWho],
    ['By message length', i.byLength],
    ['Drafted with Claude or not', i.byDrafted],
    ['By follow-ups sent', i.repliesByFollowUps],
  ]
  const stages = STAGES.map((s) => ({ ...s, count: i.roleStages[s.value] ?? 0 }))

  return (
    <div className="max-w-5xl pt-6 pb-10 lg:pt-10">
      <h1 className="text-3xl font-semibold tracking-tight">Insights</h1>
      <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
        What&rsquo;s getting replies. Rates count conversations that have had a week to get an answer, so a message sent
        yesterday doesn&rsquo;t count against you.
      </p>

      {o.sent === 0 ? (
        <div className="mt-8 rounded-xl border border-dashed border-rule px-6 py-10 text-center">
          <BarChart3 className="mx-auto size-6 text-faint" aria-hidden />
          <p className="mt-3 font-medium">Nothing to measure yet</p>
          <p className="mx-auto mt-1 max-w-sm text-sm text-slate">
            Record the messages you send from a role&rsquo;s Track panel, and their replies, and this page shows what works.
          </p>
        </div>
      ) : (
        <>
          <div className="mt-6 grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
            <Tile
              label="Reply rate"
              value={percent(o.replyRate)}
              note={o.settled > 0 ? `${o.replied} of ${o.settled} settled conversations` : 'Waiting on replies'}
            />
            <Tile label="Referral rate" value={percent(o.referralRate)} note={`${o.referred} referred`} />
            <Tile
              label="Typical time to reply"
              value={i.medianDaysToReply == null ? '–' : `${i.medianDaysToReply < 1 ? '<1' : Math.round(i.medianDaysToReply)} d`}
              note="Median, first reply"
            />
            <Tile label="Applied this week" value={i.appliedLast7Days} note="Last 7 days" />
          </div>

          <div className="mt-8 flex items-center justify-between">
            <h2 className="text-lg font-semibold">Reply rate by group</h2>
            <button
              type="button"
              onClick={() => setTable((v) => !v)}
              aria-pressed={table}
              className="text-sm font-medium text-accent hover:underline"
            >
              {table ? 'See the charts' : 'See the numbers'}
            </button>
          </div>
          {table ? (
            <div className="mt-3">
              <Table sections={sections} />
            </div>
          ) : (
            <div className="mt-3 grid gap-4 md:grid-cols-2">
              {sections.map(([title, groups]) => (
                <Bars key={title} title={title} groups={groups} />
              ))}
            </div>
          )}

          <h2 className="mt-10 text-lg font-semibold">Roles by stage</h2>
          {/* Cells grow to fill the last row, so no empty cell shows at any width */}
          <ol className="mt-3 flex flex-wrap gap-px overflow-hidden rounded-xl border border-rule bg-rule shadow-card">
            {stages.map((s) => (
              <li key={s.value} className="min-w-[calc(50%-1px)] flex-1 bg-surface px-4 py-3 sm:min-w-[calc(25%-1px)] lg:min-w-0">
                <p className="text-xs text-slate">{s.label}</p>
                <p className="mt-0.5 text-xl font-semibold tabular-nums">{s.count}</p>
              </li>
            ))}
          </ol>
        </>
      )}
    </div>
  )
}
