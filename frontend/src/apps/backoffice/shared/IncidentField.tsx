// Sự cố khẩn cấp và chi phí trên app Tài xế / Hộ tống (Flow 5, PRD mục 6; Flow 6, PRD mục 7.3).
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { IncidentPlanView } from './IncidentPlanView'
import { needsFitCheck } from '@shared/lib/booking'
import { EXPENSE_CATEGORY, INCIDENT_ACTION, INCIDENT_KIND, SOS_KINDS, type ExpenseCategory, type IncidentKind } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, Incident, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { useNow } from '@shared/ui/useNow'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Panel } from './field'
import s from './field.module.css'
import { FormSelect } from '@shared/ui/FormSelect'
import { Modal } from '@shared/ui/Modal'

export type Run = (fn: () => Promise<unknown>, msg: string) => Promise<void>

// Nút SOS: chọn nhóm sự cố, chụp ảnh hiện trường, gửi
export function SosPanel({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState<IncidentKind>('horse_health')
  const [photo, setPhoto] = useState<string>()
  const [note, setNote] = useState('')
  const [horseIds, setHorseIds] = useState<string[]>([])
  if (!open) return <button className={`btn btn-outline ${s.big}`} style={{ color: 'var(--red)', borderColor: 'var(--red)' }} onClick={() => setOpen(true)}><i className="fa-solid fa-triangle-exclamation" /> Báo sự cố khẩn cấp (SOS)</button>
  return (
    <Panel title="Báo sự cố khẩn cấp" icon="fa-triangle-exclamation" tone="warn">
      <div role="radiogroup" aria-label="Nhóm sự cố" style={{ display: 'grid', gap: 8 }}>
        {SOS_KINDS.map(k => (
          <label key={k} style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 52, padding: '0 14px', border: `1.5px solid ${kind === k ? 'var(--orange)' : 'var(--line)'}`, borderRadius: 'var(--radius-sm)' }}>
            <input type="radio" name="kind" checked={kind === k} onChange={() => setKind(k)} style={{ width: 20, height: 20, accentColor: 'var(--orange)' }} />
            <span><b>{INCIDENT_KIND[k].label}</b><br /><small>{INCIDENT_KIND[k].hint}</small></span>
          </label>
        ))}
      </div>
      {kind === 'horse_health' && <fieldset><legend>Ngựa bị ảnh hưởng</legend>{b.horses.filter(h => t.horseIds.includes(h.horseId)).map(h => <label key={h.horseId} style={{ display: 'block', padding: 6 }}><input type="checkbox" checked={horseIds.includes(h.horseId)} onChange={e => setHorseIds(ids => e.target.checked ? [...ids, h.horseId] : ids.filter(id => id !== h.horseId))} /> {h.name} · chip {h.microchip}</label>)}</fieldset>}
      <CaptureField purpose="SOS" label="Ảnh hiện trường" required value={photo} onChange={setPhoto} />
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="sos-note">Ghi chú ngắn</label><input id="sos-note" className="form-control" style={{ minHeight: 48 }} value={note} onChange={e => setNote(e.target.value)} /></div>
      <div style={{ display: 'flex', gap: 8 }}>
        <button className="btn btn-ghost" onClick={() => setOpen(false)}>Hủy</button>
        <button className={`btn btn-primary ${s.big}`} disabled={!photo || (kind === 'horse_health' && !horseIds.length)} onClick={() => run(() => bookingsApi.reportIncident(b.id, t.tripId, session!.name, kind, photo!, note, b, t, horseIds), 'Đã gửi SOS vào danh sách xử lý của Điều phối viên và Quản lý')}>Gửi báo động khẩn cấp</button>
      </div>
    </Panel>
  )
}

