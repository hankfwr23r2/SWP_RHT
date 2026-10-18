// Việc của Hộ tống theo từng mốc (Flow 4, PRD mục 5): quét microchip tại điểm đón, nhật ký an sinh tại trạm nghỉ và điểm giao.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { WELFARE_CONDITION, type WelfareCondition } from '@shared/config/booking-rules'
import { openIncidentOf, currentCheckpoint } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, BookingHorse, Checkpoint, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { useToast } from '@shared/ui/toast'
import { Panel } from '../../shared/field'
import { HomeReturnPanel } from '../../shared/HomeReturnPanel'
import { IncidentPanel, SosPanel } from '../../shared/IncidentField'
import s from '../../shared/field.module.css'

import { Modal } from '@shared/ui/Modal'
import { formatVND } from '@shared/lib/format'
import { calculateHorseExclusionDiscount } from '@shared/lib/pricing'

type Run = (fn: () => Promise<unknown>, msg: string) => Promise<void>

function UnfitHorseModal({
  b,
  t,
  horse,
  onClose,
  run,
}: {
  b: Booking
  t: VehicleTrip
  horse: BookingHorse
  onClose: () => void
  run: Run
}) {
  const { session } = useAuth()
  const [reason, setReason] = useState('Sốt cao / Viêm nhiễm cấp tính')
  const [temperature, setTemperature] = useState('39.5')
  const [symptoms, setSymptoms] = useState('Ngựa sốt cao, ủ rũ, mắt đỏ, không đủ thể trạng di chuyển đường dài')
  const [photo, setPhoto] = useState<string>()
  const [busy, setBusy] = useState(false)

  const activeCount = b.horses.filter(h => h.status !== 'unfit').length
  const discount = calculateHorseExclusionDiscount(b.quote?.lines ?? [], activeCount)

  const submit = async () => {
    setBusy(true)
    try {
      await run(
        () =>
          bookingsApi.excludeUnfitHorse(b.id, t.tripId, session!.name, {
            horseId: horse.horseId,
            reason,
            symptoms: symptoms.trim(),
            photo: photo!,
            temperature: temperature ? Number(temperature) : undefined,
            discountAmount: discount,
          }),
        `Đã ghi nhận ngựa ${horse.name} ốm/sốt, loại bỏ khỏi chuyến và giảm trừ ${formatVND(discount)} cho khách`
      )
      const bookingNumId = toNumericBookingId(b.id)
      const hId = Number(horse.horseId.replace(/\D/g, ''))
      if (hId) {
        await healthLogsApi
          .create({
            horseId: hId,
            bookingId: bookingNumId,
            healthStatus: 'SỐT CAO / KHÔNG ĐỦ ĐIỀU KIỆN KHỞI HÀNH',
            temperature: temperature ? Number(temperature) : undefined,
            symptoms: `${reason}: ${symptoms.trim()}`,
          })
          .catch(() => null)
      }
      onClose()
    } catch {
      setBusy(false)
    }
  }

  return (
    <Modal
      onClose={onClose}
      title={`Báo ngựa không đủ điều kiện: ${horse.name}`}
      subtitle={`Microchip: ${horse.microchip} · Cá thể này sẽ bị giữ lại tại điểm đón`}
      footer={
        <>
          <button className="btn btn-ghost" onClick={onClose} disabled={busy}>
            Đóng
          </button>
          <button className="btn btn-danger" disabled={busy || !photo} onClick={submit}>
            {busy ? 'Đang xử lý...' : 'Xác nhận loại bỏ khỏi chuyến'}
          </button>
        </>
      }
    >
      <div style={{ display: 'grid', gap: 14 }}>
        <div style={{ padding: '10px 12px', background: 'var(--orange-soft, #fff7ed)', borderRadius: 'var(--radius)', border: '1px solid var(--orange)' }}>
          <p style={{ margin: 0, fontSize: '0.9rem', color: '#9a3412', fontWeight: 600 }}>
            <i className="fa-solid fa-triangle-exclamation" /> Luồng ngoại lệ an sinh (PRD mục 5): Cước xe giữ nguyên. Chi phí chăm sóc, khoang đơn, dinh dưỡng và kiểm dịch của cá thể này ({formatVND(discount)}) sẽ được khấu trừ vào số dư của khách hàng.
          </p>
        </div>

        <div className="form-group" style={{ margin: 0 }}>
          <label htmlFor="unfit-reason" className="required font-semibold" style={{ fontSize: '0.88rem' }}>
            Tình trạng phát hiện tại chỗ
          </label>
          <select id="unfit-reason" className="form-control" value={reason} onChange={e => setReason(e.target.value)}>
            <option value="Sốt cao / Viêm nhiễm cấp tính">Sốt cao / Viêm nhiễm cấp tính</option>
            <option value="Đau bụng / Chướng bụng (Colic)">Đau bụng / Chướng bụng (Colic)</option>
            <option value="Chấn thương chân / Không đứng vững">Chấn thương chân / Không đứng vững</option>
            <option value="Kiệt sức / Mất nước nghiêm trọng">Kiệt sức / Mất nước nghiêm trọng</option>
            <option value="Dấu hiệu bệnh truyền nhiễm">Dấu hiệu bệnh truyền nhiễm</option>
          </select>
        </div>

        <div className="form-group" style={{ margin: 0 }}>
          <label htmlFor="unfit-temp" className="font-semibold" style={{ fontSize: '0.88rem' }}>
            Thân nhiệt đo được (°C)
          </label>
          <input
            id="unfit-temp"
            type="number"
            step="0.1"
            className="form-control"
            value={temperature}
            onChange={e => setTemperature(e.target.value)}
            placeholder="VD: 39.5"
          />
        </div>

        <div className="form-group" style={{ margin: 0 }}>
          <label htmlFor="unfit-symptoms" className="font-semibold" style={{ fontSize: '0.88rem' }}>
            Triệu chứng lâm sàng quan sát được
          </label>
          <input
            id="unfit-symptoms"
            className="form-control"
            value={symptoms}
            onChange={e => setSymptoms(e.target.value)}
            placeholder="VD: Ngựa thở dốc, mắt lờ đờ, không chịu bước lên xe..."
          />
        </div>

        <CaptureField label="Ảnh chụp thể trạng cá thể ngựa tại chỗ" required value={photo} onChange={setPhoto} />
      </div>
    </Modal>
  )
}

