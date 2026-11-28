// @vitest-environment jsdom
import '@shared/i18n'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Button } from './Button'

afterEach(cleanup)

describe('Button', () => {
  it('mặc định là type="button", không vô tình gửi form', () => {
    render(<form><Button>Lưu</Button></form>)
    expect(screen.getByRole('button', { name: 'Lưu' }).getAttribute('type')).toBe('button')
  })

  it('đang xử lý thì khóa nút, báo cho trình đọc màn hình và không nhận bấm lần hai', () => {
    const onClick = vi.fn()
    render(<Button variant="primary" loading onClick={onClick}>Gửi báo giá</Button>)
    const button = screen.getByRole('button', { name: /Gửi báo giá/ })

    fireEvent.click(button)

    expect(button.hasAttribute('disabled')).toBe(true)
    expect(button.getAttribute('aria-busy')).toBe('true')
    expect(button.textContent).toContain('Đang xử lý')
    expect(onClick).not.toHaveBeenCalled()
  })

  it('bấm được khi không xử lý', () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Hủy</Button>)
    fireEvent.click(screen.getByRole('button', { name: 'Hủy' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('nút chỉ có biểu tượng lấy tên từ aria-label, biểu tượng bị ẩn với trình đọc màn hình', () => {
    render(<Button aria-label="Xóa ngựa" icon={<i className="fa-solid fa-trash" />} />)
    const button = screen.getByRole('button', { name: 'Xóa ngựa' })
    expect(button.querySelector('[aria-hidden="true"] i')).not.toBeNull()
  })

  it('kiểu dữ liệu bắt buộc aria-label khi không có chữ', () => {
    // @ts-expect-error nút không có chữ thì phải có aria-label
    const missingLabel = <Button icon={<i />} />
    expect(missingLabel).toBeTruthy()
  })
})
