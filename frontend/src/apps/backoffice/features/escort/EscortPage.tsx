// App Hộ tống (Escort): nhận Lệnh điều xe, chuẩn bị tủ thuốc (Flow 3), rồi quét chip và ghi nhật ký an sinh (Flow 4, PRD mục 4.4, 5). Mỗi xe một chuyến riêng.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { TripTimeline } from '@shared/ui/TripTimeline'
import { useNow } from '@shared/ui/useNow'
import { useToast } from '@shared/ui/toast'
import { Checklist, Empty, Panel, Segmented, TripHeader, TripPicker } from '../../shared/field'
import { ManifestView } from '../../shared/ManifestView'
import { useFieldTrips } from '../../shared/useFieldTrips'
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
  const { ready, trips, trip, setSelected, reload, vehicle, driver, escort } = useFieldTrips('escort')
  const [tab, setTab] = useState<Tab>('job')
  const [checked, setChecked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)

  if (!ready) return <p className="text-muted">Đang tải…</p>
  if (!trip) return <Empty text="Bạn chưa có chuyến nào cần xử lý." />

  const { b, t } = trip
  const acked = t.acks.escort
  const run = async (fn: () => Promise<unknown>, msg: string) => {
    setBusy(true)
    try { await fn(); toast(msg); setChecked([]); reload() } catch (e) { toast(e instanceof Error ? e.message : 'Không thực hiện được', 'error') }
    setBusy(false)
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
          {!t.departedAt && !acked && (
            <Panel title="Nhận lệnh và chuẩn bị" icon="fa-clipboard-check">
              <p>Lệnh điều xe đã phát ngay sau khi khách đặt cọc. Tick từng mục khi đã chuẩn bị, rồi xác nhận nhận lệnh.</p>
              <Checklist items={KIT} checked={checked} onChange={setChecked} />
              <button className={`btn btn-primary ${s.big}`} disabled={checked.length < KIT.length || busy} onClick={() => run(() => bookingsApi.acknowledgeTrip(b.id, t.tripId, 'escort', session!.name), 'Đã xác nhận nhận Lệnh điều xe')}>
                <i className="fa-solid fa-check" /> Tôi đã nhận lệnh ({checked.length}/{KIT.length})
              </button>
            </Panel>
          )}
          {!t.departedAt && acked && (
            <Panel title="Đã nhận lệnh" icon="fa-circle-check" tone="ok"><p>Bạn xác nhận lúc {formatDateTime(acked)}. {!b.clearance?.doneAt ? 'Nhà xe đang làm giấy tờ, xe chỉ đi đón ngựa khi giấy tờ xong. ' : ''}{t.acks.driver ? '' : 'Đang chờ tài xế xác nhận.'}</p></Panel>
          )}
          {!t.departedAt && acked && !!t.acks.driver && !!b.clearance?.doneAt && <Panel title="Sẵn sàng đón ngựa" icon="fa-truck-fast" tone="ok"><p>Giờ đón dự kiến {b.route && formatDateTime(b.route.legs[0].departAt)}. Chờ tài xế bắt đầu đến điểm đón.</p></Panel>}
          <EscortJob key={t.tripId} b={b} t={t} reload={reload} />
        </>
      )}
    </>
  )
}
