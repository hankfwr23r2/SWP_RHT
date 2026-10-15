// Chọn xe, tài xế hoặc hộ tống cho một chuyến: popup dạng thẻ. Thẻ tài xế, hộ tống cho biết đang bận đơn nào; thẻ xe chỉ nêu trạng thái
// (đi đơn được không). Ai trùng lịch (ngày đi cách dưới 3 ngày) thì khóa lại.
import { useEffect, useState } from 'react'
import { clashOf, vehicleDocsOk, type Booked } from '@shared/lib/booking'
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

interface Props {
  kind: PickKind
  vehicles: Vehicle[]
  people: CrewMember[] // tài xế hoặc hộ tống tùy kind
  schedules: Map<string, Booked[]> // lịch các đơn khác đang giữ từng xe / người
  departAt: number
  international: boolean
  usedHere: Set<string> // đã chọn ở chuyến khác của đơn này
  selected: string
  horses: number // số ngựa đang xếp trên chuyến này (để cảnh báo xe không đủ chỗ)
  onPick: (id: string) => void
  onClose: () => void
}

interface Row { id: string; vehicle?: Vehicle; person?: CrewMember; booked: Booked[]; reason: string; clash: boolean }

export function ResourcePicker({ kind, vehicles, people, schedules, departAt, international, usedHere, selected, horses, onPick, onClose }: Props) {
  const [text, setText] = useState('')
  const [onlyOk, setOnlyOk] = useState(false) // chỉ hiện người / xe chọn được
  const [shake, setShake] = useState<string | null>(null) // thẻ khóa vừa bị bấm: rung và nêu lý do
  const [notice, setNotice] = useState('')
  const t = TEXT[kind]

  const base: Pick<Row, 'id' | 'vehicle' | 'person'>[] = kind === 'vehicle' ? vehicles.map(v => ({ id: v.id, vehicle: v })) : people.map(c => ({ id: c.id, person: c }))
  const rows: Row[] = base.map(r => {
    const booked = schedules.get(r.id) ?? []
    // Xe: trùng lịch theo ngày đi. Tài xế, hộ tống: đã được giao việc cho đơn nào thì khóa, không xét ngày
    const clash = kind === 'vehicle' ? clashOf(booked, departAt) : undefined
    const assigned = kind !== 'vehicle' ? booked[0] : undefined
    const noDocs = r.vehicle && !vehicleDocsOk(r.vehicle, international)
    const reason = noDocs ? `Thiếu giấy đăng kiểm${international ? ' / liên vận' : ''}` : clash ? 'Đã có đơn trùng lịch chạy' : assigned ? (assigned.returning ? `Đang về trụ sở từ ${assigned.returning.from}, dự kiến ${formatDateTime(assigned.returning.eta)}` : `Đang đi đơn ${assigned.order} (${assigned.countries})`) : usedHere.has(r.id) && r.id !== selected ? 'Đã chọn ở chuyến khác của đơn này' : ''
    return { ...r, booked, reason, clash: !!clash && !noDocs }
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
    <Modal wide onClose={onClose} title={t.title} subtitle={kind === 'vehicle' ? `Chuyến này đang xếp ${horses} ngựa. Xe bị khóa là xe trùng lịch hoặc thiếu giấy.` : 'Tài xế và hộ tống luôn xuất phát từ trụ sở ở Việt Nam. Người đang đi đơn hoặc đang chạy về trụ sở thì bị khóa, về tới trụ sở mới chọn lại được.'}>
      <div className={p.bar}>
        <label className={p.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t.search} value={text} onChange={e => setText(e.target.value)} /></label>
        <label className={p.check}><input type="checkbox" checked={onlyOk} onChange={e => setOnlyOk(e.target.checked)} /> Chỉ hiện chọn được</label>
        <span className={p.summary}><b>{rows.filter(r => !r.reason).length}</b>/{rows.length} chọn được</span>
      </div>
      {notice && <div key={notice} className={p.notice} role="status"><i className="fa-solid fa-lock" /> {notice}</div>}

      <div className={p.grid}>
        {shown.map((r, i) => {
          const busyNow = r.booked.length > 0
          const tone = r.clash ? '#ea580c' : r.reason ? '#dc2626' : kind !== 'vehicle' && busyNow ? '#ea580c' : '#059669'
          const label = kind !== 'vehicle' && busyNow ? (r.booked[0].returning ? 'Đang về' : 'Đang đi đơn') : r.clash ? 'Có đơn' : r.reason ? 'Khóa' : kind === 'vehicle' ? t.idle : busyNow ? 'Có đơn' : t.idle
          return (
            <button
              key={r.id} type="button" aria-disabled={!!r.reason} aria-pressed={r.id === selected}
              aria-label={`${r.vehicle ? `Xe ${r.vehicle.plate}` : r.person!.name}${r.reason ? `, không chọn được: ${r.reason}` : ''}`}
              className={`${p.card} ${r.reason ? p.off : ''} ${r.id === selected ? p.picked : ''} ${shake === r.id ? p.shake : ''}`}
              style={{ ['--tone' as string]: tone, ['--i' as string]: Math.min(i, 14) }} onClick={() => press(r)}
            >
              <div className={p.top}>
                <span className={p.code}>{r.id}</span>
                <span className={p.status}><span className={`${p.dot} ${!r.reason && (kind === 'vehicle' || !busyNow) ? p.live : ''}`} />{label}</span>
              </div>

              {r.vehicle ? (
                <>
                  <div className={p.art}><VehicleArt stalls={r.vehicle.capacity} color={tone} /></div>
                  <span className={p.plate}>{r.vehicle.plate}</span>
                  <div className={p.name}>Xe chuyên dụng<small>{r.vehicle.capacity} ngăn{r.vehicle.capacity < horses ? ` · không đủ chỗ cho ${horses} ngựa` : ''}</small></div>
                </>
              ) : (
                <>
                  <span className={p.avatar} style={{ ['--h' as string]: hue(r.id) }}>{initials(r.person!.name)}</span>
                  <div className={p.name}>{r.person!.name}<small>{r.person!.phone}{r.person!.note ? ` · ${r.person!.note}` : ''}{r.person!.license ? ` · GPLX ${r.person!.license}` : ''}</small></div>
                </>
              )}

              {kind === 'vehicle' ? (
                <div className={p.sched}><span className={r.reason ? p.lock : p.free}>{r.clash ? 'Đã có đơn trùng lịch, không chọn được' : r.reason ? 'Không nhận được đơn này' : 'Sẵn sàng nhận đơn'}</span></div>
              ) : (
              <div className={p.sched}>
                <span className={p.schedHead}><i className="fa-regular fa-calendar" /> Đang làm việc</span>
                {r.booked.length ? r.booked.map(x => {
                  return <div key={x.order + x.trip} className={p.job}><b>{x.order}</b><small>{x.returning ? `đã giao ở ${x.returning.from} · về tới trụ sở dự kiến ${formatDateTime(x.returning.eta)}${x.returning.late ? ' · về trễ' : ''}` : `khởi hành ${formatDate(x.departAt)} · ${x.countries}`}</small></div>
                }) : <span className={p.free}>Sẵn sàng tại trụ sở Việt Nam</span>}
              </div>
              )}

              {r.reason && <span className={p.lock}><i className="fa-solid fa-lock" />{r.reason}</span>}
              {r.id === selected && <span className={p.tick}><i className="fa-solid fa-circle-check" /> Đang chọn</span>}
            </button>
          )
        })}
        {!shown.length && <div className={p.empty}><i className={`fa-solid ${t.icon}`} />Không có kết quả khớp.</div>}
      </div>
    </Modal>
  )
}
