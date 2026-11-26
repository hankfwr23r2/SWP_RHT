// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import type { BookingDetail } from '@shared/api/bookingOrders'
import type { DocumentChecklist } from '@shared/api/bookingDocuments'
import apiClient from '@shared/api/client'
import { reviewTexts } from '@shared/i18n/staffTexts'
import { SpecialistDetailPage } from './SpecialistDetailPage'

beforeAll(() => reviewTexts())
afterEach(() => { cleanup(); vi.restoreAllMocks() })

function open(section: 'verification' | 'legal', pending = true) {
  const detail: BookingDetail = {
    assignedToMe: true, booking: { id: 8, code: 'BK-008', status: 'UNDER_REVIEW' },
    horses: [
      { id: 21, horseId: 41, horseName: 'Sao Mai', status: 'ACTIVE', microchip: '123', insurance: { status: 'CONFIRMED' } },
      { id: 22, horseId: 42, horseName: 'Gió Nam', status: 'ACTIVE', microchip: '456', insurance: { status: 'CONFIRMED' } },
    ],
  }
  const checklist: DocumentChecklist = {
    items: [
      { requirement: { id: 1, documentTypeName: 'Hộ chiếu' }, bookingHorseId: 21,
        submitterType: 'CUSTOMER', requiredForQuoting: true, satisfied: !pending },
      { requirement: { id: 2, documentTypeName: 'Sổ tiêm' }, bookingHorseId: 22, submitterType: 'CUSTOMER', satisfied: false },
      { requirement: { id: 3, documentTypeName: 'Giấy kiểm dịch', gateLocationId: 4 },
        submitterType: 'CONSIGNEE', bookingHorseId: 22, satisfied: false },
      { requirement: { id: 4, documentTypeName: 'Sổ ATA', gateLocationId: 4 },
        submitterType: 'CUSTOMER', satisfied: false },
    ],
    documents: pending ? [
      { id: 101, requirementId: 1, bookingHorseId: 21, status: 'UPLOADED', fileUrl: 'https://example.test/passport.pdf' },
      { id: 102, requirementId: 2, bookingHorseId: 22, status: 'UPLOADED' },
    ] : [],
  }
  const post = vi.spyOn(apiClient, 'post').mockResolvedValue({ status: 200, data: { data: {} } })
  const path = `/specialist/${section}/8`
  render(<RouterProvider router={createMemoryRouter([{
    path, hydrateFallbackElement: null, element: <SpecialistDetailPage section={section} />,
    loader: () => ({ detail, checklist, documents: [], stallTypes: [], errors: [] }),
  }], { initialEntries: [path] })} />)
  return post
}

it('chuyển ngựa, duyệt và từ chối từng giấy, chọn đúng ngựa và giấy để yêu cầu bổ sung', async () => {
  const post = open('verification')
  expect(await screen.findByText('123')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Xem trước tệp' }))
  const preview = screen.getByRole('dialog')
  expect(within(preview).getByRole('link', { name: 'Mở trong tab mới' }).getAttribute('target')).toBe('_blank')
  fireEvent.keyDown(preview, { key: 'Escape' })
  fireEvent.click(screen.getByRole('button', { name: 'Duyệt giấy' }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Duyệt giấy' }))
  await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  expect(post.mock.calls[0]?.[0]).toBe('/bookings/8/documents/101/approve')
  fireEvent.click(screen.getByRole('button', { name: 'Con sau' }))
  expect(screen.getByText('456')).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Từ chối giấy' }))
  let dialog = within(screen.getByRole('alertdialog'))
  fireEvent.change(dialog.getByRole('textbox'), { target: { value: 'Ảnh mờ' } })
  fireEvent.click(dialog.getByRole('button', { name: 'Từ chối giấy' }))
  await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  expect(post.mock.calls[1]?.slice(0, 2)).toEqual(['/bookings/8/documents/102/reject', { reason: 'Ảnh mờ' }])
  fireEvent.click(screen.getByRole('button', { name: 'Trả lại để bổ sung' }))
  dialog = within(screen.getByRole('alertdialog'))
  fireEvent.click(dialog.getByRole('checkbox', { name: 'Gió Nam · Sổ tiêm' }))
  fireEvent.change(dialog.getByRole('textbox'), { target: { value: 'Cần bản rõ' } })
  fireEvent.click(dialog.getByRole('button', { name: 'Trả lại để bổ sung' }))
  await waitFor(() => expect(post.mock.calls[2]?.slice(0, 2)).toEqual([
    '/bookings/8/return-for-resubmission', { reason: 'Gió Nam · Sổ tiêm\nCần bản rõ' },
  ]))
})

it('xác nhận bắt đầu báo giá khi đã đối chiếu đủ ngựa', async () => {
  const post = open('verification', false)
  await screen.findByText('123')
  expect(screen.queryByRole('button', { name: 'Bắt đầu báo giá' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Đánh dấu tất cả' }))
  fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu báo giá' }))
  fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Bắt đầu báo giá' }))
  await waitFor(() => expect(post.mock.calls[0]?.[0]).toBe('/bookings/8/start-quoting'))
})

it('hiện giấy riêng cửa khẩu và cho nộp thay đúng ngựa', async () => {
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:document-preview')
  const post = open('legal')
  expect(await screen.findByText('Đã nộp 0/2')).toBeTruthy()
  expect(screen.getByText('Giấy kiểm dịch · Gió Nam')).toBeTruthy()
  expect(screen.getByText('Sổ ATA')).toBeTruthy()
  expect(screen.queryByText('Hộ chiếu · Sao Mai')).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Nộp giấy thay' }))
  const upload = screen.getByRole('dialog')
  fireEvent.change(upload.querySelector('input[type="file"]')!, {
    target: { files: [new File(['%PDF-1.7'], 'border.pdf', { type: 'application/pdf' })] },
  })
  fireEvent.click(within(upload).getByRole('button', { name: 'Nộp giấy thay' }))
  await waitFor(() => expect(post.mock.calls[0]?.[0]).toBe('/bookings/8/documents/upload-for-partner'))
  const form = post.mock.calls[0]?.[1] as FormData
  expect(form.get('bookingHorseId')).toBe('22')
  expect(form.get('requirementId')).toBe('3')
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
})
