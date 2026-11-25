// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeAll, expect, it } from 'vitest'
import type { DocumentChecklist } from '@shared/api/bookingDocuments'
import { reviewTexts } from '@shared/i18n/staffTexts'
import { useReviewActions } from '../useReviewActions'
import { DocumentsCard } from './DocumentsCard'

beforeAll(() => reviewTexts())
afterEach(cleanup)

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
  expect(reused.queryByRole('button')).toBeNull()
  const missing = within(screen.getByText('Hộ chiếu ngựa · Gió Nam').closest('li')!)
  expect(missing.getByText('Chưa đáp ứng')).toBeTruthy()
  expect(missing.queryByRole('link')).toBeNull()
  expect(missing.getByRole('button', { name: 'Yêu cầu khách nộp' })).toBeTruthy()
})
