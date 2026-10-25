import { describe, expect, it } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import CompanyLogo from './CompanyLogo'

describe('CompanyLogo', () => {
  it('shows the logo the app serves for the company', () => {
    const { container } = render(<CompanyLogo companyId={7} name="Airbnb" />)

    expect(container.querySelector('img')).toHaveAttribute('src', '/api/companies/7/logo')
  })

  it('falls back to initials when there is no logo', () => {
    const { container } = render(<CompanyLogo companyId={7} name="Lockheed Martin" />)

    fireEvent.error(container.querySelector('img'))

    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('LM')).toBeInTheDocument()
  })

  it('shows a tiny icon at its own size instead of blurring it', () => {
    const { container } = render(<CompanyLogo companyId={11} name="Lockheed Martin" />)
    const img = container.querySelector('img')
    Object.defineProperty(img, 'naturalWidth', { value: 16 })

    fireEvent.load(img)

    expect(img).toHaveClass('size-4')
    expect(img).not.toHaveClass('size-full')
  })
})
