// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { Horse } from '@shared/api/horses'
import '@shared/i18n'
import { keepOnQueryChange } from '@shared/routing/pageData'
import { ToastProvider } from '@shared/ui/toast'
import HorsesPage from './HorsesPage'
import { horseProfilesLoader } from './loaders'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function renderPage() {
  const router = createMemoryRouter([{
    path: '/horses',
    loader: horseProfilesLoader,
    shouldRevalidate: keepOnQueryChange,
    element: <ToastProvider><HorsesPage /></ToastProvider>,
  }], { initialEntries: ['/horses'] })
  render(<RouterProvider router={router} />)
}

it('đếm, lọc và hiển thị trạng thái hồ sơ và giấy của từng con ngựa', async () => {
  const horses: Horse[] = [
    { id: 1, name: 'Sao Mai', microchip: '985112000000001', horseTypeName: 'Arabian', sex: 'MARE', color: 'Nâu', birthYear: 2019,
      missingDocumentTypes: [], documentStatuses: [
        { documentTypeCode: 'HORSE_PASSPORT', status: 'APPROVED' }, { documentTypeCode: 'VACCINATION_RECORD', status: 'UPLOADED' },
      ] },
    { id: 2, name: 'Gió Nam', microchip: '985112000000002', activeBookingCode: 'BK-010',
      missingDocumentTypes: ['VACCINATION_RECORD'], documentStatuses: [{ documentTypeCode: 'VACCINATION_RECORD' }] },
    { id: 3, name: 'Bạch Long', microchip: '985112000000003',
      missingDocumentTypes: ['HORSE_PASSPORT', 'VACCINATION_RECORD'],
      documentStatuses: [{ documentTypeCode: 'HORSE_PASSPORT', status: 'REJECTED' }] },
  ]
  const get = vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { data: horses } })
  renderPage()
  await screen.findByRole('heading', { name: 'Sao Mai' })

  const totals = screen.getAllByRole('link', { name: 'Xem danh sách' }).map(link => link.parentElement?.textContent)
  expect(totals).toEqual(['3Tổng số ngựaXem danh sách', '1Sẵn sàng đặtXem danh sách',
    '1Đang trong đơnXem danh sách', '2Thiếu giấyXem danh sách'])
  const card = (name: string) => within(screen.getByRole('heading', { name }).closest('article')!)
  expect(card('Sao Mai').getByText('Sẵn sàng đặt')).toBeTruthy()
  expect(card('Sao Mai').getByText('Arabian')).toBeTruthy()
  expect(card('Sao Mai').getByText('Nâu')).toBeTruthy()
  expect(card('Sao Mai').getByText('Hộ chiếu ngựa · Đã duyệt')).toBeTruthy()
  expect(card('Sao Mai').getByText('Sổ tiêm phòng · Đã nộp')).toBeTruthy()
  expect(card('Gió Nam').getByText('Đang trong đơn BK-010')).toBeTruthy()
  expect(card('Gió Nam').getByText('Sổ tiêm phòng · Chưa nộp')).toBeTruthy()
  expect(card('Bạch Long').getByText('Hộ chiếu ngựa · Bị trả lại')).toBeTruthy()
  expect(card('Bạch Long').getByRole('button', { name: 'Bổ sung giấy tờ' })).toBeTruthy()

  fireEvent.click(screen.getByRole('tab', { name: /Thiếu giấy/ }))
  expect(await screen.findAllByRole('article')).toHaveLength(2)
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: '985112000000003' } })
  expect(screen.getAllByRole('article')).toHaveLength(1)
  expect(screen.getByRole('heading', { name: 'Bạch Long' })).toBeTruthy()
  fireEvent.click(screen.getByRole('button', { name: 'Xóa tìm kiếm' }))
  expect(screen.getAllByRole('article')).toHaveLength(2)
  fireEvent.click(screen.getByRole('tab', { name: /Sẵn sàng đặt/ }))
  await screen.findByRole('heading', { name: 'Sao Mai' })
  expect(screen.getAllByRole('article')).toHaveLength(1)
  expect(get).toHaveBeenCalledExactlyOnceWith('/horses')
})

it('hiển thị chín thẻ mỗi trang và về đầu khi tìm kiếm', async () => {
  const horses = Array.from({ length: 10 }, (_, index) => ({ id: index + 1, name: `Ngựa ${index + 1}`, missingDocumentTypes: [] }))
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { data: horses } })
  renderPage()
  expect(await screen.findAllByRole('article')).toHaveLength(9)
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }))
  expect(await screen.findByRole('heading', { name: 'Ngựa 10' })).toBeTruthy()
  expect(screen.getAllByRole('article')).toHaveLength(1)
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'Ngựa' } })
  expect(await screen.findByText('Trang 1 / 2')).toBeTruthy()
  expect(screen.getAllByRole('article')).toHaveLength(9)
})

it('thử lại khi không tải được hồ sơ', async () => {
  const get = vi.spyOn(apiClient, 'get').mockRejectedValueOnce(new Error('Mất kết nối'))
    .mockResolvedValueOnce({ data: { data: [] } })
  renderPage()
  fireEvent.click(await screen.findByRole('button', { name: 'Thử lại' }))
  expect(await screen.findByText('Chưa có hồ sơ ngựa nào')).toBeTruthy()
  expect(get).toHaveBeenCalledTimes(2)
})
