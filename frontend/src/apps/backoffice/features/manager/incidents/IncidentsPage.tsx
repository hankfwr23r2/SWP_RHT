// Manager: duyệt phương án sự cố và theo dõi sự cố đang xử lý (Flow 5, PRD mục 6.6). Đối soát chi phí nằm ở trang Tiến độ đơn.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { INCIDENT_ACTION, INCIDENT_KIND } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { useLoad } from '@shared/services/useLoad'
import type { Booking, Incident } from '@shared/types/booking'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Modal } from '@shared/ui/Modal'
import { ReadMore } from '@shared/ui/ReadMore'
import { useToast } from '@shared/ui/toast'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import { IncidentPlanView } from '../../../shared/IncidentPlanView'
import s from '../../../shared/booking.module.css'

type Tab = 'approve' | 'active' | 'done'
type Item = { b: Booking; i: Incident }

function ApproveModal({ item, onClose, onDone }: { item: Item; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const { b, i } = item
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
        <button className="btn btn-primary" disabled={busy} onClick={() => act(() => bookingsApi.approveIncident(b.id, i.id, session!.name), 'Đã duyệt phương án khẩn cấp')}><i className="fa-solid fa-check" /> Duyệt phương án</button>
      </>}>
      <p><b>Báo từ hiện trường ({formatDateTime(i.reportedAt)}):</b> {i.note || 'Không có ghi chú.'} <ImageThumb name={i.photo} size={40} /></p>
      {i.plan && <div style={{ margin: '10px 0' }}><IncidentPlanView b={b} i={i} /></div>}
      {i.plan && <div className="alert alert-info"><i className="fa-solid fa-route" /><div><b>{INCIDENT_ACTION[i.plan.action]}.</b> ETA mới {formatDateTime(i.plan.newEta)}. {i.plan.note ? ` Ghi chú: ${i.plan.note}` : ''}</div></div>}
      <div className="form-group"><label htmlFor="rs">Lý do trả về (chỉ cần khi trả về)</label><input id="rs" className="form-control" value={reason} onChange={e => setReason(e.target.value)} /></div>
      <ReadMore className={s.hint} text={'Chỉ Quản lý được duyệt thay đổi phương án di chuyển và trực tiếp làm việc với khách khi có sự cố. Duyệt xong, khách và tài xế, hộ tống nhận thông báo.'} />
    </Modal>
  )
}

type Row = { key: string; b: Booking; i?: Incident }

export default function IncidentsPage() {
  const { data: all, reload } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('approve')
  const [approve, setApprove] = useState<Item | null>(null)
  const list = all ?? []
  const incidents = list.flatMap(b => (b.incidents ?? []).map(i => ({ b, i })))
  const inc = (xs: Item[]): Row[] => xs.map(x => ({ key: x.i.id, ...x }))
  const ord = (xs: Booking[]): Row[] => xs.map(b => ({ key: b.id, b }))
  const groups: Record<Tab, Row[]> = {
    approve: inc(incidents.filter(x => x.i.status === 'pending_approval')),
    active: inc(incidents.filter(x => x.i.status === 'active' || x.i.status === 'reported')),
    done: ord(list.filter(b => b.status === 'completed')),
  }
  const done = () => { setApprove(null); reload() }
  const expenses = (b: Booking) => (b.incidents ?? []).flatMap(i => i.expenses).reduce((n, e) => n + e.amount, 0)

  const columns: Column<Row>[] = [
    { head: 'Mã đơn', cell: r => idCell(r.b) },
    { head: 'Khách hàng', cell: r => r.b.customer, nowrap: true },
    { head: 'Tuyến', cell: r => routeCell(placeShort(r.b.origin.name), placeShort(r.b.dest.name)) },
    { head: 'Nội dung', minWidth: 220, cell: r => (r.i ? <><b>{INCIDENT_KIND[r.i.kind].label}</b> · xe {r.i.tripId}<div className="sub-text">{r.i.plan ? `${INCIDENT_ACTION[r.i.plan.action]}, ETA mới ${formatDateTime(r.i.plan.newEta)}` : r.i.note || 'Chưa có ghi chú'}</div></> : <>Chi phí sự cố <b>{formatVND(expenses(r.b))}</b></>) },
    { head: 'Thời gian', cell: r => (r.i ? `Báo ${formatDateTime(r.i.reportedAt)}` : r.b.settlement ? `${r.b.settlement.paid ? 'Đã trả' : 'Hạn trả'} ${formatDateTime(r.b.settlement.paid?.paidAt ?? r.b.settlement.dueAt)}` : '-'), minWidth: 110 },
    { head: 'Trạng thái', cell: r => statusCell(r.b.status) },
    { head: 'Giá trị', cell: r => (r.i ? '-' : r.b.settlement ? formatVND(r.b.settlement.total) : '-'), right: true },
    { head: 'Thao tác', cell: r => (tab === 'approve' && r.i ? <button className="btn btn-primary btn-sm" onClick={() => setApprove({ b: r.b, i: r.i! })}>Xem và duyệt</button> : null), right: true },
  ]
  return (
    <>
      <ListPage title="Sự cố" subtitle="Duyệt phương án sự cố và theo dõi sự cố đang xử lý." tab={tab} onTab={setTab} hot={['approve']}
        tabs={[['approve', 'Chờ duyệt phương án', groups.approve.length], ['active', 'Sự cố đang xử lý', groups.active.length], ['done', 'Đã hoàn tất', groups.done.length]]}
        rows={groups[tab]} rowKey={r => r.key} columns={columns} haystack={r => [r.b.id, r.b.customer, r.b.origin.name, r.b.dest.name]} dateOf={r => r.b.departAt} loaded={!!all}
        emptyText="Không có đơn nào ở mục này." hotRow={r => tab === 'approve' || r.b.status === 'payment_overdue'} />
      {approve && <ApproveModal item={approve} onClose={() => setApprove(null)} onDone={done} />}
    </>
  )
}
