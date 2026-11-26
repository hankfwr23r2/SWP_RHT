// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import apiClient from '@shared/api/client'
import type { HorseDocument } from '@shared/api/horses'
import '@shared/i18n'
import { ToastProvider } from '@shared/ui/toast'
import { HorseProfileForm } from './HorseFormModal'

const catalog = [{ code: 'HORSE_PASSPORT', name: 'Hộ chiếu ngựa' }, { code: 'VACCINATION_RECORD', name: 'Sổ tiêm phòng' }]
const oldHorse = { id: 11, name: 'Sao Mai', microchip: '985112000374959', horseTypeId: 1, sex: 'MARE' }
const documents: HorseDocument[] = [
  { documentTypeCode: 'HORSE_PASSPORT', status: 'REJECTED', fileName: 'returned.pdf', canUpload: true,
    fileUrl: 'https://example.test/returned.pdf', reviewNote: 'Thiếu trang nhận dạng' },
  { documentTypeCode: 'VACCINATION_RECORD', status: 'APPROVED', canUpload: false },
]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function setupReads() {
  const responses: Record<string, unknown> = {
    '/horse-types': [{ id: 1, name: 'Thoroughbred' }],
    '/horses/document-types': catalog,
    '/horses/11': oldHorse,
    '/horses/11/documents': documents,
  }
  vi.spyOn(apiClient, 'get').mockImplementation(async url => {
    if (!(url in responses)) throw new Error(url)
    return { data: { data: responses[url] } }
  })
}

it('hiện giấy đã trả trong tab giấy tờ và quay về tab có lỗi khi sửa', async () => {
  setupReads()
  const put = vi.spyOn(apiClient, 'put').mockResolvedValue({ status: 200, data: { data: oldHorse } })
  render(<ToastProvider><HorseProfileForm id={11} initialTab="documents" onClose={vi.fn()} onSaved={vi.fn()} /></ToastProvider>)

  const docsTab = await screen.findByRole('tab', { name: 'Giấy tờ (1/2)' })
  const dialog = within(screen.getByRole('dialog'))
  expect(docsTab.getAttribute('aria-selected')).toBe('true')
  expect(dialog.getByText('Bị trả lại')).toBeTruthy()
  expect(dialog.getByText(/Thiếu trang nhận dạng/)).toBeTruthy()
  expect(dialog.getByRole('link', { name: 'returned.pdf' }).getAttribute('href')).toBe('https://example.test/returned.pdf')

  fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }))
  expect(dialog.getByRole('tab', { name: 'Nhận dạng & Đặc điểm' }).getAttribute('aria-selected')).toBe('true')
  expect(dialog.getByText('Nhập màu lông.')).toBeTruthy()
  expect(dialog.getByText('Nhập năm sinh hợp lệ.')).toBeTruthy()
  expect(dialog.getByRole('textbox', { name: 'Mã microchip' })).toHaveProperty('disabled', true)
  fireEvent.change(dialog.getByRole('textbox', { name: 'Màu lông' }), { target: { value: 'Nâu' } })
  fireEvent.change(dialog.getByRole('spinbutton', { name: 'Năm sinh' }), { target: { value: '1989' } })
  fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }))
  expect(dialog.getByText('Nhập năm sinh hợp lệ.')).toBeTruthy()
  expect(put).not.toHaveBeenCalled()

  fireEvent.change(dialog.getByRole('spinbutton', { name: 'Năm sinh' }), { target: { value: '1990' } })
  fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }))
  await waitFor(() => expect(put).toHaveBeenCalledExactlyOnceWith('/horses/11',
    { name: 'Sao Mai', horseTypeId: 1, sex: 'MARE', birthYear: 1990, color: 'Nâu', identificationMarks: undefined }, expect.any(Object)))
})

it('tải lại đúng tệp lỗi sau khi đã lưu ngựa và tệp còn lại', async () => {
  setupReads()
  const uploaded = (documentTypeCode: string) => ({ status: 201, data: { data: { documentTypeCode, status: 'UPLOADED', canUpload: false } } })
  const put = vi.spyOn(apiClient, 'put')
  const post = vi.spyOn(apiClient, 'post')
    .mockResolvedValueOnce({ status: 201, data: { data: { id: 9 } } })
    .mockResolvedValueOnce(uploaded('HORSE_PASSPORT'))
    .mockResolvedValueOnce({ status: 400, data: { message: 'Tệp lỗi', data: { file: 'Không đọc được tệp giấy tờ.' } } })
    .mockResolvedValueOnce(uploaded('VACCINATION_RECORD'))
  const saved = vi.fn()
  render(<ToastProvider><HorseProfileForm onClose={vi.fn()} onSaved={saved} /></ToastProvider>)

  await screen.findByRole('tab', { name: 'Giấy tờ (0/2)' })
  const dialog = within(screen.getByRole('dialog'))
  fireEvent.change(dialog.getByRole('textbox', { name: 'Tên ngựa' }), { target: { value: 'Sao Mai' } })
  fireEvent.change(dialog.getByRole('textbox', { name: 'Mã microchip' }), { target: { value: '985112000374959' } })
  fireEvent.change(dialog.getByRole('textbox', { name: 'Màu lông' }), { target: { value: 'Nâu' } })
  fireEvent.change(dialog.getByRole('spinbutton', { name: 'Năm sinh' }), { target: { value: '2019' } })
  fireEvent.click(dialog.getByRole('combobox', { name: 'Giống' }))
  fireEvent.click(screen.getByRole('option', { name: 'Thoroughbred' }))
  fireEvent.click(dialog.getByRole('combobox', { name: 'Giới tính' }))
  fireEvent.click(screen.getByRole('option', { name: 'Cái' }))
  fireEvent.click(dialog.getByRole('button', { name: 'Tiếp tục tới giấy tờ' }))
  fireEvent.change(dialog.getByLabelText('Chọn tệp Hộ chiếu ngựa'), {
    target: { files: [new File(['%PDF-1.7'], 'passport.pdf', { type: 'application/pdf' })] },
  })
  fireEvent.change(dialog.getByLabelText('Chọn tệp Sổ tiêm phòng'), {
    target: { files: [new File(['%PDF-1.7'], 'vaccines.pdf', { type: 'application/pdf' })] },
  })
  expect(dialog.getByRole('tab', { name: 'Giấy tờ (2/2)' })).toBeTruthy()
  fireEvent.click(dialog.getByRole('button', { name: 'Lưu' }))

  expect(await dialog.findByText(/Thông tin ngựa đã được lưu/)).toBeTruthy()
  expect(dialog.getByRole('tab', { name: 'Giấy tờ (1/2)' })).toBeTruthy()
  expect(dialog.getByText('Không đọc được tệp giấy tờ.')).toBeTruthy()
  expect(dialog.getByText('Đã nộp')).toBeTruthy()
  expect(dialog.getByText('vaccines.pdf')).toBeTruthy()
  expect(saved.mock.calls).toEqual([[false]])
  fireEvent.click(dialog.getByRole('button', { name: 'Thử lại giấy tờ' }))
  await waitFor(() => expect(saved.mock.calls).toEqual([[false], [true]]))
  expect(put).not.toHaveBeenCalled()
  expect(post.mock.calls.map(([url]) => url)).toEqual(['/horses', '/horses/9/documents', '/horses/9/documents', '/horses/9/documents'])
  expect(post.mock.calls.slice(1).map(([, body]) => (body as FormData).get('documentTypeCode')))
    .toEqual(['HORSE_PASSPORT', 'VACCINATION_RECORD', 'VACCINATION_RECORD'])
})
