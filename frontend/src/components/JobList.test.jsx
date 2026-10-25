import { beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import JobList from './JobList'
import { markSeen, resetSeen } from '../lib/seen'

const now = new Date().toISOString()
const job = (id, title, isNew) => ({ id, title, isNew, companyId: null, companyName: 'Acme', listedAt: now, open: true })

beforeEach(() => {
  const data = new Map()
  vi.stubGlobal('localStorage', { getItem: (k) => data.get(k) ?? null, setItem: (k, v) => data.set(k, v) })
  resetSeen()
})

describe('JobList', () => {
  it('highlights new roles until they are opened, like unread mail', () => {
    render(<JobList jobs={[job(1, 'Backend Engineer', true), job(2, 'Data Engineer', true), job(3, 'Old Role', false)]} onSelect={() => {}} />)
    const highlighted = () => [...document.querySelectorAll('.highlight-soft')].map((el) => el.textContent)
    expect(highlighted()).toEqual(['Backend Engineer', 'Data Engineer'])

    act(() => markSeen(1))

    expect(highlighted()).toEqual(['Data Engineer'])
    expect(screen.getAllByText('(new, not opened yet)', { exact: false })).toHaveLength(1)
  })
})
