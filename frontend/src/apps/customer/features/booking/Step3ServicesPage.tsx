// Bước 3: chọn dịch vụ đang được áp dụng cho từng ngựa.
import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { formatVND } from '@shared/lib/format'
import { insuranceFee } from '@shared/lib/booking'
import { usePageData } from '@shared/routing/pageData'
import { SEX_LABEL, type BookingRequiredServiceCategory } from '@shared/types/booking'
import { PolicyLink } from '@shared/ui/PolicyLink'
import type { ServiceCatalogItem } from '@shared/api/serviceCatalog'
import { BookingShell } from './BookingShell'
import type { horseListLoader } from '../horses/loaders'
import { defaultConfig, isDeclinedInsurance, serviceOptionFor, useBookingDraft, type HorseConfig } from './draft'
import s from './Booking.module.css'

const SERVICE_GROUPS: { key: BookingRequiredServiceCategory; label: string; icon: string }[] = [
  { key: 'stall', label: 'Khoang trên xe', icon: 'fa-border-all' },
  { key: 'feed', label: 'Gói thức ăn', icon: 'fa-wheat-awn' },
  { key: 'insurance', label: 'Bảo hiểm chuyến đi', icon: 'fa-shield-halved' },
]

function legacySelection(category: BookingRequiredServiceCategory, service: ServiceCatalogItem): Partial<HorseConfig> {
  if (category === 'stall') return { stall: service.serviceCode === 'STALL_SINGLE_EXTENDED' ? 'single' : 'standard' }
  if (category === 'feed') {
    const feedPackage = service.serviceCode === 'FEED_ADVANCED' ? 'advanced'
      : service.serviceCode === 'FEED_SPORT' ? 'sport' : 'basic'
    return { feedPackage }
  }
  return { insurance: isDeclinedInsurance(service) ? 'decline' : 'buy' }
}

