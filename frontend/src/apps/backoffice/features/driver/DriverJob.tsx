// Việc của Tài xế theo từng mốc hành trình (Flow 4, PRD mục 5): check-in kèm ảnh chụp trực tiếp, thu và trả bản gốc, ký biên bản.
import { ReadMore } from '@shared/ui/ReadMore'
import { useState, useEffect } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { openIncidentOf, currentCheckpoint, manifestDocuments, pendingDeparture, spotLabel } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import type { Booking, Checkpoint, VehicleTrip } from '@shared/types/booking'
import { CaptureField } from '@shared/ui/CaptureField'
import { Modal } from '@shared/ui/Modal'
import { FormSelect } from '@shared/ui/FormSelect'
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

const GRACE_PERIOD_MS = 2 * 60 * 60 * 1000 // 2 giờ chờ miễn phí theo hợp đồng

function formatRemaining(ms: number) {
  const totalSec = Math.floor(ms / 1000)
  const h = Math.floor(totalSec / 3600)
  const m = Math.floor((totalSec % 3600) / 60)
  const s = totalSec % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

function DeliveryStep({ b, t, cp, run }: { b: Booking; t: VehicleTrip; cp: Checkpoint; run: Run }) {
  const { session } = useAuth()
  const [photo, setPhoto] = useState<string | undefined>(cp.handoverPhoto)
  const [returned, setReturned] = useState(false)
  const [now, setNow] = useState(Date.now())
  const [showTimeoutModal, setShowTimeoutModal] = useState(false)

  // Form states cho báo quá hạn 2h
  const [timeoutReason, setTimeoutReason] = useState<'UNREACHABLE' | 'DESTINATION_UNREADY' | 'CONSIGNEE_REJECTED'>('UNREACHABLE')
  const [selectedAction, setSelectedAction] = useState<'STABLE_NEARBY' | 'RETURN_TO_HQ'>('STABLE_NEARBY')
  const [partnerName, setPartnerName] = useState('')
  const [partnerPhone, setPartnerPhone] = useState('')
  const [estimatedCost, setEstimatedCost] = useState('1500000')
  const [timeoutNote, setTimeoutNote] = useState('')

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])

  const arrivedAt = cp.arrivedAt ?? now
  const elapsedMs = Math.max(0, now - arrivedAt)
  const remainingMs = Math.max(0, GRACE_PERIOD_MS - elapsedMs)
  const isExpired = remainingMs === 0

  const welfare = t.run!.welfare.some(w => w.checkpointId === cp.id)
  const ready = welfare && returned && !!cp.handoverPhoto

  return (
    <>
      {/* Khung đếm ngược thời gian chờ 2h bàn giao */}
      {!isExpired ? (
        <div className="alert alert-info" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 12, background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 12, padding: '12px 16px' }}>
          <div>
            <i className="fa-solid fa-clock" style={{ marginRight: 8, color: 'var(--blue)' }} />
            <b>Thời gian chờ bàn giao miễn phí:</b> Còn lại <span style={{ fontFamily: 'monospace', fontWeight: 700, fontSize: 16, color: 'var(--blue)' }}>{formatRemaining(remainingMs)}</span> (tối đa 2 giờ).
          </div>
        </div>
      ) : (
        <div className="alert alert-danger" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, marginBottom: 12, borderRadius: 12, padding: '12px 16px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <i className="fa-solid fa-triangle-exclamation" style={{ fontSize: 20 }} />
            <div>
              <b>ĐÃ QUÁ HẠN 2 GIỜ BÀN GIAO!</b>
              <div style={{ fontSize: 13, marginTop: 2 }}>Người nhận chưa nhận ngựa. Nhằm bảo đảm an toàn cho ngựa, tài xế kích hoạt phương án an sinh (phí thuê chuồng/lưu ca xe sẽ tính vào quyết toán).</div>
            </div>
          </div>
        </div>
      )}

      <Panel title="Hộ tống kiểm tra lần cuối" icon="fa-heart-pulse" tone={welfare ? 'ok' : 'warn'}>
        <p>{welfare ? 'Hộ tống đã kiểm tra thể trạng ngựa và ghi nhật ký.' : 'Hộ tống đang hạ ngựa và kiểm tra thể trạng lần cuối. Chờ hộ tống ghi nhật ký.'}</p>
      </Panel>
      <Panel title="Trả chứng từ gốc và ký biên bản" icon="fa-file-signature">
        <label className={s.checklist} style={{ display: 'flex', gap: 12, alignItems: 'center', minHeight: 48 }}><input type="checkbox" checked={returned} onChange={e => setReturned(e.target.checked)} style={{ width: 22, height: 22, accentColor: 'var(--orange)' }} /><span>Đã trả toàn bộ chứng từ gốc cho người nhận</span></label>
        <ReadMore text={'Dùng 02 bản in “Biên bản Bàn giao & Hoàn tất Chuyến đi”. Người nhận xác nhận đủ ngựa, thể trạng an toàn, đủ hồ sơ gốc. Hai bên ký, mỗi bên giữ 01 bản.'} />
        <CaptureField label="Ảnh biên bản có đủ chữ ký" required value={photo} onChange={setPhoto} />
        <button className="btn btn-outline" disabled={!photo || photo === cp.handoverPhoto} onClick={() => run(() => bookingsApi.uploadHandover(b.id, t.tripId, session!.name, photo!), 'Đã tải ảnh biên bản')}><i className="fa-solid fa-upload" /> Tải ảnh biên bản</button>
      </Panel>

      <div style={{ display: 'grid', gap: 10, marginTop: 6 }}>
        <button className={`btn btn-primary ${s.big}`} disabled={!ready} onClick={() => run(() => bookingsApi.completeDelivery(b.id, t.tripId, session!.name), 'Đã giao ngựa. Khách nhận thông báo')}><i className="fa-solid fa-flag-checkered" /> Hoàn tất giao ngựa</button>

        <button
          type="button"
          className={`btn ${isExpired ? 'btn-danger' : 'btn-outline'} ${s.big}`}
          disabled={!isExpired}
          style={{ width: '100%', borderColor: isExpired ? undefined : 'var(--line)', opacity: isExpired ? 1 : 0.6 }}
          onClick={() => setShowTimeoutModal(true)}
        >
          <i className="fa-solid fa-triangle-exclamation" /> {isExpired ? 'Báo quá hạn 2h & Kích hoạt an sinh' : `Báo quá hạn 2h (Mở sau ${formatRemaining(remainingMs)})`}
        </button>
      </div>

      {showTimeoutModal && (
        <Modal
          title="Kích hoạt An sinh: Quá hạn 2 giờ bàn giao"
          subtitle="Nhập lý do người nhận chưa đến và chọn phương án lưu trú khẩn cấp để đảm bảo thể trạng cho ngựa."
          onClose={() => setShowTimeoutModal(false)}
          footer={
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', width: '100%' }}>
              <button type="button" className="btn btn-ghost" onClick={() => setShowTimeoutModal(false)}>Hủy</button>
              <button
                type="button"
                className="btn btn-danger"
                disabled={!timeoutNote.trim() || (selectedAction === 'STABLE_NEARBY' && !partnerName.trim())}
                onClick={() => {
                  setShowTimeoutModal(false)
                  run(
                    () => bookingsApi.reportDeliveryTimeout(b.id, t.tripId, session!.name, {
                      timeoutReason,
                      selectedAction,
                      partnerName: selectedAction === 'STABLE_NEARBY' ? partnerName : undefined,
                      partnerPhone: selectedAction === 'STABLE_NEARBY' ? partnerPhone : undefined,
                      estimatedCost: selectedAction === 'STABLE_NEARBY' ? Number(estimatedCost) || 1500000 : 0,
                      note: timeoutNote,
                    }),
                    'Đã ghi nhận ngoại lệ quá hạn 2h & kích hoạt an sinh'
                  )
                }}
              >
                <i className="fa-solid fa-check" /> Xác nhận gửi báo cáo
              </button>
            </div>
          }
        >
          <div style={{ display: 'grid', gap: 14 }}>
            <div className="alert alert-warning" style={{ margin: 0, fontSize: 13 }}>
              <i className="fa-solid fa-circle-info" />
              <div>
                Theo hợp đồng, thời gian chờ miễn phí tại điểm giao là <b>2 giờ</b>. Chi phí thuê chuồng khẩn cấp và lưu ca xe phát sinh sẽ do <b>Khách hàng chi trả</b> tại bước Quyết toán.
              </div>
            </div>

            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: 6 }}>Lý do quá hạn / chưa giao được:</label>
              <FormSelect
                className="form-control"
                style={{ width: '100%' }}
                value={timeoutReason}
                onChange={e => setTimeoutReason(e.target.value as any)}
              >
                <option value="UNREACHABLE">Không liên lạc được với người nhận (gọi nhiều cuộc không nghe máy)</option>
                <option value="DESTINATION_UNREADY">Cơ sở đích chưa sẵn sàng tiếp nhận, yêu cầu chờ</option>
                <option value="CONSIGNEE_REJECTED">Người nhận từ chối tiếp nhận vì lý do cá nhân</option>
              </FormSelect>
            </div>

            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: 6 }}>Phương án an sinh khẩn cấp:</label>
              <FormSelect
                className="form-control"
                style={{ width: '100%' }}
                value={selectedAction}
                onChange={e => setSelectedAction(e.target.value as any)}
              >
                <option value="STABLE_NEARBY">Thuê chuồng lưu trú khẩn cấp gần nhất (Khuyến nghị)</option>
                <option value="RETURN_TO_HQ">Chở ngựa quay về Trụ sở chính Việt Nam</option>
              </FormSelect>
            </div>

            {selectedAction === 'STABLE_NEARBY' && (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={{ display: 'block', fontWeight: 600, marginBottom: 4 }}>Tên trại / Chuồng lưu trú (*):</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="VD: Trại ngựa Thú y Bình Dương"
                    value={partnerName}
                    onChange={e => setPartnerName(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <label style={{ display: 'block', fontWeight: 600, marginBottom: 4 }}>Số điện thoại liên hệ:</label>
                  <input
                    type="tel"
                    className="form-control"
                    placeholder="VD: 0987654321"
                    value={partnerPhone}
                    onChange={e => setPartnerPhone(e.target.value)}
                  />
                </div>
                <div style={{ gridColumn: 'span 2' }}>
                  <label style={{ display: 'block', fontWeight: 600, marginBottom: 4 }}>Chi phí thuê chuồng dự kiến (VNĐ/ngày):</label>
                  <input
                    type="number"
                    className="form-control"
                    value={estimatedCost}
                    onChange={e => setEstimatedCost(e.target.value)}
                  />
                </div>
              </div>
            )}

            <div>
              <label style={{ display: 'block', fontWeight: 600, marginBottom: 4 }}>Ghi chú tình hình tại chỗ (*):</label>
              <textarea
                className="form-control"
                rows={3}
                placeholder="Mô tả cụ thể: đã gọi người nhận bao nhiêu cuộc, tình trạng ngựa trên xe, thỏa thuận tại cổng..."
                value={timeoutNote}
                onChange={e => setTimeoutNote(e.target.value)}
                required
              />
            </div>
          </div>
        </Modal>
      )}
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

  if (t.run?.deliveredAt) {
    if (!t.returnedToHqAt) {
      return (
        <Panel title="Đã giao ngựa — Chờ về Trụ sở chính" icon="fa-house-flag" tone="warn">
          <p>Ngựa đã được giao thành công lúc {formatDateTime(t.run.deliveredAt)}. Theo quy tắc vận hành <b>Đơn Trụ sở (Single HQ)</b>, xe và kíp xe cần di chuyển về Trụ sở chính tại Việt Nam để hoàn tất nhiệm vụ và chuyển trạng thái phương tiện sang <b>AVAILABLE</b>.</p>
          <button className={`btn btn-primary ${s.big}`} onClick={() => run(() => bookingsApi.returnToHq(b.id, t.tripId, session!.name), 'Đã xác nhận xe và kíp xe về Trụ sở chính')}>
            <i className="fa-solid fa-house" /> Xác nhận xe đã về Trụ sở chính (HQ)
          </button>
        </Panel>
      )
    }
    return <Panel title="Đã hoàn tất hành trình & Về Trụ sở" icon="fa-flag-checkered" tone="ok"><p>Giao xong lúc {formatDateTime(t.run.deliveredAt)}. Đã về Trụ sở chính lúc {formatDateTime(t.returnedToHqAt)}.</p></Panel>
  }

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
