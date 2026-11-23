// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Field } from './Field'
import { Input } from './Input'

afterEach(cleanup)

describe('Field', () => {
  it('nhãn gắn với ô nhập', () => {
    render(<Field label="Biển số xe">{control => <Input {...control} />}</Field>)
    expect(screen.getByLabelText('Biển số xe').tagName).toBe('INPUT')
  })

  it('gợi ý và lỗi được gắn vào ô qua aria-describedby, lỗi được đọc ngay', () => {
    render(
      <Field label="Biển số xe" hint="Ví dụ 51C-123.45" error="Biển số đã tồn tại">
        {control => <Input {...control} />}
      </Field>,
    )
    const input = screen.getByLabelText('Biển số xe')
    const described = (input.getAttribute('aria-describedby') ?? '').split(' ').map(id => document.getElementById(id)?.textContent)

    expect(described).toEqual(['Ví dụ 51C-123.45', 'Biển số đã tồn tại'])
    expect(input.getAttribute('aria-invalid')).toBe('true')
    expect(screen.getByRole('alert').textContent).toBe('Biển số đã tồn tại')
  })

  it('bắt buộc: ô có thuộc tính required, dấu * chỉ để nhìn', () => {
    render(<Field label="Tên ngựa" required>{control => <Input {...control} />}</Field>)
    expect(screen.getByRole('textbox', { name: /Tên ngựa/ }).hasAttribute('required')).toBe(true)
    expect(screen.getByText('*').getAttribute('aria-hidden')).toBe('true')
  })

  it('không lỗi thì ô không bị đánh dấu lỗi', () => {
    render(<Field label="Ghi chú">{control => <Input {...control} />}</Field>)
    const input = screen.getByLabelText('Ghi chú')
    expect(input.hasAttribute('aria-invalid')).toBe(false)
    expect(input.hasAttribute('aria-describedby')).toBe(false)
  })
})
