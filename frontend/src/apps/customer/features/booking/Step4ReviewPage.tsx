// Bước 4: xác nhận và gửi đơn. Đơn gửi đi ở trạng thái "Chờ Manager tiếp nhận".
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@shared/auth/AuthContext'
import { VEHICLE_CLASS } from '@shared/config/booking-rules'
import { COUNTRIES } from '@shared/config/network'
import { classForHorses, findLocation, fromIsoDay } from '@shared/lib/booking'
import { formatDate, formatVND } from '@shared/lib/format'
import { cleanFeeding, feedingLines } from '@shared/lib/feeding'
import { usePageData } from '@shared/routing/pageData'
import { customerBookingsApi } from '@shared/services/bookings'
import { SEX_LABEL, type BookingRequiredServiceCategory, type PlaceRef } from '@shared/types/booking'
import { SERVICE_BILLING_UNIT_LABEL } from '@shared/api/serviceCatalog'
import { Modal } from '@shared/ui/Modal'
import { useToast } from '@shared/ui/toast'
import { BookingShell } from './BookingShell'
import { InsuranceSummary } from '@shared/ui/InsuranceFields'
import type { horseListLoader } from '../horses/loaders'
import { countriesOf, serviceOptionFor, useBookingDraft } from './draft'
import { isBookingRouteReady } from './routeValidation'
import s from './Booking.module.css'


