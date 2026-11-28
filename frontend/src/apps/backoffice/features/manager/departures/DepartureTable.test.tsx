// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import '@shared/i18n'
import { DepartureTable } from './DepartureTable'

afterEach(cleanup)

it('shows one primary decision and keeps other permitted actions under the disclosure', () => {
  const row = { id: 1, status: 'OPEN' as const, confirmable: true, canRevise: true,
    committedStallCount: 0, stallCapacity: 9, belowMinimum: true }
  const confirm = vi.fn()
  const delay = vi.fn()
  const { container } = render(<MemoryRouter><DepartureTable rows={[row]} busy={false}
    onConfirm={confirm} onDelay={delay} onAssign={vi.fn()} onCancel={vi.fn()} /></MemoryRouter>)
  expect(screen.getByText(/^0\/9/)).toBeTruthy()
  const actions = screen.getAllByRole('cell').at(-1)!
  const primaryActions = within(actions).getAllByRole('button').filter(button => !button.closest('details'))
  expect(primaryActions).toHaveLength(1)
  fireEvent.click(primaryActions[0])
  expect(confirm).toHaveBeenCalledWith(row)
  const disclosure = container.querySelector('details')!
  disclosure.open = true
  const options = within(disclosure).getAllByRole('button')
  expect(options).toHaveLength(3)
  fireEvent.click(options[0])
  expect(delay).toHaveBeenCalledWith(row)
  expect(disclosure.open).toBe(false)
})
