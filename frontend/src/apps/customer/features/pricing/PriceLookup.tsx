// Bảng giá: dùng chung cho trang chủ công khai và trang khách đã đăng nhập. Dùng đúng công thức báo giá thật (PRD mục 11).
import { useTranslation } from 'react-i18next'
import { CLASS_FACTOR, CLEARANCE_FEE, DEMURRAGE_PER_HOUR, DEPOSIT_RATE, FUEL_BOT_PER_KM, FUEL_BUFFER_RATE, MARGIN_RATE, QUOTE_VALID_HOURS, VEHICLE_CLASS, type VehicleClass } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { formatVND } from '@shared/lib/format'
import { truckCost } from '@shared/lib/pricing'
import { SERVICE_BILLING_UNIT_LABEL, serviceCatalogApi } from '@shared/api/serviceCatalog'
import { useLoad } from '@shared/services/useLoad'
import s from '../home/HomePage.module.css'

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')
const roundK = (n: number) => Math.round(n / 1000) * 1000

// ===== Bảng giá (cùng công thức với báo giá) =====
const CLASSES = Object.keys(VEHICLE_CLASS) as VehicleClass[]
const PRICE_TIER_KM = [50, 150, 300, 600, 1000, 1500]
export function PriceTable() {
  const { t } = useTranslation()
  const { data: services, loaded, reload } = useLoad(serviceCatalogApi.listActive)
  return (
    <>
      <div className={s.priceGrid}>
        <div className={s.priceCard}>
          <h3><i className="fa-solid fa-truck" /> {t('home.pricingTable.freightTitle')}</h3>
          <table className={s.priceTable}>
            <thead>
              <tr>
                <th className={s.colText}>{t('home.pricingTable.distance')}</th>
                {CLASSES.map(c => <th key={c} className={s.colNum}>{VEHICLE_CLASS[c].label} ({VEHICLE_CLASS[c].stalls})</th>)}
              </tr>
            </thead>
            <tbody>
              {PRICE_TIER_KM.map(km => (
                <tr key={km}>
                  <td className={s.colText}><b>{km} km</b></td>
                  {CLASSES.map(c => (
                    <td key={c} className={s.colNum}>
                      {formatVND(roundK(truckCost(km, false) * CLASS_FACTOR[c] * (1 + MARGIN_RATE)))}
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
              <tr><td className={s.colText}>{t('home.pricingTable.fuelRow')}</td><td className={s.colNum}>{formatVND(roundK(100 * FUEL_BOT_PER_KM * (1 + FUEL_BUFFER_RATE) * (1 + MARGIN_RATE)))}{t('home.pricingTable.fuelUnit')}</td></tr>
              {!loaded
                ? <tr><td className={s.colText} colSpan={2}>Đang tải bảng giá dịch vụ…</td></tr>
                : !services
                  ? <tr><td className={s.colText} colSpan={2}>Không tải được bảng giá dịch vụ. <button type="button" className="btn btn-ghost btn-sm" onClick={reload}>Thử lại</button></td></tr>
                  : services.length
                    ? services.filter(service => service.categoryId !== 4).map(service => (
                        <tr key={service.id}>
                          <td className={s.colText}>{service.categoryName} · {service.serviceName}</td>
                          <td className={s.colNum}>{service.price > 0
                            ? `${formatVND(service.price)} / ${SERVICE_BILLING_UNIT_LABEL[service.billingUnit]}`
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
        <div><i className="fa-solid fa-calendar-check" />{t('home.pricingTable.leadNote', { days: MIN_LEAD_DAYS })}</div>
        <div><i className="fa-solid fa-receipt" />{t('home.pricingTable.fixedNote', { hours: QUOTE_VALID_HOURS })}</div>
        <div><i className="fa-solid fa-kit-medical" />{t('home.pricingTable.incidentNote')}</div>
        <div><i className="fa-solid fa-clock" />{t('home.pricingTable.demurrageNote', { fee: formatVND(DEMURRAGE_PER_HOUR) })}</div>
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
