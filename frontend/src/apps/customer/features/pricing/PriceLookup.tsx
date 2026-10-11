// Bảng giá: dùng chung cho trang chủ công khai và trang khách đã đăng nhập. Dùng đúng công thức báo giá thật (PRD mục 11).
import { FEED_PACKAGE, FEED_PACKAGE_IDS, WATER_PLAN, WATER_PLAN_IDS, CLASS_FACTOR, CLEARANCE_FEE, CREW_FEE_PER_DAY, DEMURRAGE_PER_HOUR, DEPOSIT_RATE, FUEL_BOT_PER_KM, FUEL_BUFFER_RATE, HORSE_BREEDS, MARGIN_RATE, QUOTE_VALID_HOURS, SINGLE_STALL_FEE, VEHICLE_CLASS, type VehicleClass } from '@shared/config/booking-rules'
import { MIN_LEAD_DAYS } from '@shared/config/business-rules'
import { insuranceFee } from '@shared/lib/booking'
import { formatVND } from '@shared/lib/format'
import { truckCost } from '@shared/lib/pricing'
import s from '../home/HomePage.module.css'

const cx = (...c: (string | false | undefined)[]) => c.filter(Boolean).join(' ')
const roundK = (n: number) => Math.round(n / 1000) * 1000

// ===== Bảng giá (cùng công thức với báo giá) =====
const CLASSES = Object.keys(VEHICLE_CLASS) as VehicleClass[]
const SAMPLE_KM = [50, 150, 300, 600, 1000, 1500]
export function PriceTable() {
  return (
    <>
      <div className={s.priceGrid}>
        <div>
          <h3><i className="fa-solid fa-truck" /> Cước vận chuyển, mỗi xe</h3>
          <table className={s.priceTable}>
            <thead><tr><th>Quãng đường</th>{CLASSES.map(c => <th key={c}>{VEHICLE_CLASS[c].label} ({VEHICLE_CLASS[c].stalls})</th>)}</tr></thead>
            <tbody>{SAMPLE_KM.map(km => <tr key={km}><td>{km} km</td>{CLASSES.map(c => <td key={c}>{formatVND(roundK(truckCost(km, false) * CLASS_FACTOR[c] * (1 + MARGIN_RATE)))}</td>)}</tr>)}</tbody>
          </table>
        </div>
        <div>
          <h3><i className="fa-solid fa-horse-head" /> Các khoản cố định khác</h3>
          <table className={s.priceTable}>
            <thead><tr><th>Hạng mục</th><th>Đơn giá</th></tr></thead>
            <tbody>
              <tr><td>Nhân sự (01 tài xế + 01 hộ tống), mỗi xe</td><td>{formatVND(roundK(CREW_FEE_PER_DAY * (1 + MARGIN_RATE)))}/ngày</td></tr>
              <tr><td>Nhiên liệu và phí cầu đường, mỗi xe</td><td>{formatVND(roundK(100 * FUEL_BOT_PER_KM * (1 + FUEL_BUFFER_RATE) * (1 + MARGIN_RATE)))}/100 km</td></tr>
              <tr><td>Khoang đơn mở rộng</td><td>{formatVND(SINGLE_STALL_FEE)}/ngựa</td></tr>
              {FEED_PACKAGE_IDS.filter(id => FEED_PACKAGE[id].fee > 0).map(id => <tr key={id}><td>Thức ăn · {FEED_PACKAGE[id].label}</td><td>{formatVND(FEED_PACKAGE[id].fee)}/ngựa</td></tr>)}
              {WATER_PLAN_IDS.filter(id => WATER_PLAN[id].fee > 0).map(id => <tr key={id}><td>Cữ nước · {WATER_PLAN[id].label}</td><td>{formatVND(WATER_PLAN[id].fee)}/ngựa</td></tr>)}
              <tr><td>Thủ tục kiểm dịch (nội địa)</td><td>{CLEARANCE_FEE.domestic ? formatVND(CLEARANCE_FEE.domestic) : 'Đã gồm trong cước'}</td></tr>
              <tr><td>Thủ tục kiểm dịch và hải quan (quốc tế)</td><td>{formatVND(CLEARANCE_FEE.international)}/chuyến</td></tr>
              {HORSE_BREEDS.map(b => <tr key={b}><td>Bảo hiểm Động vật Sống · {b}</td><td>{formatVND(insuranceFee(b))}/ngựa</td></tr>)}
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