export function ExpenseForm({ b, inc, run }: { b: Booking; inc: Incident; run: Run }) {
  const { session } = useAuth()
  const [category, setCategory] = useState<ExpenseCategory>('vet_fee')
  const [label, setLabel] = useState('')
  const [photo, setPhoto] = useState<string>()
  const [amount, setAmount] = useState('')
  const reset = () => { setPhoto(undefined); setAmount(''); setLabel('') }
  return (
    <>
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="ex-cat">Loại chi phí</label>
        <FormSelect id="ex-cat" className="form-control" style={{ minHeight: 48 }} value={category} onChange={e => setCategory(e.target.value as ExpenseCategory)}>{(Object.keys(EXPENSE_CATEGORY) as ExpenseCategory[]).map(k => <option key={k} value={k}>{EXPENSE_CATEGORY[k]}</option>)}</FormSelect></div>
      <CaptureField purpose="EXPENSE" label="Ảnh hóa đơn / biên lai (chụp trước)" required value={photo} onChange={setPhoto} />
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="ex-label">Nội dung</label><input id="ex-label" className="form-control" style={{ minHeight: 48 }} value={label} onChange={e => setLabel(e.target.value)} /></div>
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="ex-amount">Số tiền trên hóa đơn (VNĐ){!photo && ' — chụp ảnh trước để mở khóa'}</label><input id="ex-amount" inputMode="numeric" className="form-control" style={{ minHeight: 48 }} disabled={!photo} value={amount} onChange={e => setAmount(e.target.value.replace(/\D/g, ''))} /></div>
      <button className={`btn btn-outline ${s.big}`} disabled={!photo || !Number(amount)} onClick={() => run(async () => { await bookingsApi.addIncidentExpense(b.id, inc.id, session!.name, { category, label, photo: photo!, amount: Number(amount) }, inc); reset() }, 'Đã ghi chi phí kèm chứng từ')}>Thêm chi phí</button>
    </>
  )
}

function HealthCareForm({ b, inc, run }: { b: Booking; inc: Incident; run: Run }) {
  const { session } = useAuth()
  const now = useNow(1000)
  const [askEarly, setAskEarly] = useState(false)
  const [photo, setPhoto] = useState<string>()
  const [water, setWater] = useState('0')
  const [hay, setHay] = useState(false)
  const [records, setRecords] = useState<Record<string, { note: string; temperature: string }>>({})
  const trip = b.trips?.find(t => t.tripId === inc.tripId)
  const horses = b.horses.filter(h => (inc.horseIds?.length ? inc.horseIds : trip?.horseIds ?? []).includes(h.horseId))
  const save = (confirmEarly: boolean) => run(() => bookingsApi.confirmFit(b.id, inc.id, session!.name, {
    photo: photo!, waterLiters: Number(water || 0), hay, confirmEarly, horses: horses.map(h => ({ horseId: h.horseId, careNote: records[h.horseId].note.trim(), ...(records[h.horseId].temperature ? { temperature: Number(records[h.horseId].temperature) } : {}) })),
  }), 'Đã lưu nhật ký sức khỏe và xác nhận đủ sức đi tiếp')
  const ready = !!photo && Number.isFinite(Number(water || 0)) && Number(water || 0) >= 0 && horses.length > 0 && horses.every(h => records[h.horseId]?.note.trim())
  return <>
    {horses.map(h => <div key={h.horseId} className="form-group"><label htmlFor={`care-${h.horseId}`}>{h.name} · chip {h.microchip}: kết quả chăm sóc</label>
      <textarea id={`care-${h.horseId}`} className="form-control" value={records[h.horseId]?.note ?? ''} onChange={e => setRecords(r => ({ ...r, [h.horseId]: { temperature: r[h.horseId]?.temperature ?? '', note: e.target.value } }))} />
      <label htmlFor={`temp-${h.horseId}`}>Thân nhiệt (°C, nếu đo)</label><input id={`temp-${h.horseId}`} type="number" min="0" max="60" step="0.1" className="form-control" value={records[h.horseId]?.temperature ?? ''} onChange={e => setRecords(r => ({ ...r, [h.horseId]: { note: r[h.horseId]?.note ?? '', temperature: e.target.value } }))} />
    </div>)}
    <div className="form-group"><label htmlFor="sos-water">Nước đã cấp (lít)</label><input id="sos-water" type="number" min="0" step="0.1" className="form-control" value={water} onChange={e => setWater(e.target.value)} /><label><input type="checkbox" checked={hay} onChange={e => setHay(e.target.checked)} /> Đã cấp cỏ</label></div>
    <CaptureField purpose="SUBMIT_WELFARE" target={`sos-${Number(inc.id.replace('INC-', ''))}`} label="Ảnh kết quả chăm sóc tại trạm SOS" required value={photo} onChange={setPhoto} />
    <button className={`btn btn-primary ${s.big}`} disabled={!ready} onClick={() => sosRest(b, inc, now).done ? save(false) : setAskEarly(true)}>Lưu kết quả chăm sóc và xác nhận đủ sức</button>
    {askEarly && (() => { const rest = sosRest(b, inc, now); return (
      <EarlyConfirmModal title="Xác nhận ngựa đủ sức sớm?" confirmLabel="Xác nhận đủ sức" onClose={() => setAskEarly(false)}
        message={<>Ngựa mới nghỉ <b>{rest.rested} phút</b> ở trạm SOS, phương án đã duyệt là <b>{rest.planned} phút</b>. Chỉ xác nhận khi ngựa đã thật sự ổn định.</>}
        onConfirm={() => save(true).then(() => setAskEarly(false))} />
    ) })()}
  </>
}

