// App Hộ tống (Escort): nhận Lệnh điều xe, chuẩn bị tủ thuốc (Flow 3), rồi quét chip và ghi nhật ký an sinh (Flow 4, PRD mục 4.4, 5). Mỗi xe một chuyến riêng.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDateTime } from '@shared/lib/format'
import { useAutoRefresh } from '@shared/services/useLoad'
import { bookingsApi } from '@shared/services/bookings'
import { TripTimeline } from '@shared/ui/TripTimeline'
import { useNow } from '@shared/ui/useNow'
import { useToast } from '@shared/ui/toast'
import { crewStep } from '../../shared/crewStep'
import { Checklist, Empty, Panel, Segmented, TripHeader, TripPicker } from '../../shared/field'
import { ManifestView } from '../../shared/ManifestView'
import { isFieldBookingUpdate, useFieldTrips } from '../../shared/useFieldTrips'
import s from '../../shared/field.module.css'
import { EscortJob } from './EscortJob'

type Tab = 'job' | 'journey' | 'manifest'

const KIT = [
  'Tủ thuốc thú y: thuốc giảm đau chống co thắt, dung dịch bù điện giải',
  'Nhiệt kế và ống nghe, máy quét microchip cầm tay',
  'Nước sạch và cỏ khô đủ cho cả chuyến',
  'Bình xịt nước làm mát thùng xe, quạt đối lưu dự phòng',
  'Sổ ghi nhật ký an sinh trên app đã đăng nhập, pin điện thoại đủ',
]

export default function EscortPage() {
  const { session } = useAuth()
  const toast = useToast()
  const now = useNow()
  const { ready, trips, trip, setSelected, reload, applyBooking, vehicle, driver, escort } = useFieldTrips('escort')
  const [tab, setTab] = useState<Tab>('job')
  const [checked, setChecked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  useAutoRefresh(reload)

  if (!ready) return <p className="text-muted">Đang tải…</p>
  if (!trip) return <Empty text="Bạn chưa có chuyến nào cần xử lý." />

  const { b, t } = trip
  const acked = t.acks?.escort
  const step = crewStep(b, t, 'escort')
  const run = async (fn: () => Promise<unknown>, msg: string) => {
    setBusy(true)
    try {
      const result = await fn()
      if (isFieldBookingUpdate(result)) applyBooking(result)
      toast(msg)
      setChecked([])
      await reload()
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <TripPicker trips={trips} value={t.tripId} onChange={id => { setSelected(id); setChecked([]) }} />
      <TripHeader b={b} t={t} />
      <Segmented<Tab> value={tab} onChange={setTab} tabs={[['job', 'Việc', 'fa-list-check'], ['journey', 'Hành trình', 'fa-route'], ['manifest', 'Lệnh', 'fa-clipboard-list']]} />

      {tab === 'manifest' && <ManifestView b={b} trip={t} vehicle={vehicle} driver={driver} escort={escort} />}
      {tab === 'journey' && <Panel title="Các mốc hành trình" icon="fa-route"><TripTimeline trip={t} now={now} staff /></Panel>}
      {tab === 'job' && (
        <>
          {step === 'ready' && (
            <Panel title="Sẵn sàng đón ngựa" icon="fa-truck-fast" tone="ok">
              <p>Giấy tờ kiểm dịch & hải quan đã hoàn tất. Giờ đón dự kiến: {b.route && formatDateTime(b.route.legs[0].departAt)}. Đang chờ tài xế bắt đầu xuất phát đến điểm đón.</p>
            </Panel>
          )}
          {step === 'acknowledge' && (
            <Panel title="Nhận lệnh và chuẩn bị" icon="fa-clipboard-check">
              <p>Lệnh điều xe đã phát. Tick từng mục khi đã chuẩn bị, rồi xác nhận nhận lệnh.</p>
              <Checklist items={KIT} checked={checked} onChange={setChecked} />
              <button className={`btn btn-primary ${s.big}`} disabled={checked.length < KIT.length || busy} onClick={() => run(() => bookingsApi.acknowledgeTrip(b.id, t.tripId, 'escort', session!.name), 'Đã xác nhận nhận Lệnh điều xe')}>
                <i className="fa-solid fa-check" /> Tôi đã nhận lệnh ({checked.length}/{KIT.length})
              </button>
            </Panel>
          )}
          {step === 'waiting_documents' && acked && (
            <Panel title="Đã nhận lệnh, chờ hoàn tất giấy tờ" icon="fa-clock">
              <p>Bạn đã xác nhận lúc {formatDateTime(acked)}. Chuyên viên kiểm dịch đang hoàn tất thủ tục giấy tờ pháp lý cho đơn hàng.</p>
            </Panel>
          )}
          {step === 'waiting_crew' && (
            <Panel title="Đã nhận lệnh, chờ tài xế" icon="fa-clock">
              <p>Giấy tờ đã xong. Đang chờ tài xế nhận lệnh.</p>
            </Panel>
          )}
          <EscortJob key={t.tripId} b={b} t={t} run={run} />
        </>
      )}
    </>
  )
}
