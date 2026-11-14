// Bước 3: chọn dịch vụ đang được áp dụng cho từng ngựa.
import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { formatVND } from '@shared/lib/format'
import { CustomerInsuranceFields } from '@shared/ui/InsuranceFields'
import { usePageData } from '@shared/routing/pageData'
import { SEX_LABEL, type BookingRequiredServiceCategory } from '@shared/types/booking'
import { PolicyLink } from '@shared/ui/PolicyLink'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import { BookingShell } from './BookingShell'
import type { horseListLoader } from '../horses/loaders'
import { defaultConfig, serviceOptionFor, useBookingDraft, type HorseConfig } from './draft'
import { isBookingRouteReady } from './routeValidation'
import s from './Booking.module.css'

type CatalogCategory = Exclude<BookingRequiredServiceCategory, 'insurance'>

const SERVICE_GROUPS: { key: CatalogCategory; label: string; icon: string }[] = [
  { key: 'stall', label: 'Khoang trên xe', icon: 'fa-border-all' },
  { key: 'feed', label: 'Gói thức ăn', icon: 'fa-wheat-awn' },
]

function legacySelection(category: CatalogCategory, service: ServiceCatalogItem): Partial<HorseConfig> {
  if (category === 'stall') return { stall: service.serviceCode === 'STALL_SINGLE_EXTENDED' ? 'single' : 'standard' }
  if (category === 'feed') {
    const feedPackage = service.serviceCode === 'FEED_ADVANCED' ? 'advanced'
      : service.serviceCode === 'FEED_SPORT' ? 'sport' : 'basic'
    return { feedPackage }
  }
  return {}
}

function hasExplicitServiceSelection(config: HorseConfig, category: CatalogCategory): boolean {
  if (config.selectedServiceCodes?.[category] || config.selectedServices?.[category] !== undefined) return true
  if (category === 'stall') return config.stall !== 'standard'
  if (category === 'feed') return config.feedPackage !== 'basic'
  return false
}

