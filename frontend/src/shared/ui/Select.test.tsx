// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Field } from './Field'
import { Select, type SelectOption } from './Select'

afterEach(cleanup)

const options: SelectOption[] = [
  { value: 'v1', label: '51C-123.45' },
  { value: 'v2', label: '29H-456.78', disabled: true },
  { value: 'v3', label: '43A-111.22' },
]

function Vehicle({ initial = '', onChange = vi.fn() }: { initial?: string; onChange?: (v: string) => void }) {
  const [value, setValue] = useState(initial)
  return (
    <Field label="Xe" hint="Chỉ xe đang rảnh" error={value ? undefined : 'Chọn xe'}>
      {c => <Select {...c} value={value} options={options} placeholder="Chọn xe" onChange={v => { setValue(v); onChange(v) }} />}
    </Field>
  )
}

describe('Select', () => {
  it('dùng trong Field: có nhãn, gợi ý, lỗi và hiện chữ gợi ý khi chưa chọn', () => {
    render(<Vehicle />)
    const box = screen.getByRole('combobox', { name: 'Xe' })
    expect(box.textContent).toBe('Chọn xe')
    expect(box.getAttribute('aria-invalid')).toBe('true')
    expect(box.getAttribute('aria-describedby')?.split(' ')).toHaveLength(2)
  })

  it('bấm mở danh sách, chọn một mục thì đóng và báo giá trị', () => {
    const onChange = vi.fn()
    render(<Vehicle onChange={onChange} />)
    fireEvent.click(screen.getByRole('combobox', { name: 'Xe' }))
    fireEvent.click(screen.getByRole('option', { name: '43A-111.22' }))

    expect(onChange).toHaveBeenCalledWith('v3')
    expect(screen.queryByRole('listbox')).toBeNull()
    expect(screen.getByRole('combobox', { name: 'Xe' }).textContent).toBe('43A-111.22')
  })

  it('mục bị khóa không chọn được, phím mũi tên bỏ qua mục bị khóa', () => {
    const onChange = vi.fn()
    render(<Vehicle initial="v1" onChange={onChange} />)
    const box = screen.getByRole('combobox', { name: 'Xe' })

    fireEvent.click(box)
    fireEvent.click(screen.getByRole('option', { name: '29H-456.78' }))
    expect(onChange).not.toHaveBeenCalled()

    fireEvent.keyDown(box, { key: 'ArrowDown' })
    expect(document.getElementById(box.getAttribute('aria-activedescendant')!)?.textContent).toBe('43A-111.22')
    fireEvent.keyDown(box, { key: 'Enter' })
    expect(onChange).toHaveBeenCalledWith('v3')
  })

  it('Escape đóng danh sách và đánh dấu đã xử lý để hộp thoại không đóng theo', () => {
    render(<Vehicle />)
    const box = screen.getByRole('combobox', { name: 'Xe' })
    fireEvent.click(box)
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
    act(() => { box.dispatchEvent(event) })

    expect(event.defaultPrevented).toBe(true)
    expect(box.getAttribute('aria-expanded')).toBe('false')
  })
})
