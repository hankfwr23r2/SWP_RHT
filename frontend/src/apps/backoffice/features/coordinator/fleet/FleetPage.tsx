// Quản lý đội xe. Chuyển từ Fleet And Route/OPS-07.html + ops-07.js.
// Thêm / sửa / xóa xe; mỗi xe một tài xế (chọn xe là gán tài xế theo). Trạng thái xe chỉ là có đơn hay chưa có đơn, suy từ các đơn.
// Ô số liệu tính từ danh sách xe (bản cũ ghi cứng 24 / 18 / 4 / 2).
import { useState } from 'react'
import { HOLDING } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { vehiclesApi, type Vehicle } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { cx } from '../../../shared/parts'
import { VehicleArt } from './VehicleArt'
import f from './Fleet.module.css'
import c from '../Coordinator.module.css'
import { useOps } from '../../../shared/useOps'

// Trạng thái xe chỉ có hai: đã có đơn (đang giữ chỗ cho một đơn chưa kết thúc) hoặc chưa có đơn. Không quản lý bảo dưỡng.
const HAS_ORDER: [label: string, color: string] = ['Có đơn', '#ea580c']
const NO_ORDER: [label: string, color: string] = ['Chưa có đơn', '#059669']
type Filter = 'all' | 'busy' | 'free'
const VEHICLE_NAME = 'Xe chuyên dụng' // mọi xe đều là xe chuyên dụng chở ngựa
const EMPTY: Omit<Vehicle, 'id'> = { name: VEHICLE_NAME, type: VEHICLE_NAME, capacity: 2, plate: '', inspectionNo: '', transitPermit: '' }

