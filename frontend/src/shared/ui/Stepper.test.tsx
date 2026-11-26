// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import '@shared/i18n'
import { Stepper } from './Stepper'

afterEach(cleanup)

const steps = ['Lộ trình', 'Ngựa', 'Dịch vụ', 'Xem lại']

describe('Stepper', () => {
  it('đánh dấu bước đang làm, bước đã xong được đọc là đã xong', () => {
    render(<Stepper label="Các bước đặt đơn" steps={steps} current={2} />)
    const items = within(screen.getByRole('navigation', { name: 'Các bước đặt đơn' })).getAllByRole('listitem')

    expect(items.map(li => li.getAttribute('aria-current'))).toEqual([null, null, 'step', null])
    expect(items.map(li => li.textContent)).toEqual(['Lộ trình (Đã xong)', 'Ngựa (Đã xong)', '3Dịch vụ', '4Xem lại'])
  })

  it('dòng tóm tắt cho điện thoại ghi số bước và tên bước', () => {
    render(<Stepper label="Các bước đặt đơn" steps={steps} current={1} />)
    expect(screen.getByText(/Bước 2\/4/).textContent).toBe('Bước 2/4: Ngựa')
  })
})
