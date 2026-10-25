import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AddCompanyForm from './AddCompanyForm'
import { ToastProvider } from './Toasts'

function renderForm() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AddCompanyForm />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

function respond(status, body) {
  return Promise.resolve(new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }))
}

afterEach(() => vi.restoreAllMocks())

describe('AddCompanyForm', () => {
  it('shows which board a pasted link points to before saving', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => respond(200, { ats: 'LEVER', board: 'palantir', searchBased: false }))
    renderForm()

    await userEvent.type(screen.getByLabelText('Careers link'), 'https://jobs.lever.co/palantir')

    expect(await screen.findByText(/Lever board/)).toHaveTextContent('Lever board “palantir”')
    expect(screen.getByRole('button', { name: 'Add company' })).toBeEnabled()
  })

  it('asks for search phrases when the company posts thousands of roles', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => respond(200, { ats: 'AMAZON', board: 'amazon', searchBased: true }))
    renderForm()

    await userEvent.type(screen.getByLabelText('Careers link'), 'https://www.amazon.jobs')

    expect(await screen.findByText(/posts thousands of roles/)).toBeInTheDocument()
    expect(screen.getByText('amazon.jobs')).toBeInTheDocument()
  })

  it('explains a link it cannot read and keeps the button off', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() =>
      respond(400, { detail: "Couldn't recognise a Greenhouse, Lever or Ashby board in that link." }),
    )
    renderForm()

    await userEvent.type(screen.getByLabelText('Careers link'), 'https://careers.example.com')

    expect(await screen.findByText(/Couldn't recognise/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add company' })).toBeDisabled()
  })
})
