// Việc của Tài xế theo từng mốc hành trình (Flow 4, PRD mục 5): check-in kèm ảnh chụp trực tiếp, thu và trả bản gốc, ký biên bản.
import { ReadMore } from '@shared/ui/ReadMore'
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { openIncidentOf, currentCheckpoint, manifestDocuments, pendingDeparture, spotLabel } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, Checkpoint, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { useToast } from '@shared/ui/toast'
import { Checklist, Panel } from '../../shared/field'
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
  const horses = b.horses.filter(h => t.horseIds.includes(h.horseId))
  const originalsAll = manifestDocuments(b).originals
  const [photo, setPhoto] = useState<string | undefined>(pickup.handoverPhoto)
  const scanned = pickup.chips?.length ?? 0
  const paid = !!b.balance
  const ready = paid && scanned >= horses.length && (pickup.originals?.length ?? 0) >= originalsAll.length && !!pickup.handoverPhoto
  return (
    <>
      <Panel title="Hộ tống quét microchip" icon="fa-wave-square" tone={scanned >= horses.length ? 'ok' : undefined}>
        <p>Hộ tống quét từng con trên xe này và đối soát với hộ chiếu ngựa. Đã quét <b>{scanned}/{horses.length}</b> ngựa.</p>
        <ul className={s.checklist}>{horses.map(h => <li key={h.horseId} style={{ padding: '6px 0', fontSize: '0.9rem' }}><i className={`fa-solid ${pickup.chips?.includes(h.microchip.toUpperCase()) ? 'fa-circle-check' : 'fa-circle'}`} style={{ color: pickup.chips?.includes(h.microchip.toUpperCase()) ? 'var(--green)' : 'var(--line)', marginRight: 8 }} />{h.name} · {h.microchip}</li>)}</ul>
      </Panel>
      <Panel title="Thu chứng từ gốc" icon="fa-folder-open">
        <p>Kiểm tra và nhận đủ bản gốc từ người gửi. Tick từng giấy khi đã nhận.</p>
        <Checklist items={originalsAll} checked={pickup.originals ?? []} onChange={next => run(() => bookingsApi.collectOriginals(b.id, t.tripId, session!.name, next), 'Đã ghi nhận chứng từ gốc')} />
      </Panel>
      <Panel title="Ký biên bản giao nhận" icon="fa-file-signature">
        <ReadMore text={'Dùng 02 bản in “Biên bản Giao nhận Động vật sống & Chứng từ gốc”. Người gửi và bạn cùng ký bút mực, mỗi bên giữ 01 bản, rồi chụp biên bản đã ký.'} />
        <CaptureField label="Ảnh biên bản có đủ chữ ký" required value={photo} onChange={setPhoto} />
        <button className="btn btn-outline" disabled={!photo || photo === pickup.handoverPhoto} onClick={() => run(() => bookingsApi.uploadHandover(b.id, t.tripId, session!.name, photo!), 'Đã tải ảnh biên bản')}><i className="fa-solid fa-upload" /> Tải ảnh biên bản</button>
      </Panel>
      <Panel title="Bắt đầu hành trình" icon="fa-truck-fast" tone={ready ? 'ok' : undefined}>
        <p>{ready ? 'Đủ điều kiện xuất phát. Khách sẽ thấy ngựa đã được tiếp nhận.' : !paid ? 'Khách chưa thanh toán 70% còn lại nên xe chưa được bắt đầu hành trình. Nhà xe đã nhắc khách.' : 'Cần quét đủ microchip, thu đủ chứng từ gốc và tải ảnh biên bản có chữ ký.'}</p>
        <button className={`btn btn-primary ${s.big}`} disabled={!ready} onClick={() => run(() => bookingsApi.startJourney(b.id, t.tripId, session!.name), 'Hành trình bắt đầu')}><i className="fa-solid fa-play" /> Bắt đầu hành trình</button>
      </Panel>
    </>
  )
}

