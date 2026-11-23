// Quản lý đội xe. Chuyển từ Fleet And Route/OPS-07.html + ops-07.js.
// Quản trị viên thêm / sửa / xóa xe (manage); điều phối chỉ xem, bấm vào xe để xem lịch giữ xe. Trạng thái do backend tính: Sẵn sàng, Đã nhận đơn (ở trụ sở, có đơn chưa lăn bánh),
// Đang chạy (đang đi đơn hoặc chưa xác nhận về trụ sở), Chưa sẵn sàng (bảo dưỡng). Xe đã nhận đơn vẫn chọn được
// cho đơn ở ngày khác: việc đó chỉ quyết định lúc chọn xe cho một đơn (theo ngày đi của đơn đó).
// Ô số liệu tính từ danh sách xe (bản cũ ghi cứng 24 / 18 / 4 / 2).
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'
import { formatDate, formatDateTime } from '@shared/lib/format'
import { vehiclesApi, type Vehicle } from '@shared/services/fleet'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { cx } from '@backoffice/shared/parts'
import { VehicleArt } from './VehicleArt'
import { VehicleScheduleModal } from './VehicleScheduleModal'
import f from './Fleet.module.css'
import c from '../Coordinator.module.css'
import { useOps } from '@backoffice/shared/useOps'

const VEHICLE_NAME = 'Xe chuyên dụng' // mọi xe đều là xe chuyên dụng chở ngựa
const VEHICLE_CAPACITIES = [2, 4, 6, 9] as const
const isSupportedCapacity = (capacity: number) => VEHICLE_CAPACITIES.some(option => option === capacity)
const EMPTY: Omit<Vehicle, 'id'> = { name: VEHICLE_NAME, type: VEHICLE_NAME, capacity: 2, plate: '', status: 'AVAILABLE' }

const normalizeStatusKey = (st?: string): string => {
  const s = (st || 'AVAILABLE').toUpperCase().replace(/[\s_-]/g, '')
  if (s === 'BUSY' || s === 'INUSE' || s === 'ONTRIP') return 'BUSY'
  if (s === 'MAINTENANCE' || s === 'UNAVAILABLE') return 'MAINTENANCE'
  if (s === 'AVAILABLE' || s === 'ACTIVE' || s === '') return 'AVAILABLE'
  return s
}

type FleetState = 'AVAILABLE' | 'ASSIGNED' | 'ON_TRIP' | 'MAINTENANCE'
type FleetFilter = 'all' | FleetState

// Trạng thái hiển thị của xe. Bảo dưỡng là trạng thái người dùng đặt; còn lại do backend tính từ các đơn.
const stateOf = (v: Vehicle): FleetState => {
  const physical = normalizeStatusKey(v.status)
  if (physical === 'MAINTENANCE') return 'MAINTENANCE'
  if (v.onTrip || physical === 'BUSY') return 'ON_TRIP'
  if (v.upcoming?.length) return 'ASSIGNED'
  return 'AVAILABLE'
}

const STATE_META: Record<FleetState, { key: string; fallback: string; color: string }> = {
  AVAILABLE: { key: 'coordinatorFleet.statusAvailable', fallback: 'Sẵn sàng', color: '#059669' },
  ASSIGNED: { key: 'coordinatorFleet.statusAssigned', fallback: 'Đã nhận đơn', color: '#2563eb' },
  ON_TRIP: { key: 'coordinatorFleet.statusOnTrip', fallback: 'Đang chạy', color: '#ea580c' },
  MAINTENANCE: { key: 'coordinatorFleet.statusMaintenance', fallback: 'Chưa sẵn sàng', color: '#d97706' },
}
const STATES: FleetState[] = ['AVAILABLE', 'ASSIGNED', 'ON_TRIP', 'MAINTENANCE']
const stateLabel = (state: FleetState, t: TFunction) => t(STATE_META[state].key, STATE_META[state].fallback)

