// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import '@shared/i18n'
import { Input } from './Input'
import { ListPanel } from './ListPanel'
import { Table } from './Table'

afterEach(cleanup)

it('groups the filter tabs, search, table and pagination in the same panel', () => {
  const change = vi.fn()
  const more = vi.fn()
  render(<ListPanel
    tabs={{ label: 'Filters', value: 'all', onChange: change, items: [
      { value: 'all', label: 'All', count: 1 }, { value: 'open', label: 'Open' },
    ] }}
    toolbar={<Input type="search" aria-label="Search" />}
    count={1}
    more={{ onClick: more, label: 'Next results' }}>
    <Table caption="Orders" columns={[{ key: 'name', header: 'Order', render: row => row }]}
      rows={['DEV-001']} rowKey={row => row} />
  </ListPanel>)
  expect(screen.getByRole('searchbox')).toBeTruthy()
  expect(screen.getByRole('table', { name: 'Orders' }).textContent).toContain('DEV-001')
  expect(screen.getByRole('status').textContent).toContain('1')
  fireEvent.click(screen.getByRole('tab', { name: 'Open' }))
  expect(change).toHaveBeenCalledWith('open')
  fireEvent.click(screen.getByRole('button', { name: 'Next results' }))
  expect(more).toHaveBeenCalledOnce()
})

it('keeps the toolbar available and exposes the loading state', () => {
  render(<ListPanel loading toolbar={<Input aria-label="Search" />}><p>Results</p></ListPanel>)
  expect(screen.getByRole('textbox')).toBeTruthy()
  expect(screen.getByRole('status').closest('[aria-busy="true"]')).toBeTruthy()
  expect(screen.queryByText('Results')).toBeNull()
})
