import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, Pencil, Printer, Trash2, X } from 'lucide-react'
import { useDeleteDocument, useDocument, useEditDocument } from '../api/hooks'
import { useToast } from '../components/Toasts'
import { formatDate } from '../lib/dates'
import { primaryButton, secondaryButton, textareaClass } from '../lib/ui'

/** What the check found about one part, on screen only. */
function Problems({ problems }) {
  if (!problems?.length) return null
  return (
    <span className="mt-1 flex flex-wrap gap-1.5 print:hidden">
      {problems.map((p) => (
        <span key={p} className="inline-flex items-center gap-1 rounded bg-warn-wash px-1.5 py-0.5 text-[11px] font-medium text-warn">
          <AlertTriangle className="size-3" aria-hidden />
          {p}
        </span>
      ))}
    </span>
  )
}

function Editable({ editing, value, onChange, rows = 2, className = '' }) {
  if (!editing) return <span className={className}>{value}</span>
  return <textarea value={value} rows={rows} onChange={(e) => onChange(e.target.value)} className={`${textareaClass} mt-0 text-sm`} />
}

function Resume({ doc, editing, update }) {
  const c = doc
  return (
    <>
      <header className="border-b border-black/15 pb-3 text-center">
        <h1 className="text-2xl font-bold tracking-tight">{c.name}</h1>
        <p className="mt-1 text-[12px] text-black/70">{c.contactLine}</p>
      </header>
      {(c.summary || editing) && (
        <p className="mt-3 text-[13px] leading-snug">
          <Editable editing={editing} value={c.summary} onChange={(v) => update((d) => (d.summary = v))} />
        </p>
      )}
      {c.sections?.map((section, si) => (
        <section key={si} className="mt-4">
          <h2 className="border-b border-black/15 pb-0.5 text-[12px] font-bold tracking-wide uppercase">{section.heading}</h2>
          {section.entries?.map((entry, ei) => (
            <div key={ei} className="mt-2 break-inside-avoid">
              <div className="flex items-baseline justify-between gap-4 text-[13px]">
                <span>
                  <span className="font-semibold">{entry.title}</span>
                  {entry.subtitle && <span className="text-black/75">, {entry.subtitle}</span>}
                </span>
                <span className="shrink-0 text-[12px] text-black/65">{entry.dates}</span>
              </div>
              <ul className="mt-1 list-disc space-y-0.5 pl-5 text-[13px] leading-snug">
                {entry.bullets?.map((b, bi) => (
                  <li key={bi} className={b.problems?.length ? 'rounded bg-warn-wash/60 print:bg-transparent' : undefined}>
                    <Editable editing={editing} value={b.text} onChange={(v) => update((d) => (d.sections[si].entries[ei].bullets[bi].text = v))} />
                    <Problems problems={b.problems} />
                    {b.problems?.length > 0 && !editing && (
                      <button
                        type="button"
                        onClick={() => update((d) => d.sections[si].entries[ei].bullets.splice(bi, 1), true)}
                        className="ml-2 inline-flex items-center gap-0.5 text-[11px] font-medium text-slate hover:text-danger print:hidden"
                      >
                        <X className="size-3" aria-hidden />
                        Remove
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </section>
      ))}
      {c.skills?.length > 0 && (
        <section className="mt-4">
          <h2 className="border-b border-black/15 pb-0.5 text-[12px] font-bold tracking-wide uppercase">Skills</h2>
          <p className="mt-1.5 text-[13px]">{c.skills.join(', ')}</p>
        </section>
      )}
    </>
  )
}

function Letter({ doc, editing, update }) {
  return (
    <div className="space-y-3 text-[14px] leading-relaxed">
      <p>{doc.greeting}</p>
      {doc.paragraphs?.map((p, i) => (
        <div key={i} className={p.problems?.length ? 'rounded bg-warn-wash/60 print:bg-transparent' : undefined}>
          <p>
            <Editable editing={editing} rows={5} value={p.text} onChange={(v) => update((d) => (d.paragraphs[i].text = v))} />
          </p>
          <Problems problems={p.problems} />
        </div>
      ))}
      <p className="pt-2">{doc.closing}</p>
      <p>{doc.signature}</p>
    </div>
  )
}

export default function DocumentPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const query = useDocument(id)
  const edit = useEditDocument()
  const remove = useDeleteDocument()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(null)

  if (query.isPending) return <div className="skeleton mt-10 h-[60vh] w-full max-w-3xl" />
  if (query.error) return <p className="mt-10 text-sm text-danger">{query.error.message}</p>

  const d = query.data
  const content = draft ?? d.content

  /** Changes a copy of the content; {@code save} sends it straight away (used for removing a flagged line). */
  function update(change, save = false) {
    const next = structuredClone(content)
    change(next)
    if (save) {
      next.flagged = Math.max(0, (next.flagged ?? 1) - 1)
      edit.mutate({ id: d.id, content: next }, { onSuccess: () => setDraft(null) })
    } else {
      setDraft(next)
    }
  }

  function saveEdits() {
    edit.mutate(
      { id: d.id, content },
      {
        onSuccess: () => {
          setDraft(null)
          setEditing(false)
          toast('Saved.')
        },
      },
    )
  }

  const title = d.kind === 'RESUME' ? 'Tailored resume' : 'Cover letter'

  return (
    <div className="pt-6 pb-10 lg:pt-10 print:p-0">
      <div className="mx-auto max-w-[8.5in] print:hidden">
        <Link to={`/jobs?job=${d.jobId}`} className="inline-flex items-center gap-1.5 text-sm text-slate hover:text-ink">
          <ArrowLeft className="size-4" aria-hidden />
          {d.jobTitle} at {d.companyName}
        </Link>
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <h1 className="mr-auto text-2xl font-semibold tracking-tight">{title}</h1>
          {editing ? (
            <>
              <button type="button" onClick={() => (setDraft(null), setEditing(false))} className={secondaryButton}>
                Cancel
              </button>
              <button type="button" onClick={saveEdits} disabled={edit.isPending} className={primaryButton}>
                Save changes
              </button>
            </>
          ) : (
            <>
              <button type="button" onClick={() => setEditing(true)} className={secondaryButton}>
                <Pencil className="size-4" aria-hidden />
                Edit
              </button>
              <button type="button" onClick={() => window.print()} className={primaryButton}>
                <Printer className="size-4" aria-hidden />
                Print or save as PDF
              </button>
              <button
                type="button"
                aria-label="Delete"
                title="Delete"
                onClick={() =>
                  window.confirm(`Delete this ${title.toLowerCase()}?`) &&
                  remove.mutate(d.id, { onSuccess: () => navigate(`/jobs?job=${d.jobId}`) })
                }
                className="inline-flex size-10 items-center justify-center rounded-lg text-slate hover:bg-sunken hover:text-danger"
              >
                <Trash2 className="size-4" />
              </button>
            </>
          )}
        </div>
        {!content.checked && (
          <p className="mt-3 rounded-lg bg-warn-wash px-4 py-3 text-sm text-warn">
            Your resume PDF has no readable text (it may be a scan), so nothing here could be checked against it. Read every line
            before using it.
          </p>
        )}
        {content.checked && content.flagged > 0 && (
          <p className="mt-3 rounded-lg bg-warn-wash px-4 py-3 text-sm text-warn">
            {content.flagged} part{content.flagged === 1 ? '' : 's'} couldn&rsquo;t be backed by your resume. They&rsquo;re marked
            below: fix or remove them before using this. The marks don&rsquo;t print.
          </p>
        )}
        {content.droppedSkills?.length > 0 && (
          <p className="mt-2 text-sm text-slate">
            Left out of skills because your resume doesn&rsquo;t name them: {content.droppedSkills.join(', ')}.
          </p>
        )}
        <p className="mt-2 text-xs text-faint">
          Made {formatDate(d.createdAt)} with {d.model}. Printing tip: choose &ldquo;Save as PDF&rdquo; and turn off headers and
          footers.
        </p>
      </div>

      <article className="mx-auto mt-6 max-w-[8.5in] bg-white px-[0.7in] py-[0.6in] text-black shadow-card ring-1 ring-rule print:mt-0 print:max-w-none print:p-0 print:shadow-none print:ring-0">
        {d.kind === 'RESUME' ? (
          <Resume doc={content} editing={editing} update={update} />
        ) : (
          <Letter doc={content} editing={editing} update={update} />
        )}
      </article>
    </div>
  )
}