// Backend chặn sửa/xóa khi xe còn gắn với đơn đang giữ xe hoặc chưa về trụ sở: nêu đúng lý do thay cho "đang bận"
const lockReason = (v: Vehicle): string => {
  if (v.onTrip) return `Xe đang đi đơn${v.onTripBookingCode ? ` ${v.onTripBookingCode}` : ''}, sửa hoặc xóa được khi xe đã về trụ sở và không còn đơn`
  if (normalizeStatusKey(v.status) === 'BUSY') return 'Xe đang có chuyến đi, chưa sửa hoặc xóa được'
  const next = v.upcoming?.[0]
  return next ? `Xe đã nhận đơn ${next.bookingCode}, chỉ sửa hoặc xóa được khi xe không còn đơn` : ''
}

export default function FleetPage({ manage = false }: { manage?: boolean }) {
  const { t } = useTranslation()
  const toast = useToast()
  const { vehicles, reload } = useOps()
  const [editing, setEditing] = useState<string | null>(null) // null = thêm mới
  const [formOpen, setFormOpen] = useState(false)
  const [text, setText] = useState('')
  const [filterStatus, setFilterStatus] = useState<FleetFilter>('all')
  const [form, setForm] = useState(EMPTY)
  const [invalid, setInvalid] = useState('')
  const [confirmDelete, setConfirmDelete] = useState<Vehicle | null>(null)
  const [viewing, setViewing] = useState<Vehicle | null>(null)

  const edit = (v: Vehicle | null) => {
    const locked = v ? lockReason(v) : ''
    if (locked) {
      toast(`${locked}.`, 'error')
      return
    }
    setEditing(v?.id ?? null)
    setForm(v ? { ...v, status: v.status || 'AVAILABLE' } : EMPTY)
    setInvalid('')
    setFormOpen(true)
  }
  const closeForm = () => { setFormOpen(false); setEditing(null); setInvalid('') }
  const save = async () => {
    if (!isSupportedCapacity(form.capacity)) return setInvalid('capacity')
    if (!form.plate.trim()) return setInvalid('plate')
    const plate = form.plate.trim()
    const data = {
      ...form,
      name: VEHICLE_NAME,
      type: VEHICLE_NAME,
      plate,
      status: form.status || 'AVAILABLE',
    }
    if (editing) await vehiclesApi.update(editing, data)
    else await vehiclesApi.create(data)
    toast(editing ? `Đã lưu xe ${editing}` : `Đã thêm xe ${data.plate}`)
    closeForm()
    reload()
  }
  const remove = async (v: Vehicle) => {
    const locked = lockReason(v)
    if (locked) {
      toast(`${locked}.`, 'error')
      return
    }
    await vehiclesApi.remove(v.id)
    setConfirmDelete(null)
    if (editing === v.id) closeForm()
    toast(`Đã xóa xe ${v.id}`, 'info')
    reload()
  }
  // Quản trị: mở form sửa (nếu xe không bị khóa). Điều phối: xem lịch giữ xe
  const open = (v: Vehicle) => { if (!manage) setViewing(v); else if (!lockReason(v)) edit(v) }
  const set = <K extends keyof typeof form>(key: K) => (value: (typeof form)[K]) => { setInvalid(''); setForm({ ...form, [key]: value }) }

  const countOf = (state: FleetState) => vehicles.filter(v => stateOf(v) === state).length

  const keyword = text.trim().toLowerCase()
  const shown = vehicles.filter(v => {
    if (filterStatus !== 'all' && stateOf(v) !== filterStatus) return false
    return !keyword || [v.id, v.plate].some(x => x.toLowerCase().includes(keyword))
  })

  return (
    <div className="page">
      <div className="wrap">
        <div className="page-header">
          <h1>{t('coordinatorFleet.title', 'Đội xe vận chuyển')}</h1>
          <p>{manage ? t('coordinatorFleet.subtitle', 'Danh sách xe chuyên dụng vận chuyển ngựa, trạng thái sẵn sàng và bảo dưỡng.') : t('coordinatorFleet.subtitleView', 'Bấm vào xe để xem xe đang đi hoặc đã nhận đơn nào, từ ngày nào tới ngày nào và kíp xe đi cùng.')}</p>
        </div>

        <div className={f.bar}>
          <div className={f.chips}>
            <button type="button" className={`${f.chip} ${filterStatus === 'all' ? f.chipOn : ''}`} onClick={() => setFilterStatus('all')}>
              {t('common.all', 'Tất cả')} <span className={f.num}>{vehicles.length}</span>
            </button>
            {STATES.map(state => {
              const count = countOf(state)
              // "Sẵn sàng" luôn hiện; nhóm khác chỉ hiện khi có xe hoặc đang được chọn (để không mất bộ lọc)
              if (state !== 'AVAILABLE' && !count && filterStatus !== state) return null
              return (
                <button key={state} type="button" className={`${f.chip} ${filterStatus === state ? f.chipOn : ''}`} onClick={() => setFilterStatus(state)}>
                  <span className={f.dot} style={{ background: STATE_META[state].color }} /> {stateLabel(state, t)} <span className={f.num}>{count}</span>
                </button>
              )
            })}
          </div>
          <label className={f.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t('coordinatorFleet.searchPlaceholder', 'Tìm biển số...')} value={text} onChange={e => setText(e.target.value)} /></label>
          {manage && <button className="btn btn-primary" onClick={() => edit(null)}><i className="fa-solid fa-plus" /> {t('coordinatorFleet.btnAddVehicle', 'Thêm xe')}</button>}
        </div>

        <div className={f.grid}>
          {shown.map(v => {
            const state = stateOf(v)
            const color = STATE_META[state].color
            const locked = lockReason(v)
            const upcoming = v.upcoming ?? []
            return (
              <article key={v.id} className={f.card} style={{ ['--tone' as string]: color }} onClick={() => open(v)} tabIndex={manage && locked ? -1 : 0} onKeyDown={e => e.key === 'Enter' && open(v)} aria-label={`Xe ${v.plate}, ${stateLabel(state, t)}`}>
                <div className={f.top}>
                  <span className={f.code}>{v.id}</span>
                  <span className={f.status}>{stateLabel(state, t)}</span>
                </div>
                <div className={f.art}><VehicleArt stalls={v.capacity} color={color} /></div>
                <span className={f.plate}>{v.plate}</span>
                <div className={f.name}>{VEHICLE_NAME}<small>{t('coordinatorFleet.horsesCapacity', '{{count}} ngựa', { count: v.capacity })}</small></div>
                {(v.onTrip || upcoming.length > 0) && (
                  <div className={f.rows}>
                    {v.onTrip && (
                      <div><span><i className="fa-solid fa-truck-fast" /> {v.returnEta ? 'Đang về trụ sở' : 'Đang đi đơn'}</span><b title={v.onTripBookingCode}>{v.onTripBookingCode}{v.returnEta ? ` · ${formatDateTime(v.returnEta)}` : ''}</b></div>
                    )}
                    {upcoming.length > 0 && (
                      <div><span><i className="fa-regular fa-calendar" /> Đơn kế tiếp</span><b title={upcoming.map(x => `${x.bookingCode} (${formatDate(x.departAt)})`).join(', ')}>{upcoming[0].bookingCode} · đi {formatDate(upcoming[0].departAt)}</b></div>
                    )}
                    {upcoming.length > 1 && <div><span /><b>+{upcoming.length - 1} đơn khác</b></div>}
                  </div>
                )}
                {manage && locked && <span className={f.lockNote}><i className="fa-solid fa-lock" /> {locked}</span>}
                {manage && <div className={f.actions} onClick={e => e.stopPropagation()}>
                  <button
                    className="btn btn-ghost btn-sm"
                    disabled={!!locked}
                    title={locked || 'Sửa xe'}
                    style={locked ? { opacity: 0.45, cursor: 'not-allowed' } : undefined}
                    onClick={() => edit(v)}
                  >
                    {t('common.edit', 'Sửa')}
                  </button>
                  <button
                    className={`btn btn-ghost btn-sm ${locked ? '' : 'text-red'}`}
                    disabled={!!locked}
                    title={locked || 'Xóa xe'}
                    style={locked ? { opacity: 0.45, cursor: 'not-allowed' } : undefined}
                    onClick={() => !locked && setConfirmDelete(v)}
                  >
                    {t('common.delete', 'Xóa')}
                  </button>
                </div>}
              </article>
            )
          })}
          {!shown.length && <div className={f.empty}><i className="fa-solid fa-truck" />{t('common.empty', 'Không có dữ liệu')}</div>}
        </div>
      </div>

      {formOpen && (
        <Modal wide onClose={closeForm} title={editing ? `Xe chuyên dụng ${editing}` : 'Thêm xe chuyên dụng mới'} subtitle={editing ? form.plate : undefined}
          footer={<><button className="btn btn-ghost" onClick={closeForm}>{t('common.cancel', 'Hủy')}</button><button className="btn btn-primary" onClick={save}><i className="fa-solid fa-floppy-disk" /> {t('coordinatorFleet.saveVehicle', 'Lưu xe')}</button></>}>
          <div className={c.formRow}>
            <div className="form-group">
              <label>{t('coordinatorFleet.capacity', 'Sức chứa')}</label>
              <select
                className={cx('form-control', invalid === 'capacity' && 'invalid')}
                value={isSupportedCapacity(form.capacity) ? form.capacity : ''}
                onChange={e => set('capacity')(Number(e.target.value))}
              >
                <option value="" disabled>Chọn sức chứa</option>
                {VEHICLE_CAPACITIES.map(capacity => <option key={capacity} value={capacity}>{t('coordinatorFleet.horsesCapacity', '{{count}} ngựa', { count: capacity })}</option>)}
              </select>
              {!isSupportedCapacity(form.capacity) && (
                <div className={`form-hint ${invalid === 'capacity' ? 'text-red' : ''}`}>
                  Sức chứa hiện tại không thuộc các mức cho phép. Hãy chọn 2, 4, 6 hoặc 9 ngăn.
                </div>
              )}
            </div>
            <div className="form-group">
              <label className="required">{t('coordinatorFleet.plate', 'Biển số')}</label>
              <input
                className={cx('form-control', invalid === 'plate' && 'invalid')}
                value={form.plate}
                disabled={!!editing}
                style={editing ? { background: '#f1f5f9', cursor: 'not-allowed' } : undefined}
                onChange={e => set('plate')(e.target.value)}
              />
              {editing && <div className="form-hint">Biển số xe là định danh cố định, không thể thay đổi.</div>}
            </div>
            <div className="form-group">
              <label>{t('common.status', 'Trạng thái')}</label>
              <select
                className="form-control"
                value={form.status ?? 'AVAILABLE'}
                disabled={!!editing}
                style={editing ? { background: '#f1f5f9', cursor: 'not-allowed' } : undefined}
                onChange={e => set('status')(e.target.value)}
              >
                <option value="AVAILABLE">{t('coordinatorFleet.statusAvailable', 'Sẵn sàng')}</option>
                <option value="MAINTENANCE">{t('coordinatorFleet.statusMaintenance', 'Chưa sẵn sàng')}</option>
              </select>
              {editing && (
                <div className="form-hint">Khi sửa xe, chỉ có thể thay đổi số ngăn.</div>
              )}
            </div>
          </div>
        </Modal>
      )}

      {viewing && <VehicleScheduleModal vehicle={viewing} onClose={() => setViewing(null)} />}

      {confirmDelete && (
        <Modal title={t('coordinatorFleet.deleteVehicle', 'Xóa xe')} onClose={() => setConfirmDelete(null)} footer={<><button className="btn btn-ghost" onClick={() => setConfirmDelete(null)}>{t('common.cancel', 'Hủy')}</button><button className="btn btn-danger" onClick={() => remove(confirmDelete)}>{t('common.delete', 'Xóa')}</button></>}>
          <p>{t('coordinatorFleet.confirmDelete', 'Bạn có chắc chắn muốn xóa xe {{plate}}?', { plate: confirmDelete.plate })}</p>
        </Modal>
      )}
    </div>
  )
}
