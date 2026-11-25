// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { Incident } from '@shared/api/incidentOps'
import { AuthProvider } from '@shared/auth/AuthContext'
import { SESSION_KEY } from '@shared/auth/session'
import { TripIncidentPanel } from './TripIncidentPanel'

vi.mock('@shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

const activeIncident: Incident = { id: 5, title: 'Ngựa bỏ ăn', status: 'PLAN_ACTIVE', requiresFit: true }
const restingIncident: Incident = {
  ...activeIncident, restStartedAt: '2031-03-05T10:00:00Z', fitEligibleAt: '2031-03-05T10:02:00Z',
}
const fitPhoto = { id: 42, documentTypeCode: 'FIT_CHECK_PHOTO', fileName: 'sao-mai.jpg', fileUrl: 'https://cdn.example/sao-mai.jpg' }
const ok = (data: unknown) => ({ data: { data } })

beforeEach(() => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date('2031-03-05T10:00:00Z'))
})

afterEach(() => {
  cleanup()
  sessionStorage.clear()
  vi.useRealTimers()
  vi.resetAllMocks()
})

async function renderPanel(role: 'escort' | 'driver') {
  sessionStorage.setItem(`${SESSION_KEY}_backoffice`, JSON.stringify({ role, name: 'Nhân viên', username: role }))
  await act(async () => {
    render(<AuthProvider app="backoffice"><TripIncidentPanel run={{ trip: { id: 9, status: 'IN_PROGRESS' } }} /></AuthProvider>)
  })
}

const confirmFitButton = () => screen.getByRole<HTMLButtonElement>('button', { name: 'Đủ sức đi tiếp' })

it('starts rest and waits until the backend deadline even after a fitness photo is uploaded', async () => {
  let incident = activeIncident
  vi.mocked(apiClient.get).mockImplementation(async url => ok(url === '/vehicle-trips/9/incidents' ? [incident] : []))
  vi.mocked(apiClient.post).mockImplementation(async url => {
    if (url === '/incidents/5/rest-start') {
      incident = restingIncident
      return ok(incident)
    }
    if (url === '/incidents/5/photos') return ok([fitPhoto])
    incident = { ...restingIncident, fitConfirmedAt: '2031-03-05T10:02:00Z' }
    return ok(incident)
  })

  await renderPanel('escort')
  fireEvent.click(screen.getByRole('button', { name: 'Bắt đầu cho ngựa nghỉ' }))
  const startDialog = screen.getByRole('alertdialog', { name: 'Bắt đầu tính giờ nghỉ?' })
  await act(async () => { fireEvent.click(within(startDialog).getByRole('button', { name: 'Bắt đầu cho ngựa nghỉ' })) })
  expect(apiClient.post).toHaveBeenCalledWith('/incidents/5/rest-start', undefined)
  expect(screen.getByText('Còn 2 phút nghỉ')).toBeTruthy()
  expect(confirmFitButton().disabled).toBe(true)

  const file = new File(['image'], fitPhoto.fileName, { type: 'image/jpeg' })
  fireEvent.change(screen.getByLabelText('Chọn ảnh con ngựa'), { target: { files: [file] } })
  await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Tải ảnh xác nhận đủ sức' })) })
  expect(apiClient.post).toHaveBeenCalledWith('/incidents/5/photos', expect.any(FormData),
    expect.objectContaining({ params: { documentType: 'FIT_CHECK_PHOTO' } }))
  expect(screen.getByRole('link', { name: fitPhoto.fileName })).toBeTruthy()
  expect(confirmFitButton().disabled).toBe(true)

  await act(async () => { vi.advanceTimersByTime(120_000) })
  expect(screen.getByText('Đã nghỉ đủ thời gian')).toBeTruthy()
  expect(confirmFitButton().disabled).toBe(false)
  fireEvent.click(confirmFitButton())
  const fitDialog = screen.getByRole('alertdialog', { name: 'Xác nhận ngựa đủ sức đi tiếp?' })
  await act(async () => { fireEvent.click(within(fitDialog).getByRole('button', { name: 'Đủ sức đi tiếp' })) })
  expect(apiClient.post).toHaveBeenCalledWith('/incidents/5/fit-confirmation', undefined)
})

it('keeps confirmation disabled after rest if there is only a scene photo', async () => {
  vi.setSystemTime(new Date('2031-03-05T10:10:00Z'))
  vi.mocked(apiClient.get).mockImplementation(async url => ok(url === '/vehicle-trips/9/incidents' ? [restingIncident] :
    url === '/incidents/5/photos' ? [{ id: 43, documentTypeCode: 'INCIDENT_PHOTO', fileName: 'hien-truong.jpg' }] : []))

  await renderPanel('escort')
  expect(screen.getByText('Đã nghỉ đủ thời gian')).toBeTruthy()
  expect(confirmFitButton().disabled).toBe(true)
})

it('hides fitness confirmation from the driver even after rest and photo upload', async () => {
  vi.setSystemTime(new Date('2031-03-05T10:10:00Z'))
  vi.mocked(apiClient.get).mockImplementation(async url => ok(url === '/vehicle-trips/9/incidents' ? [restingIncident] :
    url === '/incidents/5/photos' ? [fitPhoto] : []))

  await renderPanel('driver')
  expect(screen.getByText(activeIncident.title ?? '')).toBeTruthy()
  expect(screen.queryByRole('button', { name: 'Bắt đầu cho ngựa nghỉ' })).toBeNull()
  expect(screen.queryByRole('button', { name: 'Đủ sức đi tiếp' })).toBeNull()
})
