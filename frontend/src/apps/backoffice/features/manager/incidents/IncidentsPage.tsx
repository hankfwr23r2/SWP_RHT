import { useAutoRefresh } from '@shared/services/useLoad'
// Manager: duyệt phương án sự cố và theo dõi sự cố đang xử lý (Flow 5, PRD mục 6.6).
import { useCallback, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { INCIDENT_ACTION, INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { bookingsApi, invalidateBookingsListCache } from '@shared/services/bookings'
import type { Booking, Incident } from '@shared/types/booking'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Modal } from '@shared/ui/Modal'
import { ReadMore } from '@shared/ui/ReadMore'
import { useToast } from '@shared/ui/toast'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import { IncidentPlanView } from '../../../shared/IncidentPlanView'
import s from '../../../shared/booking.module.css'
import { usePageData } from '@shared/routing/pageData'
import type { bookingListLoader } from '../../../shared/loaders'

type Tab = 'pickup' | 'approve' | 'active' | 'returned' | 'done'
type Item = { b: Booking; i: Incident }
export { type Item as IncidentItem }

export function ApproveModal({ item, onClose, onDone }: { item: Item; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const { b, i } = item
  const [directive, setDirective] = useState('')
  const [costBearer, setCostBearer] = useState<'COMPANY' | 'CUSTOMER'>('COMPANY')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const act = async (fn: () => Promise<unknown>, msg: string) => {
    setBusy(true)
    try { await fn(); toast(msg); onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error'); setBusy(false) }
  }
  return (
    <Modal wide onClose={onClose} title={`Duyệt phương án ${i.id}`} subtitle={`${b.id} · ${b.customer} · ${INCIDENT_KIND[i.kind].label} · xe ${i.tripId}`}
      footer={<>
        <button className="btn btn-ghost" disabled={busy || !reason.trim()} onClick={() => act(() => bookingsApi.rejectIncident(b.id, i.id, session!.name, reason), 'Đã trả phương án về Điều phối viên')}><i className="fa-solid fa-rotate-left" /> Trả về</button>
        <button className="btn btn-primary" disabled={busy} onClick={() => act(() => bookingsApi.approveIncident(b.id, i.id, session!.name, { directive, costBearer }), 'Đã duyệt phương án khẩn cấp')}><i className="fa-solid fa-check" /> Duyệt phương án</button>
      </>}>
      <p><b>Báo từ hiện trường ({formatDateTime(i.reportedAt)}):</b> {i.note || 'Không có ghi chú.'} <ImageThumb name={i.photo} size={40} /></p>
      {i.plan && <div style={{ margin: '10px 0' }}><IncidentPlanView b={b} i={i} /></div>}
      {i.plan && <div className="alert alert-info"><i className="fa-solid fa-route" /><div><b>{INCIDENT_ACTION[i.plan.action]}.</b> ETA mới {formatDateTime(i.plan.newEta)}. {i.plan.note ? ` Ghi chú: ${i.plan.note}` : ''}</div></div>}
      <div className="form-group">
        <label htmlFor="dir">Ý kiến chỉ đạo khi duyệt (tùy chọn - gửi trực tiếp đến kíp xe &amp; ĐPV)</label>
        <input id="dir" className="form-control" placeholder="Ví dụ: Đồng ý cứu hộ, yêu cầu đưa ngựa về trạm nghỉ gần nhất..." value={directive} onChange={e => setDirective(e.target.value)} />
      </div>
      <div className="form-group">
        <label htmlFor="cb">Bên chịu chi phí</label>
        <select id="cb" className="form-control" value={costBearer} onChange={e => setCostBearer(e.target.value as 'COMPANY' | 'CUSTOMER')}>
          <option value="COMPANY">Nhà xe bảo lãnh / chịu chi phí</option>
          <option value="CUSTOMER">Khách hàng chịu chi phí (phát sinh do ngựa)</option>
        </select>
      </div>
      <div className="form-group">
        <label htmlFor="rs">Lý do trả về (chỉ cần khi bấm nút Trả về)</label>
        <input id="rs" className="form-control" placeholder="Nhập lý do nếu cần ĐPV lập lại phương án..." value={reason} onChange={e => setReason(e.target.value)} />
      </div>
      <ReadMore className={s.hint} text={'Chỉ Quản lý được duyệt thay đổi phương án di chuyển và trực tiếp làm việc với khách khi có sự cố. Sau khi duyệt, phương án được cập nhật trên màn hình của kíp xe; khách xem được trạng thái xử lý và giờ đến dự kiến mới.'} />
    </Modal>
  )
}

type Row = { key: string; b: Booking; i?: Incident }

// Ngựa bị loại ở điểm đón kèm lý do (chip không khớp hồ sơ, hoặc ốm/sốt)
const excludedHorses = (b: Booking) => b.horses.filter(h => h.status === 'chip_mismatch' || h.status === 'unfit')

// Đơn "Không nhận được ngựa": quản lý xem lý do rồi hủy đơn, khách mất phần cọc vận chuyển
function PickupFailedModal({ b, onClose, onDone }: { b: Booking; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const [reason, setReason] = useState('Ngựa ở điểm đón không khớp hồ sơ')
  const [busy, setBusy] = useState(false)
  const cancel = async () => {
    setBusy(true)
    try { toast(await bookingsApi.cancelPickupFailed(b.id, reason)); onDone() } catch (e) { toast(e instanceof Error ? e.message : 'Không hủy được đơn', 'error'); setBusy(false) }
  }
  return (
    <Modal wide onClose={onClose} title={`Không nhận được ngựa · ${b.id}`} subtitle={`${b.customer} · ${placeShort(b.origin.name)} → ${placeShort(b.dest.name)}`}
      footer={<>
        <button className="btn btn-ghost" onClick={onClose}>Để sau</button>
        <button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={cancel}><i className="fa-solid fa-ban" /> Hủy đơn, khách mất cọc</button>
      </>}>
      <p style={{ marginTop: 0 }}>Kíp xe đã tới điểm đón nhưng không nhận được con ngựa nào:</p>
      <ul>
        {excludedHorses(b).map(h => (
          <li key={h.horseId}>
            <b>{h.name}</b>: {h.unfitInfo?.reason ?? 'Không đủ điều kiện'}
            {h.unfitInfo?.chipRead && <> · hồ sơ <b>{h.microchip}</b>, đọc được <b>{h.unfitInfo.chipRead}</b></>}
            {h.unfitInfo?.note && <div className="sub-text">Ghi chú: {h.unfitInfo.note}</div>}
            {h.unfitInfo?.by && <div className="sub-text">Báo bởi {h.unfitInfo.by}{h.unfitInfo.at ? ` lúc ${formatDateTime(h.unfitInfo.at)}` : ''}</div>}
          </li>
        ))}
      </ul>
      <div className="alert alert-warning"><i className="fa-solid fa-circle-info" /><div>Khách chưa thanh toán 70% còn lại nên không có khoản nào phải hoàn. Hủy đơn thì khách mất phần cọc vận chuyển, phần bảo hiểm thu hộ (nếu có) chờ đối soát riêng. Xe, tài xế và hộ tống được nhả cho đơn khác.</div></div>
      <div className="form-group" style={{ margin: 0 }}>
        <label htmlFor="pf-reason" className="required">Lý do hủy (khách xem được)</label>
        <input id="pf-reason" className="form-control" value={reason} onChange={e => setReason(e.target.value)} />
      </div>
    </Modal>
  )
}

export default function IncidentsPage() {
  const { t } = useTranslation()
  const { all, reload } = usePageData<typeof bookingListLoader>()
  const refresh = useCallback(() => { invalidateBookingsListCache(); reload() }, [reload])
  useAutoRefresh(refresh)
  const [params, setParams] = useSearchParams()
  const list = all ?? []
  const pickupFailed = list.filter(b => b.status === 'pickup_failed')
  const [tab, setTab] = useState<Tab>(() => (params.get('pickup') ? 'pickup' : 'approve'))
  const [approve, setApprove] = useState<Item | null>(null)
  // Bấm "Xử lý BK-…" trên thanh đỏ thì mở thẳng hộp xử lý của đơn đó
  const opened = pickupFailed.find(b => b.id === params.get('pickup'))
  const closePickup = () => setParams(p => { p.delete('pickup'); return p }, { replace: true })
  const incidents = list.flatMap(b => (b.incidents ?? []).map(i => ({ b, i })))
  const inc = (xs: Item[]): Row[] => xs.map(x => ({ key: x.i.id, ...x }))
  const ord = (xs: Booking[]): Row[] => xs.map(b => ({ key: b.id, b }))
  const groups: Record<Tab, Row[]> = {
    pickup: ord(pickupFailed),
    approve: inc(incidents.filter(x => x.i.status === 'pending_approval')),
    active: inc(incidents.filter(x => x.i.status === 'active')),
    returned: inc(incidents.filter(x => x.i.status === 'reported')),
    done: ord(list.filter(b => b.status === 'completed')),
  }
  const done = () => { setApprove(null); closePickup(); refresh() }
  const expenses = (b: Booking) => (b.incidents ?? []).flatMap(i => i.expenses).reduce((n, e) => n + e.amount, 0)

  const columns: Column<Row>[] = [
    { head: t('intakePage.colOrderId', 'Mã đơn'), cell: r => idCell(r.b) },
    { head: t('intakePage.colCustomer', 'Khách hàng'), cell: r => r.b.customer, nowrap: true },
    { head: t('intakePage.colRoute', 'Tuyến'), cell: r => routeCell(placeShort(r.b.origin.name), placeShort(r.b.dest.name)) },
    { head: t('incidentsPage.colDetails', 'Nội dung'), minWidth: 220, cell: r => (tab === 'pickup' ? <><b>Không nhận được ngựa</b><div className="sub-text">{excludedHorses(r.b).map(h => `${h.name}: ${h.unfitInfo?.reason ?? 'bị loại'}`).join('; ')}</div></> : r.i ? <><b>{INCIDENT_KIND[r.i.kind].label}</b> · xe {r.i.tripId}<div className="sub-text">{r.i.plan ? `${INCIDENT_ACTION[r.i.plan.action]}, ETA mới ${formatDateTime(r.i.plan.newEta)}` : r.i.rejection ? <span style={{ color: 'var(--red)' }}>Đã trả về: {r.i.rejection.reason}</span> : r.i.note || 'Chưa có ghi chú'}</div></> : <>Chi phí sự cố <b>{formatVND(expenses(r.b))}</b></>) },
    { head: t('incidentsPage.colTime', 'Thời gian'), cell: r => (r.i ? `Báo ${formatDateTime(r.i.reportedAt)}` : r.b.settlement ? `${r.b.settlement.paid ? 'Đã trả' : 'Hạn trả'} ${formatDateTime(r.b.settlement.paid?.paidAt ?? r.b.settlement.dueAt)}` : '-'), minWidth: 110 },
    { head: t('intakePage.colStatus', 'Trạng thái'), cell: r => statusCell(r.b.status) },
    { head: t('incidentsPage.colValue', 'Giá trị'), cell: r => (r.i ? '-' : r.b.settlement ? formatVND(r.b.settlement.total) : '-'), right: true },
    { head: t('intakePage.colAction', 'Thao tác'), cell: r => (tab === 'pickup' ? <button className="btn btn-danger btn-sm" onClick={() => setParams(p => { p.set('pickup', r.b.id); return p })}>Xem và hủy đơn</button> : tab === 'approve' && r.i ? <button className="btn btn-primary btn-sm" onClick={() => setApprove({ b: r.b, i: r.i! })}>{t('incidentsPage.viewAndApprove', 'Xem và duyệt')}</button> : null), right: true },
  ]
  return (
    <>
      <ListPage title={t('staffMenus./manager/incidents', 'Sự cố')} subtitle={t('incidentsPage.subtitle', 'Duyệt phương án sự cố và theo dõi sự cố đang xử lý.')} tab={tab} onTab={setTab} hot={['pickup', 'approve']}
        tabs={[['pickup', 'Không nhận được ngựa', groups.pickup.length], ['approve', t('incidentsPage.tabApprove', 'Chờ duyệt phương án'), groups.approve.length], ['active', t('incidentsPage.tabActive', 'Sự cố đang xử lý'), groups.active.length], ['returned', t('incidentsPage.tabReturned', 'Chờ ĐPV lập lại'), groups.returned.length], ['done', t('incidentsPage.tabDone', 'Đã hoàn tất'), groups.done.length]]}
        rows={groups[tab]} rowKey={r => r.key} columns={columns} haystack={r => [r.b.id, r.b.customer, r.b.origin.name, r.b.dest.name]} dateOf={r => r.b.departAt} loaded={!!all}
        emptyText={t('incidentsPage.empty', 'Không có đơn nào ở mục này.')} hotRow={r => tab === 'pickup' || tab === 'approve' || r.b.status === 'payment_overdue'} />
      {approve && <ApproveModal item={approve} onClose={() => setApprove(null)} onDone={done} />}
      {opened && <PickupFailedModal b={opened} onClose={closePickup} onDone={done} />}
    </>
  )
}
