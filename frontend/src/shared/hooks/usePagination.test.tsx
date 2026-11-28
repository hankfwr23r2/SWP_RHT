// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { usePagination } from './usePagination'

afterEach(cleanup)

const range = (n: number) => Array.from({ length: n }, (_, i) => i + 1)

function List({ items }: { items: number[] }) {
  const { rows, bar, reset } = usePagination(items)
  return (
    <>
      <output>{rows.join(',')}</output>
      <button onClick={reset}>Về đầu</button>
      {bar}
    </>
  )
}

const shown = () => screen.getByRole('status').textContent

describe('usePagination', () => {
  it('mỗi trang 10 dòng, ghi rõ đang xem dòng nào', () => {
    render(<List items={range(25)} />)
    expect(shown()).toBe('1,2,3,4,5,6,7,8,9,10')
    expect(screen.getByText('Hiển thị 1–10 trên 25')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '3' }))
    expect(shown()).toBe('21,22,23,24,25')
    expect((screen.getByRole('button', { name: 'Trang sau' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('danh sách ngắn lại thì về trang cuối còn dữ liệu', () => {
    const { rerender } = render(<List items={range(25)} />)
    fireEvent.click(screen.getByRole('button', { name: '3' }))

    rerender(<List items={range(12)} />)
    expect(shown()).toBe('11,12')
  })

  it('reset() về trang 1', () => {
    render(<List items={range(25)} />)
    fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }))
    fireEvent.click(screen.getByRole('button', { name: 'Về đầu' }))
    expect(shown()).toBe('1,2,3,4,5,6,7,8,9,10')
  })
})
