// Chọn xe, tài xế hoặc hộ tống cho một chuyến: popup dạng thẻ.
// Chọn được hay không do backend quyết định (danh sách tải theo ngày đi, loại chuyến của đơn), cùng quy tắc với bước lưu.
// Lịch các đơn khác chỉ để người chọn hiểu vì sao: có đơn ở ngày khác vẫn chọn được.
import { useEffect, useState } from 'react'
import { findCrewBooked, type Booked } from '@shared/lib/booking'
import { formatDate, formatDateTime } from '@shared/lib/format'
import type { CrewMember, Vehicle } from '@shared/services/fleet'
import { Modal } from '@shared/ui/Modal'
import { VehicleArt } from '../features/coordinator/fleet/VehicleArt'
import p from './ResourcePicker.module.css'

export type PickKind = 'vehicle' | 'driver' | 'escort'
const TEXT: Record<PickKind, { title: string; search: string; idle: string; icon: string }> = {
  vehicle: { title: 'Chọn xe', search: 'Tìm biển số, mã xe', idle: 'Sẵn sàng', icon: 'fa-truck' },
  driver: { title: 'Chọn tài xế', search: 'Tìm tên, mã, số điện thoại', idle: 'Rảnh', icon: 'fa-id-card' },
  escort: { title: 'Chọn nhân viên hộ tống', search: 'Tìm tên, mã, số điện thoại', idle: 'Rảnh', icon: 'fa-horse-head' },
}
const initials = (name: string) => name.split(' ').filter(Boolean).slice(-2).map(w => w[0]).join('').toUpperCase()
const hue = (id: string) => [...id].reduce((n, c) => n + c.charCodeAt(0) * 7, 0) % 360

const isVehicleMaint = (st?: string) => {
  const s = (st || '').toUpperCase().replace(/[\s_-]/g, '')
  return s === 'MAINTENANCE' || s === 'UNAVAILABLE'
}

// Màu trạng thái dùng chung với trang Đội xe
const TONE = { free: '#059669', maint: '#d97706', away: '#ea580c', locked: '#dc2626' } as const

interface Props {
  kind: PickKind
  vehicles: Vehicle[]
  people: CrewMember[] // tài xế hoặc hộ tống tùy kind
  schedules?: Map<string, Booked[]> // lịch các đơn khác của từng người (chỉ hiển thị)
  departAt: number
  usedHere: Set<string> // đã chọn ở chuyến khác của đơn này
  selected: string
  horses: number // số ngựa đang xếp trên chuyến này (để cảnh báo xe không đủ chỗ)
  stalls?: number // số ngăn các ngựa đó cần (khoang mở rộng chiếm 2), mặc định bằng số ngựa
  onPick: (id: string) => void
  onClose: () => void
}

interface Row {
  id: string
  vehicle?: Vehicle
  person?: CrewMember
  booked: Booked[] // lịch nhân sự (hiển thị)
  reason: string // khác rỗng: không chọn được
  label: string
  tone: string
}

