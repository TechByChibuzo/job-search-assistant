/** A role's match score as a small badge: strong fits stand out, weak ones recede. */
function scoreTone(score) {
  if (score >= 80) return 'bg-ok-wash text-ok'
  if (score >= 60) return 'bg-sunken text-ink'
  return 'bg-sunken text-faint'
}

export default function FitScore({ score, size = 'sm' }) {
  if (score == null) return null
  const box = size === 'lg' ? 'h-12 min-w-12 px-2 text-xl rounded-xl' : 'h-6 min-w-8 px-1.5 text-xs rounded-md'
  return (
    <span
      className={`inline-flex items-center justify-center font-semibold tabular-nums ${box} ${scoreTone(score)}`}
      title={`Fit ${score} out of 100`}
      aria-label={`Fit ${score} out of 100`}
    >
      {score}
    </span>
  )
}