export default function Step3ServicesPage() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const { draft, save } = useBookingDraft()
  const { horses, services = [] } = usePageData<typeof horseListLoader>()
  const [config, setConfig] = useState<Record<string, HorseConfig>>(() =>
    Object.fromEntries(draft.horseIds.map(id => [id, { ...defaultConfig(), ...draft.config[id] }])))
  const [activeIndex, setActiveIndex] = useState(0)
  const [ack, setAck] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  const serviceGroups = [
    { key: 'stall' as CatalogCategory, label: i18n.language === 'en' ? 'Transporter Stall' : 'Khoang trên xe', icon: 'fa-border-all' },
    { key: 'feed' as CatalogCategory, label: i18n.language === 'en' ? 'Feeding Program' : 'Gói thức ăn', icon: 'fa-wheat-awn' },
  ]

  useEffect(() => {
    if (submitted) document.querySelector('[data-problem]')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [activeIndex, submitted])

  if (!isBookingRouteReady(draft)) return <Navigate to="/booking/route" replace />
  if (!draft.horseIds.length) return <Navigate to="/booking/horses" replace />

  const rows = draft.horseIds.map(id => horses?.find(h => h.id === id)).filter(Boolean) as NonNullable<typeof horses>
  const activeHorse = rows[activeIndex]
  const set = (id: string, patch: Partial<HorseConfig>) => setConfig(current => ({ ...current, [id]: { ...current[id], ...patch } }))
  const declined = rows.filter(h => config[h.id].insurance === 'none')
  const problems = rows.length === 0 || rows.length !== draft.horseIds.length
    ? ['Không tải đủ hồ sơ ngựa cho các mã đã chọn. Vui lòng quay lại “Chọn ngựa” và chọn lại danh sách.']
    : rows.flatMap(h => SERVICE_GROUPS
      .filter(group => !serviceOptionFor(services, config[h.id], group.key))
      .map(group => `${h.name}: hiện không có dịch vụ ${group.label.toLocaleLowerCase()} đang áp dụng`))
  problems.push(...rows.flatMap(h => SERVICE_GROUPS
    .filter(group => !hasExplicitServiceSelection(config[h.id], group.key))
    .map(group => `${h.name}: chọn dịch vụ ${group.label.toLocaleLowerCase()}`)))
  problems.push(...rows.filter(h => !['personal', 'assistance', 'none'].includes(config[h.id].insurance))
    .map(h => `${h.name}: chọn bảo hiểm chuyến đi`))
  for (const h of rows) {
    const i = config[h.id].insuranceDetails
    if (config[h.id].insurance === 'personal' && (!i?.provider?.trim() || !i.policyNumber?.trim() || !i.validFrom || !i.validUntil || i.validUntil < i.validFrom)) problems.push(`${h.name}: điền đầy đủ thông tin bảo hiểm cá nhân và thời hạn hợp lệ`)
  }
  if (declined.length && !ack) problems.push('Xác nhận Điều khoản Trách nhiệm Hạn chế')

  const next = () => {
    if (uploading) return
    setSubmitted(true)
    if (problems.length) {
      if (rows.length === 0 || rows.length !== draft.horseIds.length) return
      const firstMissing = rows.findIndex(h => SERVICE_GROUPS.some(group =>
        !serviceOptionFor(services, config[h.id], group.key)
        || !hasExplicitServiceSelection(config[h.id], group.key)))
      if (firstMissing >= 0) setActiveIndex(firstMissing)
      return
    }
    const selectedConfig = Object.fromEntries(rows.map(h => {
      const current = config[h.id]
      const selectedServiceCodes = Object.fromEntries(SERVICE_GROUPS.map(group => [
        group.key,
        current.selectedServiceCodes?.[group.key] ?? serviceOptionFor(services, current, group.key)?.serviceCode,
      ])) as Partial<Record<BookingRequiredServiceCategory, string>>
      return [h.id, { ...current, selectedServiceCodes }]
    }))
    save({ config: selectedConfig })
    navigate('/booking/review')
  }

  return (
    <BookingShell step={3} title={t('bookingWizard.step3Title')} subtitle={t('bookingWizard.step3Subtitle')}>
      {activeHorse && (
        <>
          <div className={s.horseNavigator} aria-label="Chọn ngựa để thiết lập dịch vụ">
            <label className={s.horseSelectLabel}>
              <span>{t('bookingWizard.horseNav', { current: activeIndex + 1, total: rows.length })}</span>
              <select className="form-control" value={activeHorse.id} disabled={uploading} onChange={e => setActiveIndex(rows.findIndex(h => h.id === e.target.value))}>
                {rows.map((horse, index) => <option key={horse.id} value={horse.id}>{index + 1}. {horse.name}</option>)}
              </select>
            </label>
          </div>
          {(() => {
            const horse = activeHorse
            const current = config[horse.id]
            return (
              <section key={horse.id} className={`card ${s.horseCard}`} data-card aria-labelledby={`h-${horse.id}`}>
                <div className={s.horseHead}>
                  <i className="fa-solid fa-horse" style={{ fontSize: '1.4rem', color: 'var(--icon-accent)' }} aria-hidden="true" />
                  <div><div id={`h-${horse.id}`} className={s.horseName}>{horse.name}</div><div className={s.horseChip}>Chip {horse.microchip} · {horse.breed} · {SEX_LABEL[horse.sex]}</div></div>
                </div>

                {serviceGroups.map(group => {
                  const prefix = `${group.key.toUpperCase()}_`
                  const options = services.filter(service => service.serviceCode.startsWith(prefix))
                  const resolved = serviceOptionFor(services, current, group.key)
                  const chosen = hasExplicitServiceSelection(current, group.key) ? resolved : undefined
                  return (
                    <div className={s.serviceSection} key={group.key}>
                      <div className={s.fieldLabel} id={`${group.key}-${horse.id}`}>
                        <i className={`fa-solid ${group.icon}`} aria-hidden="true" /> {group.label}
                      </div>
                      <div className={s.optRow} role="radiogroup" aria-labelledby={`${group.key}-${horse.id}`}
                        {...(submitted && !chosen
                          ? { 'data-problem': true } : {})}>
                        {options.length ? options.map(service => {
                          const price = service.price
                          const billingLabel = service.billingUnit === 'PER_HORSE_DAY'
                            ? (i18n.language === 'en' ? 'horse × day' : 'ngựa × ngày')
                            : service.billingUnit === 'PER_HORSE'
                              ? (i18n.language === 'en' ? 'horse / trip' : 'ngựa / chuyến')
                              : (i18n.language === 'en' ? 'trip' : 'chuyến')
                          return (
                          <label key={service.id} className={s.opt}>
                            <input
                              type="radio"
                              name={`${group.key}-${horse.id}`}
                              checked={chosen?.id === service.id}
                              onChange={() => set(horse.id, {
                                selectedServiceCodes: { ...current.selectedServiceCodes, [group.key]: service.serviceCode },
                                ...legacySelection(group.key, service),
                              })}
                            />
                            <b>{service.serviceName}</b>
                            <span>{price > 0
                                ? `+${formatVND(price)} / ${billingLabel}`
                                : t('bookingWizard.freeIncluded')}</span>
                            {service.description && <small>{service.description}</small>}
                          </label>
                          )
                        }) : <span>{t('bookingWizard.noOptions')}</span>}
                      </div>
                    </div>
                  )
                })}
                <CustomerInsuranceFields onBusyChange={setUploading} value={current.insuranceDetails} option={current.insurance}
                  onChange={insuranceDetails => set(horse.id, { insurance: insuranceDetails.option, insuranceDetails })} />
              </section>
            )
          })()}
        </>
      )}

      {declined.length > 0 && (
        <div className="card" data-card {...(submitted && !ack ? { 'data-problem': true } : {})}>
          <label className={s.ack}>
            <input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} />
            <span>{i18n.language === 'en' ? <>I understand and agree to the transporter <PolicyLink doc="liability">Limited Liability Terms</PolicyLink> for {declined.map(h => h.name).join(', ')}, whose transit insurance was declined.</> : <>Tôi hiểu và đồng ý <PolicyLink doc="liability">Điều khoản Trách nhiệm Hạn chế</PolicyLink> của nhà xe cho {declined.map(h => h.name).join(', ')}, những ngựa tôi không mua bảo hiểm.</>}</span>
          </label>
          {submitted && !ack && <div className="form-error">{t('bookingWizard.ackRequired')}</div>}
        </div>
      )}

      {submitted && problems.length > 0 && <div className="form-error">{problems[0]}</div>}

      <div className={s.actions}>
        <Link to="/booking/horses" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> {t('bookingWizard.btnBackHorses')}</Link>
        <button type="button" className="btn btn-primary" onClick={next} disabled={uploading}>{t('bookingWizard.btnNextReview')} <i className="fa-solid fa-arrow-right" /></button>
      </div>
    </BookingShell>
  )
}
