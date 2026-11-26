// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { Table, type TableColumn } from './Table'

afterEach(cleanup)

interface Vehicle { id: number; plate: string; capacity: number }
const rows: Vehicle[] = [{ id: 7, plate: '51C-123.45', capacity: 4 }, { id: 8, plate: '29H-456.78', capacity: 2 }]
const columns: TableColumn<Vehicle>[] = [
  { key: 'plate', header: 'Biển số', render: v => v.plate },
  { key: 'capacity', header: 'Số khoang', render: v => v.capacity, align: 'end' },
]

describe('Table', () => {
  it('có tên bảng và tiêu đề cột cho trình đọc màn hình', () => {
    render(<Table caption="Đội xe" columns={columns} rows={rows} rowKey={v => v.id} />)
    const table = screen.getByRole('table', { name: 'Đội xe' })
    expect(within(table).getAllByRole('columnheader').map(h => h.textContent)).toEqual(['Biển số', 'Số khoang'])
    expect(within(table).getAllByRole('row')).toHaveLength(3)
  })

  it('mỗi ô mang nhãn cột để hiện khi bảng thành thẻ trên điện thoại', () => {
    render(<Table caption="Đội xe" columns={columns} rows={rows} rowKey={v => v.id} />)
    const cells = screen.getAllByRole('cell')
    expect(cells.map(c => [c.getAttribute('data-label'), c.textContent])).toEqual([
      ['Biển số', '51C-123.45'], ['Số khoang', '4'],
      ['Biển số', '29H-456.78'], ['Số khoang', '2'],
    ])
  })
})
