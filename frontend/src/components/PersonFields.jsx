import { Field } from './Field'
import { inputClass } from '../lib/ui'
import { KINDS } from '../lib/tracker'

/** The fields for a new or edited person. {@code value} is a plain object; onChange gets a patch. */
export default function PersonFields({ value, onChange, idPrefix = 'person' }) {
  const set = (key) => (e) => onChange({ [key]: e.target.value })
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field id={`${idPrefix}-name`} label="Name">
        <input id={`${idPrefix}-name`} required value={value.name ?? ''} onChange={set('name')} className={inputClass} />
      </Field>
      <Field id={`${idPrefix}-kind`} label="Who they are">
        <select id={`${idPrefix}-kind`} value={value.kind ?? 'ENGINEER'} onChange={set('kind')} className={inputClass}>
          {KINDS.map((k) => (
            <option key={k.value} value={k.value}>
              {k.label}
            </option>
          ))}
        </select>
      </Field>
      <Field id={`${idPrefix}-title`} label="Title" optional>
        <input
          id={`${idPrefix}-title`}
          value={value.title ?? ''}
          onChange={set('title')}
          placeholder="Senior Software Engineer"
          className={inputClass}
        />
      </Field>
      <Field id={`${idPrefix}-linkedin`} label="LinkedIn profile" optional>
        <input
          id={`${idPrefix}-linkedin`}
          inputMode="url"
          value={value.linkedinUrl ?? ''}
          onChange={set('linkedinUrl')}
          placeholder="https://www.linkedin.com/in/…"
          className={inputClass}
        />
      </Field>
    </div>
  )
}
