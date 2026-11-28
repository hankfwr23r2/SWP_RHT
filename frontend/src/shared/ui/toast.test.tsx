// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ToastProvider, useToast } from './toast'

afterEach(() => {
  cleanup()
  vi.useRealTimers()
})

function Trigger({ message, type }: { message: string; type?: 'info' | 'success' | 'error' }) {
  const toast = useToast()
  return <button onClick={() => toast(message, type)}>Báo</button>
}

const renderWith = (message: string, type?: 'info' | 'success' | 'error') =>
  render(<ToastProvider><Trigger message={message} type={type} /></ToastProvider>)
const polite = () => document.querySelector('[aria-live="polite"]')!
const assertive = () => document.querySelector('[aria-live="assertive"]')!

describe('Toast', () => {
  it('vùng thông báo có sẵn từ đầu để trình đọc màn hình theo dõi, không chiếm role status/alert của trang', () => {
    renderWith('Đã lưu')
    expect(polite().textContent).toBe('')
    expect(assertive().textContent).toBe('')
    expect(screen.queryByRole('status')).toBeNull()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('thành công đọc ở vùng polite', () => {
    renderWith('Đã lưu xe')
    fireEvent.click(screen.getByText('Báo'))
    expect(polite().textContent).toBe('Đã lưu xe')
    expect(assertive().textContent).toBe('')
  })

  it('lỗi đọc ngay ở vùng assertive', () => {
    renderWith('Không lưu được xe', 'error')
    fireEvent.click(screen.getByText('Báo'))
    expect(assertive().textContent).toBe('Không lưu được xe')
    expect(polite().textContent).toBe('')
  })

  it('tự ẩn sau 3,5 giây', () => {
    vi.useFakeTimers()
    renderWith('Đã lưu xe')
    fireEvent.click(screen.getByText('Báo'))
    act(() => vi.advanceTimersByTime(3500))
    expect(polite().textContent).toBe('')
  })
})
