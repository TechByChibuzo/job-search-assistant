import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import InsightsPage from './InsightsPage'

const json = (body) => Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }))

afterEach(() => vi.restoreAllMocks())

const group = (label, sent, settled, replied, enough = settled >= 5) => ({
  label,
  sent,
  settled,
  replied,
  referred: 0,
  replyRate: settled ? replied / settled : null,
  referralRate: settled ? 0 : null,
  enough,
})

const insights = {
  overall: { ...group('All', 12, 10, 4), referred: 2, referralRate: 0.2 },
  medianDaysToReply: 2.4,
  byChannel: [group('LinkedIn', 9, 8, 3), group('Email', 3, 2, 1)],
  byWho: [],
  byLength: [],
  byDrafted: [group('Drafted with Claude', 6, 5, 2), group('Written yourself', 6, 5, 2)],
  repliesByFollowUps: [],
  roleStages: { SAVED: 3, REACHING_OUT: 2, APPLIED: 1 },
  appliedLast7Days: 1,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <InsightsPage />
    </QueryClientProvider>,
  )
}

describe('InsightsPage', () => {
  it('shows the headline numbers and a bar per group, marking small groups as early signals', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => json(insights))
    renderPage()

    const tile = (await screen.findByText('Reply rate')).parentElement
    expect(tile).toHaveTextContent('40%')
    expect(screen.getByText('4 of 10 settled conversations')).toBeInTheDocument()
    expect(screen.getByText('2 d')).toBeInTheDocument()
    expect(screen.getByText('3 of 8')).toBeInTheDocument()
    expect(screen.getAllByText('Early signal: under 5 conversations')).toHaveLength(1)
    expect(screen.queryByText('By who you messaged')).not.toBeInTheDocument()
  })

  it('shows the same numbers as a table', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => json(insights))
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'See the numbers' }))
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /LinkedIn 9 8 3 0 38%/ })).toBeInTheDocument()
  })

  it('explains what to do when nothing has been sent yet', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      json({ ...insights, overall: group('All', 0, 0, 0), byChannel: [], byDrafted: [] }),
    )
    renderPage()
    expect(await screen.findByText('Nothing to measure yet')).toBeInTheDocument()
  })
})
