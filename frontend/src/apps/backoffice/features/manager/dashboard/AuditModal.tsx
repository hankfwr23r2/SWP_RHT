import { incidentExpenseItems } from '@shared/lib/incidentCosts'
// Manager approves internal company expenses and closes the order.
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { EXPENSE_CATEGORY, INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking } from '@shared/types/booking'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Modal } from '@shared/ui/Modal'
import { ReadMore } from '@shared/ui/ReadMore'
import { useToast } from '@shared/ui/toast'
import { placeShort } from '../../../shared/place'
import s from '../../../shared/booking.module.css'

const route = (b: Booking) => `${placeShort(b.origin.name)} → ${placeShort(b.dest.name)}`

export function AuditModal({ b, onClose, onDone }: { b: Booking; onClose: () => void; onDone: () => void }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { session } = useAuth()
  const expenses = incidentExpenseItems(b)
  const [busy, setBusy] = useState(false)
  const carrierTotal = expenses.reduce((n, e) => n + e.amount, 0)
  // Toll receipts are internal operating records and are shown for reconciliation.
  const tolls = (b.trips ?? []).flatMap(trip => trip.tolls ?? [])
  const tollTotal = tolls.reduce((n, x) => n + x.amount, 0)
  const issue = async () => {
    setBusy(true)
    try { await bookingsApi.issueSettlement(b.id, session!.name); toast(t('auditModal.issuedSuccess', 'Đã duyệt chi phí nội bộ {{id}}', { id: b.id })); onDone() } catch (e) { toast(e instanceof Error ? e.message : t('auditModal.issueFailed', 'Không phát hành được'), 'error'); setBusy(false) }
  }
  return (
    <Modal wide onClose={onClose} title={t('auditModal.title', 'Đối soát chi phí {{id}}', { id: b.id })} subtitle={`${b.customer} · ${route(b)}`}
      footer={<><button className="btn btn-ghost" onClick={onClose}>{t('common.close', 'Đóng')}</button><button className="btn btn-primary" disabled={busy} onClick={issue}><i className="fa-solid fa-file-invoice-dollar" /> {t('auditModal.issueSettlement', 'Duyệt chi phí và hoàn tất đơn')}</button></>}>
      <div className="table-wrap"><table className="data-table">
        <thead><tr><th>{t('auditModal.colDoc', 'Chứng từ')}</th><th>{t('auditModal.colItem', 'Khoản chi')}</th><th>{t('auditModal.colAmount', 'Số tiền')}</th><th>{t('auditModal.colPayer', 'Bên chịu')}</th></tr></thead>
        <tbody>{expenses.map(e => (
          <tr key={e.id}>
            <td><ImageThumb name={e.photo} size={40} /></td>
            <td>{EXPENSE_CATEGORY[e.category]}: {e.label}<div className={s.sub}>{e.kind ? INCIDENT_KIND[e.kind].label : 'Chứng từ lưu trữ'}</div></td>
            <td className="nowrap">{formatVND(e.amount)}</td>
            <td>Công ty chịu</td>
          </tr>
        ))}</tbody>
      </table></div>
      <h4 style={{ margin: '16px 0 8px' }}><i className="fa-solid fa-road" /> Phí cầu đường (nhà xe chịu)</h4>
      {tolls.length
        ? <div className="table-wrap"><table className="data-table">
            <thead><tr><th>Hóa đơn</th><th>Trạm thu phí</th><th>Số tiền</th></tr></thead>
            <tbody>{tolls.map(x => <tr key={x.id}><td><ImageThumb name={x.photo} size={40} /></td><td>{x.station || 'Trạm thu phí'}<div className={s.sub}>{formatDateTime(x.at)} · {x.by}</div></td><td className="nowrap">{formatVND(x.amount)}</td></tr>)}</tbody>
          </table></div>
        : <p className={s.sub}>Tài xế chưa ghi hóa đơn phí cầu đường nào.</p>}
      <p style={{ marginTop: 14 }}>Chi phí sự cố công ty chịu: <b>{formatVND(carrierTotal)}</b>{tolls.length ? <> · Phí cầu đường: <b>{formatVND(tollTotal)}</b></> : null}</p>
      <ReadMore className={s.hint} text="Toàn bộ chi phí sự cố, lưu chuồng và chờ bàn giao do công ty chịu. Duyệt chi phí sẽ hoàn tất đơn; xe và kíp xe cần xác nhận về trụ sở trước khi nhận chuyến mới." />
    </Modal>
  )
}
