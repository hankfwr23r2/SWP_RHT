// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { i18nReady } from '@shared/i18n'
import '@shared/i18n/staff'
import { ActionBar } from './ActionBar'

afterEach(cleanup)

it('shows instructions and keeps the optional action operable', async () => {
  await i18nReady
  const act = vi.fn()
  const { rerender } = render(<ActionBar next="Kiểm tra giấy tờ" />)
  const region = screen.getByRole('region', { name: 'Bước tiếp theo' })
  expect(within(region).getByText('Kiểm tra giấy tờ')).toBeTruthy()
  expect(within(region).queryByRole('button')).toBeNull()
  rerender(<ActionBar next="Kiểm tra giấy tờ"><button onClick={act}>Xem giấy tờ</button></ActionBar>)
  fireEvent.click(within(region).getByRole('button', { name: 'Xem giấy tờ' }))
  expect(act).toHaveBeenCalledOnce()
})
