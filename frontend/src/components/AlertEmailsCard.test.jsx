import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AlertEmailsCard from './AlertEmailsCard'
import { ToastProvider } from './Toasts'

function renderWith(gmail) {
  vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
    const body = url === '/api/gmail' ? gmail : { running: false, results: [] }
    return Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <ToastProvider>
          <AlertEmailsCard />
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const base = { configured: false, connected: false, label: 'Job alerts', lastEmailsRead: 0, lastRolesAdded: 0, lastUnreadable: 0 }

afterEach(() => vi.restoreAllMocks())

describe('AlertEmailsCard', () => {
  it('points to the setup guide until a Google client is configured', async () => {
    renderWith(base)

    expect(await screen.findByRole('link', { name: 'Follow the setup guide' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Connect Gmail' })).not.toBeInTheDocument()
  })

  it('offers to connect once configured', async () => {
    renderWith({ ...base, configured: true })

    expect(await screen.findByRole('link', { name: 'Connect Gmail' })).toHaveAttribute('href', '/api/gmail/connect')
  })

  it('reports the last import and emails it could not read', async () => {
    renderWith({
      ...base,
      configured: true,
      connected: true,
      email: 'me@gmail.com',
      lastImportAt: new Date().toISOString(),
      lastEmailsRead: 3,
      lastRolesAdded: 7,
      lastUnreadable: 1,
    })

    expect(await screen.findByText(/3 new emails, 7 roles added/)).toBeInTheDocument()
    expect(screen.getByText(/1 email had no roles the app could read/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Import now' })).toBeEnabled()
  })
})
