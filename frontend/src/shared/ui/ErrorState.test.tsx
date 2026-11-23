// @vitest-environment jsdom
import '@shared/i18n'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ErrorState } from './ErrorState'

afterEach(cleanup)

describe('ErrorState', () => {
  it('báo lỗi ngay, có tiêu đề mặc định và nút thử lại', () => {
    const onRetry = vi.fn()
    render(<ErrorState message="Máy chủ chưa trả lời." onRetry={onRetry} />)

    expect(screen.getByRole('alert')).toBeTruthy()
    expect(screen.getByRole('heading', { name: 'Không tải được dữ liệu' })).toBeTruthy()
    expect(screen.getByText('Máy chủ chưa trả lời.')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('đang thử lại thì khóa nút', () => {
    const onRetry = vi.fn()
    render(<ErrorState onRetry={onRetry} retrying />)
    const button = screen.getByRole('button', { name: /Thử lại/ })

    fireEvent.click(button)

    expect(button.hasAttribute('disabled')).toBe(true)
    expect(onRetry).not.toHaveBeenCalled()
  })
})