export default function Step4ReviewPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const toast = useToast()
  const { session } = useAuth()
  const owner = session!.name
  const { draft, clear } = useBookingDraft()
  const { horses, services = [] } = usePageData<typeof horseListLoader>()
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false) // đã gửi: bản nháp bị xóa, không chuyển ngược về bước 1

  const [placed, setPlaced] = useState<{ id: string; route: string; date: string; horses: number } | null>(null)

  // Đã gửi: hiện popup xác nhận, khách chọn xem đơn vừa đặt hoặc chi tiết đơn
  if (sent) {
    if (!placed) return null
    const toList = () => navigate(`/orders?group=confirm&new=${placed.id}`)
    return (
      <Modal onClose={toList} title={<><i className="fa-solid fa-circle-check" style={{ color: 'var(--green)' }} /> {t('bookingWizard.orderPlacedTitle', { id: placed.id })}</>} subtitle={t('bookingWizard.orderPlacedSubtitle')}
        footer={<button className="btn btn-primary" onClick={toList}>{t('bookingWizard.toMyOrders')}</button>}>
        <dl className={s.reviewGrid}>
          <div><dt>{t('booking.summary')}</dt><dd>{placed.route}</dd></div>
          <div><dt>{t('bookingWizard.departureDate')}</dt><dd>{placed.date}</dd></div>
          <div><dt>{t('booking.horsesCount')}</dt><dd>{placed.horses} {t('booking.horsesUnit')}</dd></div>
        </dl>
        <p className="form-hint" style={{ marginTop: 14 }}>{t('bookingWizard.orderPlacedHint')}</p>
      </Modal>
    )
  }
  if (!isBookingRouteReady(draft)) return <Navigate to="/booking/route" replace />
  const countries = countriesOf(draft)
  if (!countries || !draft.departDate) return <Navigate to="/booking/route" replace />
  if (!draft.horseIds.length) return <Navigate to="/booking/horses" replace />
  if (draft.horseIds.some(id => !draft.config[id]?.insuranceDetails?.option)) return <Navigate to="/booking/services" replace />

  const international = draft.type === 'international'
  const rows = draft.horseIds.map(id => horses?.find(h => h.id === id)).filter(Boolean) as NonNullable<typeof horses>
  const serviceSelections = (horseId: string): Record<BookingRequiredServiceCategory, ReturnType<typeof serviceOptionFor>> => {
    const config = draft.config[horseId]
    return Object.fromEntries((['stall', 'feed'] as BookingRequiredServiceCategory[]).map(category => [
      category,
      serviceOptionFor(services, config, category),
    ])) as Record<BookingRequiredServiceCategory, ReturnType<typeof serviceOptionFor>>
  }
  const serviceSelectionsValid = rows.length > 0
    && rows.length === draft.horseIds.length
    && rows.every(h => Object.values(serviceSelections(h.id)).every(Boolean))
  const place = (id: string, country: PlaceRef['country']): PlaceRef => ({ id, name: findLocation(id)?.name ?? id, country })
  const origin = place(draft.originId, countries.origin)
  const dest = place(draft.destId, countries.dest)
  const cls = VEHICLE_CLASS[classForHorses(rows.length)]

  const submit = async () => {
    if (busy) return
    if (!serviceSelectionsValid) {
      toast('Một dịch vụ đã tạm ngưng hoặc không còn áp dụng. Vui lòng quay lại chọn lại dịch vụ.', 'error')
      return
    }
    if (!draft.consignor.name?.trim() || !draft.consignor.phone?.trim() || !draft.consignor.idNumber?.trim()) {
      toast('Thông tin người gửi chưa đầy đủ (Họ tên, SĐT, CCCD/Hộ chiếu). Vui lòng bấm "Sửa" để quay lại Bước 1 bổ sung.', 'error')
      return
    }
    if (!draft.consignee.name?.trim() || !draft.consignee.phone?.trim() || !draft.consignee.idNumber?.trim()) {
      toast('Thông tin người nhận chưa đầy đủ (Họ tên, SĐT, CCCD/Hộ chiếu). Vui lòng bấm "Sửa" để quay lại Bước 1 bổ sung.', 'error')
      return
    }
    setBusy(true)
    try {
      const order = await customerBookingsApi.create(owner, {
        type: draft.type as 'domestic' | 'international', origin, dest,
        departAt: fromIsoDay(draft.departDate), consignor: draft.consignor, consignee: draft.consignee,
        horses: rows.map(h => {
          const selectedServices = serviceSelections(h.id)
          const insurance = draft.config[h.id].insuranceDetails!
          return {
            horseId: h.id, name: h.name, microchip: h.microchip, breed: h.breed, sex: h.sex,
            insurance: { ...insurance, opted: insurance.option === 'assistance' },
            feeding: cleanFeeding(draft.config[h.id].feeding),
            selectedServiceCodes: Object.fromEntries(Object.entries(selectedServices).map(([key, service]) => [key, service?.serviceCode])) as Partial<Record<BookingRequiredServiceCategory, string>>,
          }
        }),
      })
      setPlaced({ id: order.id, route: `${origin.name.split(' — ')[0]} → ${dest.name.split(' — ')[0]}`, date: formatDate(fromIsoDay(draft.departDate)), horses: rows.length })
      setSent(true)
      clear()
      toast(`Đã gửi đơn ${order.id}`)
    } catch (err) {
      toast(err instanceof Error ? err.message : 'Không gửi được đơn.', 'error')
      setBusy(false)
    }
  }

  return (
    <BookingShell step={4} title={t('bookingWizard.step4Title')} subtitle={t('bookingWizard.step4Subtitle')}>
      <div className={`card ${s.review}`} data-card>
        <div className="card-header"><h3><i className="fa-solid fa-route" /> {t('booking.step1')}</h3><Link to="/booking/route" className={s.editLink}>{t('common.edit')}</Link></div>
        <dl className={s.reviewGrid}>
          <div><dt>{t('bookingWizard.tripType')}</dt><dd>{international ? <>{t('bookingWizard.international')} <span className={s.subNote}>({COUNTRIES[countries.origin].name} → {COUNTRIES[countries.dest].name})</span></> : t('bookingWizard.domestic')}</dd></div>
          <div><dt>{t('bookingWizard.departureDate')}</dt><dd>{formatDate(fromIsoDay(draft.departDate))}</dd></div>
          <div><dt>{t('booking.origin')}</dt><dd>{origin.name}</dd></div>
          <div><dt>{t('booking.destination')}</dt><dd>{dest.name}</dd></div>
          <div>
            <dt>{t('booking.sender')}</dt>
            <dd>
              {draft.consignor.name}
              <br /><small className="text-muted">{draft.consignor.phone}</small>
              {draft.consignor.idNumber ? <><br /><small className="text-muted">ID: {draft.consignor.idNumber}</small></> : <><br /><small className="text-danger font-semibold">{i18n.language === 'en' ? 'Missing ID/Passport' : 'Thiếu CCCD/Hộ chiếu'}</small></>}
            </dd>
          </div>
          <div>
            <dt>{t('booking.receiver')}</dt>
            <dd>
              {draft.consignee.name}
              <br /><small className="text-muted">{draft.consignee.phone}</small>
              {draft.consignee.idNumber ? <><br /><small className="text-muted">ID: {draft.consignee.idNumber}</small></> : <><br /><small className="text-danger font-semibold">{i18n.language === 'en' ? 'Missing ID/Passport' : 'Thiếu CCCD/Hộ chiếu'}</small></>}
            </dd>
          </div>
        </dl>
      </div>

      <div className="card" data-card>
        <div className="card-header"><h3><i className="fa-solid fa-horse-head" /> {rows.length} {t('booking.horsesUnit')} · {cls.label} <span className={s.subNote}>({cls.stalls})</span></h3><Link to="/booking/services" className={s.editLink}>{t('common.edit')}</Link></div>
        {rows.map(h => {
          const selected = serviceSelections(h.id)
          return (
            <div key={h.id} className={s.reviewHorseItem}>
              <div className={s.reviewHorseHead}>
                <div>
                  <span className={s.reviewHorseName}>{h.name}</span>
                  <span className={s.reviewHorseMeta}>Chip {h.microchip} · {h.breed} · {SEX_LABEL[h.sex]}</span>
                </div>
              </div>
              <div className={s.reviewServicesGrid}>
                <div className={s.reviewServiceCol}>
                  <span className={s.reviewServiceLabel}><i className="fa-solid fa-border-all" /> {i18n.language === 'en' ? 'Transporter Stall' : 'Khoang xe'}</span>
                  <span className={s.reviewServiceVal}>{selected.stall?.serviceName ?? (i18n.language === 'en' ? 'Unavailable' : 'Không còn áp dụng')}</span>
                  <span className={s.reviewServiceSub}>{selected.stall ? (selected.stall.price > 0
                    ? `${formatVND(selected.stall.price)} / ${SERVICE_BILLING_UNIT_LABEL[selected.stall.billingUnit]}`
                    : t('bookingWizard.freeIncluded')) : (i18n.language === 'en' ? 'Please return to select service' : 'Vui lòng quay lại chọn dịch vụ')}</span>
                </div>
                <div className={s.reviewServiceCol}>
                  <span className={s.reviewServiceLabel}><i className="fa-solid fa-wheat-awn" /> {i18n.language === 'en' ? 'Feeding Program' : 'Gói thức ăn'}</span>
                  <span className={s.reviewServiceVal}>{selected.feed?.serviceName ?? (i18n.language === 'en' ? 'Unavailable' : 'Không còn áp dụng')}</span>
                  <span className={s.reviewServiceSub}>{selected.feed ? (selected.feed.price > 0
                    ? `${formatVND(selected.feed.price)} / ${SERVICE_BILLING_UNIT_LABEL[selected.feed.billingUnit]}`
                    : t('bookingWizard.freeIncluded')) : (i18n.language === 'en' ? 'Please return to select service' : 'Vui lòng quay lại chọn dịch vụ')}</span>
                  {feedingLines(draft.config[h.id].feeding).map(([label, text]) => <span key={label} className={s.reviewServiceSub}>{label}: {text}</span>)}
                </div>
                <div className={s.reviewServiceCol}>
                  <span className={s.reviewServiceLabel}><i className="fa-solid fa-shield-halved" /> {i18n.language === 'en' ? 'Transit Insurance' : 'Bảo hiểm chuyến đi'}</span>
                  <InsuranceSummary insurance={draft.config[h.id].insuranceDetails} />
                </div>
              </div>
            </div>
          )
        })}
      </div>


      <div className={s.actions}>
        <Link to="/booking/services" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> {t('bookingWizard.step3Title')}</Link>
        {!serviceSelectionsValid && <span className="form-error">{i18n.language === 'en' ? 'Services have changed or failed to load. Please return and select again.' : 'Dịch vụ đã thay đổi hoặc không tải được. Hãy quay lại chọn lại dịch vụ.'}</span>}
        <button type="button" className="btn btn-primary btn-lg" onClick={submit} disabled={busy || !serviceSelectionsValid}><i className="fa-solid fa-paper-plane" /> {busy ? t('bookingWizard.sending') : t('bookingWizard.btnSendOrder')}</button>
      </div>
    </BookingShell>
  )
}
