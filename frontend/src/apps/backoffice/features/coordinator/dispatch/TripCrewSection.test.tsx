// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import { dispatchTexts } from '@shared/i18n/staffTexts'
import '@shared/i18n/staff'
import { ToastProvider } from '@shared/ui/toast'
import { TripCrewSection } from './TripCrewSection'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn() } }))
afterEach(() => { cleanup(); vi.resetAllMocks() })

it('requests candidates for the trip and presents projected locations in backend order', async () => {
  await dispatchTexts()
  vi.mocked(apiClient.get).mockResolvedValue({ data: { data: [
    { staffId: 2, staffCode: 'B', fullName: 'Bình', positionLocationName: 'Hub Đà Nẵng' },
    { staffId: 1, staffCode: 'A', fullName: 'An', positionTripCode: 'VT-1' },
  ] } })
  render(<ToastProvider><TripCrewSection tripId={8} trip={{ status: 'PLANNED' }} busy={false}
    onRelease={vi.fn()} onAssign={vi.fn()} /></ToastProvider>)

  fireEvent.click(await screen.findByLabelText('Nhân viên'))
  expect(apiClient.get).toHaveBeenCalledWith('/dispatch/crew-candidates', { params: { role: 'DRIVER', tripId: '8' } })
  const options = screen.getAllByRole('option')
  expect(options.map(option => option.textContent)).toEqual(['Bình (B) · Hub Đà Nẵng', 'An (A) · Trên xe VT-1'])
})
