// Bảng giá: dùng chung cho trang chủ công khai và trang khách đã đăng nhập. Dùng đúng công thức báo giá thật (PRD mục 11).
import { useTranslation } from 'react-i18next'
import { CLASS_FACTOR, CLEARANCE_FEE, DEPOSIT_RATE, FUEL_BOT_PER_KM, FUEL_BUFFER_RATE, MARGIN_RATE, VEHICLE_CLASS, type VehicleClass } from '@shared/config/booking-rules'
import { formatVND } from '@shared/lib/format'
import { serviceCatalogApi, type ServiceBillingUnit } from '@shared/api/serviceCatalog'
import { useLoad } from '@shared/services/useLoad'
import { KM_TIERS, TRIP_OPEN_FEE } from '@shared/config/public-pricing'
import s from '../home/HomePage.module.css'

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')
const billingUnitLabelKey = {
  PER_TRIP: 'home.pricingTable.perTripUnit',
  PER_HORSE: 'home.pricingTable.perHorseTripUnit',
  PER_HORSE_DAY: 'home.pricingTable.perHorseDayUnit',
} as const satisfies Record<ServiceBillingUnit, string>

// ===== Bảng giá (cùng công thức với báo giá) =====
const CLASSES = Object.keys(VEHICLE_CLASS) as VehicleClass[]
export function PriceTable() {
  const { t } = useTranslation()
  const { data: services, loaded, reload } = useLoad(serviceCatalogApi.listActive)
  const freightTiers = [
    { label: t('home.pricingTable.firstKmTier', { limit: KM_TIERS[0][0] }), rate: KM_TIERS[0][1] },
    { label: t('home.pricingTable.secondKmTier', { from: KM_TIERS[0][0] + 1, to: KM_TIERS[1][0] }), rate: KM_TIERS[1][1] },
    { label: t('home.pricingTable.thirdKmTier', { limit: KM_TIERS[1][0] }), rate: KM_TIERS[2][1] },
  ]

  return (
    <>
      <div className={s.priceGrid}>
        <div className={s.priceCard}>
          <h3><i className="fa-solid fa-truck" /> {t('home.pricingTable.freightTitle')}</h3>
          <table className={s.priceTable}>
            <thead>
              <tr>
                <th className={s.colText}>{t('home.pricingTable.freightTier')}</th>
                {CLASSES.map(c => <th key={c} className={s.colNum}>{VEHICLE_CLASS[c].label} ({VEHICLE_CLASS[c].stalls})</th>)}
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className={s.colText}><b>{t('home.pricingTable.tripOpenFee')}</b></td>
                {CLASSES.map(c => (
                  <td key={c} className={s.colNum}>
                    {formatVND(TRIP_OPEN_FEE * CLASS_FACTOR[c] * (1 + MARGIN_RATE))}
                  </td>
                ))}
              </tr>
              {freightTiers.map(tier => (
                <tr key={tier.label}>
                  <td className={s.colText}><b>{tier.label}</b></td>
                  {CLASSES.map(c => (
                    <td key={c} className={s.colNum}>
                      {formatVND(tier.rate * CLASS_FACTOR[c] * (1 + MARGIN_RATE))}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className={s.priceCard}>
          <h3><i className="fa-solid fa-horse-head" /> {t('home.pricingTable.otherFixedTitle')}</h3>
          <table className={s.priceTable}>
            <thead>
              <tr>
                <th className={s.colText}>{t('home.pricingTable.category')}</th>
                <th className={s.colNum}>{t('home.pricingTable.rate')}</th>
              </tr>
            </thead>
            <tbody>
              <tr><td className={s.colText}>{t('home.pricingTable.fuelRow')}</td><td className={s.colNum}>{formatVND(FUEL_BOT_PER_KM * (1 + FUEL_BUFFER_RATE))}{t('home.pricingTable.fuelUnit')}</td></tr>
              {!loaded
                ? <tr><td className={s.colText} colSpan={2}>Đang tải bảng giá dịch vụ…</td></tr>
                : !services
                  ? <tr><td className={s.colText} colSpan={2}>Không tải được bảng giá dịch vụ. <button type="button" className="btn btn-ghost btn-sm" onClick={reload}>Thử lại</button></td></tr>
                  : services.length
                    ? services.filter(service => service.categoryId !== 4).map(service => (
                        <tr key={service.id}>
                          <td className={s.colText}>{service.categoryName} · {service.serviceName}</td>
                          <td className={s.colNum}>{service.price > 0
                              ? `${formatVND(service.price)} / ${t(billingUnitLabelKey[service.billingUnit])}`
                            : t('home.pricingTable.freeIncluded')}</td>
                        </tr>
                      ))
                    : <tr><td className={s.colText} colSpan={2}>Chưa có dịch vụ đang áp dụng.</td></tr>}
              <tr><td className={s.colText}>{t('home.pricingTable.insuranceRow')}</td><td className={s.colNum}>{t('home.pricingTable.insuranceVal')}</td></tr>
              <tr><td className={s.colText}>{t('home.pricingTable.domesticClearance')}</td><td className={s.colNum}>{CLEARANCE_FEE.domestic ? formatVND(CLEARANCE_FEE.domestic) : t('home.pricingTable.domesticIncluded')}</td></tr>
              <tr><td className={s.colText}>{t('home.pricingTable.intlClearance')}</td><td className={s.colNum}>{formatVND(CLEARANCE_FEE.international)}{t('home.pricingTable.perTrip')}</td></tr>
            </tbody>
          </table>
        </div>
      </div>
      <div className={s.priceNotes}>
        <div><i className="fa-solid fa-wallet" />{t('home.pricingTable.depositNote', { rate: DEPOSIT_RATE * 100, balance: 100 - DEPOSIT_RATE * 100 })}</div>
        <div><i className="fa-solid fa-kit-medical" />{t('home.pricingTable.incidentNote')}</div>
      </div>
    </>
  )
}

// Khung Bảng giá cho trang khách đã đăng nhập (trang chủ công khai dùng PriceTable trong tab Bảng giá)
export function PriceLookup() {
  return (
    <section className={cx('wrap', s.lookup)} style={{ padding: 0 }}>
      <div className={s.lookupCard}>
        <div className={cx(s.lookupPanel, s.active)}>
          <PriceTable />
        </div>
      </div>
    </section>
  )
}
