import { DEPOSIT_RATE } from '../config/booking-rules'
import { formatDate, formatDateTime, formatVND, formatVNDWords } from '../lib/format'
import { PolicyLink } from './PolicyLink'
import type { Adjustment, Party, QuoteLine } from '../types/booking'
import s from './QuoteSheet.module.css'

export interface QuoteSheetProps {
  lines: QuoteLine[]
  adjustments?: Adjustment[]
  subtotal: number
  total: number
  deposit: number
  balance: number
  expiresAt?: number
  showCalculation?: boolean

  // Extended fields for commercial e-invoice standard
  bookingCode?: string
  waybillNo?: string
  paymentRef?: string
  consignor?: Partial<Party>
  consignee?: Partial<Party>
  routeDetails?: {
    originName?: string
    destName?: string
    departAt?: number
    distanceKm?: number
    vehicleType?: string
  }
  invoiceDate?: number
  title?: string
  showPrintButton?: boolean
}

// Giải thích chi tiết nghiệp vụ tính toán
function calculationOf(line: QuoteLine) {
  if (line.calculation) return line.calculation
  const l = line.label.toLowerCase()
  if (l.includes('cước vận chuyển') || l.includes('cước cự ly')) return 'Tính theo bảng cước quãng đường bậc thang và hệ số hạng xe chuyên dụng.'
  if (l.includes('nhân sự')) return `Dựa trên số ngày hành trình (${line.detail}), kíp xe gồm 01 tài xế chuyên tuyến và 01 hộ tống chăm sóc an sinh ngựa.`
  if (l.includes('nhiên liệu')) return `Ước tính tiêu hao nhiên liệu theo km thực tế và phí cao tốc/BOT; ${line.detail.toLowerCase()}.`
  if (l.includes('kiểm dịch') || l.includes('hải quan')) return 'Trọn gói hồ sơ kiểm dịch thú y liên tỉnh / thông quan xuất nhập cảnh tại cửa khẩu quốc tế.'
  if (l.includes('bảo hiểm')) return 'Phí bảo hiểm chuyến đi tính theo tỷ lệ phí 2% trên định mức giá trị của giống ngựa áp tải.'
  return undefined
}

// Nhận diện các khoản mục cước vận hành chuyến đi để gộp
function isFreightLine(label: string) {
  const l = label.toLowerCase()
  return (
    l.includes('cước vận chuyển') ||
    l.includes('cước cự ly') ||
    l.includes('nhân sự kỹ thuật') ||
    l.includes('nhân sự chuyên môn') ||
    l.includes('nhiên liệu')
  )
}

function getLineUnitAndQty(label: string): { unit: string; qty: number } {
  const l = label.toLowerCase()
  if (l.includes('khoang đơn')) return { unit: 'Khoang', qty: 1 }
  if (l.includes('dinh dưỡng') || l.includes('thức ăn')) return { unit: 'Gói/Ngựa', qty: 1 }
  if (l.includes('bảo hiểm')) return { unit: 'Gói/Ngựa', qty: 1 }
  if (l.includes('kiểm dịch') || l.includes('hải quan')) return { unit: 'Bộ hồ sơ', qty: 1 }
  return { unit: 'Dịch vụ', qty: 1 }
}