function DeliveryStep({ b, t, cp, run }: { b: Booking; t: VehicleTrip; cp: Checkpoint; run: Run }) {
  const { session } = useAuth()
  const [photo, setPhoto] = useState<string | undefined>(cp.handoverPhoto)
  const [returned, setReturned] = useState(false)
  const welfare = t.run!.welfare.some(w => w.checkpointId === cp.id)
  const ready = welfare && returned && !!cp.handoverPhoto
  return (
    <>
      <Panel title="Hộ tống kiểm tra lần cuối" icon="fa-heart-pulse" tone={welfare ? 'ok' : 'warn'}>
        <p>{welfare ? 'Hộ tống đã kiểm tra thể trạng ngựa và ghi nhật ký.' : 'Hộ tống đang hạ ngựa và kiểm tra thể trạng lần cuối. Chờ hộ tống ghi nhật ký.'}</p>
      </Panel>
      <Panel title="Trả chứng từ gốc và ký biên bản" icon="fa-file-signature">
        <label className={s.checklist} style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 48 }}><input type="checkbox" checked={returned} onChange={e => setReturned(e.target.checked)} style={{ width: 22, height: 22, accentColor: 'var(--orange)' }} /><span>Đã trả toàn bộ chứng từ gốc cho người nhận</span></label>
        <ReadMore text={'Dùng 02 bản in “Biên bản Bàn giao & Hoàn tất Chuyến đi”. Người nhận xác nhận đủ ngựa, thể trạng an toàn, đủ hồ sơ gốc. Hai bên ký, mỗi bên giữ 01 bản.'} />
        <CaptureField label="Ảnh biên bản có đủ chữ ký" required value={photo} onChange={setPhoto} />
        <button className="btn btn-outline" disabled={!photo || photo === cp.handoverPhoto} onClick={() => run(() => bookingsApi.uploadHandover(b.id, t.tripId, session!.name, photo!), 'Đã tải ảnh biên bản')}><i className="fa-solid fa-upload" /> Tải ảnh biên bản</button>
      </Panel>
      <button className={`btn btn-primary ${s.big}`} disabled={!ready} onClick={() => run(() => bookingsApi.completeDelivery(b.id, t.tripId, session!.name), 'Đã giao ngựa. Khách nhận thông báo')}><i className="fa-solid fa-flag-checkered" /> Hoàn tất giao ngựa</button>
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

export function DriverJob({ b, t, reload }: { b: Booking; t: VehicleTrip; reload: () => void }) {
  const toast = useToast()
  const run: Run = async (fn, msg) => {
    try { await fn(); toast(msg); reload() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error') }
  }
  const inc = openIncidentOf(b, t.tripId)
  if (inc) return <IncidentPanel b={b} inc={inc} role="driver" run={run} />
  return (
    <>
      {t.run?.startedAt && !t.run.deliveredAt && <div className="alert alert-info" style={{ marginBottom: 12 }}><i className="fa-solid fa-truck-fast" /><div><b>{spotLabel(t)}</b></div></div>}
      <DriverSteps b={b} t={t} run={run} />
      {t.run?.startedAt && !t.run.deliveredAt && <SosPanel b={b} t={t} run={run} />}
      {b.status === 'delivered_pending_settlement' && <SubmitExpensesPanel b={b} run={run} />}
    </>
  )
}

function DriverSteps({ b, t, run }: { b: Booking; t: VehicleTrip; run: Run }) {
  const { session } = useAuth()
  const cp = currentCheckpoint(t)
  const pickup = t.run?.checkpoints[0]

  if (t.run?.deliveredAt) return <Panel title="Đã giao ngựa" icon="fa-flag-checkered" tone="ok"><p>Giao xong lúc {formatDateTime(t.run.deliveredAt)}. </p></Panel>

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
    const arrive = (title: string, button: string, hint: string, icon: string) => <ArriveStep key={cp.id} title={title} icon={icon} button={button} hint={hint} run={run} action={photo => bookingsApi.arriveCheckpoint(b.id, t.tripId, session!.name, photo)} />
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
