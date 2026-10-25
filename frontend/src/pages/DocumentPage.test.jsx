import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import DocumentPage from './DocumentPage'
import { ToastProvider } from '../components/Toasts'

const json = (body) => Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }))

afterEach(() => vi.restoreAllMocks())

const resume = {
  id: 5,
  jobId: 9,
  jobTitle: 'Software Engineer',
  companyName: 'Chalk',
  kind: 'RESUME',
  model: 'claude-opus-5-5',
  createdAt: '2026-10-25T12:00:00Z',
  content: {
    name: 'Sam Rivera',
    contactLine: 'sam@example.com',
    summary: 'Backend engineer.',
    skills: ['Java', 'Postgres'],
    droppedSkills: ['Rust'],
    sections: [
      {
        heading: 'Experience',
        entries: [
          {
            title: 'Engineer',
            subtitle: 'Acme',
            dates: '2025',
            bullets: [
              { text: 'Built the billing API in Java.', source: 'Built the billing API', problems: [] },
              { text: 'Cut latency by 60%.', source: 'Made it faster', problems: ['"60%" isn\'t in your resume'] },
            ],
          },
        ],
      },
    ],
    flagged: 1,
    checked: true,
  },
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <MemoryRouter initialEntries={['/documents/5']}>
          <Routes>
            <Route path="/documents/:id" element={<DocumentPage />} />
          </Routes>
        </MemoryRouter>
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('DocumentPage', () => {
  it('shows the tailored resume with what the check flagged, and removes a flagged line', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      if (init?.method === 'PATCH') return json({ ...resume, content: JSON.parse(init.body).content })
      return json(resume)
    })
    renderPage()

    expect(await screen.findByText('Sam Rivera')).toBeInTheDocument()
    expect(screen.getByText(/1 part couldn/)).toBeInTheDocument()
    expect(screen.getByText(/Left out of skills because your resume doesn.t name them: Rust/)).toBeInTheDocument()
    const flagged = screen.getByText('Cut latency by 60%.').closest('li')
    expect(within(flagged).getByText('"60%" isn\'t in your resume')).toBeInTheDocument()

    await userEvent.click(within(flagged).getByRole('button', { name: 'Remove this line' }))
    const patch = fetch.mock.calls.find(([, init]) => init?.method === 'PATCH')
    const sent = JSON.parse(patch[1].body).content
    expect(sent.sections[0].entries[0].bullets.map((b) => b.text)).toEqual(['Built the billing API in Java.'])
    expect(sent.flagged).toBe(0)
  })

  it('edits the text and saves it', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url, init) => {
      if (init?.method === 'PATCH') return json({ ...resume, content: JSON.parse(init.body).content })
      return json(resume)
    })
    renderPage()

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }))
    const summary = screen.getByDisplayValue('Backend engineer.')
    await userEvent.clear(summary)
    await userEvent.type(summary, 'Backend engineer who ships.')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    const patch = fetch.mock.calls.find(([, init]) => init?.method === 'PATCH')
    expect(patch[0]).toBe('/api/documents/5')
    expect(JSON.parse(patch[1].body).content.summary).toBe('Backend engineer who ships.')
  })
})
