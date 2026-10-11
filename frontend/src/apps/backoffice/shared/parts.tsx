// Thành phần dùng chung các trang nội bộ: thanh bước, mục có số, thông tin chuyến, ngựa & hồ sơ, báo giá.
import type { ReactNode } from 'react'
import { DOC_LABEL, requiredDocs } from '@shared/config/documents'
import { formatDate, formatVND } from '@shared/lib/format'
import { orderTotal, type Order } from '@shared/types/order'
import s from './parts.module.css'

export const cx = (...c: (string | false | undefined | null)[]) => c.filter(Boolean).join(' ')

export const MANAGER_STEPS = ['Tiếp nhận', 'Kiểm dịch', 'Lập lộ trình', 'Phê duyệt']

// current: bước đang ở; rejectedAt: bước bị từ chối (nếu có)
export function Stepper({ steps = MANAGER_STEPS, current, rejectedAt }: { steps?: string[]; current: number; rejectedAt?: number }) {
  return (
    <div className={s.stepper}>
      {steps.map((label, i) => {
        const state = rejectedAt !== undefined
          ? (i < rejectedAt ? 'done' : i === rejectedAt ? 'rejected' : '')
          : (i < current ? 'done' : i === current ? 'current' : '')
        return [
          i > 0 && <div key={`l${i}`} className={s.line} />,
          <div key={label} className={cx(s.step, state === 'done' && s.stepDone, state === 'current' && s.stepCurrent, state === 'rejected' && s.stepRejected)}>
            <span className={s.dot}>{state === 'done' ? <i className="fa-solid fa-check" /> : state === 'rejected' ? <i className="fa-solid fa-xmark" /> : i + 1}</span>{label}
          </div>,
        ]
      })}
    </div>
  )
}

export function Section({ num, title, children }: { num: number; title: string; children: ReactNode }) {
  return (
    <div className={s.section}>
      <div className={s.sectionTitle}><span className={s.sectionNum}>{num}</span>{title}</div>
      {children}
    </div>
  )
}

export const InfoItem = ({ label, children }: { label: string; children: ReactNode }) =>
  <div className={s.infoItem}><span className={s.infoLabel}>{label}</span><span className={s.infoValue}>{children}</span></div>

export function TripInfo({ order: o }: { order: Order }) {
  return (
    <div className={s.infoGrid}>
      <InfoItem label="Điểm đi">{o.from}</InfoItem>
      <InfoItem label="Điểm đến">{o.to}</InfoItem>
      <InfoItem label="Loại tuyến">{o.border ? 'Xuyên quốc gia (đường bộ)' : 'Nội địa (đường bộ)'}</InfoItem>
      <InfoItem label="Cửa khẩu">{o.border || '—'}</InfoItem>
      <InfoItem label="Quãng đường / Thời gian">{o.distance} · {o.duration}</InfoItem>
      <InfoItem label="Ngày khởi hành">{formatDate(o.departAt)}</InfoItem>
      <InfoItem label="Số ngựa">{o.horses.length} con</InfoItem>
    </div>
  )
}

export function HorsesDocs({ order: o }: { order: Order }) {
  const docs = requiredDocs(!!o.border)
  return (
    <>
      {o.horses.map((h, i) => (
        <details key={h.name} className={s.horse} open={i === 0}>
          <summary><span><b>{h.name}</b> · {h.breed} · {h.sex}</span><span className="text-muted">Chip {h.chip} · Đã nộp {docs.length}/{docs.length} giấy tờ</span></summary>
          <ul className={s.docs}>{docs.map(d => <li key={d}><i className="fa-solid fa-file-lines" />{DOC_LABEL[d]}</li>)}</ul>
        </details>
      ))}
      {o.customerNote && <div className={s.note}><i className="fa-solid fa-comment-dots" /> Yêu cầu của khách: {o.customerNote}</div>}
    </>
  )
}

export function QuoteList({ order: o, totalLabel = 'Tổng giá đã báo khách' }: { order: Order; totalLabel?: string }) {
  return (
    <>
      <ul className={s.costList}>{o.services.map(sv => <li key={sv[0]}><span>{sv[0]}{sv[1] && <span className="sub-text"> · {sv[1]}</span>}</span><span className="nowrap">{formatVND(sv[2])}</span></li>)}</ul>
      <div className={s.costTotal}><span>{totalLabel}</span><span>{formatVND(orderTotal(o))}</span></div>
    </>
  )
}

export function SearchBox({ value, onChange, placeholder }: { value: string; onChange: (v: string) => void; placeholder: string }) {
  return <div className={s.search}><i className="fa-solid fa-search" /><input value={value} onChange={e => onChange(e.target.value)} placeholder={placeholder} /></div>
}

// Lọc theo chữ trong các trường chính của đơn
export const matches = (o: Order, q: string) => !q || [o.id, o.customer, o.routeShort, o.border ?? ''].join(' ').toLowerCase().includes(q.toLowerCase())

export { s as partStyles }
