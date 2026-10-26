// Việc của Tài xế theo từng mốc hành trình (Flow 4, PRD mục 5): check-in kèm ảnh chụp trực tiếp, thu và trả bản gốc, ký biên bản.
import { ReadMore } from '@shared/ui/ReadMore'
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { openIncidentOf, currentCheckpoint, manifestDocuments, pendingDeparture, spotLabel } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, Checkpoint, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { HorseDocsModal } from '../../shared/HorseDocsModal'
import { Checklist, Panel } from '../../shared/field'
import { HomeReturnPanel } from '../../shared/HomeReturnPanel'
import { IncidentPanel, SosPanel, SubmitExpensesPanel } from '../../shared/IncidentField'
import s from '../../shared/field.module.css'

type Run = (fn: () => Promise<unknown>, msg: string) => Promise<void>

// Chụp ảnh rồi xác nhận một mốc
function ArriveStep({ title, icon, button, hint, run, action }: { title: string; icon: string; button: string; hint: string; run: Run; action: (photo: string) => Promise<unknown> }) {
  const [photo, setPhoto] = useState<string>()
  return (
    <Panel title={title} icon={icon} tone="warn">
      <p>{hint}</p>
      <CaptureField label="Ảnh chụp tại chỗ" required value={photo} onChange={setPhoto} />
      <button className={`btn btn-primary ${s.big}`} disabled={!photo} onClick={() => run(() => action(photo!), 'Đã xác nhận có mặt, khách và điều phối thấy ngay')}><i className="fa-solid fa-location-dot" /> {button}</button>
    </Panel>
  )
}