// Thẻ sự cố đang mở của xe: thay cho các bước hành trình cho đến khi xử lý xong
// Làm sớm hơn giờ đã duyệt (rời trạm, xác nhận đủ sức): không bắt kíp xe chờ, nhưng hỏi lại bằng form
function EarlyConfirmModal({ title, message, confirmLabel, withNote, onClose, onConfirm }: { title: string; message: React.ReactNode; confirmLabel: string; withNote?: boolean; onClose: () => void; onConfirm: (note: string) => Promise<void> }) {
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)
  return (
    <Modal title={title} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose} disabled={busy}>Ở lại nghỉ tiếp</button>
      <button className="btn btn-primary" disabled={busy} onClick={async () => { setBusy(true); await onConfirm(note.trim()); setBusy(false) }}>{confirmLabel}</button>
    </>}>
      <p style={{ marginTop: 0 }}>{message}</p>
      {withNote && <div className="form-group" style={{ margin: 0 }}>
        <label htmlFor="early-note">Ghi chú</label>
        <textarea id="early-note" className="form-control" rows={3} value={note} onChange={e => setNote(e.target.value)} placeholder="VD: Ngựa đã ổn định, hộ tống đồng ý đi tiếp" />
      </div>}
    </Modal>
  )
}

// Ngựa đã nghỉ bao nhiêu phút ở trạm SOS so với phương án đã duyệt
function sosRest(b: Booking, inc: Incident, now: number) {
  const checkpointId = `sos-${Number(inc.id.replace('INC-', ''))}`
  const checkpoint = b.trips?.find(t => t.tripId === inc.tripId)?.run?.checkpoints.find(cp => cp.id === checkpointId)
  const planned = inc.plan?.restMinutes ?? 30
  const rested = checkpoint?.arrivedAt ? Math.max(0, Math.floor((now - checkpoint.arrivedAt) / 60000)) : 0
  return { planned, rested, done: !!checkpoint?.arrivedAt && rested >= planned }
}

