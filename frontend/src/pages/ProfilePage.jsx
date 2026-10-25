import { useState } from 'react'
import { ShieldCheck, Sparkles } from 'lucide-react'
import { useAi, useProfile, useProfileFromResume, useSaveProfile } from '../api/hooks'
import { Field } from '../components/Field'
import { useToast } from '../components/Toasts'
import { timeAgo } from '../lib/dates'
import { inputClass, primaryButton, secondaryButton } from '../lib/ui'

const SECTIONS = [
  {
    title: 'You',
    fields: [
      ['firstName', 'First name'],
      ['lastName', 'Last name'],
      ['email', 'Email', 'email'],
      ['phone', 'Phone', 'tel'],
      ['city', 'City'],
      ['state', 'State'],
      ['country', 'Country'],
    ],
  },
  {
    title: 'Links',
    fields: [
      ['linkedinUrl', 'LinkedIn', 'url'],
      ['githubUrl', 'GitHub', 'url'],
      ['websiteUrl', 'Website or portfolio', 'url'],
    ],
  },
  {
    title: 'Work and education',
    fields: [
      ['currentTitle', 'Current title'],
      ['currentCompany', 'Current company'],
      ['school', 'School'],
      ['degree', 'Degree'],
      ['graduationYear', 'Graduation year'],
    ],
  },
]

const EMPTY = Object.fromEntries(SECTIONS.flatMap((s) => s.fields.map(([k]) => [k, ''])))

function ProfileForm({ initial, resumeFileName, updatedAt }) {
  const toast = useToast()
  const save = useSaveProfile()
  const fromResume = useProfileFromResume()
  const ai = useAi()
  const [values, setValues] = useState(() => ({ ...EMPTY, ...Object.fromEntries(Object.entries(initial).map(([k, v]) => [k, v ?? ''])) }))
  const [suggested, setSuggested] = useState(false)

  function readResume() {
    fromResume.mutate(undefined, {
      onSuccess: (s) => {
        // Only fill blanks: what the owner typed wins
        setValues((v) => Object.fromEntries(Object.entries(v).map(([k, cur]) => [k, cur || s[k] || ''])))
        setSuggested(true)
        toast('Filled the blanks from your resume. Check them, then save.')
      },
      onError: (e) => toast(e.message, { tone: 'error' }),
    })
  }

  function submit(e) {
    e.preventDefault()
    save.mutate(values, {
      onSuccess: () => {
        setSuggested(false)
        toast('Profile saved.')
      },
      onError: (err) => toast(err.message, { tone: 'error' }),
    })
  }

  return (
    <form onSubmit={submit} className="mt-8 space-y-6">
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-rule bg-surface p-4 shadow-card">
        <Sparkles className="size-5 text-faint" aria-hidden />
        <p className="min-w-0 flex-1 text-sm text-slate">
          {resumeFileName ? (
            <>
              Claude can copy your details from <span className="font-medium text-ink">{resumeFileName}</span>. It fills
              only the empty fields, and nothing is saved until you press Save.
            </>
          ) : (
            'Put your resume in the private folder and Claude can fill this in from it.'
          )}
        </p>
        <button
          type="button"
          onClick={readResume}
          disabled={!resumeFileName || !ai.data?.configured || fromResume.isPending}
          className={secondaryButton}
        >
          {fromResume.isPending ? 'Reading your resume…' : 'Fill from my resume'}
        </button>
      </div>

      {SECTIONS.map((section) => (
        <fieldset key={section.title} className="rounded-xl border border-rule bg-surface p-5 shadow-card">
          <legend className="px-1 text-sm font-semibold">{section.title}</legend>
          <div className="grid gap-4 sm:grid-cols-2">
            {section.fields.map(([key, label, type]) => (
              <Field key={key} id={`profile-${key}`} label={label}>
                <input
                  id={`profile-${key}`}
                  type={type ?? 'text'}
                  value={values[key]}
                  onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
                  className={inputClass}
                />
              </Field>
            ))}
          </div>
        </fieldset>
      ))}

      <div className="flex flex-wrap items-center gap-3">
        <button type="submit" disabled={save.isPending} className={primaryButton}>
          {save.isPending ? 'Saving…' : 'Save profile'}
        </button>
        {suggested && <span className="text-sm text-warn">Filled from your resume; not saved yet.</span>}
        {!suggested && updatedAt && <span className="text-sm text-faint">Saved {timeAgo(updatedAt)}</span>}
      </div>
    </form>
  )
}

export default function ProfilePage() {
  const profile = useProfile()

  return (
    <div className="max-w-4xl pt-6 lg:pt-10">
      <h1 className="text-3xl font-semibold tracking-tight">Profile</h1>
      <p className="mt-2 max-w-[62ch] text-[15px] text-slate">
        What the browser extension fills into application forms, with your resume attached. You always check the form and
        press submit yourself.
      </p>
      <p className="mt-3 flex max-w-[62ch] items-start gap-2 text-sm text-slate">
        <ShieldCheck className="mt-0.5 size-4 shrink-0 text-ok" aria-hidden />
        Work authorization, sponsorship, EEO and consent questions are never stored or filled. You answer those on each form.
      </p>
      {profile.isPending && <div className="skeleton mt-8 h-96 w-full" />}
      {profile.error && <p className="mt-8 text-sm text-danger">{profile.error.message}</p>}
      {profile.isSuccess && (
        <ProfileForm
          key={profile.data.updatedAt ?? 'new'}
          initial={profile.data.fields}
          resumeFileName={profile.data.resumeFileName}
          updatedAt={profile.data.updatedAt}
        />
      )}
    </div>
  )
}
