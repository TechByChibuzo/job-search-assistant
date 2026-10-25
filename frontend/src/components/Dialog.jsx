import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'

/** A modal built on <dialog>: focus trapping, Escape and the backdrop come from the browser. */
export default function Dialog({ open, onClose, title, children, wide = false }) {
  const ref = useRef(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className={`m-auto w-[calc(100%-2rem)] rounded-2xl border border-rule bg-surface p-0 text-ink shadow-card backdrop:bg-black/40 backdrop:backdrop-blur-[2px] ${
        wide ? 'max-w-2xl' : 'max-w-lg'
      }`}
    >
      {open && (
        <div className="p-6">
          <div className="flex items-start justify-between gap-4">
            <h2 className="text-lg font-semibold tracking-tight">{title}</h2>
            <button type="button" onClick={onClose} className="rounded-md p-1 text-faint hover:text-ink" aria-label="Close">
              <X className="size-5" />
            </button>
          </div>
          <div className="mt-5">{children}</div>
        </div>
      )}
    </dialog>
  )
}
