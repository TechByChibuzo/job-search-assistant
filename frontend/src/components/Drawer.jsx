import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'

/** A panel that slides over the right edge, built on <dialog> like Dialog. */
export default function Drawer({ open, onClose, label, children }) {
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
      aria-label={label}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="scroll-quiet ml-auto mr-0 h-dvh max-h-dvh w-full max-w-2xl overflow-y-auto border-l border-rule bg-surface p-0 text-ink shadow-card backdrop:bg-black/30"
    >
      {open && (
        <div className="relative">
          <button
            type="button"
            onClick={onClose}
            className="absolute top-4 right-4 z-10 rounded-md p-1.5 text-faint hover:bg-sunken hover:text-ink"
            aria-label="Close"
          >
            <X className="size-5" />
          </button>
          {children}
        </div>
      )}
    </dialog>
  )
}
