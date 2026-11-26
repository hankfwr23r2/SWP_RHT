// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { DateInput } from './DateInput'

afterEach(cleanup)

it('hiện và nhận ngày dd/mm/yyyy, trả về yyyy-mm-dd', () => {
  const change = vi.fn()
  render(<DateInput aria-label="Ngày đón" value="2026-12-04" onValueChange={change} />)
  const input = screen.getByRole<HTMLInputElement>('textbox', { name: 'Ngày đón' })
  expect(input.value).toBe('04/12/2026')

  fireEvent.change(input, { target: { value: '05/12/2026' } })
  expect(change).toHaveBeenLastCalledWith('2026-12-05')
})

it('báo ngày sai hoặc ngoài khoảng cho phép và không gửi giá trị', () => {
  const change = vi.fn()
  const { container } = render(<form><DateInput aria-label="Ngày" name="day" min="2026-12-01" onValueChange={change} /></form>)
  const input = screen.getByRole<HTMLInputElement>('textbox', { name: 'Ngày' })
  const hidden = container.querySelector<HTMLInputElement>('input[type="hidden"][name="day"]')!

  fireEvent.change(input, { target: { value: '30/11/2026' } })
  expect(change).not.toHaveBeenCalled()
  expect(input.getAttribute('aria-invalid')).toBe('true')
  expect(hidden.value).toBe('')

  fireEvent.change(input, { target: { value: '02/12/2026' } })
  expect(change).toHaveBeenLastCalledWith('2026-12-02')
  expect(hidden.value).toBe('2026-12-02')
})
