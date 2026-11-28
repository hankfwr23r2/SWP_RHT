// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Input } from './Input'
import { Textarea } from './Textarea'

afterEach(cleanup)

describe('Input và Textarea', () => {
  it('invalid gắn aria-invalid để trình đọc màn hình và CSS biết ô lỗi', () => {
    render(<><Input aria-label="Biển số" invalid /><Textarea aria-label="Ghi chú" invalid /></>)
    expect(screen.getByRole('textbox', { name: 'Biển số' }).getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByRole('textbox', { name: 'Ghi chú' }).getAttribute('aria-invalid')).toBe('true')
  })

  it('không lỗi thì không có aria-invalid', () => {
    render(<Input aria-label="Biển số" />)
    expect(screen.getByRole('textbox', { name: 'Biển số' }).hasAttribute('aria-invalid')).toBe(false)
  })

  it('giữ các thuộc tính gốc của ô nhập', () => {
    render(<Input aria-label="Số điện thoại" type="tel" placeholder="0909…" disabled />)
    const input = screen.getByRole('textbox', { name: 'Số điện thoại' })
    expect(input.getAttribute('type')).toBe('tel')
    expect(input.getAttribute('placeholder')).toBe('0909…')
    expect(input.hasAttribute('disabled')).toBe(true)
  })
})
