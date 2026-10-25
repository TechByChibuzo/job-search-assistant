import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import LogMessageDialog from './LogMessageDialog'
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

const json = (body) => Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }))

afterEach(() => vi.restoreAllMocks())

describe('LogMessageDialog', () => {
  it('drafts a connection note for a saved person and counts it against LinkedIn’s limit', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      if (url.startsWith('/api/contacts')) return json([{ id: 4, name: 'Ada Lovelace', title: 'Engineer' }])
      if (url === '/api/ai') return json({ configured: true, resume: { fileName: 'resume.pdf' } })
      if (url === '/api/drafts') return json({ message: 'Hi Ada, fellow Boilermaker here.', subject: '', characters: 32, limit: 300 })
      return json({})
    })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <ToastProvider>
          <LogMessageDialog open onClose={() => {}} companyId={2} companyName="Microsoft" jobId={9} />
        </ToastProvider>
      </QueryClientProvider>,
    )

    await screen.findByRole('option', { name: /Ada Lovelace/ })
    await userEvent.selectOptions(screen.getByLabelText('Who at Microsoft?'), '4')
    await userEvent.click(await screen.findByRole('button', { name: 'Draft with Claude' }))

    expect(await screen.findByDisplayValue('Hi Ada, fellow Boilermaker here.')).toBeInTheDocument()
    expect(screen.getByText('32/300')).toBeInTheDocument()
    const draftCall = fetch.mock.calls.find(([url]) => url === '/api/drafts')
    expect(JSON.parse(draftCall[1].body)).toEqual({ kind: 'CONNECTION_NOTE', channel: 'LINKEDIN', jobId: 9, contactId: 4 })
  })
})
