import { DEPOSIT_RATE } from '../config/booking-rules'
import { formatDateTime, formatVND } from '../lib/format'
import { PolicyLink } from './PolicyLink'
import type { Adjustment, QuoteLine } from '../types/booking'
import s from './QuoteSheet.module.css'

interface QuoteSheetProps {
  lines: QuoteLine[]
  adjustments?: Adjustment[]
  subtotal: number
  total: number
  deposit: number
  balance: number
  expiresAt?: number
}

// Phiếu báo giá (PRD mục 2.5): mọi khoản cố định, không phụ thu; cọc 30% và số dư 70% ngày bốc ngựa
export function QuoteSheet({ lines, adjustments = [], subtotal, total, deposit, balance, expiresAt }: QuoteSheetProps) {
  return (
    <div className={s.sheet}>
      <table className={s.table}>
        <caption className={s.caption}>Chi phí cố định trọn gói</caption>
        <tbody>
          {lines.map(l => (
            <tr key={l.label}>
              <td><div className={s.label}>{l.label}</div>{l.detail && <div className={s.detail}>{l.detail}</div>}</td>
              <td className={s.amount}>{l.amount ? formatVND(l.amount) : 'Đã gồm'}</td>
            </tr>
          ))}
          {adjustments.map(a => (
            <tr key={a.label} className={a.amount < 0 ? s.discount : ''}>
              <td><div className={s.label}>{a.label}</div><div className={s.detail}>{a.amount < 0 ? 'Chiết khấu thương mại' : 'Phụ phí'}</div></td>
              <td className={s.amount}>{a.amount < 0 ? '−' : '+'}{formatVND(Math.abs(a.amount))}</td>
            </tr>
          ))}
        </tbody>
        {adjustments.length > 0 && <tfoot><tr><td>Cộng trước điều chỉnh</td><td className={s.amount}>{formatVND(subtotal)}</td></tr></tfoot>}
      </table>

      <div className={s.totals}>
        <div className={s.total}><span>Tổng giá trị</span><b>{formatVND(total)}</b></div>
        <div className={s.note}>Chưa gồm VAT. Giá cố định, nhiên liệu và phí cầu đường đã nằm trong báo giá.</div>
        <div className={s.deposit}><span>Đặt cọc để nhận vận đơn ({DEPOSIT_RATE * 100}%)</span><b>{formatVND(deposit)}</b></div>
        <div className={s.deposit}><span>Thanh toán còn lại vào ngày bốc ngựa ({100 - DEPOSIT_RATE * 100}%)</span><b>{formatVND(balance)}</b></div>
      </div>

      <div className={s.after}>
        <div><i className="fa-solid fa-lock" aria-hidden="true" /> <b>Không phụ thu ngoài phiếu:</b> chỉ phát sinh thêm khi có sự cố liên quan đến ngựa hoặc dịch vụ bạn chọn thêm (xem <PolicyLink doc="incident_cost">chính sách chi phí sự cố</PolicyLink>).</div>
        {expiresAt && <div><i className="fa-solid fa-hourglass-half" aria-hidden="true" /> <b>Báo giá có hiệu lực đến</b> {formatDateTime(expiresAt)}.</div>}
      </div>
    </div>
  )
}
