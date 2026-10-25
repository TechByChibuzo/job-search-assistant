import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import Conversation from './Conversation'
import { ToastProvider } from './Toasts'

const awaiting = {
  id: 7,
  person: { id: 1, name: 'Ada Lovelace', title: 'Staff Engineer', kind: 'ENGINEER', linkedinUrl: null, companyName: 'Microsoft' },
  role: { id: 3, title: 'Software Engineer' },
  status: 'AWAITING_REPLY',
  message: 'Hi Ada',
  firstSentAt: new Date(Date.now() - 6 * 86_400_000).toISOString(),
  lastContactedAt: new Date(Date.now() - 6 * 86_400_000).toISOString(),
  followUps: 0,
  followUpOn: '2026-10-24',
  followUpDue: true,
  events: [{ id: 1, type: 'SENT', at: new Date().toISOString(), note: null }],
}

function renderConversation(outreach) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <ul>
          <Conversation outreach={outreach} />
        </ul>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.restoreAllMocks())

describe('Conversation', () => {
  it('flags a due follow-up and records it', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ ...awaiting, followUps: 1, followUpDue: false }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    renderConversation(awaiting)

    expect(screen.getByText('Follow-up due')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'I followed up' }))

    expect(fetch).toHaveBeenCalledWith(
      '/api/outreach/7/events',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ type: 'FOLLOW_UP' }) }),
    )
    expect(await screen.findByText('Follow-up recorded.')).toBeInTheDocument()
  })

  it('offers only the actions that still make sense', () => {
    renderConversation({ ...awaiting, status: 'REFERRED', followUpDue: false, followUpOn: null })

    expect(screen.getByText('Referred you')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'I followed up' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'They referred me' })).not.toBeInTheDocument()
  })
})