function PickupStep({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const pickup = t.run!.checkpoints[0]
  const horses = b.horses.filter(h => t.horseIds.includes(h.horseId) && h.status !== 'unfit')
  const unfitHorses = b.horses.filter(h => h.status === 'unfit')
  const originalsAll = manifestDocuments(b).originals
  const [photo, setPhoto] = useState<string | undefined>(pickup.handoverPhoto)
  const [showDocs, setShowDocs] = useState(false)
  const scanned = pickup.chips?.length ?? 0
  const paid = !!b.balance
  const horsesOk = horses.length === 0 || scanned >= horses.length
  const ready = paid && horsesOk && (pickup.originals?.length ?? 0) >= originalsAll.length && !!pickup.handoverPhoto
  return (
    <>
      <Panel title="Hộ tống quét microchip" icon="fa-wave-square" tone={horsesOk ? 'ok' : undefined}>
        <p>Hộ tống quét từng con trên xe này và đối soát với hộ chiếu ngựa. Đã quét <b>{scanned}/{horses.length}</b> ngựa.</p>
        <ul className={s.checklist}>{horses.map(h => <li key={h.horseId} style={{ padding: '6px 0', fontSize: '0.9rem' }}><i className={`fa-solid ${pickup.chips?.includes(h.microchip.toUpperCase()) ? 'fa-circle-check' : 'fa-circle'}`} style={{ color: pickup.chips?.includes(h.microchip.toUpperCase()) ? 'var(--green)' : 'var(--line)', marginRight: 8 }} />{h.name} · {h.microchip}</li>)}</ul>
        {unfitHorses.length > 0 && (
          <div style={{ marginTop: 10, padding: '8px 12px', background: 'var(--danger-soft, #fee2e2)', borderRadius: 'var(--radius)', fontSize: '0.88rem', color: 'var(--red)' }}>
            <i className="fa-solid fa-ban" /> Đã loại bỏ {unfitHorses.length} cá thể ngựa do ốm/sốt ({unfitHorses.map(h => h.name).join(', ')}). Chi phí đã được giảm trừ cho khách.
          </div>
        )}
      </Panel>
      <Panel title="Thu chứng từ gốc" icon="fa-folder-open">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, flexWrap: 'wrap', gap: 6 }}>
          <p style={{ margin: 0 }}>Kiểm tra và nhận đủ bản gốc từ người gửi. Tick từng giấy khi đã nhận.</p>
          {horses.length > 0 && (
            <button
              type="button"
              className="btn btn-outline btn-sm"
              style={{ fontSize: '0.8rem', padding: '3px 8px', display: 'inline-flex', alignItems: 'center', gap: 6, whiteSpace: 'nowrap' }}
              onClick={() => setShowDocs(true)}
              title="Đối chiếu bản điện tử đã được chuyên viên thẩm định"
            >
              <i className="fa-solid fa-file-shield" /> Xem hồ sơ điện tử
            </button>
          )}
        </div>
        <Checklist
          items={originalsAll}
          checked={pickup.originals ?? []}
          onChange={next => {
            const current = pickup.originals ?? []
            const isAdding = next.length > current.length
            const added = next.find(x => !current.includes(x))
            const removed = current.find(x => !next.includes(x))
            const msg = isAdding
              ? (added ? `Đã nhận chứng từ: ${added}` : 'Đã ghi nhận nhận chứng từ gốc')
              : (removed ? `Đã hủy nhận chứng từ: ${removed}` : 'Đã bỏ chọn chứng từ gốc')
            return run(() => bookingsApi.collectOriginals(b.id, t.tripId, session!.name, next), msg)
          }}
        />
      </Panel>
      {showDocs && <HorseDocsModal horses={horses} onClose={() => setShowDocs(false)} />}
      <Panel title="Ký biên bản giao nhận" icon="fa-file-signature">
        <CaptureField label="Ảnh biên bản có đủ chữ ký" required value={photo} onChange={setPhoto} />
        <button className="btn btn-outline" disabled={!photo || photo === pickup.handoverPhoto} onClick={() => run(() => bookingsApi.uploadHandover(b.id, t.tripId, session!.name, pickup.id, photo!), 'Đã tải ảnh biên bản')}><i className="fa-solid fa-upload" /> Tải ảnh biên bản</button>
      </Panel>
      <Panel title="Bắt đầu hành trình" icon="fa-truck-fast" tone={ready ? 'ok' : undefined}>
        <p>{ready ? 'Đủ điều kiện xuất phát. Khách sẽ thấy ngựa đã được tiếp nhận.' : !paid ? 'Khách chưa thanh toán 70% còn lại nên xe chưa được bắt đầu hành trình. Nhà xe đã nhắc khách.' : 'Cần quét đủ microchip, thu đủ chứng từ gốc và tải ảnh biên bản có chữ ký.'}</p>
        <button className={`btn btn-primary ${s.big}`} disabled={!ready} onClick={() => run(() => bookingsApi.startJourney(b.id, t.tripId, session!.name), 'Hành trình bắt đầu')}><i className="fa-solid fa-play" /> Bắt đầu hành trình</button>
      </Panel>
    </>
  )
}

import { backendDeliveryApi } from '@shared/api/delivery'
import { toNumericBookingId } from '@shared/api/bookings'
import { Modal } from '@shared/ui/Modal'