export default function Step3ServicesPage() {
  const navigate = useNavigate()
  const { draft, save } = useBookingDraft()
  const { horses, services = [] } = usePageData<typeof horseListLoader>()
  const [config, setConfig] = useState<Record<string, HorseConfig>>(() =>
    Object.fromEntries(draft.horseIds.map(id => [id, { ...defaultConfig(), ...draft.config[id] }])))
  const [activeIndex, setActiveIndex] = useState(0)
  const [ack, setAck] = useState(false)
  const [submitted, setSubmitted] = useState(false)

  useEffect(() => {
    if (submitted) document.querySelector('[data-problem]')?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  }, [activeIndex, submitted])

  if (!draft.type || !draft.departDate) return <Navigate to="/booking/route" replace />
  if (!draft.horseIds.length) return <Navigate to="/booking/horses" replace />

  const rows = draft.horseIds.map(id => horses?.find(h => h.id === id)).filter(Boolean) as NonNullable<typeof horses>
  const activeHorse = rows[activeIndex]
  const set = (id: string, patch: Partial<HorseConfig>) => setConfig(current => ({ ...current, [id]: { ...current[id], ...patch } }))
  const declined = rows.filter(h => isDeclinedInsurance(serviceOptionFor(services, config[h.id], 'insurance')))
  const problems = rows.length === 0 || rows.length !== draft.horseIds.length
    ? ['Không tải được đủ hồ sơ ngựa hoặc danh mục dịch vụ. Vui lòng tải lại trang.']
    : rows.flatMap(h => SERVICE_GROUPS
      .filter(group => !serviceOptionFor(services, config[h.id], group.key))
      .map(group => `${h.name}: hiện không có dịch vụ ${group.label.toLocaleLowerCase()} đang áp dụng`))
  problems.push(...rows.filter(h => !config[h.id].insurance && !config[h.id].selectedServiceCodes?.insurance
    && !config[h.id].selectedServices?.insurance)
    .map(h => `${h.name}: chọn dịch vụ bảo hiểm chuyến đi`))
  if (declined.length && !ack) problems.push('Xác nhận Điều khoản Trách nhiệm Hạn chế')

  const next = () => {
    setSubmitted(true)
    if (problems.length) {
      if (rows.length === 0 || rows.length !== draft.horseIds.length) return
      const firstMissing = rows.findIndex(h => SERVICE_GROUPS.some(group => !serviceOptionFor(services, config[h.id], group.key))
        || (!config[h.id].insurance && !config[h.id].selectedServiceCodes?.insurance
          && !config[h.id].selectedServices?.insurance))
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
    <BookingShell step={3} title="Dịch vụ và bảo hiểm" subtitle="Các lựa chọn và đơn giá được cập nhật theo danh mục dịch vụ đang áp dụng.">
      {activeHorse && (
        <>
          <div className={s.horseNavigator} aria-label="Chọn ngựa để thiết lập dịch vụ">
            <label className={s.horseSelectLabel}>
              <span>Ngựa {activeIndex + 1}/{rows.length}</span>
              <select className="form-control" value={activeHorse.id} onChange={e => setActiveIndex(rows.findIndex(h => h.id === e.target.value))}>
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

                {SERVICE_GROUPS.map(group => {
                  const prefix = `${group.key.toUpperCase()}_`
                  const options = services.filter(service => service.serviceCode.startsWith(prefix))
                  const chosen = serviceOptionFor(services, current, group.key)
                  return (
                    <div className={s.serviceSection} key={group.key}>
                      <div className={`${s.fieldLabel} ${group.key === 'insurance' ? 'required' : ''}`} id={`${group.key}-${horse.id}`}>
                        <i className={`fa-solid ${group.icon}`} aria-hidden="true" /> {group.label}
                      </div>
                      <div className={s.optRow} role="radiogroup" aria-labelledby={`${group.key}-${horse.id}`}
                        {...(submitted && (!chosen || (group.key === 'insurance' && !current.insurance
                          && !current.selectedServiceCodes?.insurance && !current.selectedServices?.insurance))
                          ? { 'data-problem': true } : {})}>
                        {options.length ? options.map(service => {
                          const declinedInsurance = group.key === 'insurance' && isDeclinedInsurance(service)
                          const price = group.key === 'insurance'
                            ? declinedInsurance ? 0 : insuranceFee(horse.breed)
                            : service.price
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
                            <span>{declinedInsurance
                              ? 'Không mua bảo hiểm'
                              : price > 0
                                ? `+${formatVND(price)} / ${group.key === 'insurance' ? 'ngựa' : service.billingUnit === 'PER_HORSE_DAY' ? 'ngựa × ngày' : service.billingUnit === 'PER_HORSE' ? 'ngựa' : 'chuyến'}`
                                : 'Miễn phí / Đã gồm'}</span>
                            {service.description && <small>{service.description}</small>}
                          </label>
                          )
                        }) : <span>Hiện chưa có lựa chọn đang áp dụng.</span>}
                      </div>
                    </div>
                  )
                })}
              </section>
            )
          })()}
        </>
      )}

      {declined.length > 0 && (
        <div className="card" data-card {...(submitted && !ack ? { 'data-problem': true } : {})}>
          <label className={s.ack}>
            <input type="checkbox" checked={ack} onChange={e => setAck(e.target.checked)} />
            <span>Tôi hiểu và đồng ý <PolicyLink doc="liability">Điều khoản Trách nhiệm Hạn chế</PolicyLink> của nhà xe cho {declined.map(h => h.name).join(', ')}, những ngựa tôi không mua bảo hiểm.</span>
          </label>
          {submitted && !ack && <div className="form-error">Cần xác nhận để tiếp tục.</div>}
        </div>
      )}

      {submitted && problems.length > 0 && <div className="form-error">{problems[0]}</div>}

      <div className={s.actions}>
        <Link to="/booking/horses" className="btn btn-ghost"><i className="fa-solid fa-arrow-left" /> Chọn ngựa</Link>
        <button type="button" className="btn btn-primary" onClick={next}>Tiếp tục: Xác nhận <i className="fa-solid fa-arrow-right" /></button>
      </div>
    </BookingShell>
  )
}
