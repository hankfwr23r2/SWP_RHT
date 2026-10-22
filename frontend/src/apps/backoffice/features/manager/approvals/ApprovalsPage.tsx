// Manager: duyệt báo giá cuối sau khi Kiểm dịch viên và Điều phối viên đều đạt (PRD mục 2.5).
// Manager nhìn số xe Điều phối viên đã chọn để chọn tài xế và hộ tống cho từng xe. Báo giá do hệ thống tính; Manager chỉ điều chỉnh phụ phí và chiết khấu thương mại rồi gửi cho khách.
import { useEffect, useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { QUOTE_VALID_HOURS } from '@shared/config/booking-rules'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { finalizeQuote, sameVehicleId, schedulesOf, vehicleClassOf } from '@shared/lib/booking'
import { formatDate, formatDateTime, formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { crewApi, vehiclesApi } from '@shared/services/fleet'
import { useLoad } from '@shared/services/useLoad'
import type { Booking } from '@shared/types/booking'
import { QuoteSheet } from '@shared/ui/QuoteSheet'
import { useToast } from '@shared/ui/toast'
import { FleetRouteSection, HorseDocsSection } from '../../../shared/BookingEvidence'
import { OrderInfo, PartiesInfo } from '../../../shared/BookingParts'
import { SideTabsModal } from '../../../shared/SideTabsModal'
import { ResourcePicker } from '../../../shared/ResourcePicker'
import { ListPage, idCell, routeCell, statusCell, type Column } from '../../../shared/ListPage'
import { placeShort } from '../../../shared/place'
import s from '../../../shared/booking.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

type Tab = 'todo' | 'sent'
interface AdjRow { kind: 'surcharge' | 'discount'; label: string; amount: string }
const digits = (v: string) => Number(v.replace(/\D/g, '')) || 0

type Pick2 = { driverId: string; escortId: string }
export function QuoteModal({ b, all, onClose, onDone }: { b: Booking; all: Booking[]; onClose: () => void; onDone: () => void }) {
  const toast = useToast()
  const { session } = useAuth()
  const { data: draft } = useLoad(() => bookingsApi.quoteDraft(b.id), [b.id])
  const { data: vehicles } = useLoad(vehiclesApi.list)
  const { data: crew } = useLoad(crewApi.list)
  const trips = b.trips ?? []
  const sched = schedulesOf(all, b.id)
  const [picks, setPicks] = useState<Record<string, Pick2>>(() => Object.fromEntries(trips.map(t => [t.tripId, { driverId: t.driverId, escortId: t.escortId }])))

  useEffect(() => {
    if (!crew?.length) return
    setPicks(prev => {
      let changed = false
      const next = { ...prev }
      const availableDrivers = crew.filter(c => c.role === 'driver' && !sched.drivers.has(c.id))
      const availableEscorts = crew.filter(c => c.role === 'escort' && !sched.escorts.has(c.id))
      let dIdx = 0, eIdx = 0
      for (const t of trips) {
        const cur = next[t.tripId] || { driverId: '', escortId: '' }
        let dId = cur.driverId
        let eId = cur.escortId
        if (!dId && dIdx < availableDrivers.length) {
          dId = availableDrivers[dIdx++].id
          changed = true
        }
        if (!eId && eIdx < availableEscorts.length) {
          eId = availableEscorts[eIdx++].id
          changed = true
        }
        next[t.tripId] = { driverId: dId, escortId: eId }
      }
      return changed ? next : prev
    })
  }, [crew, trips, sched])

  const [picking, setPicking] = useState<{ tripId: string; kind: 'driver' | 'escort' } | null>(null)
  const [back, setBack] = useState<{ to: 'specialist' | 'coordinator' | 'both'; reason: string } | null>(null) // đang nhập lý do trả lại
  const used = (k: keyof Pick2) => new Set(Object.values(picks).map(p => p[k]).filter(Boolean))
  // Chưa chọn đủ, hoặc người đã giữ cho đơn khác có ngày đi gần ngày này: chưa gửi được báo giá
  const crewErrors = trips.flatMap((t, i) => {
    const p = picks[t.tripId]
    const out: string[] = []
    if (!p.driverId) out.push(`Xe ${i + 1} chưa chọn tài xế.`)
    if (!p.escortId) out.push(`Xe ${i + 1} chưa chọn nhân viên hộ tống.`)
    const d = crew?.find(c => c.id === p.driverId), e = crew?.find(c => c.id === p.escortId)
    const dc = sched.drivers.get(p.driverId)?.[0], ec = sched.escorts.get(p.escortId)?.[0]
    const why = (x: NonNullable<typeof dc>) => (x.returning ? `đang về trụ sở từ ${x.returning.from} (đơn ${x.order})` : `đang đi đơn ${x.order} (${x.countries})`)
    if (d && dc) out.push(`Tài xế ${d.name} ${why(dc)}.`)
    if (e && ec) out.push(`Hộ tống ${e.name} ${why(ec)}.`)
    return out
  })
  const [rows, setRows] = useState<AdjRow[]>([])
  const [busy, setBusy] = useState(false)
  const adjustments = rows.filter(r => r.label.trim() && digits(r.amount) > 0).map(r => ({ label: r.label.trim(), amount: r.kind === 'discount' ? -digits(r.amount) : digits(r.amount) }))
  const preview = draft && finalizeQuote(draft.lines, adjustments, Date.now(), session!.name)
  const set = (i: number, patch: Partial<AdjRow>) => setRows(r => r.map((x, j) => (j === i ? { ...x, ...patch } : x)))

  const sendBack = async () => {
    setBusy(true)
    const targetLabel = back!.to === 'specialist' ? 'Kiểm dịch viên' : back!.to === 'coordinator' ? 'Điều phối viên' : 'cả hai bên'
    try {
      await bookingsApi.sendBack(b.id, session!.name, back!.to, back!.reason)
      toast(`Đã trả ${b.id} về ${targetLabel}`)
      onDone()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không trả lại được', 'error')
      setBusy(false)
    }
  }
  const send = async () => {
    setBusy(true)
    try { await bookingsApi.assignCrew(b.id, session!.name, trips.map(t => ({ tripId: t.tripId, ...picks[t.tripId] }))); await bookingsApi.sendQuote(b.id, session!.name, adjustments); toast(`Đã gửi báo giá ${b.id} cho khách, hiệu lực ${QUOTE_VALID_HOURS} giờ`); onDone() }
    catch (e) { toast(e instanceof Error ? e.message : 'Không gửi được', 'error'); setBusy(false) }
  }

  const crewTab = (
    <>
      <h4 style={{ margin: '0 0 10px' }}>Chọn tài xế và nhân viên hộ tống</h4>
      <p className={s.hint} style={{ marginBottom: 10 }}>Điều phối viên đã chọn <b>{trips.length} xe</b>. Mỗi xe cần đúng 1 tài xế và 1 nhân viên hộ tống. Tài xế và hộ tống luôn xuất phát từ trụ sở ở Việt Nam. Người đang đi đơn khác hoặc đang chạy về trụ sở thì bị khóa, về tới trụ sở mới chọn lại được.</p>
      {trips.map((t, i) => {
        const v = vehicles?.find(x => sameVehicleId(x.id, t.vehicleId))
        const p = picks[t.tripId]
        const d = crew?.find(c => c.id === p.driverId), e = crew?.find(c => c.id === p.escortId)
        return (
          <div key={t.tripId} className={s.pick} style={{ display: 'block', marginBottom: 12 }}>
            <div className={s.pickName}>Xe {i + 1}{v ? ` · ${v.plate} (${VEHICLE_CLASS[vehicleClassOf(v.capacity)].label}, ${v.capacity} ngăn)` : ''} · {t.horseIds.length} ngựa: {t.horseIds.map(h => b.horses.find(x => x.horseId === h)?.name).join(', ')}</div>
            <div className={s.form2} style={{ marginTop: 10 }}>
              {([['driver', 'Tài xế', d, 'fa-id-card'], ['escort', 'Nhân viên hộ tống', e, 'fa-horse-head']] as const).map(([kind, label, who, icon]) => (
                <div key={kind} className="form-group">
                  <label htmlFor={`${kind}${i}`}>{label}</label>
                  <button id={`${kind}${i}`} type="button" className={`form-control ${s.vehBtn}`} onClick={() => setPicking({ tripId: t.tripId, kind })}>
                    <i className={`fa-solid ${icon}`} aria-hidden="true" />
                    <span key={who?.id} className={s.vehPop}>{who ? <b>{who.name}</b> : `Chọn ${label.toLowerCase()}`}</span>
                    <em>{who ? 'Đổi' : 'Chọn'}</em>
                  </button>
                </div>
              ))}
            </div>
          </div>
        )
      })}
      {crewErrors.length > 0 && <div className="alert alert-warning"><i className="fa-solid fa-triangle-exclamation" /><ul>{crewErrors.map(x => <li key={x}>{x}</li>)}</ul></div>}
    </>
  )
  const quoteTab = (
    <>
      <div className="alert alert-info" style={{ marginBottom: 16 }}><i className="fa-solid fa-eye" /><div><b>Giấy tờ ở các mục bên trái chỉ để Quản lý xem.</b> Khi bấm “Duyệt và gửi báo giá”, khách chỉ nhận chi tiết đơn và bảng giá, không kèm giấy tờ nào.</div></div>
      <h4 style={{ margin: '0 0 10px' }}>Điều chỉnh báo giá (không bắt buộc)</h4>
      {rows.map((r, i) => (
        <div key={i} className={s.adj}>
          <input className="form-control" aria-label="Nội dung điều chỉnh" placeholder={r.kind === 'discount' ? 'VD: Khách hàng thân thiết' : 'VD: Phụ phí chuyến gấp'} value={r.label} onChange={e => set(i, { label: e.target.value })} />
          <FormSelect className="form-control" aria-label="Loại điều chỉnh" value={r.kind} onChange={e => set(i, { kind: e.target.value as AdjRow['kind'] })}><option value="surcharge">Phụ phí</option><option value="discount">Chiết khấu</option></FormSelect>
          <input className="form-control" aria-label="Số tiền" inputMode="numeric" placeholder="Số tiền" value={r.amount ? digits(r.amount).toLocaleString('en-US') : ''} onChange={e => set(i, { amount: String(digits(e.target.value) || '') })} />
          <button className={s.iconBtn} aria-label="Xóa dòng" onClick={() => setRows(x => x.filter((_, j) => j !== i))}><i className="fa-solid fa-trash" /></button>
        </div>
      ))}
      <button className="btn btn-outline btn-sm" onClick={() => setRows(r => [...r, { kind: 'surcharge', label: '', amount: '' }])}><i className="fa-solid fa-plus" /> Thêm dòng điều chỉnh</button>

      <h4 style={{ margin: '18px 0 10px' }}>Báo giá gửi khách</h4>
      {preview ? <QuoteSheet {...preview} /> : <p className="text-muted">Đang tính…</p>}
    </>
  )
  return (
    <>
      <SideTabsModal
        onClose={onClose} title={`Duyệt báo giá ${b.id}`} subtitle={`${b.customer} · khởi hành ${formatDate(b.departAt)}`}
        top={
          back && (
            <div className="form-group">
              <label style={{ marginBottom: 6 }}>Đối tượng nhận lại yêu cầu</label>
              <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
                <button
                  type="button"
                  className={`btn btn-sm ${back.to === 'specialist' ? 'btn-primary' : 'btn-outline'}`}
                  onClick={() => setBack({ ...back, to: 'specialist' })}
                >
                  Kiểm dịch viên
                </button>
                <button
                  type="button"
                  className={`btn btn-sm ${back.to === 'coordinator' ? 'btn-primary' : 'btn-outline'}`}
                  onClick={() => setBack({ ...back, to: 'coordinator' })}
                >
                  Điều phối viên
                </button>
                <button
                  type="button"
                  className={`btn btn-sm ${back.to === 'both' ? 'btn-primary' : 'btn-outline'}`}
                  onClick={() => setBack({ ...back, to: 'both' })}
                >
                  Cả hai bên
                </button>
              </div>
              <label htmlFor="sb" className="required">
                {back.to === 'specialist'
                  ? 'Lý do trả lại Kiểm dịch viên (duyệt lại hồ sơ ngựa)'
                  : back.to === 'coordinator'
                  ? 'Lý do trả lại Điều phối viên (làm lại xe và lộ trình)'
                  : 'Lý do trả lại cả hai bên (làm lại cả hồ sơ ngựa, xe và lộ trình)'}
              </label>
              <textarea id="sb" className="form-control" rows={3} value={back.reason} onChange={e => setBack({ ...back, reason: e.target.value })} />
              <div className="form-hint">
                {back.to === 'specialist'
                  ? 'Đơn quay về Kiểm dịch viên để duyệt lại hồ sơ ngựa, xe và lộ trình của Điều phối viên được giữ nguyên.'
                  : back.to === 'coordinator'
                  ? 'Đơn quay về Điều phối viên để chọn lại xe và lập lại lộ trình, Driver và Escort bạn đã chọn sẽ bị bỏ và chọn lại sau.'
                  : 'Đơn quay về trạng thái thẩm định song song để cả Kiểm dịch viên và Điều phối viên thực hiện lại từ đầu.'}
              </div>
            </div>
          )
        }
        tabs={[
          { id: 'customer', label: 'Khách hàng', icon: 'fa-user', content: <PartiesInfo b={b} /> },
          { id: 'docs', label: 'Hồ sơ ngựa', icon: 'fa-file-medical', content: <HorseDocsSection b={b} /> },
          { id: 'order', label: 'Thông tin đơn', icon: 'fa-clipboard-list', content: <OrderInfo b={b} /> },
          { id: 'route', label: 'Xe và lộ trình', icon: 'fa-route', content: <FleetRouteSection b={b} /> },
          { id: 'crew', label: 'Tài xế, hộ tống', icon: 'fa-id-card', state: crewErrors.length ? 'warn' : 'ok', count: crewErrors.length || undefined, content: crewTab },
          { id: 'quote', label: 'Báo giá', icon: 'fa-file-invoice-dollar', content: quoteTab },
        ]}
        footer={back
          ? <>
              <button className="btn btn-ghost" onClick={() => setBack(null)}>Quay lại</button>
              <button className="btn btn-danger" disabled={busy || !back.reason.trim()} onClick={sendBack}>Xác nhận trả lại</button>
            </>
          : <>
              <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
              <button className="btn btn-ghost" onClick={() => setBack({ to: 'specialist', reason: '' })}>Trả lại Kiểm dịch viên</button>
              <button className="btn btn-ghost" onClick={() => setBack({ to: 'coordinator', reason: '' })}>Trả lại Điều phối viên</button>
              <button className="btn btn-ghost" onClick={() => setBack({ to: 'both', reason: '' })}>Trả lại cả hai bên</button>
              <button className="btn btn-primary" disabled={busy || !preview || !!crewErrors.length} onClick={send}><i className="fa-solid fa-paper-plane" /> Duyệt và gửi báo giá</button>
            </>}
      />
      {picking && vehicles && crew && (() => {
        const kind = picking.kind
        return (
          <ResourcePicker
            kind={kind} vehicles={vehicles} people={crew.filter(c => c.role === kind)} departAt={b.departAt}
            schedules={kind === 'driver' ? sched.drivers : sched.escorts} usedHere={used(kind === 'driver' ? 'driverId' : 'escortId')}
            selected={picks[picking.tripId][kind === 'driver' ? 'driverId' : 'escortId']} horses={0}
            onPick={id => setPicks(x => ({ ...x, [picking.tripId]: { ...x[picking.tripId], [kind === 'driver' ? 'driverId' : 'escortId']: id } }))}
            onClose={() => setPicking(null)}
          />
        )
      })()}
    </>
  )
}

export default function ApprovalsPage() {
  const { data: all, reload } = useLoad(bookingsApi.list)
  const [tab, setTab] = useState<Tab>('todo')
  const [open, setOpen] = useState<Booking | null>(null)
  const list = all ?? []
  const todo = list.filter(b => b.status === 'pending_commercial')
  const sent = list.filter(b => b.quote)
  const columns: Column<Booking>[] = [
    { head: 'Mã đơn', cell: b => idCell(b) },
    { head: 'Khách hàng', cell: b => b.customer, nowrap: true },
    { head: 'Tuyến', cell: b => routeCell(placeShort(b.origin.name), placeShort(b.dest.name)) },
    { head: 'Khởi hành', cell: b => formatDate(b.departAt), nowrap: true },
    { head: 'Ngựa / xe', cell: b => `${b.horses.length} con · ${b.trips?.length ?? 0} xe`, nowrap: true },
    { head: 'Trạng thái', cell: b => statusCell(b.status) },
    ...(tab === 'sent' ? [{ head: 'Gửi lúc', cell: (b: Booking) => (b.quote ? formatDateTime(b.quote.sentAt) : '-'), nowrap: true }, { head: 'Báo giá', cell: (b: Booking) => (b.quote ? <>{formatVND(b.quote.total)}<div className="sub-text">Cọc {formatVND(b.quote.deposit)}</div></> : '-'), right: true }] : []),
    { head: 'Thao tác', cell: b => (tab === 'todo' ? <button className="btn btn-primary btn-sm" onClick={() => setOpen(b)}>Duyệt báo giá</button> : null), right: true },
  ]
  return (
    <>
      <ListPage title="Duyệt báo giá" subtitle={`Duyệt để gửi khách, khách có ${QUOTE_VALID_HOURS} giờ đặt cọc 30%.`} tabs={[['todo', 'Chờ duyệt báo giá', todo.length], ['sent', 'Đã gửi báo giá', sent.length]]} tab={tab} onTab={setTab} hot={['todo']}
        rows={tab === 'todo' ? todo : sent} rowKey={b => b.id} columns={columns} haystack={b => [b.id, b.customer, b.origin.name, b.dest.name]} dateOf={b => b.departAt} loaded={!!all}
        emptyText={tab === 'todo' ? 'Không có đơn nào chờ duyệt báo giá.' : 'Chưa gửi báo giá nào.'}
        summary={r => (tab === 'sent' ? <>Tổng báo giá: <b>{formatVND(r.reduce((n, b) => n + (b.quote?.total ?? 0), 0))}</b></> : null)} />
      {open && <QuoteModal b={open} all={list} onClose={() => setOpen(null)} onDone={() => { setOpen(null); reload() }} />}
    </>
  )
}