function TimeoutModal({ b, onClose, run }: { b: Booking; onClose: () => void; run: Run }) {
  const [reason, setReason] = useState('UNREACHABLE')
  const [action, setAction] = useState('STABLE_NEARBY')
  const [partnerName, setPartnerName] = useState('')
  const [partnerPhone, setPartnerPhone] = useState('')
  const [cost, setCost] = useState('1500000')
  const [note, setNote] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async () => {
    setBusy(true)
    try {
      await run(() => backendDeliveryApi.reportTimeout(toNumericBookingId(b.id), {
        timeoutReason: reason,
        selectedAction: action,
        partnerName: action === 'STABLE_NEARBY' ? partnerName.trim() || undefined : undefined,
        partnerPhone: action === 'STABLE_NEARBY' ? partnerPhone.trim() || undefined : undefined,
        estimatedCost: action === 'STABLE_NEARBY' ? Number(cost) || 0 : undefined,
        note: note.trim() || 'Quá hạn 2 giờ người nhận không tới nhận ngựa.',
      }), 'Đã ghi nhận ngoại lệ quá 2h và kích hoạt phương án an sinh')
      onClose()
    } catch {
      setBusy(false)
    }
  }

  return (
    <Modal onClose={onClose} title="Báo sự cố quá hạn 2h bàn giao" subtitle="Kích hoạt quy trình an sinh khẩn cấp khi người nhận không đến"
      footer={<><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-danger" disabled={busy} onClick={submit}>Xác nhận kích hoạt an sinh</button></>}>
      <div className="form-group">
        <label htmlFor="tr">Lý do quá hạn</label>
        <select id="tr" className="form-control" value={reason} onChange={e => setReason(e.target.value)}>
          <option value="UNREACHABLE">Mất liên lạc với người nhận (Không nghe máy)</option>
          <option value="DESTINATION_UNREADY">Cơ sở tiếp nhận / chuồng đích chưa sẵn sàng</option>
          <option value="CONSIGNEE_REJECTED">Người nhận từ chối nhận ngựa</option>
        </select>
      </div>
      <div className="form-group">
        <label htmlFor="ta">Phương án xử lý khẩn cấp</label>
        <select id="ta" className="form-control" value={action} onChange={e => setAction(e.target.value)}>
          <option value="STABLE_NEARBY">Thuê chuồng / đưa ngựa vào trạm nghỉ gần nhất</option>
          <option value="RETURN_TO_HQ">Quay xe chuyển ngựa về Trụ sở chính (Việt Nam)</option>
        </select>
      </div>
      {action === 'STABLE_NEARBY' && (
        <>
          <div className="form-group">
            <label htmlFor="pn">Tên trạm nghỉ / chuồng đệm</label>
            <input id="pn" className="form-control" placeholder="VD: Trại ngựa tạm Đức Hòa" value={partnerName} onChange={e => setPartnerName(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="pp">Số điện thoại liên hệ</label>
            <input id="pp" className="form-control" placeholder="VD: 0901 234 567" value={partnerPhone} onChange={e => setPartnerPhone(e.target.value)} />
          </div>
          <div className="form-group">
            <label htmlFor="pc">Chi phí lưu chuồng ước tính (VNĐ)</label>
            <input id="pc" inputMode="numeric" className="form-control" value={cost} onChange={e => setCost(e.target.value.replace(/\D/g, ''))} />
          </div>
        </>
      )}
      <div className="form-group">
        <label htmlFor="tn">Ghi chú hiện trường</label>
        <textarea id="tn" className="form-control" rows={3} placeholder="Mô tả tình hình thực tế tại điểm giao..." value={note} onChange={e => setNote(e.target.value)} />
      </div>
    </Modal>
  )
}

function DeliveryStep({ b, t, cp, run }: { b: Booking; t: VehicleTrip; cp: Checkpoint; run: Run }) {
  const { session } = useAuth()
  const [photo, setPhoto] = useState<string | undefined>(cp.handoverPhoto)
  const [savingReturned, setSavingReturned] = useState(false)
  const [showTimeout, setShowTimeout] = useState(false)
  const returned = !!cp.returnedOriginals
  const welfare = t.run!.welfare.some(w => w.checkpointId === cp.id)
  const ready = welfare && returned && !!cp.handoverPhoto
  return (
    <>
      <Panel title="Hộ tống kiểm tra lần cuối" icon="fa-heart-pulse" tone={welfare ? 'ok' : 'warn'}>
        <p>{welfare ? 'Hộ tống đã kiểm tra thể trạng ngựa và ghi nhật ký.' : 'Hộ tống đang hạ ngựa và kiểm tra thể trạng lần cuối. Chờ hộ tống ghi nhật ký.'}</p>
      </Panel>
      <Panel title="Trả chứng từ gốc và ký biên bản" icon="fa-file-signature">
        <label className={s.checklist} style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 48 }}><input type="checkbox" checked={returned} disabled={savingReturned} onChange={async e => {
          const next = e.target.checked
          setSavingReturned(true)
          try {
            await run(
              () => bookingsApi.setOriginalsReturned(b.id, t.tripId, session!.name, cp.id, next),
              next ? 'Đã lưu xác nhận trả chứng từ gốc' : 'Đã hủy xác nhận trả chứng từ gốc',
            )
          } finally {
            setSavingReturned(false)
          }
        }} style={{ width: 22, height: 22, accentColor: 'var(--orange)' }} /><span>Đã trả toàn bộ chứng từ gốc cho người nhận</span></label>
        <ReadMore text={'Dùng 02 bản in “Biên bản Bàn giao & Hoàn tất Chuyến đi”. Người nhận xác nhận đủ ngựa, thể trạng an toàn, đủ hồ sơ gốc. Hai bên ký, mỗi bên giữ 01 bản.'} />
        <CaptureField label="Ảnh biên bản có đủ chữ ký" required value={photo} onChange={setPhoto} />
      <button className="btn btn-outline" disabled={!photo || photo === cp.handoverPhoto} onClick={() => run(() => bookingsApi.uploadHandover(b.id, t.tripId, session!.name, cp.id, photo!), 'Đã tải ảnh biên bản')}><i className="fa-solid fa-upload" /> Tải ảnh biên bản</button>
      </Panel>
      <button className={`btn btn-primary ${s.big}`} disabled={!ready} onClick={() => run(() => bookingsApi.completeDelivery(b.id, t.tripId, session!.name), 'Đã giao ngựa. Khách nhận thông báo')}><i className="fa-solid fa-flag-checkered" /> Hoàn tất giao ngựa</button>
      <button type="button" className="btn btn-outline btn-full" style={{ marginTop: 10, borderColor: 'var(--orange)', color: 'var(--orange)' }} onClick={() => setShowTimeout(true)}><i className="fa-solid fa-clock-rotate-left" /> Quá hạn 2 giờ: Báo sự cố không nhận ngựa</button>
      {showTimeout && <TimeoutModal b={b} onClose={() => setShowTimeout(false)} run={run} />}
    </>
  )
}


function CustomsStep({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const [hc, setHc] = useState<string>()
  const [ata, setAta] = useState<string>()
  const needAta = !!b.clearance?.items.some(i => i.type === 'ata_carnet')
  const photos = [hc, needAta ? ata : 'n/a'].filter(Boolean) as string[]
  return (
    <Panel title="Hoàn tất thông quan" icon="fa-stamp" tone="warn">
      <p>Sau khi Thú y và Hải quan đóng dấu, chụp trang mộc đỏ để làm bằng chứng.</p>
      <CaptureField label="Ảnh trang mộc đỏ kiểm dịch trên Health Cert" required value={hc} onChange={setHc} />
      {needAta && <CaptureField label="Ảnh cuống sổ ATA Carnet có dấu Hải quan" required value={ata} onChange={setAta} />}
      <button className={`btn btn-primary ${s.big}`} disabled={!hc || (needAta && !ata)} onClick={() => run(() => bookingsApi.customsCleared(b.id, t.tripId, session!.name, photos.filter(p => p !== 'n/a')), 'Đã thông quan. Khách nhận thông báo')}><i className="fa-solid fa-circle-check" /> Đã thông quan thành công</button>
    </Panel>
  )
}

export function DriverJob({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const inc = openIncidentOf(b, t.tripId)
  if (inc) return <IncidentPanel b={b} inc={inc} role="driver" run={run} />
  const atDelivery = !!t.run?.checkpoints.some(cp => cp.type === 'delivery' && cp.arrivedAt)
  return (
    <>
      {t.run?.startedAt && !t.run.deliveredAt && <div className="alert alert-info" style={{ marginBottom: 12 }}><i className="fa-solid fa-truck-fast" /><div><b>{spotLabel(t)}</b></div></div>}
      <DriverSteps b={b} t={t} run={run} />
      {!atDelivery && !t.run?.deliveredAt && (b.status === 'in_transit' || t.run?.startedAt) && <SosPanel b={b} t={t} run={run} />}
      {b.status === 'delivered_pending_settlement' && <SubmitExpensesPanel b={b} run={run} />}
    </>
  )
}

function DriverSteps({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const cp = currentCheckpoint(t)
  const pickup = t.run?.checkpoints[0]

  if (t.run?.deliveredAt) return (
    <>
      <Panel title="Đã giao ngựa" icon="fa-flag-checkered" tone="ok"><p>Giao xong lúc {formatDateTime(t.run.deliveredAt)}. </p></Panel>
      <HomeReturnPanel b={b} t={t} role="driver" run={run} />
    </>
  )

  if (t.departedAt && !t.run?.startedAt) {
    if (!pickup?.arrivedAt) return <ArriveStep key="pickup" title="Xác nhận có mặt tại điểm đón" icon="fa-location-dot" button="Đã tới điểm đón" hint={`Khi tới trang trại, chụp ảnh cổng hoặc khu chuồng. Dự kiến ${b.route ? formatDateTime(b.route.legs[0].departAt) : 'đúng hẹn'}.`} run={run} action={photo => bookingsApi.arriveAtPickup(b.id, t.tripId, session!.name, photo)} />
    return <PickupStep b={b} t={t} run={run} />
  }

  if (t.run?.startedAt && pendingDeparture(t)) return (
    <Panel title="Đã thông quan, xe còn ở cửa khẩu" icon="fa-flag" tone="ok">
      <p>Khi Escort và xe sẵn sàng, bấm tiếp tục để rời cửa khẩu. Khách và Điều phối viên sẽ thấy xe đang chạy tiếp.</p>
      <button className={`btn btn-primary ${s.big}`} onClick={() => run(() => bookingsApi.continueJourney(b.id, t.tripId, session!.name), 'Tiếp tục hành trình')}><i className="fa-solid fa-play" /> Rời cửa khẩu, tiếp tục hành trình</button>
    </Panel>
  )

  if (t.run?.startedAt && cp) {
    const arrive = (title: string, button: string, hint: string, icon: string) => <ArriveStep key={cp.id} title={title} icon={icon} button={button} hint={hint} run={run} action={photo => bookingsApi.arriveCheckpoint(b.id, t.tripId, session!.name, cp.id, photo)} />
    if (cp.type === 'rest') {
      if (!cp.arrivedAt) return arrive(`Tới ${cp.place}`, 'Xác nhận đã tới trạm nghỉ', 'Chụp rõ biển hiệu trạm nghỉ hoặc cây xăng. Hệ thống tự gắn giờ vào ảnh.', 'fa-location-dot')
      const ok = t.run.welfare.some(w => w.checkpointId === cp.id)
      return (
        <Panel title="Đang dừng ở trạm nghỉ" icon="fa-location-dot" tone={ok ? 'ok' : 'warn'}>
          <p>{ok ? 'Hộ tống đã gửi nhật ký an sinh. Hết giờ nghỉ, bấm tiếp tục.' : 'Chờ hộ tống kiểm tra ngựa và gửi nhật ký an sinh.'}</p>
          <button className={`btn btn-primary ${s.big}`} disabled={!ok} onClick={() => run(() => bookingsApi.continueJourney(b.id, t.tripId, session!.name), 'Tiếp tục hành trình')}><i className="fa-solid fa-play" /> Tiếp tục hành trình</button>
        </Panel>
      )
    }
    if (cp.type === 'border') return arrive('Tới cửa khẩu', 'Đã tới cửa khẩu', 'Chụp barie hoặc cổng trạm kiểm soát.', 'fa-flag')
    if (cp.type === 'customs') return <CustomsStep b={b} t={t} run={run} />
    if (cp.type === 'delivery') return cp.arrivedAt ? <DeliveryStep b={b} t={t} cp={cp} run={run} /> : arrive('Tới điểm giao', 'Đã tới điểm giao', 'Chụp ảnh cổng cơ sở tiếp nhận.', 'fa-flag-checkered')
  }
  return null
}
