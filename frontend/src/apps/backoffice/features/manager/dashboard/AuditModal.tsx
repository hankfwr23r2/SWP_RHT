// Manager: đối soát chi phí sự cố, chọn bên chịu từng khoản (mặc định theo chính sách 11.5), phát hành bảng quyết toán (Flow 6, PRD mục 7.4).
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { EXPENSE_CATEGORY, INCIDENT_KIND, type Payer } from '@shared/config/booking-rules'
import { formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking } from '@shared/types/booking'
import { FormSelect } from '@shared/ui/FormSelect'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Modal } from '@shared/ui/Modal'
import { ReadMore } from '@shared/ui/ReadMore'
import { useToast } from '@shared/ui/toast'
import { placeShort } from '../../../shared/place'
import s from '../../../shared/booking.module.css'

const route = (b: Booking) => `${placeShort(b.origin.name)} → ${placeShort(b.dest.name)}`

export function AuditModal({ b, onClose, onDone }: { b: Booking; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const expenses = (b.incidents ?? []).flatMap(i => i.expenses.map(e => ({ ...e, kind: i.kind })))
  const [payers, setPayers] = useState<Record<string, Payer>>(() => Object.fromEntries(expenses.map(e => [e.id, e.payer])))
  const [busy, setBusy] = useState(false)
  const customerTotal = expenses.filter(e => payers[e.id] === 'customer').reduce((n, e) => n + e.amount, 0)
  const carrierTotal = expenses.filter(e => payers[e.id] === 'carrier').reduce((n, e) => n + e.amount, 0)
  const issue = async () => {
    setBusy(true)
    try { await bookingsApi.issueSettlement(b.id, session!.name, payers); toast(`Đã phát hành bảng quyết toán ${b.id}`); onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không phát hành được', 'error'); setBusy(false) }
  }
  return (
    <Modal wide onClose={onClose} title={`Đối soát chi phí ${b.id}`} subtitle={`${b.customer} · ${route(b)}`}
      footer={<><button className="btn btn-ghost" onClick={onClose}>Đóng</button><button className="btn btn-primary" disabled={busy} onClick={issue}><i className="fa-solid fa-file-invoice-dollar" /> Phát hành bảng quyết toán</button></>}>
      <div className="table-wrap"><table className="data-table">
        <thead><tr><th>Chứng từ</th><th>Khoản chi</th><th>Số tiền</th><th>Bên chịu</th></tr></thead>
        <tbody>{expenses.map(e => (
          <tr key={e.id}>
            <td><ImageThumb name={e.photo} size={40} /></td>
            <td>{EXPENSE_CATEGORY[e.category]}: {e.label}<div className={s.sub}>{INCIDENT_KIND[e.kind].label}</div></td>
            <td className="nowrap">{formatVND(e.amount)}</td>
            <td><FormSelect aria-label={`Bên chịu ${e.label}`} className="form-control" value={payers[e.id]} onChange={ev => setPayers({ ...payers, [e.id]: ev.target.value as Payer })}><option value="customer">Khách chịu</option><option value="carrier">Nhà xe chịu</option></FormSelect></td>
          </tr>
        ))}</tbody>
      </table></div>
      <p style={{ marginTop: 14 }}>Khách phải trả thêm: <b>{formatVND(customerTotal)}</b> · Nhà xe chịu: <b>{formatVND(carrierTotal)}</b></p>
      <ReadMore className={s.hint} text={'Chính sách: chi phí về ngựa (thú y, thuốc, chuồng đệm) khách chịu; chi phí về vận chuyển (cứu hộ, sửa xe) nhà xe chịu; tắc cửa khẩu nhà xe chịu. Giá chuyến đã cố định nên nhiên liệu và cầu đường không tính thêm. Bảng quyết toán chỉ gồm khoản khách chịu, khách có 24 giờ để thanh toán.'} />
    </Modal>
  )
}
