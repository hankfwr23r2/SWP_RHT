// Bảng giá: dùng chung cho trang chủ công khai và trang khách đã đăng nhập. Dùng đúng công thức báo giá thật (PRD mục 11).
import { CLASS_FACTOR, CLEARANCE_FEE, CREW_FEE_PER_DAY, DEMURRAGE_PER_HOUR, DEPOSIT_RATE, FUEL_BOT_PER_KM, FUEL_BUFFER_RATE, MARGIN_RATE, QUOTE_VALID_HOURS, VEHICLE_CLASS, type VehicleClass } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { formatVND } from '@shared/lib/format'
import { truckCost } from '@shared/lib/pricing'
import { serviceCatalogApi } from '@shared/api/serviceCatalog'
import { useLoad } from '@shared/services/useLoad'
import s from '../home/HomePage.module.css'

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')
const roundK = (n: number) => Math.round(n / 1000) * 1000

// ===== Bảng giá (cùng công thức với báo giá) =====
const CLASSES = Object.keys(VEHICLE_CLASS) as VehicleClass[]
const PRICE_TIER_KM = [50, 150, 300, 600, 1000, 1500]
export function PriceTable() {
  const { data: services, loaded, reload } = useLoad(serviceCatalogApi.listActive)
  return (
    <>
      <div className={s.priceGrid}>
        <div className={s.priceCard}>
          <h3><i className="fa-solid fa-truck" /> Cước vận chuyển, mỗi xe</h3>
          <table className={s.priceTable}>
            <thead>
              <tr>
                <th className={s.colText}>Quãng đường</th>
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
          <h3><i className="fa-solid fa-horse-head" /> Các khoản cố định khác</h3>
          <table className={s.priceTable}>
            <thead>
              <tr>
                <th className={s.colText}>Hạng mục</th>
                <th className={s.colNum}>Đơn giá</th>
              </tr>
            </thead>
            <tbody>
              <tr><td className={s.colText}>Nhân sự (01 tài xế + 01 hộ tống), mỗi xe</td><td className={s.colNum}>{formatVND(roundK(CREW_FEE_PER_DAY * (1 + MARGIN_RATE)))}/ngày</td></tr>
              <tr><td className={s.colText}>Nhiên liệu và phí cầu đường, mỗi xe</td><td className={s.colNum}>{formatVND(roundK(100 * FUEL_BOT_PER_KM * (1 + FUEL_BUFFER_RATE) * (1 + MARGIN_RATE)))}/100 km</td></tr>
              {!loaded
                ? <tr><td className={s.colText} colSpan={2}>Đang tải bảng giá dịch vụ…</td></tr>
                : !services
                  ? <tr><td className={s.colText} colSpan={2}>Không tải được bảng giá dịch vụ. <button type="button" className="btn btn-ghost btn-sm" onClick={reload}>Thử lại</button></td></tr>
                  : services.length
                    ? services.map(service => (
                        <tr key={service.id}>
                          <td className={s.colText}>{service.categoryName} · {service.serviceName}</td>
                          <td className={s.colNum}>{service.price > 0 ? `${formatVND(service.price)}/ngựa` : 'Miễn phí / Đã gồm'}</td>
                        </tr>
                      ))
                    : <tr><td className={s.colText} colSpan={2}>Chưa có dịch vụ đang áp dụng.</td></tr>}
              <tr><td className={s.colText}>Thủ tục kiểm dịch (nội địa)</td><td className={s.colNum}>{CLEARANCE_FEE.domestic ? formatVND(CLEARANCE_FEE.domestic) : 'Đã gồm trong cước'}</td></tr>
              <tr><td className={s.colText}>Thủ tục kiểm dịch và hải quan (quốc tế)</td><td className={s.colNum}>{formatVND(CLEARANCE_FEE.international)}/chuyến</td></tr>
            </tbody>
          </table>
        </div>
      </div>
      <div className={s.priceNotes}>
        <div><i className="fa-solid fa-wallet" />Đặt cọc {DEPOSIT_RATE * 100}% để nhận vận đơn, {100 - DEPOSIT_RATE * 100}% còn lại trả vào ngày bốc ngựa.</div>
        <div><i className="fa-solid fa-calendar-check" />Đặt trước tối thiểu {MIN_LEAD_DAYS} ngày so với ngày khởi hành.</div>
        <div><i className="fa-solid fa-receipt" />Giá cố định, chưa gồm VAT, đã gồm nhiên liệu và cầu đường. Báo giá chính thức có hiệu lực {QUOTE_VALID_HOURS} giờ, không phụ thu ngoài phiếu.</div>
        <div><i className="fa-solid fa-kit-medical" />Chỉ phát sinh thêm khi có sự cố liên quan đến ngựa (thuốc, viện phí, chuồng đệm): có chứng từ và có Bảng quyết toán. Xe hỏng, tắc đường do nhà xe chịu.</div>
        <div><i className="fa-solid fa-clock" />Phí lưu xe chờ {formatVND(DEMURRAGE_PER_HOUR)}/giờ chỉ tính khi xe phải chờ do lỗi phía khách hoặc người nhận.</div>
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
