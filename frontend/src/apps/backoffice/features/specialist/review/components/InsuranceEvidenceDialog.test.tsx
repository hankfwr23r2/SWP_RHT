// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import type { components } from '@shared/api/generated/schema'
import type { InsuranceEvidence } from '@shared/api/bookingReviews'
import { reviewTexts } from '@shared/i18n/staffTexts'
import { InsuranceEvidenceDialog } from './InsuranceEvidenceDialog'

beforeAll(() => reviewTexts())
afterEach(cleanup)

describe('InsuranceEvidenceDialog', () => {
  it('formats the premium with commas and submits its raw numeric value', async () => {
    const recordEvidence = vi.fn(async (_id: number, _body: InsuranceEvidence) => true)
    const actions = { busy: false, clear: vi.fn(), fields: {}, error: '', recordEvidence }
    const insurance: components['schemas']['InsuranceDetail'] = { coverageOption: 'ARRANGED' }
    const onClose = vi.fn()
    render(<InsuranceEvidenceDialog insurance={insurance} id={7} actions={actions} onClose={onClose} />)

    fireEvent.change(screen.getByLabelText(/^Hãng bảo hiểm/), { target: { value: 'ABC Insurance' } })
    fireEvent.change(screen.getByLabelText(/^Phí bảo hiểm/), { target: { value: '12312313' } })
    fireEvent.change(screen.getByLabelText(/^Hạn báo giá bảo hiểm/), {
      target: { value: '2026-12-01T10:00' },
    })

    expect((screen.getByLabelText(/^Phí bảo hiểm/) as HTMLInputElement).value).toBe('12,312,313')

    fireEvent.click(screen.getByRole('button', { name: 'Ghi báo giá bảo hiểm' }))
    await waitFor(() => expect(recordEvidence).toHaveBeenCalledWith(7, {
      insurerName: 'ABC Insurance',
      premiumAmount: 12312313,
      quoteExpiresAt: new Date('2026-12-01T10:00+07:00').toISOString(),
    }))
    expect(onClose).toHaveBeenCalledOnce()
  })
})
