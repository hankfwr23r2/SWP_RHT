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
  showCalculation?: boolean
}

// Phiếu báo giá (PRD mục 2.5): mọi khoản cố định, không phụ thu; số cọc và số dư theo tỷ lệ cấu hình.
function calculationOf(line: QuoteLine) {
  if (line.calculation) return line.calculation
  if (line.label.startsWith('Cước vận chuyển')) return 'Tính theo quãng đường và hạng xe nêu trên.'
  if (line.label.startsWith('Nhân sự kỹ thuật')) return `Dựa trên thời gian chuyến đi (${line.detail}), gồm 1 tài xế và 1 hộ tống.`
  if (line.label.startsWith('Nhiên liệu và BOT')) return `Ước tính theo lộ trình; ${line.detail.toLowerCase()}.`
  if (line.label.includes('kiểm dịch') || line.label.includes('hải quan')) return 'Phí thủ tục theo loại tuyến.'
  return undefined
}

export function QuoteSheet({ lines, adjustments = [], subtotal, total, deposit, balance, expiresAt, showCalculation = false }: QuoteSheetProps) {
  const depositPercentage = total > 0
    ? Number((deposit / total * 100).toFixed(2))
    : DEPOSIT_RATE * 100
  return (
    <div className={s.sheet}>
      <table className={s.table}>
        <caption className={s.caption}>Chi phí cố định trọn gói</caption>
        <tbody>
          {lines.map(l => (
            <tr key={l.label}>
              <td>
                <div className={s.label}>{l.label}</div>
                {l.detail && <div className={s.detail}>{l.detail}</div>}
                {showCalculation && calculationOf(l) && <div className={s.calculation}><b>Cách tính:</b> {calculationOf(l)}</div>}
              </td>
              <td className={s.amount}>{l.amount ? formatVND(l.amount) : 'Đã gồm'}</td>
            </tr>
          ))}
          <tr className={s.subtotal}><td>Cộng các khoản</td><td className={s.amount}>{formatVND(subtotal)}</td></tr>
          {adjustments.map(a => (
            <tr key={a.label} className={a.amount < 0 ? s.discount : ''}>
              <td><div className={s.label}>{a.label}</div><div className={s.detail}>{a.amount < 0 ? 'Chiết khấu thương mại' : 'Phụ phí'}</div></td>
              <td className={s.amount}>{a.amount < 0 ? '−' : '+'}{formatVND(Math.abs(a.amount))}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className={s.totals}>
        <div className={s.total}><span>Tổng giá trị</span><b>{formatVND(total)}</b></div>
        <div className={s.note}>Chưa gồm VAT. Giá cố định, nhiên liệu và phí cầu đường đã nằm trong báo giá.</div>
        <div className={s.deposit}><span>Đặt cọc để nhận vận đơn ({depositPercentage.toLocaleString('vi-VN')}%)</span><b>{formatVND(deposit)}</b></div>
        <div className={s.deposit}><span>Thanh toán còn lại vào ngày bốc ngựa ({(100 - depositPercentage).toLocaleString('vi-VN')}%)</span><b>{formatVND(balance)}</b></div>
      </div>

      <div className={s.after}>
        <div><i className="fa-solid fa-lock" aria-hidden="true" /> <b>Không phụ thu ngoài phiếu:</b> chỉ phát sinh thêm khi có sự cố liên quan đến ngựa hoặc dịch vụ bạn chọn thêm (xem <PolicyLink doc="incident_cost">chính sách chi phí sự cố</PolicyLink>).</div>
        {expiresAt && <div><i className="fa-solid fa-hourglass-half" aria-hidden="true" /> <b>Báo giá có hiệu lực đến</b> {formatDateTime(expiresAt)}.</div>}
      </div>
    </div>
  )
}
