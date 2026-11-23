// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { afterEach, describe, expect, it } from 'vitest'
import { Tabs } from './Tabs'

afterEach(cleanup)

const items = [
  { value: 'new', label: 'Mới', count: 3 },
  { value: 'running', label: 'Đang chạy' },
  { value: 'done', label: 'Đã xong' },
]

function Orders() {
  const [tab, setTab] = useState('new')
  return <Tabs label="Trạng thái đơn" items={items} value={tab} onChange={setTab}>Nội dung {tab}</Tabs>
}

describe('Tabs', () => {
  it('tab đang chọn gắn với vùng nội dung, chỉ tab đó nằm trong thứ tự Tab', () => {
    render(<Orders />)
    const selected = screen.getByRole('tab', { selected: true })
    expect(selected.textContent).toBe('Mới3')
    expect(screen.getByRole('tabpanel', { name: 'Mới3' }).textContent).toBe('Nội dung new')
    expect(screen.getAllByRole('tab').map(t => t.tabIndex)).toEqual([0, -1, -1])
  })

  it('bấm chuột chuyển tab', () => {
    render(<Orders />)
    fireEvent.click(screen.getByRole('tab', { name: 'Đã xong' }))
    expect(screen.getByRole('tabpanel').textContent).toBe('Nội dung done')
  })

  it('phím mũi tên chuyển tab và tiêu điểm, đi vòng ở hai đầu; Home, End về đầu, cuối', () => {
    render(<Orders />)
    const tab = (name: string) => screen.getByRole('tab', { name })

    fireEvent.keyDown(tab('Mới3'), { key: 'ArrowRight' })
    expect(document.activeElement).toBe(tab('Đang chạy'))
    expect(tab('Đang chạy').getAttribute('aria-selected')).toBe('true')

    fireEvent.keyDown(tab('Đang chạy'), { key: 'End' })
    expect(document.activeElement).toBe(tab('Đã xong'))

    fireEvent.keyDown(tab('Đã xong'), { key: 'ArrowRight' })
    expect(document.activeElement).toBe(tab('Mới3'))

    fireEvent.keyDown(tab('Mới3'), { key: 'ArrowLeft' })
    expect(document.activeElement).toBe(tab('Đã xong'))

    fireEvent.keyDown(tab('Đã xong'), { key: 'Home' })
    expect(screen.getByRole('tabpanel').textContent).toBe('Nội dung new')
  })
})
