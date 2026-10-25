import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AnswerDialog from './AnswerDialog'
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

function renderDialog(props) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <AnswerDialog open onClose={() => {}} {...props} />
      </ToastProvider>
    </QueryClientProvider>,
  )
}

describe('AnswerDialog', () => {
  it('drafts an answer for the role and saves it to the bank', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      if (url === '/api/ai') return json({ configured: true, resume: { fileName: 'resume.pdf' } })
      if (url === '/api/answers/draft') return json({ answer: 'I want to build data infrastructure at Chalk.' })
      if (url === '/api/answers') return json({ id: 1 })
      return json({})
    })
    renderDialog({ jobId: 7, jobTitle: 'Software Engineer' })

    await userEvent.type(screen.getByLabelText('Question'), 'Why Chalk?')
    await userEvent.click(await screen.findByRole('button', { name: 'Draft for Software Engineer' }))
    expect(await screen.findByDisplayValue('I want to build data infrastructure at Chalk.')).toBeInTheDocument()
    expect(screen.getByText(/^8 words/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Add to answer bank' }))
    const drafted = fetch.mock.calls.find(([url]) => url === '/api/answers/draft')
    expect(JSON.parse(drafted[1].body)).toEqual({ question: 'Why Chalk?', jobId: 7 })
    const saved = fetch.mock.calls.find(([url, init]) => url === '/api/answers' && init?.method === 'POST')
    expect(JSON.parse(saved[1].body)).toEqual({ question: 'Why Chalk?', answer: 'I want to build data infrastructure at Chalk.' })
  })

  it('edits a saved answer in place, and hides drafting until Claude is set up', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((url) => {
      if (url === '/api/ai') return json({ configured: false })
      return json({ id: 3 })
    })
    renderDialog({ answer: { id: 3, question: 'Why us?', answer: 'Old.' } })

    const text = screen.getByLabelText('Your answer')
    await userEvent.clear(text)
    await userEvent.type(text, 'New.')
    await userEvent.click(screen.getByRole('button', { name: 'Save answer' }))

    expect(screen.queryByRole('button', { name: /Draft/ })).not.toBeInTheDocument()
    const saved = fetch.mock.calls.find(([url]) => url === '/api/answers/3')
    expect(saved[1].method).toBe('PATCH')
    expect(JSON.parse(saved[1].body)).toEqual({ question: 'Why us?', answer: 'New.' })
  })
})
