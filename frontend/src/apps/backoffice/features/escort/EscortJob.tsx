// Việc của Hộ tống theo từng mốc (Flow 4, PRD mục 5): quét microchip tại điểm đón, nhật ký an sinh tại trạm nghỉ và điểm giao.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { WELFARE_CONDITION, type WelfareCondition } from '@shared/config/booking-rules'
import { openIncidentOf, currentCheckpoint } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, Checkpoint, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { useToast } from '@shared/ui/toast'
import { Panel } from '../../shared/field'
import { IncidentPanel, SosPanel } from '../../shared/IncidentField'
import s from '../../shared/field.module.css'

type Run = (fn: () => Promise<unknown>, msg: string) => Promise<void>

function ScanStep({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const [chip, setChip] = useState('')
  const pickup = t.run!.checkpoints[0]
  const scanned = pickup.chips ?? []
  const horses = b.horses.filter(h => t.horseIds.includes(h.horseId))
  const all = scanned.length >= horses.length
  return (
    <Panel title="Quét microchip" icon="fa-wave-square" tone={all ? 'ok' : 'warn'}>
      <p>Quét máy cầm tay vào cổ từng con, nhập số hiển thị và đối soát với Hộ chiếu, Health Cert. Đã quét <b>{scanned.length}/{horses.length}</b>.</p>
      <ul>{horses.map(h => { const ok = scanned.includes(h.microchip.toUpperCase()); return <li key={h.horseId} style={{ display: 'flex', gap: 10, padding: '8px 0', fontSize: '0.92rem' }}><i className={`fa-solid ${ok ? 'fa-circle-check' : 'fa-circle'}`} style={{ color: ok ? 'var(--green)' : 'var(--line)', marginTop: 4 }} /><span><b>{h.name}</b>{ok && <span className="sub-text"> · {h.microchip}</span>}</span></li> })}</ul>
      {!all && (
        <form style={{ display: 'grid', gap: 10 }} onSubmit={e => { e.preventDefault(); if (chip.trim()) run(() => bookingsApi.scanChip(b.id, t.tripId, session!.name, chip), 'Microchip khớp').then(() => setChip('')) }}>
          <label htmlFor="chip" className="font-semibold" style={{ fontSize: '0.88rem' }}>Số microchip trên máy quét</label>
          <input id="chip" className="form-control" style={{ minHeight: 52, fontSize: '1.05rem' }} value={chip} onChange={e => setChip(e.target.value)} placeholder="VD: VN-985211" autoComplete="off" />
          <button type="submit" className={`btn btn-primary ${s.big}`} disabled={!chip.trim()}><i className="fa-solid fa-wave-square" /> Ghi nhận</button>
        </form>
      )}
    </Panel>
  )
}

function WelfareForm({ b, t, cp, run }: { b: Booking; t: VehicleTrip; cp: Checkpoint; run: Run }) {
  const { session } = useAuth()
  const final = cp.type === 'delivery'
  const [condition, setCondition] = useState<WelfareCondition>('normal')
  const [water, setWater] = useState('8')
  const [hay, setHay] = useState(true)
  const [photo, setPhoto] = useState<string>()
  const [note, setNote] = useState('')
  const valid = !!photo && water !== ''
  return (
    <Panel title={final ? 'Kiểm tra thể trạng lần cuối' : `Nhật ký an sinh · ${cp.place}`} icon="fa-heart-pulse" tone="warn">
      <p>{final ? 'Hạ ngựa an toàn, kiểm tra lần cuối cùng người nhận.' : 'Trong lúc xe dừng ở trạm nghỉ, kiểm tra ngựa và ghi nhật ký.'}</p>
      <div role="radiogroup" aria-label="Thể trạng" style={{ display: 'grid', gap: 8 }}>
        {(Object.keys(WELFARE_CONDITION) as WelfareCondition[]).map(k => (
          <label key={k} style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 52, padding: '0 14px', border: `1.5px solid ${condition === k ? 'var(--orange)' : 'var(--line)'}`, borderRadius: 'var(--radius)', background: condition === k ? 'var(--orange-soft)' : 'white', cursor: 'pointer', fontWeight: 600 }}>
            <input type="radio" name="cond" checked={condition === k} onChange={() => setCondition(k)} style={{ width: 20, height: 20, accentColor: 'var(--orange)' }} />{WELFARE_CONDITION[k].label}
          </label>
        ))}
      </div>
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="water">Nước đã cấp (lít, ước tính)</label><input id="water" inputMode="decimal" className="form-control" style={{ minHeight: 48 }} value={water} onChange={e => setWater(e.target.value.replace(/[^\d.]/g, ''))} /></div>
      <label style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 48 }}><input type="checkbox" checked={hay} onChange={e => setHay(e.target.checked)} style={{ width: 22, height: 22, accentColor: 'var(--orange)' }} />Đã bổ sung cỏ khô</label>
      <CaptureField label="Ảnh ngựa trong khoang" required value={photo} onChange={setPhoto} />
      <div className="form-group" style={{ margin: 0 }}><label htmlFor="wnote">Ghi chú</label><input id="wnote" className="form-control" style={{ minHeight: 48 }} value={note} onChange={e => setNote(e.target.value)} placeholder="VD: ngựa đổ mồ hôi nhẹ, đã xịt nước" /></div>
      <button className={`btn btn-primary ${s.big}`} disabled={!valid} onClick={() => run(() => bookingsApi.submitWelfare(b.id, t.tripId, session!.name, { condition, waterLiters: Number(water), hay, photo: photo!, note: note.trim() }), 'Đã gửi nhật ký an sinh')}><i className="fa-solid fa-paper-plane" /> Gửi nhật ký an sinh</button>
    </Panel>
  )
}