export function IncidentPanel({ b, inc, role, run }: { b: Booking; inc: Incident; role: 'driver' | 'escort'; run: Run }) {
  const { session } = useAuth()
  const spent = inc.expenses.reduce((n, e) => n + e.amount, 0)
  const needFit = needsFitCheck(inc.kind)
  const now = useNow(1000)
  const [arrivalPhoto, setArrivalPhoto] = useState<string>()
  const [askEarly, setAskEarly] = useState(false)
  const health = inc.kind === 'horse_health'
  const checkpointId = `sos-${Number(inc.id.replace('INC-', ''))}`
  const checkpoint = b.trips?.find(t => t.tripId === inc.tripId)?.run?.checkpoints.find(cp => cp.id === checkpointId)
  const atStation = !health || !!checkpoint?.arrivedAt
  const leaveAt = checkpoint?.arrivedAt ? checkpoint.arrivedAt + (inc.plan?.restMinutes ?? 30) * 60_000 : undefined
  const restReady = !health || (leaveAt != null && now >= leaveAt)
  return (
    <>
      <Panel title={`Sự cố: ${INCIDENT_KIND[inc.kind].label}`} icon="fa-triangle-exclamation" tone="warn">
        <p>Báo lúc {formatDateTime(inc.reportedAt)}{inc.note ? `: ${inc.note}` : ''}. {inc.status === 'active' ? 'Thực hiện phương án đã duyệt, giữ nguyên xe và cửa khẩu.' : 'Chờ phương án được duyệt; không tự đổi cửa khẩu hay xe.'}</p>
        {inc.status === 'reported' && <p>{inc.rejection ? <><b>Quản lý trả phương án về:</b> {inc.rejection.reason}. Điều phối viên đang lập lại.</> : 'Điều phối viên đang lập phương án.'}</p>}
        {inc.status === 'pending_approval' && <p>Phương án đã gửi, chờ Quản lý duyệt.</p>}
        {inc.status === 'active' && inc.plan && inc.approval && (
          <>
          <IncidentPlanView b={b} i={inc} height={300} />
          <p><b>Đã duyệt:</b> {INCIDENT_ACTION[inc.plan.action]}. ETA mới {formatDateTime(inc.plan.newEta)}.{inc.plan.note ? ` Ghi chú: ${inc.plan.note}` : ''}</p>
          {inc.directive && <p style={{ marginTop: 6, color: 'var(--primary, #0284c7)' }}><b>Chỉ đạo từ Quản lý:</b> {inc.directive}</p>}
          </>
        )}
      </Panel>
      {inc.status === 'active' && health && !atStation && <Panel title="Tới trạm chăm sóc SOS" icon="fa-location-dot" tone="warn">
        {!checkpoint ? <p>Phương án cũ chưa có mốc trạm SOS. Điều phối viên cần lập lại phương án để quản lý duyệt.</p> : <>
          <p>Đi tới {inc.plan?.station}. Xác nhận tới trạm trước khi ghi kết quả chăm sóc.</p>
          {role === 'driver' ? <><CaptureField purpose="ARRIVE_CHECKPOINT" target={checkpointId} label="Ảnh đã tới trạm SOS" required value={arrivalPhoto} onChange={setArrivalPhoto} /><button className={`btn btn-primary ${s.big}`} disabled={!arrivalPhoto} onClick={() => run(() => bookingsApi.arriveCheckpoint(b.id, inc.tripId, session!.name, checkpointId, arrivalPhoto!), 'Đã tới trạm SOS')}>Xác nhận đã tới trạm SOS</button></> : <p>Chờ tài xế xác nhận đã tới trạm.</p>}
        </>}
      </Panel>}
      {inc.status === 'active' && atStation && (
        <>
          <Panel title="Chi phí tại chỗ" icon="fa-receipt">
            <p>Đã chi {formatVND(spent)}. Chụp hóa đơn trước, số tiền mở khóa sau.</p>
            <ul className={s.checklist}>{inc.expenses.map(e => <li key={e.id} style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '6px 0', fontSize: '0.9rem' }}><ImageThumb name={e.photo} size={40} /><span>{EXPENSE_CATEGORY[e.category]}: {e.label} — <b>{formatVND(e.amount)}</b></span></li>)}</ul>
            <ExpenseForm b={b} inc={inc} run={run} />
          </Panel>
          {needFit && (
            <Panel title="Ngựa đủ sức đi tiếp" icon="fa-heart-pulse" tone={inc.fitConfirmedAt ? 'ok' : 'warn'}>
              <p>{inc.fitConfirmedAt ? 'Hộ tống đã xác nhận ngựa đủ sức đi tiếp.' : role === 'escort' ? 'Chỉ hộ tống khám và xác nhận được. Bấm khi ngựa đã ổn định.' : 'Chờ hộ tống khám và xác nhận ngựa đủ sức.'}</p>
              {role === 'escort' && !inc.fitConfirmedAt && health && <HealthCareForm b={b} inc={inc} run={run} />}
              {role === 'escort' && !inc.fitConfirmedAt && !health && <button className={`btn btn-primary ${s.big}`} onClick={() => run(() => bookingsApi.confirmFit(b.id, inc.id, session!.name), 'Đã xác nhận ngựa đủ sức')}>Xác nhận ngựa đủ sức đi tiếp</button>}
            </Panel>
          )}
          {health && leaveAt && !restReady && <p role="status">Nghỉ tại trạm SOS đến {formatDateTime(leaveAt)}; còn {Math.ceil((leaveAt - now) / 60000)} phút.</p>}
          {role === 'driver' && (
            <button className={`btn btn-primary ${s.big}`} disabled={needFit && !inc.fitConfirmedAt} onClick={() => restReady ? run(() => bookingsApi.resumeJourney(b.id, inc.id, session!.name), 'Tiếp tục theo phương án đã duyệt') : setAskEarly(true)}><i className="fa-solid fa-truck-fast" /> Tiếp tục theo phương án đã duyệt</button>
          )}
          {askEarly && checkpoint?.arrivedAt && (() => { const rest = sosRest(b, inc, now); return (
            <EarlyConfirmModal title="Rời trạm SOS sớm?" withNote confirmLabel="Xác nhận rời trạm" onClose={() => setAskEarly(false)}
              message={<>Ngựa mới nghỉ <b>{rest.rested} phút</b>, phương án đã duyệt là <b>{rest.planned} phút</b>. Hộ tống đã xác nhận ngựa đủ sức. Hệ thống ghi lại số phút đã nghỉ và ghi chú của bạn.</>}
              onConfirm={note => run(() => bookingsApi.resumeJourney(b.id, inc.id, session!.name, { note }), 'Đã rời trạm SOS sớm, tiếp tục hành trình').then(() => setAskEarly(false))} />
          ) })()}
        </>
      )}
    </>
  )
}