function ScanStep({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const [chip, setChip] = useState('')
  const [unfitTarget, setUnfitTarget] = useState<BookingHorse | null>(null)
  const pickup = t.run!.checkpoints[0]
  const scanned = pickup.chips ?? []
  const activeHorses = b.horses.filter(h => t.horseIds.includes(h.horseId) && h.status !== 'unfit')
  const unfitHorses = b.horses.filter(h => h.status === 'unfit')
  const all = activeHorses.length > 0 && scanned.length >= activeHorses.length

  return (
    <>
      <Panel title="Quét microchip" icon="fa-wave-square" tone={all ? 'ok' : 'warn'}>
        <p>Quét máy cầm tay vào cổ từng con, nhập số hiển thị và đối soát với Hộ chiếu, Health Cert. Đã quét <b>{scanned.length}/{activeHorses.length}</b>.</p>
        <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
          {activeHorses.map(h => {
            const ok = scanned.includes(h.microchip.toUpperCase())
            return (
              <li key={h.horseId} style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 0', borderBottom: '1px solid var(--line-light, #eee)', fontSize: '0.92rem' }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <i className={`fa-solid ${ok ? 'fa-circle-check' : 'fa-circle'}`} style={{ color: ok ? 'var(--green)' : 'var(--line)' }} />
                  <span><b>{h.name}</b>{ok && <span className="sub-text"> · {h.microchip}</span>}</span>
                </span>
                {!ok && (
                  <button
                    type="button"
                    className="btn btn-outline btn-sm"
                    style={{ color: 'var(--red, #dc2626)', borderColor: 'var(--red, #dc2626)', padding: '3px 8px', fontSize: '0.82rem' }}
                    onClick={() => setUnfitTarget(h)}
                  >
                    <i className="fa-solid fa-stethoscope" /> Báo ốm/sốt
                  </button>
                )}
              </li>
            )
          })}
        </ul>

        {unfitHorses.length > 0 && (
          <div style={{ marginTop: 12, padding: '10px 12px', background: 'var(--danger-soft, #fee2e2)', borderRadius: 'var(--radius)', border: '1px solid var(--red, #dc2626)' }}>
            <p style={{ margin: '0 0 6px', fontWeight: 600, color: 'var(--red, #dc2626)', fontSize: '0.88rem' }}>
              <i className="fa-solid fa-ban" /> Ngựa không đủ điều kiện (đã loại khỏi chuyến):
            </p>
            <ul style={{ margin: 0, paddingLeft: 18, fontSize: '0.85rem', color: '#991b1b' }}>
              {unfitHorses.map(h => (
                <li key={h.horseId}>
                  <b>{h.name}</b> ({h.microchip}): {h.unfitInfo?.reason ?? 'Ốm/Sốt'}
                  {h.unfitInfo?.temperature ? ` · ${h.unfitInfo.temperature}°C` : ''}
                  {h.unfitInfo?.discountAmount ? ` · Giảm trừ: ${formatVND(h.unfitInfo.discountAmount)}` : ''}
                </li>
              ))}
            </ul>
          </div>
        )}

        {!all && activeHorses.length > 0 && (
          <form style={{ display: 'grid', gap: 10, marginTop: 12 }} onSubmit={e => { e.preventDefault(); if (chip.trim()) run(() => bookingsApi.scanChip(b.id, t.tripId, session!.name, chip), 'Microchip khớp').then(() => setChip('')) }}>
            <label htmlFor="chip" className="font-semibold" style={{ fontSize: '0.88rem' }}>Số microchip trên máy quét</label>
            <input id="chip" className="form-control" style={{ minHeight: 52, fontSize: '1.05rem' }} value={chip} onChange={e => setChip(e.target.value)} placeholder="VD: VN-985211" autoComplete="off" />
            <button type="submit" className={`btn btn-primary ${s.big}`} disabled={!chip.trim()}><i className="fa-solid fa-wave-square" /> Ghi nhận</button>
          </form>
        )}
      </Panel>

      {unfitTarget && (
        <UnfitHorseModal
          b={b}
          t={t}
          horse={unfitTarget}
          onClose={() => setUnfitTarget(null)}
          run={run}
        />
      )}
    </>
  )
}

import { healthLogsApi } from '@shared/api/healthLogs'
import { toNumericBookingId } from '@shared/api/bookings'

function WelfareForm({ b, t, cp, run }: { b: Booking; t: VehicleTrip; cp: Checkpoint; run: Run }) {
  const { session } = useAuth()
  const final = cp.type === 'delivery'
  const [condition, setCondition] = useState<WelfareCondition>('normal')
  const [water, setWater] = useState('8')
  const [hay, setHay] = useState(true)
  const [photo, setPhoto] = useState<string>()
  const [note, setNote] = useState('')
  const valid = !!photo && water !== ''

  const submit = async () => {
    // Ghi nhận nhật ký an sinh của chuyến đi
    await bookingsApi.submitWelfare(b.id, t.tripId, session!.name, { condition, waterLiters: Number(water), hay, photo: photo!, note: note.trim() })

    // Ghi nhận nhật ký sức khỏe từng cá thể ngựa vào bảng horse_health_logs (POST /api/v1/horse-health-logs)
    const tripHorses = b.horses.filter(h => t.horseIds.includes(h.horseId))
    const bookingNumId = toNumericBookingId(b.id)
    await Promise.all(tripHorses.map(h => {
      const hId = Number(h.horseId.replace(/\D/g, ''))
      if (!hId) return Promise.resolve(null)
      return healthLogsApi.create({
        horseId: hId,
        bookingId: bookingNumId,
        healthStatus: condition.toUpperCase(),
        symptoms: note.trim() || `Cấp nước ${water}L, cỏ: ${hay ? 'đủ' : 'chưa'}`,
      }).catch(() => null)
    }))
  }

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
      <button className={`btn btn-primary ${s.big}`} disabled={!valid} onClick={() => run(submit, 'Đã gửi nhật ký an sinh')}><i className="fa-solid fa-paper-plane" /> Gửi nhật ký an sinh</button>
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
      {(b.status === 'in_transit' || (t.run?.startedAt && !t.run.deliveredAt)) && <SosPanel b={b} t={t} run={run} />}
    </>
  )
}

function EscortSteps({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const cp = currentCheckpoint(t)
  const pickup = t.run?.checkpoints[0]

  if (t.run?.deliveredAt) return (
    <>
      <Panel title="Đã giao ngựa" icon="fa-flag-checkered" tone="ok"><p>Chuyến hoàn tất lúc {formatDateTime(t.run.deliveredAt)}. Cảm ơn bạn.</p></Panel>
      <HomeReturnPanel b={b} t={t} role="escort" run={run} />
    </>
  )
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
