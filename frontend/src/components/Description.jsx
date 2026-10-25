import { parseDescription } from '../lib/description'

export default function Description({ text }) {
  if (!text) {
    return <p className="text-slate">This posting has no description in its feed. Open the posting to read it.</p>
  }
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
