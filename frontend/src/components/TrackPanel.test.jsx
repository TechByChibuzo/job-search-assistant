import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import TrackPanel from './TrackPanel'
import { ToastProvider } from './Toasts'

// jsdom has no <dialog> methods
beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () {
    this.open = true
  }
  HTMLDialogElement.prototype.close = function () {
    this.open = false
  }
})

const json = (body, status = 200) =>
  Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))

afterEach(() => vi.restoreAllMocks())

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <TrackPanel job={{ id: 9, companyId: 2, companyName: 'Deloitte', title: 'Software Engineer II' }} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('TrackPanel', () => {
  it('marks an untracked role as applied in one click', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      if (url === '/api/jobs/9/tracking') return json({ pursuit: null, outreach: [] })
      if (url === '/api/pursuits' && init?.method === 'POST') return json({ id: 3, stage: 'SAVED' }, 201)
      if (url === '/api/pursuits/3') return json({ id: 3, stage: 'APPLIED', appliedOn: '2026-10-25' })
      if (url.startsWith('/api/contacts')) return json([])
      return json({})
    })
    renderPanel()

    await userEvent.click(await screen.findByRole('button', { name: 'I applied' }))

    expect(await screen.findByText('Marked as applied. It’s in your tracker.')).toBeInTheDocument()
    const patch = fetch.mock.calls.find(([url, init]) => url === '/api/pursuits/3' && init?.method === 'PATCH')
    expect(JSON.parse(patch[1].body)).toEqual({ stage: 'APPLIED' })
  })

  it('offers it only until the role is applied to', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((url) =>
      url === '/api/jobs/9/tracking'
        ? json({ pursuit: { id: 3, stage: 'INTERVIEWING' }, outreach: [] })
        : json(url.startsWith('/api/contacts') ? [] : {}),
    )
    renderPanel()

    await screen.findByRole('combobox', { name: 'Stage' })
    expect(screen.queryByRole('button', { name: 'I applied' })).not.toBeInTheDocument()
  })
})