export function EscortJob({ b, t, reload }: { b: Booking; t: VehicleTrip; reload: () => void }) {
  const toast = useToast()
  const run: Run = async (fn, msg) => {
    try { await fn(); toast(msg); reload() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error') }
  }
  const inc = openIncidentOf(b, t.tripId)
  if (inc) return <IncidentPanel b={b} inc={inc} role="escort" run={run} />
  return (
    <>
      <EscortSteps b={b} t={t} run={run} />
      {t.run?.startedAt && !t.run.deliveredAt && <SosPanel b={b} t={t} run={run} />}
    </>
  )
}

function EscortSteps({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const cp = currentCheckpoint(t)
  const pickup = t.run?.checkpoints[0]

  if (t.run?.deliveredAt) return <Panel title="Đã giao ngựa" icon="fa-flag-checkered" tone="ok"><p>Chuyến hoàn tất lúc {formatDateTime(t.run.deliveredAt)}. Cảm ơn bạn.</p></Panel>
  if (t.departedAt && !t.run?.startedAt) {
    return pickup?.arrivedAt ? <ScanStep key={pickup.chips?.length ?? 0} b={b} t={t} run={run} /> : <Panel title="Xe đang đến điểm đón" icon="fa-truck-moving" tone="warn"><p>Khi tài xế xác nhận có mặt tại điểm đón, bạn sẽ quét microchip từng con trên xe này và đối soát với hộ chiếu ngựa.</p></Panel>
  }
  if (t.run?.startedAt && cp) {
    const logged = t.run.welfare.some(w => w.checkpointId === cp.id)
    if ((cp.type === 'rest' || cp.type === 'delivery') && cp.arrivedAt && !logged) return <WelfareForm key={cp.id} b={b} t={t} cp={cp} run={run} />
    if ((cp.type === 'rest' || cp.type === 'delivery') && cp.arrivedAt && logged) return <Panel title="Đã gửi nhật ký an sinh" icon="fa-circle-check" tone="ok"><p>{cp.type === 'rest' ? 'Chờ tài xế bấm tiếp tục hành trình.' : 'Chờ tài xế ký biên bản bàn giao và hoàn tất giao ngựa.'}</p></Panel>
    return <Panel title="Đang di chuyển" icon="fa-truck-fast" tone="warn"><p>Mốc tiếp theo: <b>{cp.label.toLowerCase()}</b> tại {cp.place}, dự kiến {formatDateTime(cp.plannedAt)}. Theo dõi ngựa và sẵn sàng ghi nhật ký khi xe dừng.</p></Panel>
  }
  return null
}
