// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it } from 'vitest'
import { StatRow, StatTile } from './StatTile'

afterEach(cleanup)

it('displays a zero metric and links only tiles with a destination', () => {
  render(<MemoryRouter><StatRow>
    <StatTile icon={<span>Icon</span>} label="Trips" value={0} link={{ to: '/trips', label: 'View trips' }} />
    <StatTile icon={<span>Icon</span>} label="Horses" value={4} />
  </StatRow></MemoryRouter>)
  expect(screen.getByText('0')).toBeTruthy()
  expect(screen.getByText('Horses')).toBeTruthy()
  expect(screen.getAllByText('Icon').every(icon => icon.closest('[aria-hidden="true"]'))).toBe(true)
  expect(screen.getByRole('link', { name: 'View trips' }).getAttribute('href')).toBe('/trips')
  expect(screen.getAllByRole('link')).toHaveLength(1)
})
