// App Tài xế: nhận Lệnh điều xe (Flow 3), rồi check-in từng mốc hành trình (Flow 4, PRD mục 4.4, 5). Mỗi xe một chuyến riêng.
import { useState } from 'react'
import { useAuth } from '@shared/auth/AuthContext'
import { manifestDocuments } from '@shared/lib/booking'
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
import { DriverJob } from './DriverJob'

type Tab = 'job' | 'journey' | 'manifest'

export default function DriverPage() {
  const { session } = useAuth()
  const toast = useToast()
  const now = useNow()
  const { ready, trips, trip, setSelected, reload, applyBooking, vehicle, driver, escort } = useFieldTrips('driver')
  const [tab, setTab] = useState<Tab>('job')
  const [checked, setChecked] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  useAutoRefresh(reload)

  if (!ready) return <p className="text-muted">Đang tải…</p>
  if (!trip) return <Empty text="Bạn chưa có chuyến nào cần xử lý." />

  const { b, t } = trip
  const items = [...(t.driverPack?.items ?? manifestDocuments(b).system), 'Xe đã kiểm tra: dầu, lốp, điều hòa thùng, máy phát điện phụ']
  const acked = t.acks?.driver
  const step = crewStep(b, t, 'driver')
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
              <p>Giấy tờ kiểm dịch & hải quan đã hoàn tất, đơn hàng sẵn sàng thực hiện. Giờ đón dự kiến: {b.route && formatDateTime(b.route.legs[0].departAt)}. Khi xuất phát từ bãi xe, bấm bên dưới để bắt đầu chuyến đi.</p>
              <button className={`btn btn-primary ${s.big}`} disabled={busy} onClick={() => run(() => bookingsApi.departToPickup(b.id, t.tripId, session!.name), 'Đã bắt đầu đến điểm đón')}>
                <i className="fa-solid fa-truck-moving" /> Xuất phát đến điểm đón ngựa
              </button>
            </Panel>
          )}
          {step === 'acknowledge' && (
            <Panel title="Nhận lệnh và chuẩn bị xe" icon="fa-clipboard-check">
              <p>Lệnh điều xe đã phát. Tick từng mục khi đã kiểm tra xe, rồi xác nhận.</p>
              <Checklist items={items} checked={checked} onChange={setChecked} />
              <button className={`btn btn-primary ${s.big}`} disabled={checked.length < items.length || busy} onClick={() => run(() => bookingsApi.acknowledgeTrip(b.id, t.tripId, 'driver', session!.name), 'Đã xác nhận nhận Lệnh điều xe')}>
                <i className="fa-solid fa-check" /> Tôi đã nhận lệnh ({checked.length}/{items.length})
              </button>
            </Panel>
          )}
          {step === 'waiting_documents' && acked && (
            <Panel title="Đã nhận lệnh, chờ hoàn tất giấy tờ" icon="fa-clock">
              <p>Bạn đã xác nhận lúc {formatDateTime(acked)}. Chuyên viên kiểm dịch đang hoàn tất thủ tục giấy tờ pháp lý cho đơn hàng.</p>
            </Panel>
          )}
          {step === 'waiting_crew' && (
            <Panel title="Đã nhận lệnh, chờ hộ tống" icon="fa-clock">
              <p>Giấy tờ đã xong. Đang chờ hộ tống nhận lệnh.</p>
            </Panel>
          )}
          <DriverJob key={t.tripId} b={b} t={t} run={run} />
        </>
      )}
    </>
  )
}
