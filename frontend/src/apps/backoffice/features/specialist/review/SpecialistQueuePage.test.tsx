// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { SpecialistWork } from '@shared/api/specialistWork'
import { reviewTexts } from '@shared/i18n/staffTexts'
import { SpecialistQueuePage } from './SpecialistQueuePage'

beforeAll(() => reviewTexts())
afterEach(() => { cleanup(); vi.restoreAllMocks() })

const rows: SpecialistWork[] = [
  { booking: { id: 1, code: 'BK-001', customerName: 'Anh Nam' }, horseNames: ['Bạch Mã'],
    verification: 'TO_REVIEW', legal: 'TO_PREPARE', insurance: 'TO_PROCESS', arranged: 1, personal: 0, uninsured: 0 },
  { booking: { id: 2, code: 'BK-002' }, horseNames: ['Gió Đông'], verification: 'WAITING_CUSTOMER',
    legal: 'IN_PROGRESS', insurance: 'CONFIRMED' },
]

it.each([
  ['verification', 'Cần duyệt', 'Chờ khách bổ sung'],
  ['legal', 'Cần chuẩn bị', 'Đang làm'],
  ['insurance', 'Cần báo giá hãng / kiểm tra', 'Đã xác nhận'],
] as const)('%s đếm các thẻ và tìm theo tên ngựa', async (section, first, second) => {
  vi.spyOn(apiClient, 'get').mockResolvedValue({ data: { data: rows } })
  const route = '/specialist/' + section
  render(<RouterProvider router={createMemoryRouter([
    { path: route, element: <SpecialistQueuePage section={section} /> },
  ], { initialEntries: [route] })} />)
  expect(await screen.findByRole('tab', { name: first + ' (1)' })).toBeTruthy()
  expect(screen.getByRole('tab', { name: second + ' (1)' })).toBeTruthy()
  expect(screen.getByRole('link', { name: 'BK-001' }).getAttribute('href')).toBe(route + '/1')
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'bach ma' } })
  expect(screen.getByRole('link', { name: 'BK-001' })).toBeTruthy()
  fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'gio dong' } })
  expect(screen.queryByRole('link', { name: 'BK-001' })).toBeNull()
  fireEvent.click(screen.getByRole('tab', { name: second + ' (1)' }))
  expect(await screen.findByRole('link', { name: 'BK-002' })).toBeTruthy()
})
