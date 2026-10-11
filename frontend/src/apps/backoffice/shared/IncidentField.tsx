// Sự cố khẩn cấp và chi phí trên app Tài xế / Hộ tống (Flow 5, PRD mục 6; Flow 6, PRD mục 7.3).
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { IncidentPlanView } from './IncidentPlanView'
import { needsFitCheck } from '@shared/lib/booking'
import { EXPENSE_CATEGORY, INCIDENT_ACTION, INCIDENT_KIND, type ExpenseCategory, type IncidentKind } from '@shared/config/booking-rules'
import { formatDateTime, formatVND } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, Incident, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { ImageThumb } from '@shared/ui/ImageThumb'
import { Panel } from './field'
import s from './field.module.css'
import { FormSelect } from '@shared/ui/FormSelect'

export type Run = (fn: () => Promise<unknown>, msg: string) => Promise<void>

// Nút SOS: chọn nhóm sự cố, chụp ảnh hiện trường, gửi
export function SosPanel({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const [open, setOpen] = useState(false)
  const [kind, setKind] = useState<IncidentKind>('horse_health')
  const [photo, setPhoto] = useState<string>()
  const [note, setNote] = useState('')
  if (!open) return <button className={`btn btn-outline ${s.big}`} style={{ color: 'var(--red)', borderColor: 'var(--red)' }} onClick={() => setOpen(true)}><i className="fa-solid fa-triangle-exclamation" /> Báo sự cố khẩn cấp (SOS)</button>
  return (
    <Panel title="Báo sự cố khẩn cấp" icon="fa-triangle-exclamation" tone="warn">
      <div role="radiogroup" aria-label="Nhóm sự cố" style={{ display: 'grid', gap: 8 }}>
        {(Object.keys(INCIDENT_KIND) as IncidentKind[]).map(k => (
          <label key={k} style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 52, padding: '0 14px', border: `1.5px solid ${kind === k ? 'var(--orange)' : 'var(--line)'}`, borderRadius: 'var(--radius-sm)' }}>
            <input type="radio" name="kind" checked={kind === k} onChange={() => setKind(k)} style={{ width: 20, height: 20, accentColor: 'var(--orange)' }} />
            <span><b>{INCIDENT_KIND[k].label}</b><br /><small>{INCIDENT_KIND[k].hint}</small></span>
          </label>
        ))}
      </div>
      <CaptureField label="Ảnh hiện trường" required value={photo} onChange={setPhoto} />
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="sos-note">Ghi chú ngắn</label><input id="sos-note" className="form-control" style={{ minHeight: 48 }} value={note} onChange={e => setNote(e.target.value)} /></div>
      <div style={{ display: 'flex', gap: 8 }}>
        <button className="btn btn-ghost" onClick={() => setOpen(false)}>Hủy</button>
        <button className={`btn btn-primary ${s.big}`} disabled={!photo} onClick={() => run(() => bookingsApi.reportIncident(b.id, t.tripId, session!.name, kind, photo!, note), 'Đã gửi báo động. Quản lý và Điều phối viên nhận ngay')}>Gửi báo động khẩn cấp</button>
      </div>
    </Panel>
  )
}

function ExpenseForm({ b, inc, run }: { b: Booking; inc: Incident; run: Run }) {
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
      <CaptureField label="Ảnh hóa đơn / biên lai (chụp trước)" required value={photo} onChange={setPhoto} />
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="ex-label">Nội dung</label><input id="ex-label" className="form-control" style={{ minHeight: 48 }} value={label} onChange={e => setLabel(e.target.value)} /></div>
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="ex-amount">Số tiền trên hóa đơn (VNĐ){!photo && ' — chụp ảnh trước để mở khóa'}</label><input id="ex-amount" inputMode="numeric" className="form-control" style={{ minHeight: 48 }} disabled={!photo} value={amount} onChange={e => setAmount(e.target.value.replace(/\D/g, ''))} /></div>
      <button className={`btn btn-outline ${s.big}`} disabled={!photo || !Number(amount)} onClick={() => run(async () => { await bookingsApi.addIncidentExpense(b.id, inc.id, session!.name, { category, label, photo: photo!, amount: Number(amount) }); reset() }, 'Đã ghi chi phí kèm chứng từ')}>Thêm chi phí</button>
    </>
  )
}

