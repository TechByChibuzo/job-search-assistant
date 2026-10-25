/** A labelled form field. Children are the control; the label points at it by id. */
export function Field({ id, label, hint, optional, children, className = '' }) {
  return (
    <div className={className}>
      <label htmlFor={id} className="text-sm font-medium">
        {label} {optional && <span className="font-normal text-faint">(optional)</span>}
      </label>
      {children}
      {hint && <p className="mt-1.5 text-xs text-faint">{hint}</p>}
    </div>
  )
}