export function QuoteSheet({
  lines,
  adjustments = [],
  subtotal,
  total,
  deposit,
  balance,
  expiresAt,
  showCalculation = false,
  bookingCode,
  waybillNo,
  paymentRef,
  consignor,
  consignee,
  routeDetails,
  invoiceDate,
  title,
}: QuoteSheetProps) {

  const depositPercentage =
    total > 0
      ? Number(((deposit / total) * 100).toFixed(2))
      : DEPOSIT_RATE * 100

  // Bóc tách cước xe + nhân sự + nhiên liệu để gộp vào "Cước dịch vụ vận chuyển ngựa trọn gói"
  const freightLines = lines.filter(l => isFreightLine(l.label))
  const otherLines = lines.filter(l => !isFreightLine(l.label))
  const canBundleFreight = freightLines.length > 1
  const bundledFreightAmount = freightLines.reduce((sum, l) => sum + (l.amount || 0), 0)

  // Mã định danh hóa đơn / báo giá
  const displayCode = bookingCode ? (bookingCode.startsWith('BK-') ? bookingCode : `BK-${bookingCode}`) : 'BK-2026-DEMO'
  const createdDate = invoiceDate ? formatDate(invoiceDate) : formatDate(Date.now())

  return (
    <div className={s.invoiceWrap}>
      {/* Top action toolbar */}
      <div className={s.topToolbar}>
        <div className={s.docBadge}>
          <i className="fa-solid fa-file-invoice" /> Phiếu Báo Giá Kiêm Dự Thảo Hóa Đơn Điện Tử
        </div>
      </div>

      {/* Header Công ty & Mẫu số ký hiệu */}
      <div className={s.header}>
        <div className={s.companyInfo}>
          <div className={s.companyLogoTitle}>
            <img src="/logo.png" alt="RaceHorse Transport" className={s.brandLogo} />
            <div className={s.companyTitleCol}>
              <span className={s.companyName}>CÔNG TY CỔ PHẦN VẬN CHUYỂN NGỰA ĐUA RACEHORSE TRANSPORT</span>
              <div className={s.companyMeta}>
                <div><b>Mã số thuế:</b> <span className={s.taxCode}>0102026391-DEMO</span></div>
                <div><b>Trụ sở:</b> Khu Đua Ngựa & Trung Tâm Logistics Vận Tải Cao Cấp, TP. Hồ Chí Minh</div>
                <div><b>Hotline:</b> 1900 6868 · <b>Email:</b> billing@racehorsetransport.vn</div>
              </div>
            </div>
          </div>
        </div>

        <div className={s.invoiceMetaBox}>
          <h2 className={s.invoiceTitle}>{title || 'HÓA ĐƠN BÁO GIÁ'}</h2>
          <div className={s.metaRow}>
            <span>Mẫu số:</span>
            <b>01GTKT0/001</b>
          </div>
          <div className={s.metaRow}>
            <span>Ký hiệu:</span>
            <b>C26RHT</b>
          </div>
          <div className={s.metaRow}>
            <span>Số phiếu:</span>
            <b>{displayCode}</b>
          </div>
          <div className={s.metaRow}>
            <span>Ngày lập:</span>
            <b>{createdDate}</b>
          </div>
          {waybillNo && (
            <div className={s.metaRow}>
              <span>Mã vận đơn:</span>
              <b>{waybillNo}</b>
            </div>
          )}
          {paymentRef && (
            <div className={s.metaRow}>
              <span>Mã giao dịch:</span>
              <b>{paymentRef}</b>
            </div>
          )}
        </div>
      </div>

      {/* Thông tin Bên gửi (Consignor) & Bên nhận (Consignee) */}
      {(consignor || consignee) && (
        <div className={s.partiesGrid}>
          {/* Bên gửi */}
          <div className={s.partyCard}>
            <div className={s.partyHeader}>
              <span className={s.partyRole}>
                <i className="fa-solid fa-truck-ramp-box" /> Bên gửi hàng (Consignor)
              </span>
            </div>
            <div className={s.partyName}>{consignor?.name || 'Khách hàng cá nhân / Tổ chức gửi'}</div>
            <div className={s.partyDetail}>
              <div><b>Điện thoại:</b> {consignor?.phone || 'Chưa cung cấp'}</div>
              <div><b>CCCD / MST / HC:</b> {consignor?.idNumber || 'Chưa cung cấp'}</div>
              <div><b>Địa chỉ lấy ngựa:</b> {consignor?.address || 'Theo thỏa thuận lộ trình đón'}</div>
            </div>
          </div>

          {/* Bên nhận */}
          <div className={s.partyCard}>
            <div className={s.partyHeader}>
              <span className={s.partyRole}>
                <i className="fa-solid fa-location-dot" /> Bên nhận hàng (Consignee)
              </span>
            </div>
            <div className={s.partyName}>{consignee?.name || 'Chủ chuồng / Người nhận đại diện'}</div>
            <div className={s.partyDetail}>
              <div><b>Điện thoại:</b> {consignee?.phone || 'Chưa cung cấp'}</div>
              <div><b>CCCD / MST / HC:</b> {consignee?.idNumber || 'Chưa cung cấp'}</div>
              <div><b>Địa chỉ giao ngựa:</b> {consignee?.address || 'Theo thỏa thuận lộ trình trả'}</div>
            </div>
          </div>
        </div>
      )}

      {/* Tóm tắt hành trình nếu có */}
      {routeDetails && (routeDetails.originName || routeDetails.destName) && (
        <div className={s.routeSnippet}>
          <i className="fa-solid fa-route" style={{ color: 'var(--orange)' }} />
          <span><b>Tuyến hành trình:</b> {routeDetails.originName} ➔ {routeDetails.destName}</span>
          {routeDetails.distanceKm ? <span>· <b>Cự ly:</b> {routeDetails.distanceKm} km</span> : null}
          {routeDetails.vehicleType ? <span>· <b>Phương tiện:</b> {routeDetails.vehicleType}</span> : null}
          {routeDetails.departAt ? <span>· <b>Khởi hành:</b> {formatDateTime(routeDetails.departAt)}</span> : null}
        </div>
      )}

      {/* Bảng kê chi phí hàng hóa & dịch vụ chuẩn hóa */}
      <div className={s.tableWrap}>
        <table className={s.table}>
          <thead>
            <tr>
              <th style={{ width: '45px' }} className={s.colCenter}>STT</th>
              <th>Tên Dịch Vụ, Hàng Hóa</th>
              <th style={{ width: '90px' }} className={s.colCenter}>ĐVT</th>
              <th style={{ width: '70px' }} className={s.colCenter}>SL</th>
              <th style={{ width: '150px' }} className={s.colRight}>Đơn Giá (VNĐ)</th>
              <th style={{ width: '160px' }} className={s.colRight}>Thành Tiền (VNĐ)</th>
            </tr>
          </thead>
          <tbody>
            {/* Gộp dòng cước trọn gói nếu có nhiều thành phần xe/tài xế/nhiên liệu */}
            {canBundleFreight ? (
              <tr className={s.bundleRow}>
                <td className={`${s.colCenter} ${s.numCol}`}>1</td>
                <td>
                  <div className={s.bundleTitle}>Cước dịch vụ vận chuyển ngựa trọn gói</div>
                  <div className={s.bundleDesc}>
                    Vận chuyển nguyên chuyến bằng phương tiện chuyên dụng theo lộ trình thỏa thuận, kíp phục vụ tiêu chuẩn gồm 01 tài xế chuyên tuyến + 01 hộ tống chuyên môn; đã bao trọn toàn bộ chi phí nhiên liệu và phí cầu đường cao tốc.
                  </div>
                </td>
                <td className={s.colCenter}>Chuyến</td>
                <td className={`${s.colCenter} ${s.numCol}`}>1</td>
                <td className={s.colRight}>{formatVND(bundledFreightAmount)}</td>
                <td className={`${s.colRight} ${s.numCol}`}>{formatVND(bundledFreightAmount)}</td>
              </tr>
            ) : (
              // Nếu không đủ nhiều thành phần để gộp, hiện freightLines bình thường
              freightLines.map((l, idx) => (
                <tr key={l.label}>
                  <td className={`${s.colCenter} ${s.numCol}`}>{idx + 1}</td>
                  <td>
                    <div className={s.itemName}>{l.label}</div>
                    {l.detail && <div className={s.itemDetail}>{l.detail}</div>}
                    {showCalculation && calculationOf(l) && (
                      <div className={s.calculation}>
                        <b>Cách tính:</b> {calculationOf(l)}
                      </div>
                    )}
                  </td>
                  <td className={s.colCenter}>Chuyến</td>
                  <td className={`${s.colCenter} ${s.numCol}`}>1</td>
                  <td className={s.colRight}>{l.amount ? formatVND(l.amount) : 'Đã gồm'}</td>
                  <td className={`${s.colRight} ${s.numCol}`}>{l.amount ? formatVND(l.amount) : 'Đã gồm'}</td>
                </tr>
              ))
            )}

            {/* Các dịch vụ phụ trợ, dinh dưỡng, khoang đơn, bảo hiểm, hải quan */}
            {otherLines.map((l, idx) => {
              const stt = (canBundleFreight ? 1 : freightLines.length) + idx + 1
              const { unit, qty } = getLineUnitAndQty(l.label)
              return (
                <tr key={l.label}>
                  <td className={`${s.colCenter} ${s.numCol}`}>{stt}</td>
                  <td>
                    <div className={s.itemName}>{l.label}</div>
                    {l.detail && <div className={s.itemDetail}>{l.detail}</div>}
                    {showCalculation && calculationOf(l) && (
                      <div className={s.calculation}>
                        <b>Cách tính:</b> {calculationOf(l)}
                      </div>
                    )}
                  </td>
                  <td className={s.colCenter}>{unit}</td>
                  <td className={`${s.colCenter} ${s.numCol}`}>{qty}</td>
                  <td className={s.colRight}>{l.amount ? formatVND(l.amount) : 'Miễn phí'}</td>
                  <td className={`${s.colRight} ${s.numCol}`}>{l.amount ? formatVND(l.amount) : 'Miễn phí'}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {/* Bảng tổng kết số tiền & Chiết khấu */}
      <div className={s.summaryBlock}>
        <table className={s.summaryTable}>
          <tbody>
            <tr>
              <td className={s.summaryLabel}>Cộng tiền dịch vụ:</td>
              <td className={s.summaryVal}>{formatVND(subtotal)}</td>
            </tr>

            {adjustments.map(a => (
              <tr key={a.label} className={a.amount < 0 ? s.discountRow : ''}>
                <td className={s.summaryLabel}>{a.label}:</td>
                <td className={s.summaryVal}>
                  {a.amount < 0 ? '− ' : '+ '}
                  {formatVND(Math.abs(a.amount))}
                </td>
              </tr>
            ))}

            <tr className={s.totalRow}>
              <td className={s.summaryLabel}>Tổng cộng thanh toán:</td>
              <td className={s.summaryVal}>{formatVND(total)}</td>
            </tr>
          </tbody>
        </table>

        {/* Số tiền viết bằng chữ */}
        <div className={s.wordsBox}>
          <b>Số tiền viết bằng chữ:</b> <span>{formatVNDWords(total)}</span>
        </div>
      </div>

      {/* Lộ trình thanh toán 2 giai đoạn */}
      <div className={s.paymentStages}>
        <div className={`${s.paymentCard} ${s.depositCard}`}>
          <div className={s.paymentCardTitle}>
            <span>Đợt 1: Đặt cọc ({depositPercentage.toLocaleString('vi-VN')}%)</span>
            <i className="fa-solid fa-lock" />
          </div>
          <div className={s.paymentAmount}>{formatVND(deposit)}</div>
          <div className={s.paymentNote}>
            Thanh toán để xác nhận đơn vận chuyển, xuất Vận đơn điện tử và khóa lịch kíp xe chuyên dụng.
            {expiresAt && <div><b>Hạn thanh toán cọc:</b> {formatDateTime(expiresAt)}</div>}
          </div>
        </div>

        <div className={s.paymentCard}>
          <div className={s.paymentCardTitle}>
            <span>Đợt 2: Còn lại ({(100 - depositPercentage).toLocaleString('vi-VN')}%)</span>
            <i className="fa-solid fa-hand-holding-dollar" />
          </div>
          <div className={s.paymentAmount}>{formatVND(balance)}</div>
          <div className={s.paymentNote}>
            Thanh toán vào ngày khởi hành (D) khi Driver và Hộ tống hoàn tất thủ tục bàn giao và bốc ngựa lên xe.
          </div>
        </div>
      </div>

      {/* Chữ ký số và con dấu điện tử */}
      <div className={s.signaturesGrid}>
        <div className={s.signatureCol}>
          <div className={s.sigTitle}>Người Mua / Bên Gửi Hàng</div>
          <div className={s.sigSub}>(Ký và ghi rõ họ tên)</div>
          <div className={s.buyerSignPlaceholder}>
            {consignor?.name ? consignor.name : 'Khách hàng xác nhận'}
          </div>
        </div>

        <div className={s.signatureCol}>
          <div className={s.sigTitle}>Đại Diện Đơn Vị Vận Chuyển</div>
          <div className={s.sigSub}>(Ký số điện tử, đóng dấu)</div>
          <div className={s.digitalSignBadge}>
            <i className="fa-solid fa-circle-check" />
            <div className={s.digitalSignText}>Đã Ký Số Điện Tử Hợp Lệ</div>
            <div className={s.digitalSignOrg}>CÔNG TY CP VẬN CHUYỂN NGỰA ĐUA RACEHORSE TRANSPORT</div>
            <div className={s.digitalSignTime}>Thời gian ký: {createdDate}</div>
          </div>
        </div>
      </div>

      {/* Cam kết chính sách & ghi chú */}
      <div className={s.policyFooter}>
        <div>
          <i className="fa-solid fa-shield-halved" /> <b>Cam kết giá trọn gói không phụ thu:</b> Mọi chi phí nhiên liệu, phí cầu đường cao tốc và công tác phí kíp xe đã được bao trọn trong báo giá. Phát sinh chỉ tính khi có dịch vụ bổ sung theo yêu cầu hoặc xử lý sự cố an sinh (xem <PolicyLink doc="incident_cost">chính sách chi phí sự cố</PolicyLink>).
        </div>
        <div>
          <i className="fa-solid fa-circle-info" /> Chứng từ này là Phiếu báo giá chính thức kiêm dự thảo hóa đơn dịch vụ vận chuyển điện tử theo chuẩn quy định hiện hành.
        </div>
      </div>
    </div>
  )
}
