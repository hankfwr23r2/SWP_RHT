import type { ReactNode } from 'react'
import s from './Table.module.css'

export interface TableColumn<T> {
  key: string
  // Chữ thường, dùng làm nhãn của ô khi bảng chuyển thành thẻ trên điện thoại
  header: string
  render: (row: T) => ReactNode
  align?: 'start' | 'center' | 'end'
}

interface TableProps<T> {
  // Tên bảng cho trình đọc màn hình; showCaption để hiện ra trên màn hình
  caption: string
  showCaption?: boolean
  columns: TableColumn<T>[]
  rows: T[]
  rowKey: (row: T) => string | number
}

// Bảng dữ liệu. Từ 640px trở xuống mỗi dòng thành một thẻ, mỗi ô có nhãn cột (AGENTS.md mục 8.6).
// Không có dòng nào thì trang hiện EmptyState thay cho bảng.
export function Table<T>({ caption, showCaption = false, columns, rows, rowKey }: TableProps<T>) {
  const align = (c: TableColumn<T>) => (c.align && c.align !== 'start' ? s[c.align] : undefined)
  return (
    <div className={s.wrap}>
      <table className={s.table}>
        <caption className={showCaption ? s.caption : s.srOnly}>{caption}</caption>
        <thead>
          <tr>
            {columns.map(c => <th key={c.key} scope="col" className={align(c)}>{c.header}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map(row => (
            <tr key={rowKey(row)}>
              {columns.map(c => <td key={c.key} data-label={c.header} className={align(c)}>{c.render(row)}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
