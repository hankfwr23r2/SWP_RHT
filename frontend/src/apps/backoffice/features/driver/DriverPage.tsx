// App Tài xế: nhận Lệnh điều xe (Flow 3), rồi check-in từng mốc hành trình (Flow 4, PRD mục 4.4, 5). Mỗi xe một chuyến riêng.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { manifestDocuments } from '@shared/lib/booking'
import { formatDateTime } from '@shared/lib/format'
import { bookingsApi } from '@shared/services/bookings'
import { TripTimeline } from '@shared/ui/TripTimeline'
import { useNow } from '@shared/ui/useNow'
import { useToast } from '@shared/ui/toast'
import { Checklist, Empty, Panel, Segmented, TripHeader, TripPicker } from '../../shared/field'
import { ManifestView } from '../../shared/ManifestView'
import { useFieldTrips } from '../../shared/useFieldTrips'
import s from '../../shared/field.module.css'
import { DriverJob } from './DriverJob'

type Tab = 'job' | 'journey' | 'manifest'

export default function DriverPage() {
  const { session } = useAuth()
  const toast = useToast()
  const now = useNow()
  const { ready, trips, trip, setSelected, reload, vehicle, driver, escort } = useFieldTrips('driver')
  const [tab, setTab] = useState<Tab>('job')
  const [checked, setChecked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)

  if (!ready) return <p className="text-muted">Đang tải…</p>
  if (!trip) return <Empty text="Bạn chưa có chuyến nào cần xử lý." />

  const { b, t } = trip
  const items = [...(t.driverPack?.items ?? manifestDocuments(b).system), 'Xe đã kiểm tra: dầu, lốp, điều hòa thùng, máy phát điện phụ']
  const acked = t.acks?.driver
  const cleared = !!b.clearance?.doneAt
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
            <Panel title="Nhận lệnh và chuẩn bị xe" icon="fa-clipboard-check">
              <p>{cleared ? 'Giấy tờ chuyến đi đã được Chuyên viên hoàn tất và bàn giao. Vui lòng kiểm tra xe, tick từng mục khi đã có trên xe, rồi xác nhận nhận lệnh.' : 'Lệnh điều xe đã phát ngay sau khi khách đặt cọc. Tick từng mục khi đã có trên xe, rồi xác nhận.'}</p>
              <Checklist items={items} checked={checked} onChange={setChecked} />
              <button className={`btn btn-primary ${s.big}`} disabled={checked.length < items.length || busy} onClick={() => run(() => bookingsApi.acknowledgeTrip(b.id, t.tripId, 'driver', session!.name), 'Đã xác nhận nhận Lệnh điều xe')}>
                <i className="fa-solid fa-check" /> Tôi đã nhận lệnh ({checked.length}/{items.length})
              </button>
            </Panel>
          )}
          {!t.departedAt && acked && !cleared && (
            <Panel title={t.acks?.escort ? "Kíp xe đã xác nhận, chuyển kiểm dịch viên" : "Đã nhận lệnh, chờ hộ tống xác nhận"} icon="fa-shield-virus">
              <p>Bạn xác nhận lúc {formatDateTime(acked)}.{t.acks?.escort ? ' Cả tài xế và hộ tống đã xác nhận giấy tờ. Đơn đã được chuyển qua Kiểm dịch viên để duyệt xác nhận dịch tễ và giấy thông quan.' : ' Đang chờ nhân viên hộ tống xác nhận để tự động chuyển sang Kiểm dịch viên.'}</p>
            </Panel>
          )}
          {!t.departedAt && acked && cleared && !t.acks?.escort && (
            <Panel title="Đã nhận lệnh, giấy tờ đã hoàn tất" icon="fa-circle-check" tone="ok"><p>Bạn xác nhận lúc {formatDateTime(acked)}. Giấy tờ chuyến đi đã hoàn tất. Đang chờ nhân viên hộ tống xác nhận để bắt đầu đón ngựa.</p></Panel>
          )}
          {!t.departedAt && acked && cleared && !!t.acks?.escort && (
            <Panel title="Sẵn sàng đón ngựa" icon="fa-truck-fast" tone="ok">
              <p>Giấy tờ đã xong, tài xế và hộ tống đều đã nhận lệnh. Giờ đón dự kiến {b.route && formatDateTime(b.route.legs[0].departAt)}. Khi xuất phát từ bãi xe, bấm bên dưới.</p>
              <button className={`btn btn-primary ${s.big}`} disabled={busy} onClick={() => run(() => bookingsApi.departToPickup(b.id, t.tripId, session!.name), 'Đã bắt đầu đến điểm đón')}><i className="fa-solid fa-truck-moving" /> Bắt đầu đến điểm đón</button>
            </Panel>
          )}
          <DriverJob key={t.tripId} b={b} t={t} reload={reload} />
        </>
      )}
    </>
  )
}
