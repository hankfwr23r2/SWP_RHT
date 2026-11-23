// @vitest-environment jsdom
import '@shared/i18n'
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Skeleton } from './Skeleton'

afterEach(cleanup)

describe('Skeleton', () => {
  it('báo đang tải cho trình đọc màn hình, phần hình bị ẩn', () => {
    const { container } = render(<Skeleton />)
    expect(screen.getByRole('status').textContent).toBe('Đang tải')
    expect(container.querySelector('[aria-hidden="true"]')).not.toBeNull()
  })

  it('nhận nhãn riêng', () => {
    render(<Skeleton label="Đang tải danh sách xe" />)
    expect(screen.getByRole('status').textContent).toBe('Đang tải danh sách xe')
  })

  it('vẽ đúng số dòng để giữ bố cục', () => {
    const { container } = render(<Skeleton lines={3} />)
    expect(container.querySelectorAll('[aria-hidden="true"] > span')).toHaveLength(3)
  })

  it('dạng khác dòng chữ chỉ vẽ một khối', () => {
    const { container } = render(<Skeleton variant="block" lines={3} />)
    expect(container.querySelectorAll('[aria-hidden="true"] > span')).toHaveLength(1)
  })
})
