import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import FitPanel from './FitPanel'
import { ToastProvider } from './Toasts'

function renderPanel(ai, job, detail = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
    Promise.resolve(new Response(JSON.stringify(ai), { status: 200, headers: { 'Content-Type': 'application/json' } })),
  )
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <FitPanel job={{ id: 1, ...job }} detail={detail} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

const ready = { configured: true, model: 'claude-opus-5-5', dailyLimit: 30, resume: { fileName: 'resume.pdf' } }

afterEach(() => vi.restoreAllMocks())

describe('FitPanel', () => {
  it('explains what is needed before Claude can score', async () => {
    renderPanel({ ...ready, configured: false, resume: null }, { matchScore: null })

    expect(await screen.findByText(/Add your resume and an Anthropic API key/)).toBeInTheDocument()
  })

  it('offers to check the fit when ready', async () => {
    renderPanel(ready, { matchScore: null })

    // Enabled once the app knows Claude and the resume are ready
    await waitFor(() => expect(screen.getByRole('button', { name: 'Check my fit' })).toBeEnabled())
  })

  it('shows the score, summary, matches and gaps', async () => {
    renderPanel(
      ready,
      { matchScore: 84, matchSummary: 'Strong backend match.' },
      { matchStrengths: ['Java and Spring Boot'], matchGaps: ['Kubernetes'], matchedAt: new Date().toISOString() },
    )

    expect(await screen.findByLabelText('Fit 84 out of 100')).toBeInTheDocument()
    expect(screen.getByText('Strong backend match.')).toBeInTheDocument()
    expect(screen.getByText('Java and Spring Boot')).toBeInTheDocument()
    expect(screen.getByText('Kubernetes')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Re-check' })).toBeInTheDocument()
  })
})