// Thẻ sự cố đang mở của xe: thay cho các bước hành trình cho đến khi xử lý xong
export function IncidentPanel({ b, inc, role, run }: { b: Booking; inc: Incident; role: 'driver' | 'escort'; run: Run }) {
  const { session } = useAuth()
  const spent = inc.expenses.reduce((n, e) => n + e.amount, 0)
  const needFit = needsFitCheck(inc.kind)
  return (
    <>
      <Panel title={`Sự cố: ${INCIDENT_KIND[inc.kind].label}`} icon="fa-triangle-exclamation" tone="warn">
        <p>Báo lúc {formatDateTime(inc.reportedAt)}{inc.note ? `: ${inc.note}` : ''}. Giữ nguyên vị trí, không tự đổi cửa khẩu hay xe.</p>
        {inc.status === 'reported' && <p>{inc.rejection ? <><b>Quản lý trả phương án về:</b> {inc.rejection.reason}. Điều phối viên đang lập lại.</> : 'Điều phối viên đang lập phương án.'}</p>}
        {inc.status === 'pending_approval' && <p>Phương án đã gửi, chờ Quản lý duyệt.</p>}
        {inc.status === 'active' && inc.plan && inc.approval && (
          <>
          <IncidentPlanView b={b} i={inc} height={300} />
          <p><b>Đã duyệt:</b> {INCIDENT_ACTION[inc.plan.action]}. ETA mới {formatDateTime(inc.plan.newEta)}.{inc.plan.note ? ` Ghi chú: ${inc.plan.note}` : ''}</p>
          </>
        )}
      </Panel>
      {inc.status === 'active' && (
        <>
          <Panel title="Chi phí tại chỗ" icon="fa-receipt">
            <p>Đã chi {formatVND(spent)}. Chụp hóa đơn trước, số tiền mở khóa sau.</p>
            <ul className={s.checklist}>{inc.expenses.map(e => <li key={e.id} style={{ display: 'flex', gap: 10, alignItems: 'center', padding: '6px 0', fontSize: '0.9rem' }}><ImageThumb name={e.photo} size={40} /><span>{EXPENSE_CATEGORY[e.category]}: {e.label} — <b>{formatVND(e.amount)}</b></span></li>)}</ul>
            <ExpenseForm b={b} inc={inc} run={run} />
          </Panel>
          {needFit && (
            <Panel title="Ngựa đủ sức đi tiếp" icon="fa-heart-pulse" tone={inc.fitConfirmedAt ? 'ok' : 'warn'}>
              <p>{inc.fitConfirmedAt ? 'Hộ tống đã xác nhận ngựa đủ sức đi tiếp.' : role === 'escort' ? 'Chỉ hộ tống khám và xác nhận được. Bấm khi ngựa đã ổn định.' : 'Chờ hộ tống khám và xác nhận ngựa đủ sức.'}</p>
              {role === 'escort' && !inc.fitConfirmedAt && <button className={`btn btn-primary ${s.big}`} onClick={() => run(() => bookingsApi.confirmFit(b.id, inc.id, session!.name), 'Đã xác nhận ngựa đủ sức')}>Xác nhận ngựa đủ sức đi tiếp</button>}
            </Panel>
          )}
          {role === 'driver' && (
            <button className={`btn btn-primary ${s.big}`} disabled={needFit && !inc.fitConfirmedAt} onClick={() => run(() => bookingsApi.resumeJourney(b.id, inc.id, session!.name), 'Tiếp tục hành trình chính')}><i className="fa-solid fa-truck-fast" /> Tiếp tục hành trình chính</button>
          )}
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
