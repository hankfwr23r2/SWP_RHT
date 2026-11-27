// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, describe, expect, it } from 'vitest'
import type { BookingDocument } from '@shared/api/bookingDocuments'
import type { BookingDetail, BookingStatus } from '@shared/api/bookingOrders'
import { reviewTexts } from '@shared/i18n/staffTexts'
import { useReviewActions } from '../useReviewActions'
import { InsurancesCard } from './InsurancesCard'

beforeAll(() => reviewTexts())
afterEach(cleanup)

const horses: NonNullable<BookingDetail['horses']> = [
  { id: 1, horseName: 'Sao Mai', status: 'ACTIVE', insurance: { id: 1, coverageOption: 'ARRANGED', status: 'CONFIRMED' } },
  { id: 2, horseName: 'Gió', status: 'ACTIVE', insurance: { id: 2, coverageOption: 'ARRANGED', status: 'DRAFT' } },
  { id: 3, horseName: 'Sương', status: 'ACTIVE', insurance: { id: 3, coverageOption: 'PERSONAL', status: 'CONFIRMED' } },
  { id: 4, horseName: 'Mây', status: 'ACTIVE', insurance: { id: 4, coverageOption: 'NONE', status: 'DRAFT' } },
  { id: 5, horseName: 'Sao Băng', status: 'ACTIVE', insurance: { id: 5, coverageOption: 'PERSONAL', status: 'DRAFT' } },
]

function renderInsurances(status: BookingStatus, canEdit = true, documents: BookingDocument[] = []) {
  function Page() {
    const actions = useReviewActions(1)
    return <InsurancesCard horses={horses} status={status} canEdit={canEdit} documents={documents}
      actions={actions} />
  }
  return render(<RouterProvider router={createMemoryRouter([{ path: '/', Component: Page }])} />)
}

describe('Thao tác bảo hiểm trên hồ sơ chuyên viên', () => {
  it('hiện thao tác mua hộ cho đơn đang báo giá', () => {
    renderInsurances('QUOTING')
    const [confirmed, draft, personal, waiver] = screen.getAllByRole('listitem').map(row => within(row))
    expect(confirmed.getByRole('button', { name: 'Mở lại bảo hiểm' })).toBeTruthy()
    expect(draft.getByRole('button', { name: 'Ghi báo giá bảo hiểm' })).toBeTruthy()
    expect(draft.getByRole('button', { name: 'Xác nhận bảo hiểm' })).toBeTruthy()
    expect(personal.queryByRole('button')).toBeNull()
    expect(waiver.queryByRole('button')).toBeNull()
  })

  it('chỉ hiển thị dữ liệu bảo hiểm khi chuyên viên khác phụ trách', () => {
    renderInsurances('UNDER_REVIEW', false)
    expect(screen.getByRole('heading', { name: 'Sao Mai' })).toBeTruthy()
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('khóa thao tác khi báo giá đang chờ khách', () => {
    renderInsurances('AWAITING_DEPOSIT')
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('chuyên viên mở được tệp và xác nhận đúng bảo hiểm của con đã nộp', () => {
    renderInsurances('UNDER_REVIEW', true, [
      { bookingHorseId: 5, typeCode: 'INSURANCE_POLICY', fileName: 'policy.pdf', fileUrl: 'https://example.test/policy.pdf' },
    ])
    const personal = within(screen.getAllByRole('listitem').at(-1)!)
    fireEvent.click(personal.getByRole('button', { name: 'Xem policy.pdf' }))
    expect(screen.getByTitle('policy.pdf').getAttribute('src')).toBe('https://example.test/policy.pdf')
    expect(personal.getByRole('button', { name: 'Xác nhận bảo hiểm' })).toBeTruthy()
  })

  it('vẫn cho yêu cầu bổ sung khi tệp chỉ thuộc con khác', () => {
    renderInsurances('UNDER_REVIEW', true, [
      { bookingHorseId: 3, typeCode: 'INSURANCE_POLICY', fileName: 'policy.pdf', fileUrl: 'https://example.test/policy.pdf' },
    ])
    const personal = within(screen.getAllByRole('listitem').at(-1)!)
    expect(personal.getByText('Chưa có tệp hợp đồng.')).toBeTruthy()
    expect(personal.queryByRole('button', { name: 'Xác nhận bảo hiểm' })).toBeNull()
    expect(personal.getByRole('button', { name: 'Yêu cầu bổ sung bảo hiểm' })).toBeTruthy()
  })
})
