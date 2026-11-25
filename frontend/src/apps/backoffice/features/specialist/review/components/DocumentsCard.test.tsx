// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import type { DocumentChecklist } from '@shared/api/bookingDocuments'
import apiClient from '@shared/api/client'
import { reviewTexts } from '@shared/i18n/staffTexts'
import { useReviewActions } from '../useReviewActions'
import { DocumentsCard } from './DocumentsCard'

beforeAll(() => reviewTexts())
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

it('hiện hộ chiếu dùng lại trong hồ sơ chuyên viên với đúng con ngựa', () => {
  const requirement = { id: 5, documentTypeCode: 'HORSE_PASSPORT', documentTypeName: 'Hộ chiếu ngựa', ownerType: 'HORSE', mandatory: true } as const
  const checklist: DocumentChecklist = {
    items: [
      { requirement, bookingHorseId: 21, submitterType: 'CUSTOMER', satisfied: true },
      { requirement, bookingHorseId: 22, submitterType: 'CUSTOMER', satisfied: false },
    ],
    documents: [{ id: 1, requirementId: 4, typeCode: 'HORSE_PASSPORT', horseId: 41, bookingHorseId: 21,
      status: 'APPROVED', fileName: 'passport.pdf', fileUrl: 'https://example.test/passport.pdf' }],
  }
  function Page() {
    const actions = useReviewActions(8)
    return <DocumentsCard checklist={checklist} status="UNDER_REVIEW" actions={actions}
      horses={[{ id: 21, horseId: 41, horseName: 'Sao Mai' }, { id: 22, horseId: 42, horseName: 'Gió Nam' }]} />
  }
  render(<RouterProvider router={createMemoryRouter([{ path: '/', Component: Page }])} />)

  const reused = within(screen.getByText('Hộ chiếu ngựa · Sao Mai').closest('li')!)
  expect(reused.getByText('Đã đáp ứng')).toBeTruthy()
  expect(reused.getByRole('link', { name: 'Xem passport.pdf' }).getAttribute('href')).toBe('https://example.test/passport.pdf')
  expect(reused.queryByRole('button', { name: 'Duyệt giấy' })).toBeNull()
  expect(reused.getByRole('button', { name: 'Thu hồi' })).toBeTruthy()
  const missing = within(screen.getByText('Hộ chiếu ngựa · Gió Nam').closest('li')!)
  expect(missing.getByText('Chưa đáp ứng')).toBeTruthy()
  expect(missing.queryByRole('link')).toBeNull()
  expect(missing.getByRole('button', { name: 'Yêu cầu khách nộp' })).toBeTruthy()
})

it('bắt buộc nhập lý do khi thu hồi giấy và gửi lý do đã bỏ khoảng trắng', async () => {
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ status: 200, data: { data: { status: 'REJECTED' } } })
  const checklist: DocumentChecklist = {
    items: [{ requirement: { id: 2, documentTypeName: 'Giấy kiểm dịch' }, satisfied: true }],
    documents: [{ id: 3, requirementId: 2, status: 'APPROVED' }],
  }
  function Page() {
    const actions = useReviewActions(8)
    return <DocumentsCard checklist={checklist} status="CONFIRMED" actions={actions} />
  }
  render(<RouterProvider router={createMemoryRouter([{ path: '/', Component: Page }])} />)

  fireEvent.click(screen.getByRole('button', { name: 'Thu hồi' }))
  const dialog = within(screen.getByRole('alertdialog', { name: 'Thu hồi' }))
  const submit = dialog.getByRole('button', { name: 'Thu hồi' })
  fireEvent.click(submit)
  expect(dialog.getByText('Vui lòng điền thông tin này.')).toBeTruthy()
  expect(post).not.toHaveBeenCalled()
  await waitFor(() => expect(submit).toHaveProperty('disabled', false))

  fireEvent.change(dialog.getByRole('textbox', { name: /Lý do/ }), { target: { value: '  Giấy sai nội dung  ' } })
  fireEvent.click(submit)
  expect(post).toHaveBeenCalledExactlyOnceWith('/bookings/8/documents/3/revoke', { reason: 'Giấy sai nội dung' }, expect.any(Object))
  await waitFor(() => expect(screen.queryByRole('alertdialog', { name: 'Thu hồi' })).toBeNull())
})
