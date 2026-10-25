import { useState } from 'react'
import { CircleAlert, CircleCheck, Plus } from 'lucide-react'
import { useCreateCompany, useDetect } from '../api/hooks'
import { sourceLabel, boardLabel } from '../lib/labels'
import { useDebounced } from '../lib/useDebounced'
import { useToast } from './Toasts'

const input =
  'mt-1.5 h-10 w-full rounded-lg border border-rule bg-paper px-3 text-sm placeholder:text-faint focus:border-accent focus:outline-none'

export default function AddCompanyForm() {
  const toast = useToast()
  const [url, setUrl] = useState('')
  const [name, setName] = useState('')
  const [search, setSearch] = useState('')
  const detect = useDetect(useDebounced(url, 400))
  const create = useCreateCompany()
  const detected = detect.data
  const needsSearch = detected?.searchBased && !search.trim()

  function submit(e) {
    e.preventDefault()
    create.mutate(
      { careersUrl: url.trim(), name: name.trim() || undefined, search: search.trim() || undefined },
      {
        onSuccess: (company) => {
          toast(`Added ${company.name}. Sync it to pull its open roles.`)
          setUrl('')
          setName('')
          setSearch('')
        },
      },
    )
  }

  let hint = (
    <span className="text-faint">
      Greenhouse, Lever, Ashby, Workday and Avature boards, amazon.jobs and Google Careers.
    </span>
  )
  if (detected) {
    const board = boardLabel(detected.ats, detected.board)
    hint = (
      <span className="inline-flex items-center gap-1.5 text-ok">
        <CircleCheck className="size-3.5" aria-hidden />
        {sourceLabel(detected.ats)}
        {board && <> board &ldquo;{board}&rdquo;</>}
      </span>
    )
  } else if (detect.error) {
    hint = (
      <span className="inline-flex items-start gap-1.5 text-danger">
        <CircleAlert className="mt-0.5 size-3.5 shrink-0" aria-hidden />
        {detect.error.message}
      </span>
    )
  }

  return (
    <form onSubmit={submit} className="rounded-xl border border-rule bg-surface p-5 shadow-card">
      <h2 className="font-semibold">Add a company</h2>
      <div className="mt-4 grid gap-4 md:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div>
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
            className={input}
            aria-describedby="careers-hint"
          />
          <p id="careers-hint" className="mt-1.5 text-xs">
            {hint}
          </p>
        </div>
        <div>
          <label htmlFor="company-name" className="text-sm font-medium">
            Name <span className="font-normal text-faint">(optional)</span>
          </label>
          <input
            id="company-name"
            type="text"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Taken from the link"
            className={input}
          />
        </div>
        <div className="md:col-span-2">
          <label htmlFor="company-search" className="text-sm font-medium">
            Search phrases{' '}
            <span className="font-normal text-faint">{detected?.searchBased ? '(recommended)' : '(optional)'}</span>
          </label>
          <input
            id="company-search"
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="software engineer, backend"
            className={input}
            aria-describedby="search-hint"
          />
          <p id="search-hint" className={`mt-1.5 text-xs ${needsSearch ? 'text-warn' : 'text-faint'}`}>
            {needsSearch
              ? 'This company posts thousands of roles. Add the kinds you want, separated by commas.'
              : 'Only keep roles whose title has every word of one phrase. Leave empty for every role.'}
          </p>
        </div>
      </div>
      <div className="mt-5 flex items-center gap-3">
        <button
          type="submit"
          disabled={create.isPending || !detected}
          className="inline-flex h-10 items-center gap-2 rounded-lg bg-ink px-4 text-sm font-semibold text-paper hover:opacity-90 disabled:opacity-40"
        >
          <Plus className="size-4" aria-hidden />
          {create.isPending ? 'Adding…' : 'Add company'}
        </button>
        {create.error && <p className="text-sm text-danger">{create.error.message}</p>}
      </div>
    </form>
  )
}
