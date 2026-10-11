import { useState } from 'react'

// Phân trang phía giao diện, mặc định 10 dòng/trang như các bảng cũ.
export function usePagination<T>(items: T[], perPage = 10) {
  const [page, setPage] = useState(1)
  const totalPages = Math.max(1, Math.ceil(items.length / perPage))
  const current = Math.min(page, totalPages)
  const rows = items.slice((current - 1) * perPage, current * perPage)
  const from = items.length ? (current - 1) * perPage + 1 : 0
  const to = Math.min(current * perPage, items.length)

  const bar = (
    <div className="pagination">
      <span>Hiển thị {from}–{to} trên {items.length}</span>
      <div className="pagination-buttons">
        <button disabled={current === 1} onClick={() => setPage(current - 1)} aria-label="Trang trước"><i className="fa-solid fa-chevron-left" /></button>
        {Array.from({ length: totalPages }, (_, i) => (
          <button key={i} className={i + 1 === current ? 'active' : ''} onClick={() => setPage(i + 1)}>{i + 1}</button>
        ))}
        <button disabled={current === totalPages} onClick={() => setPage(current + 1)} aria-label="Trang sau"><i className="fa-solid fa-chevron-right" /></button>
      </div>
    </div>
  )
  return { rows, bar, reset: () => setPage(1) }
}
