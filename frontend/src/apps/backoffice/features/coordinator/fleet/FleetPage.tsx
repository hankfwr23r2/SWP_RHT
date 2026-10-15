// Quản lý đội xe. Chuyển từ Fleet And Route/OPS-07.html + ops-07.js.
// Thêm / sửa / xóa xe. Không hiện đơn đang giữ xe: trạng thái sẵn sàng / có đơn chỉ hiện lúc chọn xe cho một đơn (theo ngày đi của đơn đó).
// Ô số liệu tính từ danh sách xe (bản cũ ghi cứng 24 / 18 / 4 / 2).
import { useState } from 'react'
import { vehiclesApi, type Vehicle } from '@shared/services/fleet'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { cx } from '../../../shared/parts'
import { VehicleArt } from './VehicleArt'
import f from './Fleet.module.css'
import c from '../Coordinator.module.css'
import { useOps } from '../../../shared/useOps'

const VEHICLE_NAME = 'Xe chuyên dụng' // mọi xe đều là xe chuyên dụng chở ngựa
const EMPTY: Omit<Vehicle, 'id'> = { name: VEHICLE_NAME, type: VEHICLE_NAME, capacity: 2, plate: '', inspectionNo: '', transitPermit: '' }

export default function FleetPage() {
  const toast = useToast()
  const { vehicles, reload } = useOps()
  const [editing, setEditing] = useState<string | null>(null) // null = thêm mới
  const [formOpen, setFormOpen] = useState(false)
  const [text, setText] = useState('')
  const [form, setForm] = useState(EMPTY)
  const [invalid, setInvalid] = useState('')
  const [confirmDelete, setConfirmDelete] = useState<Vehicle | null>(null)

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
  const shown = vehicles.filter(v => !keyword || [v.id, v.plate].some(x => x.toLowerCase().includes(keyword)))

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header">
          <h1>Quản lý đội xe</h1>
          <p>Danh sách xe chuyên dụng. Bấm vào xe để xem và sửa.</p>
        </div>

        <div className={f.bar}>
          <label className={f.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder="Tìm biển số, mã xe" value={text} onChange={e => setText(e.target.value)} /></label>
          <button className="btn btn-primary" onClick={() => edit(null)}><i className="fa-solid fa-plus" /> Thêm phương tiện</button>
        </div>

        <div className={f.grid}>
          {shown.map(v => {
            const color = '#059669'
            return (
              <article key={v.id} className={f.card} style={{ ['--tone' as string]: color }} onClick={() => edit(v)} tabIndex={0} onKeyDown={e => e.key === 'Enter' && edit(v)} aria-label={`Xe ${v.plate}`}>
                <div className={f.top}><span className={f.code}>{v.id}</span></div>
                <div className={f.art}><VehicleArt stalls={v.capacity} color={color} /></div>
                <span className={f.plate}>{v.plate}</span>
                <div className={f.name}>{VEHICLE_NAME}<small>{v.capacity} ngăn</small></div>
                {!v.inspectionNo && <span className={f.warn}><i className="fa-solid fa-triangle-exclamation" />Thiếu số đăng kiểm, chưa gán được vào đơn</span>}
                <div className={f.actions} onClick={e => e.stopPropagation()}>
                  <button className="btn btn-ghost btn-sm" onClick={() => edit(v)}>Sửa</button>
                  <button className="btn btn-ghost btn-sm text-red" onClick={() => setConfirmDelete(v)}>Xóa</button>
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
          <p>Xe {confirmDelete.plate} sẽ bị xóa khỏi danh sách. Máy chủ từ chối nếu xe đang được giữ cho đơn chưa kết thúc.</p>
        </Modal>
      )}
    </div>
  )
}