// Driver: sau khi giao, gửi bảng kê chi phí (Flow 6, bước 2)
export function SubmitExpensesPanel({ b, run }: { b: Booking; run: Run }) {
  const { session } = useAuth()
  const list = (b.incidents ?? []).flatMap(i => i.expenses)
  const total = list.reduce((n, e) => n + e.amount, 0)
  return (
    <Panel title="Gửi bảng kê chi phí" icon="fa-receipt" tone="warn">
      <p>{list.length ? `Có ${list.length} khoản chi do sự cố, tổng ${formatVND(total)}, đều có ảnh chứng từ.` : 'Chuyến không có chi phí sự cố. Giá đã cố định nên không cần hóa đơn nhiên liệu.'} Bấm gửi để Quản lý đối soát và phát hành quyết toán.</p>
      <button className={`btn btn-primary ${s.big}`} onClick={() => run(() => bookingsApi.submitExpenses(b.id, session!.name), 'Đã gửi bảng kê chi phí')}>Gửi bảng kê chi phí</button>
    </Panel>
  )
}

// Tuyến được duyệt tiếp tục có hiệu lực sau khi đóng SOS.
export function ApprovedIncidentRoute({ b, t }: { b: Booking; t: VehicleTrip }) {
  const route = t.incidentRoute
  if (!route) return null
  const incident = b.incidents?.find(i => Number(i.id.replace('INC-', '')) === route.incidentId)
  // Sự cố còn đang xử lý thì panel sự cố đã vẽ phương án này, không vẽ lặp bản đồ thứ hai
  if (incident && incident.status !== 'resolved') return null
  return <Panel title="Lộ trình điều chỉnh đã duyệt" icon="fa-route">
    <p>Giờ đến đích mới: {formatDateTime(route.newEta)}. {route.note}</p>
    {incident && <IncidentPlanView b={b} i={{ ...incident, plan: route }} height={300} />}
  </Panel>
}
