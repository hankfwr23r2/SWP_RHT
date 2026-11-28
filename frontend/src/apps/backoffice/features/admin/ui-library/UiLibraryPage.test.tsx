// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import '@shared/i18n'
import { ToastProvider } from '@shared/ui/toast'
import UiLibraryPage from './UiLibraryPage'

afterEach(cleanup)

const renderPage = () => render(<ToastProvider><UiLibraryPage /></ToastProvider>)

describe('Thư viện giao diện', () => {
  it('có đủ các mục và component dùng chung', () => {
    renderPage()
    expect(screen.getByRole('heading', { level: 1, name: 'Thư viện giao diện' })).toBeTruthy()
    const sections = screen.getAllByRole('heading', { level: 2 }).map(h => h.textContent)
    expect(sections).toEqual(['Màu', 'Nút', 'Ô nhập', 'Nhãn', 'Tab và các bước', 'Bảng', 'Hộp thoại và thông báo', '4 trạng thái của màn hình'])

    for (const variant of ['Chính', 'Phụ', 'Nhẹ', 'Nguy hiểm']) expect(screen.getAllByRole('button', { name: variant })).toHaveLength(3)
    expect(screen.getByRole('button', { name: 'Sửa' })).toBeTruthy()
    expect(screen.getByRole('combobox', { name: /Loại xe/ })).toBeTruthy()
    expect(screen.getByRole('textbox', { name: 'Ghi chú' })).toBeTruthy()
    expect(screen.getByRole('navigation', { name: 'Các bước đặt đơn' })).toBeTruthy()
    expect(screen.getByRole('table', { name: 'Đội xe mẫu' })).toBeTruthy()
    expect(screen.getByRole('tablist', { name: 'Trạng thái đơn' })).toBeTruthy()
  })

  it('chuyển qua đủ 4 trạng thái của màn hình', () => {
    renderPage()
    const states = screen.getByRole('tablist', { name: 'Trạng thái màn hình' })
    const panel = () => screen.getByRole('tabpanel', { name: within(states).getByRole('tab', { selected: true }).textContent! })

    expect(within(panel()).getByRole('status').textContent).toBe('Đang tải')

    fireEvent.click(within(states).getByRole('tab', { name: 'Trống' }))
    expect(within(panel()).getByRole('heading', { name: 'Chưa có xe nào' })).toBeTruthy()

    fireEvent.click(within(states).getByRole('tab', { name: 'Lỗi' }))
    expect(within(panel()).getByRole('alert').textContent).toContain('Mất kết nối tới máy chủ')

    fireEvent.click(within(panel()).getByRole('button', { name: 'Thử lại' }))
    expect(within(panel()).getByRole('table', { name: 'Đội xe mẫu' })).toBeTruthy()
  })

  it('mở được hộp thoại và hộp xác nhận', () => {
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Mở hộp thoại' }))
    const dialog = screen.getByRole('dialog', { name: 'Sửa xe 51C-123.45' })
    fireEvent.click(within(dialog).getAllByRole('button', { name: 'Đóng' })[0])
    expect(screen.queryByRole('dialog')).toBeNull()

    fireEvent.click(screen.getByRole('button', { name: 'Ngừng dùng xe' }))
    expect(screen.getByRole('alertdialog', { name: 'Ngừng dùng xe 51C-123.45?' })).toBeTruthy()
  })
})
