// Bước 3: chọn hạng dịch vụ (giống hạng vé) và bảo hiểm cho từng ngựa.
import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import { formatVND } from '@shared/lib/format'
import { FEEDING_RATION_MAX, FEEDING_USUAL_TIMES_MAX } from '@shared/lib/feeding'
import { CustomerInsuranceFields } from '@shared/ui/InsuranceFields'
import { usePageData } from '@shared/routing/pageData'
import { SEX_LABEL } from '@shared/types/booking'
import { PolicyLink } from '@shared/ui/PolicyLink'
import { BookingShell } from './BookingShell'
import type { horseListLoader } from '../horses/loaders'
import { defaultConfig, tierOf, tierOptions, tierSelection, useBookingDraft, type HorseConfig } from './draft'
import { isBookingRouteReady } from './routeValidation'
import s from './Booking.module.css'

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

  const en = i18n.language === 'en'
  // Hạng đang bán được theo danh mục dịch vụ đang áp dụng; hạng thiếu dịch vụ khoang hoặc thức ăn thì không hiện
  const tiers = tierOptions(services)
  const chosenTier = (id: string) => tiers.find(option => option.tier.id === tierOf(config[id]))

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
    : !tiers.length
      ? ['Hiện không có hạng dịch vụ nào đang áp dụng. Vui lòng thử lại sau.']
      : rows.filter(h => !chosenTier(h.id)).map(h => `${h.name}: chọn hạng dịch vụ`)
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
      const firstMissing = rows.findIndex(h => !chosenTier(h.id))
      if (firstMissing >= 0) setActiveIndex(firstMissing)
      return
    }
    save({ config: Object.fromEntries(rows.map(h => [h.id, config[h.id]])) })
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

                <div className={s.serviceSection}>
                  <div className={s.fieldLabel} id={`tier-${horse.id}`}>
                    <i className="fa-solid fa-ticket" aria-hidden="true" /> {en ? 'Service class' : 'Hạng dịch vụ'}
                  </div>
                  <div className={s.optRow} role="radiogroup" aria-labelledby={`tier-${horse.id}`}
                    {...(submitted && !chosenTier(horse.id) ? { 'data-problem': true } : {})}>
                    {tiers.length ? tiers.map(({ tier, stall, feed }) => (
                      <label key={tier.id} className={s.opt}>
                        <input
                          type="radio"
                          name={`tier-${horse.id}`}
                          checked={chosenTier(horse.id)?.tier.id === tier.id}
                          onChange={() => set(horse.id, tierSelection(current, tier))}
                        />
                        <b>{en ? tier.label.en : tier.label.vi}</b>
                        <span>{en ? 'Stall' : 'Khoang'}: {stall.price > 0
                          ? `${formatVND(stall.price)} / ${en ? 'horse' : 'ngựa'}`
                          : t('bookingWizard.freeIncluded')}</span>
                        <span>{en ? 'Feed' : 'Thức ăn'}: {feed.price > 0
                          ? `+${formatVND(feed.price)} / ${en ? 'horse × day' : 'ngựa × ngày'}`
                          : t('bookingWizard.freeIncluded')}</span>
                        {(en ? tier.includes.en : tier.includes.vi).map(item => <small key={item}>• {item}</small>)}
                      </label>
                    )) : <span>{t('bookingWizard.noOptions')}</span>}
                  </div>
                </div>
                <div className={s.serviceSection}>
                  <div className={s.fieldLabel}>
                    <i className="fa-solid fa-wheat-awn" aria-hidden="true" /> {en ? 'Feeding notes (optional)' : 'Thông tin cho ăn (không bắt buộc)'}
                  </div>
                  <div className={s.grid2}>
                    <div className="form-group">
                      <label htmlFor={`feed-times-${horse.id}`}>{en ? 'Usual feeding times' : 'Giờ ăn quen'}</label>
                      <input id={`feed-times-${horse.id}`} className="form-control" maxLength={FEEDING_USUAL_TIMES_MAX}
                        placeholder={en ? 'e.g. 07:00, 12:00, 18:00' : 'VD: 07:00, 12:00, 18:00'}
                        value={current.feeding?.usualTimes ?? ''}
                        onChange={e => set(horse.id, { feeding: { ...current.feeding, usualTimes: e.target.value } })} />
                    </div>
                    <div className="form-group">
                      <label htmlFor={`feed-ration-${horse.id}`}>{en ? 'Ration and notes' : 'Khẩu phần, ghi chú'}</label>
                      <input id={`feed-ration-${horse.id}`} className="form-control" maxLength={FEEDING_RATION_MAX}
                        placeholder={en ? 'e.g. 8 kg hay/day, 2 kg oats, no beans' : 'VD: 8 kg cỏ/ngày, 2 kg yến mạch, kiêng đậu'}
                        value={current.feeding?.ration ?? ''}
                        onChange={e => set(horse.id, { feeding: { ...current.feeding, ration: e.target.value } })} />
                    </div>
                  </div>
                  <label className={s.ack}>
                    <input type="checkbox" checked={current.feeding?.ownFeed === true}
                      onChange={e => set(horse.id, { feeding: { ...current.feeding, ownFeed: e.target.checked } })} />
                    <span>{en ? 'I will send my own feed with this horse' : 'Tôi gửi kèm thức ăn riêng cho ngựa này'}</span>
                  </label>
                </div>
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
