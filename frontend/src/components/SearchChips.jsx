import { splitSearch } from '../lib/labels'

/** Search phrases as quiet chips; "Every role" when there are none. */
export default function SearchChips({ search }) {
  const phrases = splitSearch(search)
  if (phrases.length === 0) return <span className="text-sm text-faint">Every role</span>
  return (
    <span className="flex flex-wrap gap-1.5">
      {phrases.map((p) => (
        <span key={p} className="rounded-md bg-sunken px-2 py-0.5 text-xs font-medium text-slate">
          {p}
        </span>
      ))}
    </span>
  )
}