export default function FleetPage() {
  const toast = useToast()
  const { trips, vehicles, name, reload } = useOps()
  const { data: orders } = useLoad(bookingsApi.list)
  const [editing, setEditing] = useState<string | null>(null) // null = thêm mới
  const [formOpen, setFormOpen] = useState(false)
  const [filter, setFilter] = useState<Filter>('all')
  const [text, setText] = useState('')
  const [form, setForm] = useState(EMPTY)
  const [invalid, setInvalid] = useState('')
  const [confirmDelete, setConfirmDelete] = useState<Vehicle | null>(null)
  // Xe này đang chở những ngựa nào (đơn mới, một xe có thể chở một phần số ngựa của đơn)
  const carrying = (id: string) => (orders ?? []).filter(o => HOLDING.includes(o.status)).flatMap(o => (o.trips ?? []).filter(t => t.vehicleId === id).map(t => ({ order: o.id, trip: t.tripId, driver: t.driverId, departAt: o.departAt, horses: o.horses.filter(h => t.horseIds.includes(h.horseId)).map(h => h.name) })))
  const usedBy = (id: string) => trips.filter(t => t.status !== 'done' && t.legs.some(l => l.vehicleId === id)).map(t => t.id)

  const edit = (v: Vehicle | null) => { setEditing(v?.id ?? null); setForm(v ? { ...v } : EMPTY); setInvalid(''); setFormOpen(true) }
  const closeForm = () => { setFormOpen(false); setEditing(null); setInvalid('') }
  const save = async () => {
    if (!form.plate.trim()) return setInvalid('plate')
    const data = { ...form, name: VEHICLE_NAME, type: VEHICLE_NAME, plate: form.plate.trim(), capacity: Math.max(1, form.capacity || 2) }
    if (editing) await vehiclesApi.update(editing, data)
    else await vehiclesApi.create(data)
    toast(editing ? `Đã lưu xe ${editing}` : `Đã thêm xe ${data.plate}`)
    closeForm()
    reload()
  }
  const remove = async (v: Vehicle) => {
    await vehiclesApi.remove(v.id)
    setConfirmDelete(null)
    if (editing === v.id) closeForm()
    toast(`Đã xóa xe ${v.id}`, 'info')
    reload()
  }
  const set = <K extends keyof typeof form>(key: K) => (value: (typeof form)[K]) => { setInvalid(''); setForm({ ...form, [key]: value }) }

  const keyword = text.trim().toLowerCase()
  const hasOrder = (id: string) => carrying(id).length > 0
  const shown = vehicles.filter(v => (filter === 'all' || (filter === 'busy') === hasOrder(v.id)) && (!keyword || [v.id, v.plate, ...carrying(v.id).flatMap(x => [x.order, name(x.driver)])].some(x => x.toLowerCase().includes(keyword))))
  const count = (k: Filter) => (k === 'all' ? vehicles.length : vehicles.filter(v => (k === 'busy') === hasOrder(v.id)).length)
  const FILTERS: [Filter, string, string][] = [['all', 'Tất cả', '#64748b'], ['free', NO_ORDER[0], NO_ORDER[1]], ['busy', HAS_ORDER[0], HAS_ORDER[1]]]

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header">
          <h1>Quản lý đội xe</h1>
          <p>Theo dõi từng xe, tài xế và đơn xe đang nhận. Bấm vào xe để xem và sửa.</p>
        </div>

        <div className={f.bar}>
          <div className={f.chips} role="tablist" aria-label="Trạng thái xe">
            {FILTERS.map(([k, label, color]) => (
              <button key={k} role="tab" aria-selected={filter === k} className={cx(f.chip, filter === k && f.chipOn)} onClick={() => setFilter(k)}>
                <span className={f.dot} style={{ background: color }} />{label}<span className={f.num}>{count(k)}</span>
              </button>
            ))}
          </div>
          <label className={f.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder="Tìm biển số, mã đơn, tài xế" value={text} onChange={e => setText(e.target.value)} /></label>
          <button className="btn btn-primary" onClick={() => edit(null)}><i className="fa-solid fa-plus" /> Thêm phương tiện</button>
        </div>

        <div className={f.grid}>
          {shown.map(v => {
            const load = carrying(v.id)
            const [label, color] = load.length ? HAS_ORDER : NO_ORDER
            return (
              <article key={v.id} className={f.card} style={{ ['--tone' as string]: color }} onClick={() => edit(v)} tabIndex={0} onKeyDown={e => e.key === 'Enter' && edit(v)} aria-label={`Xe ${v.plate}`}>
                <div className={f.top}><span className={f.code}>{v.id}</span><span className={f.status}><span className={f.dot} style={{ background: color }} />{label}</span></div>
                <div className={f.art}><VehicleArt stalls={v.capacity} color={color} /></div>
                <span className={f.plate}>{v.plate}</span>
                <div className={f.name}>{VEHICLE_NAME}<small>{v.capacity} ngăn</small></div>
                <div className={f.orders}>
                  <span className={f.ordersHead}>Đơn đang nhận</span>
                  {load.length ? load.map(x => (
                    <div key={x.trip} className={f.order} title={x.horses.join(', ')}><b>{x.order}</b><small>khởi hành {formatDate(x.departAt)}{x.horses.length ? ` · ${x.horses.length} ngựa` : ''}</small><small><i className="fa-solid fa-id-card" aria-hidden="true" /> Tài xế: {name(x.driver)}</small></div>
                  )) : <span className={f.noOrder}>Chưa có đơn</span>}
                </div>
                {!v.inspectionNo && <span className={f.warn}><i className="fa-solid fa-triangle-exclamation" />Thiếu số đăng kiểm, chưa gán được vào đơn</span>}
                <div className={f.actions} onClick={e => e.stopPropagation()}>
                  <button className="btn btn-ghost btn-sm" onClick={() => edit(v)}>Sửa</button>
                  <button className="btn btn-ghost btn-sm text-red" onClick={() => (usedBy(v.id).length ? setConfirmDelete(v) : remove(v))}>Xóa</button>
                </div>
              </article>
            )
          })}
          <button className={f.add} onClick={() => edit(null)}><i className="fa-solid fa-plus" />Thêm phương tiện</button>
          {!shown.length && <div className={f.empty}><i className="fa-solid fa-truck" />Không có xe nào khớp bộ lọc.</div>}
        </div>
      </div>

      {formOpen && (
        <Modal wide onClose={closeForm} title={editing ? `Xe chuyên dụng ${editing}` : 'Thêm xe chuyên dụng mới'} subtitle={editing ? form.plate : undefined}
          footer={<><button className="btn btn-ghost" onClick={closeForm}>Hủy</button><button className="btn btn-primary" onClick={save}><i className="fa-solid fa-floppy-disk" /> Lưu thông tin</button></>}>
          <div className={c.formRow}>
            <div className="form-group"><label>Sức chứa (ngăn)</label><input type="number" min={1} className="form-control" value={form.capacity} onChange={e => set('capacity')(parseInt(e.target.value, 10))} /></div>
            <div className="form-group"><label className="required">Biển số</label><input className={cx('form-control', invalid === 'plate' && 'invalid')} value={form.plate} onChange={e => set('plate')(e.target.value)} /></div>
            <div className="form-group"><label>Số giấy đăng kiểm</label><input className="form-control" value={form.inspectionNo ?? ''} onChange={e => set('inspectionNo')(e.target.value)} /><div className="form-hint">Thiếu thì hệ thống không gán xe cho đơn.</div></div>
            <div className="form-group"><label>Giấy phép liên vận</label><input className="form-control" value={form.transitPermit ?? ''} onChange={e => set('transitPermit')(e.target.value)} /><div className="form-hint">Cần cho tuyến quốc tế.</div></div>
          </div>
        </Modal>
      )}

      {confirmDelete && (
        <Modal title={`Xóa xe ${confirmDelete.id}?`} onClose={() => setConfirmDelete(null)} footer={<><button className="btn btn-ghost" onClick={() => setConfirmDelete(null)}>Hủy</button><button className="btn btn-danger" onClick={() => remove(confirmDelete)}>Vẫn xóa</button></>}>
          <p>Xe {confirmDelete.plate} đang gán cho chuyến chưa xong: <b>{usedBy(confirmDelete.id).join(', ')}</b>. Các chặng đó sẽ không còn xe.</p>
        </Modal>
      )}
    </div>
  )
}
