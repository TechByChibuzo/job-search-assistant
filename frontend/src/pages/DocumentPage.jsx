import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AlertTriangle, ArrowLeft, Pencil, Printer, Trash2, X } from 'lucide-react'
import { useDeleteDocument, useDocument, useEditDocument } from '../api/hooks'
import { useToast } from '../components/Toasts'
import { formatDate } from '../lib/dates'
import { primaryButton, secondaryButton } from '../lib/ui'
import 'computer-modern/cmu-serif.css'

/*
 * The paper is white in both themes, so marks on it use fixed light colours rather than the theme's tokens,
 * which turn dark (and muddy on white) in dark mode.
 */
const FLAGGED = 'rounded bg-[#fff6dc] print:bg-transparent'

/** What the check found about one part, and what can be done about it, on screen only. */
function Problems({ problems, children }) {
  if (!problems?.length) return null
  return (
    <span className="mt-1 flex flex-wrap items-center gap-1.5 print:hidden">
      {problems.map((p) => (
        <span key={p} className="inline-flex items-center gap-1 rounded bg-[#ffe9a8] px-1.5 py-0.5 text-[11px] font-medium text-[#7a5200]">
          <AlertTriangle className="size-3" aria-hidden />
          {p}
        </span>
      ))}
      {children}
    </span>
  )
}

function Editable({ editing, value, onChange, rows = 2, className = '' }) {
  if (!editing) return <span className={className}>{value}</span>
  return (
    <textarea
      value={value}
      rows={rows}
      onChange={(e) => onChange(e.target.value)}
      className="w-full rounded-md border border-black/20 bg-white px-2 py-1.5 text-sm text-black focus:border-[#3a55d9] focus:outline-none"
    />
  )
}

/** "**Kubernetes** clusters" → Kubernetes in bold, as the resume marks key terms. */
function Rich({ text }) {
  return String(text ?? '')
    .split(/\*\*(.+?)\*\*/g)
    .map((part, i) => (i % 2 ? <b key={i}>{part}</b> : part))
}

/**
 * A contact item becomes a link when it is one (an email address, or a site such as github.com/name) and the app
 * has checked it against the resume, so a link planted through a job posting can't end up clickable.
 */
function contactHref(item) {
  if (/^\S+@\S+\.\S+$/.test(item)) return `mailto:${item}`
  if (/^(https?:\/\/)?[\w-]+(\.[\w-]+)+\/\S*$/.test(item)) return item.startsWith('http') ? item : `https://${item}`
  return null
}

/**
 * Older tailored resumes were saved in a simpler shape (a contact line, a summary, a flat skills list); they're
 * read into the current one so they still open.
 */
function normalize(doc) {
  const sections = (doc.sections ?? []).map((s) => ({
    ...s,
    skillGroups: s.skillGroups ?? [],
    entries: (s.entries ?? []).map((e) => ({ ...e, titleRight: e.titleRight ?? e.dates ?? '' })),
  }))
  if (doc.skills?.length) sections.push({ heading: 'Skills', entries: [], skillGroups: [{ label: '', items: doc.skills }] })
  return {
    ...doc,
    contact: doc.contact ?? (doc.contactLine ? doc.contactLine.split('|').map((x) => x.trim()) : []),
    sections,
  }
}

/** Jake's resume template, the owner's layout: what LaTeX sets, in CSS (see .jake in index.css). */
function Resume({ doc, editing, update }) {
  const c = normalize(doc)
  return (
    <div className="jake">
      <header className="jake-head">
        <h1>{c.name}</h1>
        <p>
          {c.contact.map((item, i) => {
            const href = c.contactChecked ? contactHref(item) : null
            return (
              <span key={i}>
                {i > 0 && ' | '}
                {href ? <a href={href}>{item}</a> : item}
              </span>
            )
          })}
        </p>
      </header>
      {c.summary && <p className="jake-summary">{c.summary}</p>}
      {c.sections.map((section, si) => (
        <section key={si}>
          <h2>{section.heading}</h2>
          {section.entries.map((entry, ei) => (
            <div key={ei} className="jake-entry">
              <div className="jake-row">
                <span>
                  <b>{entry.title}</b>
                  {entry.detail && (
                    <>
                      {' | '}
                      <i>{entry.detail}</i>
                    </>
                  )}
                </span>
                <span>{entry.titleRight}</span>
              </div>
              {(entry.subtitle || entry.subtitleRight) && (
                <div className="jake-row jake-sub">
                  <i>{entry.subtitle}</i>
                  <i>{entry.subtitleRight}</i>
                </div>
              )}
              <Problems problems={entry.problems} />
              {entry.bullets?.length > 0 && (
                <ul>
                  {entry.bullets.map((b, bi) => (
                    <li key={bi} className={b.problems?.length ? FLAGGED : undefined}>
                      {editing ? (
                        <Editable
                          editing
                          value={b.text}
                          onChange={(v) => update((d) => (d.sections[si].entries[ei].bullets[bi].text = v))}
                        />
                      ) : (
                        <Rich text={b.text} />
                      )}
                      <Problems problems={b.problems}>
                        {!editing && (
                          <button
                            type="button"
                            onClick={() => update((d) => d.sections[si].entries[ei].bullets.splice(bi, 1), true)}
                            className="inline-flex items-center gap-0.5 rounded px-1 py-0.5 text-[11px] font-medium text-black/60 hover:bg-black/5 hover:text-[#b42318]"
                          >
                            <X className="size-3" aria-hidden />
                            Remove this line
                          </button>
                        )}
                      </Problems>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))}
          {section.skillGroups.length > 0 && (
            <div className="jake-skills">
              {section.skillGroups.map((g, gi) => (
                <p key={gi}>
                  {g.label && <b>{g.label}</b>}
                  {g.label && ': '}
                  {g.items.join(', ')}
                </p>
              ))}
            </div>
          )}
        </section>
      ))}
    </div>
  )
}

function Letter({ doc, editing, update }) {
  return (
    <div className="jake-letter space-y-3">
      <p>{doc.greeting}</p>
      {doc.paragraphs?.map((p, i) => (
        <div key={i} className={p.problems?.length ? FLAGGED : undefined}>
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
        {content.droppedContact?.length > 0 && (
          <p className="mt-2 rounded-lg bg-warn-wash px-4 py-3 text-sm text-warn">
            Removed from your contact line because your resume doesn&rsquo;t have them: {content.droppedContact.join(', ')}.
            If one is a link you didn&rsquo;t write, the job posting may have tried to slip it in.
          </p>
        )}
        <p className="mt-2 text-xs text-faint">
          Made {formatDate(d.createdAt)} with {d.model}. Printing tip: choose &ldquo;Save as PDF&rdquo; and turn off headers and
          footers.
        </p>
      </div>

      <article className="mx-auto mt-6 max-w-[8.5in] bg-white px-5 py-7 text-black sm:px-[0.5in] sm:py-[0.5in] shadow-card ring-1 ring-rule print:mt-0 print:max-w-none print:p-0 print:shadow-none print:ring-0">
        {d.kind === 'RESUME' ? (
          <Resume doc={content} editing={editing} update={update} />
        ) : (
          <Letter doc={content} editing={editing} update={update} />
        )}
      </article>
    </div>
  )
}
