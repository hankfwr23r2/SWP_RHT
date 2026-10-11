// Trang danh sách của Manager kiểu hệ thống quản lý đơn: thanh tab trạng thái có số đơn, ô tìm kiếm, lọc ngày, bảng, chân bảng và phân trang.
import { useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { STATUS_SHORT, type BookingStatus } from '@shared/config/booking-rules'
import { BookingStatusBadge } from '@shared/ui/BookingStatusBadge'
import { usePagination } from '@shared/ui/usePagination'
import s from './ListPage.module.css'

export interface Column<T> { head: ReactNode; cell: (row: T) => ReactNode; right?: boolean; nowrap?: boolean; minWidth?: number }
export type TabDef<K extends string> = [key: K, label: string, count: number, hint?: string]

interface Props<T, K extends string> {
  title: string
  subtitle?: string
  tabs?: TabDef<K>[] // bỏ trống nếu trang dùng bộ lọc (filters) thay cho thanh tab
  tab?: K
  onTab?: (k: K) => void
  hot?: K[] // tab cần xử lý: số đơn hiện nền đỏ khi > 0
  rows: T[] // đã lọc theo tab
  rowKey: (r: T) => string
  columns: Column<T>[]
  haystack: (r: T) => string[] // chuỗi để tìm kiếm
  dateOf?: (r: T) => number // ngày để lọc (có thì hiện ô lọc ngày)
  dateLabel?: string
  loaded: boolean
  emptyText: string
  summary?: (rows: T[]) => ReactNode
  searchPlaceholder?: string
  filters?: ReactNode // ô lọc thêm, đặt đầu hàng lọc
  hideTabs?: boolean // danh mục nằm ở thanh bên nên không hiện thanh tab
  actions?: ReactNode // nút cạnh tiêu đề (ví dụ "Tạo tài khoản")
  hotRow?: (r: T) => boolean // đơn cần chú ý: viền cam bên trái
}

const day = (v: string, end = false) => (v ? new Date(`${v}T${end ? '23:59:59' : '00:00:00'}`).getTime() : 0)

export function ListPage<T, K extends string>({ title, subtitle, tabs, tab, onTab, hot = [], rows, rowKey, columns, haystack, dateOf, dateLabel = 'Khởi hành', loaded, emptyText, summary, hotRow, actions, filters, searchPlaceholder = 'Tìm theo mã đơn, khách hàng, tuyến…', hideTabs }: Props<T, K>) {
  const [params] = useSearchParams()
  const [text, setText] = useState(params.get('q') ?? '') // tìm từ ô tìm kiếm trên thanh trên
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const keyword = text.trim().toLowerCase()
  const shown = rows.filter(r =>
    (!keyword || haystack(r).some(v => v.toLowerCase().includes(keyword))) &&
    (!dateOf || ((!from || dateOf(r) >= day(from)) && (!to || dateOf(r) <= day(to, true)))))
  const pager = usePagination(shown, 20)
  const filtering = !!(keyword || from || to)
  const change = (fn: () => void) => { fn(); pager.reset() }

  return (
    <div className="page">
      <div className={`wrap ${s.wrap}`}>
        <div className={s.titleRow}><div className={s.title}><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>{actions}</div>
        <section className={s.panel}>
          {tabs && onTab && !hideTabs && (
            <div className={s.tabs} role="tablist" aria-label="Trạng thái">
            {tabs.map(([k, label, n, hint]) => (
              <button key={k} role="tab" aria-selected={tab === k} title={hint} className={`${s.tab} ${tab === k ? s.tabOn : ''} ${n === 0 && k !== tabs[0][0] ? s.tabZero : ''}`} onClick={() => change(() => onTab(k))}>
                {label}<span className={`${s.num} ${hot.includes(k) && n ? s.numHot : ''}`}>{n}</span>
              </button>
            ))}
          </div>
          )}
          <div className={s.filters}>
            {filters}
            <label className={s.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={searchPlaceholder} value={text} onChange={e => change(() => setText(e.target.value))} /></label>
            {dateOf && <div className={s.dates}>{dateLabel}<input type="date" className="form-control" aria-label={`${dateLabel} từ ngày`} value={from} onChange={e => change(() => setFrom(e.target.value))} />→<input type="date" className="form-control" aria-label={`${dateLabel} đến ngày`} value={to} onChange={e => change(() => setTo(e.target.value))} /></div>}
            {filtering && <button className="btn btn-ghost btn-sm" onClick={() => change(() => { setText(''); setFrom(''); setTo('') })}>Xóa lọc</button>}
          </div>
          <div className={s.tableWrap}>
            <table className={s.table}>
              <thead><tr>{columns.map((c, i) => <th key={i} className={c.right ? s.right : undefined} style={c.minWidth ? { minWidth: c.minWidth } : undefined}>{c.head}</th>)}</tr></thead>
              <tbody>
                {pager.rows.map(r => (
                  <tr key={rowKey(r)} className={hotRow?.(r) ? s.hotRow : undefined}>
                    {columns.map((c, i) => <td key={i} className={`${c.right ? s.right : ''} ${c.nowrap ? s.nowrap : ''}`} style={c.minWidth ? { minWidth: c.minWidth } : undefined}>{c.cell(r)}</td>)}
                  </tr>
                ))}
              </tbody>
            </table>
            {loaded && !shown.length && <div className={s.empty}><i className="fa-solid fa-box-open" /><p>{filtering ? 'Không có đơn nào khớp bộ lọc.' : emptyText}</p></div>}
          </div>
          <div className={s.foot}>
            <span>Hiển thị <b>{shown.length}</b> đơn hàng</span>
            {summary && <span>{summary(shown)}</span>}
          </div>
          {shown.length > 20 && pager.bar}
        </section>
      </div>
    </div>
  )
}

// Ô dùng chung: mã đơn kèm loại tuyến, và tuyến rút gọn
export const idCell = (b: { id: string; type: string; gate?: string }) => (
  <><b className={s.idText}>{b.id}</b><div className={s.sub}>{b.type === 'international' ? `Quốc tế${b.gate ? ` · ${b.gate}` : ''}` : 'Trong nước'}</div></>
)
export const routeCell = (from: string, to: string) => <span className={s.route} title={`${from} → ${to}`}>{from} → {to}</span>
export const statusCell = (status: BookingStatus) => <BookingStatusBadge status={status} audience="staff" text={STATUS_SHORT[status]} />
