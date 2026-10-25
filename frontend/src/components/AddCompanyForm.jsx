import { useState } from 'react'
import { useCreateCompany, useDetect } from '../api/hooks'
import { ATS_LABELS } from '../lib/labels'
import { useDebounced } from '../lib/useDebounced'

export default function AddCompanyForm() {
  const [url, setUrl] = useState('')
  const [name, setName] = useState('')
  const detect = useDetect(useDebounced(url, 400))
  const create = useCreateCompany()

  function submit(e) {
    e.preventDefault()
    create.mutate(
      { careersUrl: url.trim(), name: name.trim() || undefined },
      {
        onSuccess: () => {
          setUrl('')
          setName('')
        },
      },
    )
  }

  let hint = 'For example jobs.lever.co/palantir or boards.greenhouse.io/airbnb'
  let hintClass = 'text-slate'
  if (detect.data) {
    hint = `${ATS_LABELS[detect.data.ats]} board "${detect.data.board}"`
    hintClass = 'text-ok'
  } else if (detect.error) {
    hint = detect.error.message
    hintClass = 'text-danger'
  }

  return (
    <form onSubmit={submit} className="mt-6 rounded-lg border border-rule bg-surface p-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start">
        <div className="flex-1">
          <label htmlFor="careers-url" className="text-sm font-medium">
            Careers link
          </label>
          <input
            id="careers-url"
            type="text"
            inputMode="url"
            required
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://jobs.ashbyhq.com/openai"
            className="mt-1 w-full rounded-md border border-rule bg-paper px-3 py-2 text-sm focus:border-ink focus:outline-none"
            aria-describedby="careers-hint"
          />
          <span id="careers-hint" className={`mt-1 block text-xs ${hintClass}`}>
            {hint}
          </span>
        </div>
        <label className="sm:w-48">
          <span className="text-sm font-medium">Name</span>
          <input
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="From the link"
            className="mt-1 w-full rounded-md border border-rule bg-paper px-3 py-2 text-sm focus:border-ink focus:outline-none"
          />
        </label>
        <button
          type="submit"
          disabled={create.isPending || !detect.data}
          className="rounded-md bg-ink px-4 py-2 text-sm font-medium text-paper hover:opacity-90 disabled:opacity-50 sm:mt-6"
        >
          {create.isPending ? 'Adding…' : 'Add company'}
        </button>
      </div>
      {create.error && <p className="mt-3 text-sm text-danger">{create.error.message}</p>}
    </form>
  )
}
