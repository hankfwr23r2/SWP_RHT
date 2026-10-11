// Chọn xe, tài xế hoặc hộ tống cho một chuyến: popup dạng thẻ. Mỗi thẻ cho biết người / xe đó đang bận đơn nào;
// ai trùng lịch (ngày đi cách dưới 3 ngày) thì khóa lại và nói rõ trùng với đơn nào.
import { useEffect, useState } from 'react'
import { clashOf, vehicleDocsOk, type Booked } from '@shared/lib/booking'
import { formatDate } from '@shared/lib/format'
import type { CrewMember, Vehicle } from '@shared/services/fleet'
import { Modal } from '@shared/ui/Modal'
import { VehicleArt } from '../features/coordinator/fleet/VehicleArt'
import p from './ResourcePicker.module.css'

export type PickKind = 'vehicle' | 'driver' | 'escort'
const TEXT: Record<PickKind, { title: string; search: string; idle: string; icon: string }> = {
  vehicle: { title: 'Chọn xe', search: 'Tìm biển số, mã xe', idle: 'Chưa có đơn', icon: 'fa-truck' },
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

interface Row { id: string; vehicle?: Vehicle; person?: CrewMember; booked: Booked[]; reason: string }

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
    const reason = noDocs ? `Thiếu giấy đăng kiểm${international ? ' / liên vận' : ''}` : clash ? `Trùng lịch với ${clash.order} (khởi hành ${formatDate(clash.departAt)})` : assigned ? `Đã được giao việc cho đơn ${assigned.order}` : usedHere.has(r.id) && r.id !== selected ? 'Đã chọn ở chuyến khác của đơn này' : ''
    return { ...r, booked, reason }
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
    <Modal wide onClose={onClose} title={t.title} subtitle={kind === 'vehicle' ? `Chuyến này đang xếp ${horses} ngựa. Xe bị khóa là xe trùng lịch hoặc thiếu giấy.` : 'Người đã được giao việc cho một đơn thì bị khóa, đơn giao ngựa xong mới chọn lại được.'}>
      <div className={p.bar}>
        <label className={p.search}><i className="fa-solid fa-magnifying-glass" aria-hidden="true" /><input className="form-control" placeholder={t.search} value={text} onChange={e => setText(e.target.value)} /></label>
        <label className={p.check}><input type="checkbox" checked={onlyOk} onChange={e => setOnlyOk(e.target.checked)} /> Chỉ hiện chọn được</label>
        <span className={p.summary}><b>{rows.filter(r => !r.reason).length}</b>/{rows.length} chọn được</span>
      </div>
      {notice && <div key={notice} className={p.notice} role="status"><i className="fa-solid fa-lock" /> {notice}</div>}

      <div className={p.grid}>
        {shown.map((r, i) => {
          const clash = kind === 'vehicle' ? clashOf(r.booked, departAt) : undefined
          const busyNow = r.booked.length > 0
          const tone = r.reason ? '#dc2626' : busyNow ? '#ea580c' : '#059669'
          const label = r.reason ? 'Khóa' : busyNow ? (kind === 'vehicle' ? 'Có đơn' : 'Đã giao việc') : t.idle
          return (
            <button
              key={r.id} type="button" aria-disabled={!!r.reason} aria-pressed={r.id === selected}
              aria-label={`${r.vehicle ? `Xe ${r.vehicle.plate}` : r.person!.name}${r.reason ? `, không chọn được: ${r.reason}` : ''}`}
              className={`${p.card} ${r.reason ? p.off : ''} ${r.id === selected ? p.picked : ''} ${shake === r.id ? p.shake : ''}`}
              style={{ ['--tone' as string]: tone, ['--i' as string]: Math.min(i, 14) }} onClick={() => press(r)}
            >
              <div className={p.top}>
                <span className={p.code}>{r.id}</span>
                <span className={p.status}><span className={`${p.dot} ${!r.reason && !busyNow ? p.live : ''}`} />{label}</span>
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

              <div className={p.sched}>
                <span className={p.schedHead}><i className="fa-regular fa-calendar" /> {kind === 'vehicle' ? 'Đơn đang nhận' : 'Việc đã được giao'}</span>
                {r.booked.length ? r.booked.map(x => {
                  const hit = clash?.order === x.order
                  return <div key={x.order + x.trip} className={`${p.job} ${hit ? p.hit : ''}`}><b>{x.order}</b><small>khởi hành {formatDate(x.departAt)} · {x.route}</small>{hit && <em>Trùng ngày</em>}</div>
                }) : <span className={p.free}>{kind === 'vehicle' ? 'Chưa có đơn nào' : 'Chưa được giao việc'}</span>}
              </div>

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
