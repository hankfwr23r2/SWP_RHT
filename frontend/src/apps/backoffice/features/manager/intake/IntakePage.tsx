// Manager: tiếp nhận đơn mới và kích hoạt thẩm định song song (PRD mục 2.3).
// Manager chọn Kiểm dịch viên và Điều phối viên đang hoạt động để giao việc (không xem số đơn họ đang làm).
import { ReadMore } from '@shared/ui/ReadMore'
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { activeStaff } from '@shared/lib/booking'
import { bookingsApi } from '@shared/services/bookings'
import { staffApi } from '@shared/services/staff'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { HorseConfigList, ReviewChips, TripSummary } from '../../../shared/BookingParts'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import s from '../../../shared/booking.module.css'
import { StaffPicker } from '../../../shared/StaffPicker'

type Tab = 'new' | 'running'

// Nút chọn người giao việc: bấm mở popup thẻ
function PickButton({ person, icon, onClick }: { person?: { name: string }; icon: string; onClick: () => void }) {
  return (
    <button type="button" className="btn btn-ghost" style={{ width: '100%', justifyContent: 'flex-start', gap: 10, minHeight: 46, textAlign: 'left' }} onClick={onClick}>
      <i className={`fa-solid ${icon}`} aria-hidden="true" style={{ color: '#ea580c' }} />
      <span style={{ flex: 1 }}>{person ? <b>{person.name}</b> : 'Chọn người'}</span>
      <small style={{ color: '#ea580c', fontWeight: 600 }}>{person ? 'Đổi' : 'Chọn'}</small>
    </button>
  )
}

function IntakeModal({ b, onClose, onDone }: { b: Booking; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const { data: staff } = useLoad(staffApi.list)
  const specialists = staff ? activeStaff(staff, 'specialist') : []
  const coordinators = staff ? activeStaff(staff, 'coordinator') : []
  const [sp, setSp] = useState('')
  const [co, setCo] = useState('')
  const [busy, setBusy] = useState(false)
  const [reason, setReason] = useState<string | null>(null) // không null = đang nhập lý do từ chối
  const spId = sp, coId = co // Manager tự chọn, không có người mặc định

  const activate = async () => {
    const a = specialists.find(x => x.id === spId)
    const c = coordinators.find(x => x.id === coId)
    if (!a || !c) return
    setBusy(true)
    try {
      await bookingsApi.activate(b.id, session!.name, { id: a.id, name: a.name }, { id: c.id, name: c.name })
      toast(`Đã tiếp nhận ${b.id}, đã giao Kiểm dịch viên và Điều phối viên`)
      onDone()
    } catch (e) { toast(e instanceof Error ? e.message : 'Không tiếp nhận được', 'error'); setBusy(false) }
  }

  const reject = async () => {
    setBusy(true)
    try {
      await bookingsApi.rejectOrder(b.id, session!.name, 'manager', reason ?? '')
      toast(`Đã từ chối ${b.id}, khách nhận được lý do`)
      onDone()
    } catch (e) { toast(e instanceof Error ? e.message : 'Không từ chối được', 'error'); setBusy(false) }
  }

  const [pick, setPick] = useState<'specialist' | 'coordinator' | null>(null)

  return (
    <Modal
      wide onClose={onClose} title={`Tiếp nhận ${b.id}`} subtitle={`${b.customer} · gửi lúc ${formatDateTime(b.createdAt)}`}
      footer={reason === null
        ? <><button className="btn btn-ghost" onClick={onClose}>Đóng</button><button className="btn btn-ghost" onClick={() => setReason('')}><i className="fa-solid fa-ban" /> Từ chối đơn</button><button className="btn btn-primary" disabled={busy || !spId || !coId} onClick={activate}><i className="fa-solid fa-play" /> Tiếp nhận và kích hoạt thẩm định</button></>
        : <><button className="btn btn-ghost" onClick={() => setReason(null)}>Quay lại</button><button className="btn btn-danger" disabled={busy || !reason.trim()} onClick={reject}><i className="fa-solid fa-ban" /> Xác nhận từ chối</button></>}
    >
      {reason !== null && (
        <div className="form-group">
          <label htmlFor="rj" className="required">Lý do từ chối (khách sẽ thấy)</label>
          <textarea id="rj" className="form-control" rows={3} value={reason} onChange={e => setReason(e.target.value)} />
        </div>
      )}
      <TripSummary b={b} />
      <h4 style={{ margin: '18px 0 10px' }}>Ngựa và dịch vụ</h4>
      <HorseConfigList b={b} />
      <h4 style={{ margin: '18px 0 10px' }}>Giao việc</h4>
      <div className={s.form2}>
        <div className="form-group">
          <label>Kiểm dịch viên (duyệt hồ sơ ngựa)</label>
          <PickButton icon="fa-user-doctor" person={spId ? specialists.find(x => x.id === spId) : undefined} onClick={() => setPick('specialist')} />
        </div>
        <div className="form-group">
          <label>Điều phối viên (xe và lộ trình)</label>
          <PickButton icon="fa-route" person={coId ? coordinators.find(x => x.id === coId) : undefined} onClick={() => setPick('coordinator')} />
        </div>
      </div>
      <ReadMore className={s.hint} text={'Chọn người đang hoạt động; người đang nghỉ bị khóa. Tiếp nhận xong, Điều phối viên chọn xe (chia ngựa lên nhiều xe nếu cần) và lập lộ trình, rồi bạn chọn tài xế và hộ tống cho từng xe khi duyệt báo giá.'} />
      {pick && staff && <StaffPicker task={pick} staff={staff} selected={(pick === 'specialist' ? spId : coId) ?? ''} onPick={id => (pick === 'specialist' ? setSp(id) : setCo(id))} onClose={() => setPick(null)} />}
    </Modal>
  )
}

export default function IntakePage() {
  const { data: all, reload } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('new')
  const [open, setOpen] = useState<Booking | null>(null)
  const list = all ?? []
  const fresh = list.filter(b => b.status === 'pending_intake')
  const running = list.filter(b => b.status === 'under_review')
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Ngựa', cell: b => `${b.horses.length} con`, nowrap: true },
    tab === 'new' ? { head: 'Gửi lúc', cell: b => formatDateTime(b.createdAt), nowrap: true } : { head: 'Thẩm định', cell: b => <ReviewChips b={b} /> },
    { head: 'Trạng thái', cell: b => statusCell(b.status) },
    { head: 'Thao tác', cell: b => (tab === 'new' ? <button className="btn btn-primary btn-sm" onClick={() => setOpen(b)}>Tiếp nhận</button> : null), right: true },
  ]
  return (
    <>
      <ListPage title="Tiếp nhận đơn hàng" subtitle="Giao Kiểm dịch viên và Điều phối viên thẩm định song song." tabs={[['new', 'Chờ tiếp nhận', fresh.length], ['running', 'Đang thẩm định', running.length]]} tab={tab} onTab={setTab} hot={['new']}
        rows={tab === 'new' ? fresh : running} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all}
        emptyText={tab === 'new' ? 'Không có đơn nào chờ tiếp nhận.' : 'Không có đơn nào đang thẩm định.'} />
      {open && <IntakeModal b={open} onClose={() => setOpen(null)} onDone={() => { setOpen(null); reload() }} />}
    </>
  )
}
