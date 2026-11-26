// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, expect, it } from 'vitest'
import type { DocumentChecklist } from '@shared/api/bookingDocuments'
import { missingDocuments } from '@shared/components/orderSteps'
import '@shared/i18n'
import { ToastProvider } from '@shared/ui/toast'
import { DocumentsCard } from './DocumentsCard'

afterEach(cleanup)

it('hiện hộ chiếu đã duyệt từ quy định khác và chỉ nhắc con còn thiếu giấy', () => {
  const requirement = { id: 5, documentTypeCode: 'HORSE_PASSPORT', documentTypeName: 'Hộ chiếu ngựa', ownerType: 'HORSE', mandatory: true } as const
  const checklist: DocumentChecklist = {
    items: [
      { requirement, bookingHorseId: 21, submitterType: 'CUSTOMER', satisfied: true },
      { requirement, bookingHorseId: 22, submitterType: 'CUSTOMER', satisfied: false },
    ],
    documents: [
      { id: 1, requirementId: 4, typeCode: 'HORSE_PASSPORT', horseId: 41, bookingHorseId: 21,
        status: 'APPROVED', fileName: 'passport.pdf', fileUrl: 'https://example.test/passport.pdf' },
      { id: 2, requirementId: 5, typeCode: 'HORSE_PASSPORT', horseId: 41, bookingHorseId: 21, status: 'REJECTED' },
    ],
  }
  const router = createMemoryRouter([{ path: '/', element: <ToastProvider><DocumentsCard data={{
    id: 8, errors: [], checklist, payments: undefined, quotations: undefined, stallTypes: undefined,
    detail: { booking: { status: 'UNDER_REVIEW' }, horses: [{ id: 21, horseName: 'Sao Mai' }, { id: 22, horseName: 'Gió Nam' }] },
  }} /></ToastProvider> }])
  render(<RouterProvider router={router} />)

  const [reused, missing] = screen.getAllByRole('listitem').map(row => within(row))
  expect(reused.getByText('Đã duyệt')).toBeTruthy()
  expect(reused.getByRole('link', { name: 'passport.pdf' }).getAttribute('href')).toBe('https://example.test/passport.pdf')
  expect(reused.queryByRole('button')).toBeNull()
  expect(missing.getByRole('button', { name: 'Nộp giấy' })).toBeTruthy()
  expect(missingDocuments(checklist).map(item => item.bookingHorseId)).toEqual([22])
})

it('hiện giấy hồ sơ đang chờ duyệt trong đơn và không yêu cầu nộp trùng', () => {
  const checklist: DocumentChecklist = {
    items: [{ requirement: { id: 5, documentTypeCode: 'HORSE_PASSPORT', documentTypeName: 'Hộ chiếu ngựa',
      ownerType: 'HORSE', mandatory: true }, bookingHorseId: 21, submitterType: 'CUSTOMER', satisfied: false }],
    documents: [{ id: 1, typeCode: 'HORSE_PASSPORT', horseId: 41, bookingHorseId: 21, status: 'UPLOADED',
      fileName: 'passport.pdf', fileUrl: 'https://example.test/passport.pdf' }],
  }
  const router = createMemoryRouter([{ path: '/', element: <ToastProvider><DocumentsCard data={{
    id: 8, errors: [], checklist, payments: undefined, quotations: undefined, stallTypes: undefined,
    detail: { booking: { status: 'UNDER_REVIEW' }, horses: [{ id: 21, horseName: 'Sao Mai' }] },
  }} /></ToastProvider> }])
  render(<RouterProvider router={router} />)

  expect(screen.getByText('Chờ duyệt')).toBeTruthy()
  expect(screen.getByRole('link', { name: 'passport.pdf' }).getAttribute('href')).toBe('https://example.test/passport.pdf')
  expect(screen.queryByRole('button', { name: 'Nộp giấy' })).toBeNull()
  expect(missingDocuments(checklist)).toEqual([])
})