export function ResourcePicker({ kind, vehicles, people, schedules, usedHere, selected, horses, stalls = horses, onPick, onClose }: Props) {
  const [text, setText] = useState('')
  const [onlyOk, setOnlyOk] = useState(false) // chỉ hiện người / xe chọn được
  const [shake, setShake] = useState<string | null>(null) // thẻ khóa vừa bị bấm: rung và nêu lý do
  const [notice, setNotice] = useState('')
  const t = TEXT[kind]

  const rows: Row[] = kind === 'vehicle'
    ? vehicles.map(v => {
        const inMaint = isVehicleMaint(v.status)
        const reason = inMaint
          ? 'Xe đang bảo dưỡng định kỳ / chưa sẵn sàng'
          : v.available === false
            ? (v.busyReason ?? 'Xe không sẵn sàng')
            : usedHere.has(v.id) && v.id !== selected ? 'Đã chọn ở chuyến khác của đơn này' : ''
        // Khóa vì chính chuyến đang chạy thì nói rõ "đang đi / đang về"; xe đang chạy mà vẫn chọn được (đơn ở xa) thì hiện Sẵn sàng
        const blockedByTrip = v.onTrip && !!v.busyBookingCode && v.busyBookingCode === v.onTripBookingCode
        const [label, tone] = inMaint
          ? ['Chưa sẵn sàng', TONE.maint]
          : reason
            ? blockedByTrip
              ? [v.returnEta ? 'Đang về' : 'Đang đi đơn', TONE.away]
              : [v.busyBookingCode ? 'Trùng lịch' : 'Khóa', TONE.locked]
            : [t.idle, TONE.free]
        return { id: v.id, vehicle: v, booked: [], reason, label, tone }
      })
    : people.map(c => {
        const booked = findCrewBooked(schedules ?? new Map(), c.id, c.name)
        const reason = c.available === false
          ? (c.busyReason ?? 'Nhân sự không sẵn sàng')
          : usedHere.has(c.id) && c.id !== selected ? 'Đã chọn ở chuyến khác của đơn này' : ''
        const away = booked.find(x => (x.returning || x.active) && x.order === c.busyBookingCode)
        const [label, tone] = c.available === false
          ? !c.busyBookingCode
            ? ['Khóa', TONE.locked]
            : away
              ? [away.returning ? 'Đang về' : 'Đang đi đơn', TONE.away]
              : ['Trùng lịch', TONE.locked]
          : reason
            ? ['Khóa', TONE.locked]
            : [t.idle, TONE.free]
        return { id: c.id, person: c, booked, reason, label, tone }
      })

  const keyword = text.trim().toLowerCase()
  const shown = rows.filter(r => {
    const hay = r.vehicle ? [r.vehicle.plate, r.id] : [r.person!.name, r.id, r.person!.phone]
    return (!keyword || hay.some(x => x.toLowerCase().includes(keyword))) && (!onlyOk || !r.reason)
  })
  useEffect(() => { if (!shake) return; const h = setTimeout(() => setShake(null), 480); return () => clearTimeout(h) }, [shake])

  const press = (r: Row) => {
    if (r.reason) { setShake(r.id); setNotice(`${r.vehicle ? `Xe ${r.vehicle.plate}` : r.person!.name} không chọn được: ${r.reason}.`); return }
    onPick(r.id)
    onClose()
  }

  return (
    <Modal wide onClose={onClose} title={t.title} subtitle={kind === 'vehicle' ? `Chuyến này đang xếp ${horses} ngựa. Xe có lịch chồng lên khoảng ngày của đơn này (kể cả chuyến đang chạy) hoặc đang bảo dưỡng sẽ bị khóa. Xe có đơn ở ngày khác vẫn chọn được.` : 'Tài xế và hộ tống luôn xuất phát từ trụ sở ở Việt Nam. Người có lịch chồng lên khoảng ngày của đơn này (kể cả chuyến đang chạy hoặc chưa về trụ sở) thì bị khóa. Người có đơn ở ngày khác vẫn chọn được.'}>
      <div className={p.bar}>
        <label className={p.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t.search} value={text} onChange={e => setText(e.target.value)} /></label>
        <label className={p.check}><input type="checkbox" checked={onlyOk} onChange={e => setOnlyOk(e.target.checked)} /> Chỉ hiện chọn được</label>
        <span className={p.summary}><b>{rows.filter(r => !r.reason).length}</b>/{rows.length} chọn được</span>
      </div>
      {notice && <div key={notice} className={p.notice} role="status"><i className="fa-solid fa-lock" /> {notice}</div>}

      <div className={p.grid}>
        {shown.map((r, i) => (
          <button
            key={r.id} type="button" aria-disabled={!!r.reason} aria-pressed={r.id === selected}
            aria-label={`${r.vehicle ? `Xe ${r.vehicle.plate}` : r.person!.name}${r.reason ? `, không chọn được: ${r.reason}` : ''}`}
            className={`${p.card} ${r.reason ? p.off : ''} ${r.id === selected ? p.picked : ''} ${shake === r.id ? p.shake : ''}`}
            style={{ ['--tone' as string]: r.tone, ['--i' as string]: Math.min(i, 14) }} onClick={() => press(r)}
          >
            <div className={p.top}>
              <span className={p.code}>{r.id}</span>
              <span className={p.status}><span className={`${p.dot} ${!r.reason ? p.live : ''}`} />{r.label}</span>
            </div>

            {r.vehicle ? (
              <>
                <div className={p.art}><VehicleArt stalls={r.vehicle.capacity} color={r.tone} /></div>
                <span className={p.plate}>{r.vehicle.plate}</span>
                <div className={p.name}>Xe chuyên dụng<small>{r.vehicle.capacity} ngăn{r.vehicle.capacity < stalls ? ` · không đủ chỗ cho ${horses} ngựa` : ''}</small></div>
                <VehicleSchedule v={r.vehicle} />
              </>
            ) : (
              <>
                <span className={p.avatar} style={{ ['--h' as string]: hue(r.id) }}>{initials(r.person!.name)}</span>
                <div className={p.name}>{r.person!.name}<small>{r.person!.phone}{r.person!.note ? ` · ${r.person!.note}` : ''}{r.person!.license ? ` · GPLX ${r.person!.license}` : ''}</small></div>
                <div className={p.sched}>
                  <span className={p.schedHead}><i className="fa-regular fa-calendar" /> Lịch đã nhận</span>
                  {r.booked.length ? r.booked.map(x => (
                    <div key={x.order + x.trip} className={p.job}><b>{x.order}</b><small>{x.returning ? `đã giao ở ${x.returning.from}${x.returning.eta ? ` · về tới trụ sở dự kiến ${formatDateTime(x.returning.eta)}` : ' · chưa có giờ về dự kiến'}${x.returning.late ? ' · về trễ' : ''}` : `khởi hành ${formatDate(x.departAt)} · ${x.countries}`}</small></div>
                  )) : <span className={p.free}>Sẵn sàng tại trụ sở Việt Nam</span>}
                </div>
              </>
            )}

            {r.reason && <span className={p.lock}><i className="fa-solid fa-lock" />{r.reason}</span>}
            {r.id === selected && <span className={p.tick}><i className="fa-solid fa-circle-check" /> Đang chọn</span>}
          </button>
        ))}
        {!shown.length && <div className={p.empty}><i className={`fa-solid ${t.icon}`} />Không có kết quả khớp.</div>}
      </div>
    </Modal>
  )
}

// Lịch của xe do backend trả: chuyến đang chạy (nếu có) và các đơn đã nhận chưa lăn bánh
function VehicleSchedule({ v }: { v: Vehicle }) {
  const upcoming = v.upcoming ?? []
  return (
    <div className={p.sched}>
      <span className={p.schedHead}><i className="fa-regular fa-calendar" /> Lịch đã nhận</span>
      {v.onTrip && v.onTripBookingCode && (
        <div className={p.job}><b>{v.onTripBookingCode}</b><small>{v.returnEta ? `đang về trụ sở · dự kiến ${formatDateTime(v.returnEta)}` : 'đang đi đơn'}</small></div>
      )}
      {upcoming.map(x => (
        <div key={x.bookingCode} className={p.job}><b>{x.bookingCode}</b><small>khởi hành {formatDate(x.departAt)} · bận tới {formatDate(x.busyUntil)}</small></div>
      ))}
      {!v.onTrip && !upcoming.length && <span className={p.free}>Sẵn sàng tại trụ sở Việt Nam</span>}
    </div>
  )
}
