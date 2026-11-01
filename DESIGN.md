---
name: Vận chuyển Ngựa đua
description: Đặt và vận hành chuyến chở ngựa đua đường bộ Việt Nam · Lào · Campuchia
colors:
  orange: "#ea580c"
  orange-dark: "#c2410c"
  orange-deep: "#9a3412"
  orange-soft: "#fff7ed"
  navy: "#0f172a"
  navy-2: "#1e293b"
  muted: "#64748b"
  line: "#e2e8f0"
  line-office: "#ececf1"
  bg-soft: "#f4f4f5"
  bg-page: "#f8fafc"
  bg-page-office: "#fafafb"
  white: "#ffffff"
  green: "#059669"
  green-soft: "#ecfdf5"
  red: "#dc2626"
  red-soft: "#fef2f2"
  amber: "#b45309"
  amber-soft: "#fffbeb"
  blue: "#1d4ed8"
  blue-soft: "#eff6ff"
typography:
  display:
    fontFamily: "Roboto, sans-serif"
    fontSize: "clamp(2rem, 4.5vw, 3.2rem)"
    fontWeight: 800
    lineHeight: 1.15
  headline:
    fontFamily: "Roboto, sans-serif"
    fontSize: "clamp(1.4rem, 2.5vw, 1.8rem)"
    fontWeight: 800
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 700
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.55
  body-dense:
    fontFamily: "Roboto, sans-serif"
    fontSize: "0.88rem"
    fontWeight: 400
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 700
    letterSpacing: "0.04em"
rounded:
  sm: "8px"
  md: "12px"
  office-sm: "10px"
  office-md: "16px"
  office-modal: "20px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  2xl: "24px"
  3xl: "32px"
components:
  button-primary:
    backgroundColor: "{colors.orange-dark}"
    textColor: "{colors.white}"
    rounded: "{rounded.sm}"
    padding: "10px 20px"
  button-primary-hover:
    backgroundColor: "{colors.orange-deep}"
    textColor: "{colors.white}"
  button-outline:
    backgroundColor: "{colors.white}"
    textColor: "{colors.orange-dark}"
    rounded: "{rounded.sm}"
    padding: "10px 20px"
  button-ghost:
    backgroundColor: "{colors.white}"
    textColor: "{colors.navy-2}"
    rounded: "{rounded.sm}"
    padding: "10px 20px"
  button-navy:
    backgroundColor: "{colors.navy}"
    textColor: "{colors.white}"
    rounded: "{rounded.sm}"
    padding: "10px 20px"
  button-danger:
    backgroundColor: "{colors.red}"
    textColor: "{colors.white}"
    rounded: "{rounded.sm}"
    padding: "10px 20px"
  input:
    backgroundColor: "{colors.white}"
    textColor: "{colors.navy-2}"
    rounded: "{rounded.sm}"
    padding: "10px 12px"
  card:
    backgroundColor: "{colors.white}"
    rounded: "{rounded.md}"
    padding: "20px"
  badge-success:
    backgroundColor: "{colors.green-soft}"
    textColor: "{colors.green}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  badge-warning:
    backgroundColor: "{colors.amber-soft}"
    textColor: "{colors.amber}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  badge-danger:
    backgroundColor: "{colors.red-soft}"
    textColor: "{colors.red}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  badge-info:
    backgroundColor: "{colors.blue-soft}"
    textColor: "{colors.blue}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  table-header:
    backgroundColor: "{colors.bg-page}"
    textColor: "{colors.muted}"
    typography: "{typography.label}"
    padding: "10px 12px"
  table-total:
    backgroundColor: "{colors.navy}"
    textColor: "{colors.white}"
---

# Design System: Vận chuyển Ngựa đua

## Overview

**Creative North Star: "The Trusted Waybill" (Vận đơn đáng tin)**

Mỗi màn hình được đối xử như một tờ vận đơn in cẩn thận: lề thẳng, nhãn bên trái, giá trị bên phải, đường kẻ mảnh tách từng dòng, và không có gì mập mờ về tiền, hạn hay trạng thái. Người xem là chủ ngựa đang cân nhắc trả tiền cho việc chở một con vật quý, hoặc nhân viên đang chốt một bước nghiệp vụ. Cả hai cần đọc đúng con số ngay lần đầu.

Hệ thống sáng và lạnh: nền trắng và xám xanh rất nhạt, chữ xanh than đậm, một màu cam cháy duy nhất làm tín hiệu hành động. Xanh lá, hổ phách, đỏ, xanh dương chỉ nói về trạng thái, không trang trí. Mật độ ở mức vừa: thẻ chia nhóm thông tin, bên trong là các dòng nhãn–giá trị đặc. Component gọn gàng, chắc chắn: nút cam đặc nhích lên khi hover, viền mảnh, bóng rất nhẹ.

Có hai biến thể dáng trên cùng một bảng màu. Cổng khách hàng dùng góc bo vừa và bóng nhẹ. Backoffice (phạm vi `.mgr`) phẳng hẳn: bỏ bóng thẻ, bo góc lớn hơn, viền và nền ấm hơn một chút. Trang chủ công khai là nơi duy nhất được dùng ảnh nền tối, gradient và chuyển động chữ.

**Key Characteristics:**
- Một màu nhấn duy nhất (cam cháy) cho hành động và điểm cần chú ý.
- Màu trạng thái chỉ dùng cho trạng thái.
- Dòng nhãn–giá trị với đường kẻ đứt là đơn vị thông tin cơ bản.
- Một phông Roboto cho toàn hệ thống; tiêu đề đậm 800, nội dung 400.
- Viền mảnh 1px gánh phần lớn việc phân tách; bóng chỉ là phụ.

## Colors

Bảng màu lạnh và tiết chế: xanh than đậm làm chữ, xám xanh rất nhạt làm nền, cam cháy là màu duy nhất có cá tính.

### Primary
- **Cam cháy** (orange): icon trong tiêu đề thẻ, viền ô nhập khi focus, gạch chân tab đang chọn, thanh tải trang. Chỉ dùng cho icon, viền và vạch, không dùng cho chữ hay nền có chữ.
- **Cam đất** (orange-dark): nền nút chính, chữ và viền nút viền, link, chữ tab đang chọn, nhãn cam. Đây là tín hiệu "bấm vào đây"; đạt tương phản 5.2:1 với trắng.
- **Cam gỗ** (orange-deep): nút chính khi hover.
- **Kem cam** (orange-soft): nền nhạt cho nút viền khi hover, hàng bảng khi hover, số đếm của tab đang chọn, ô icon lợi ích.

### Neutral
- **Xanh than đêm** (navy): tiêu đề trang, tiêu đề thẻ, nhãn trường, giá trị số liệu lớn, nút phụ màu tối, dòng tổng cuối bảng (nền tối, chữ trắng).
- **Xanh than đá** (navy-2): màu chữ nội dung chính.
- **Xám đá** (muted): chữ phụ, nhãn trong dòng thông tin, tiêu đề cột bảng, gợi ý dưới ô nhập.
- **Viền sương** (line): mọi viền thẻ, ô nhập, đường kẻ bảng, đường kẻ đứt giữa các dòng thông tin. **Viền sương văn phòng** (line-office) thay thế trong backoffice.
- **Nền giấy** (bg-page): nền vùng nội dung trang, nền tiêu đề cột bảng. **Nền giấy văn phòng** (bg-page-office) thay thế trong backoffice.
- **Xám kẽm** (bg-soft): nền nhãn trạng thái trung tính và số đếm tab chưa chọn.
- **Trắng** (white): nền thẻ, hộp thoại, ô nhập.

### Trạng thái
- **Xanh lục bảo** (green, green-soft): đã xong, đã thanh toán, thành công.
- **Hổ phách** (amber, amber-soft): đang chờ, sắp hết hạn, cần lưu ý.
- **Đỏ tín hiệu** (red, red-soft): lỗi, hủy, SOS, trường bắt buộc, số đếm việc gấp trên menu.
- **Xanh dương mực** (blue, blue-soft): thông tin, đang xử lý.

### Named Rules
**The Readable Orange Rule.** Chữ cam và nền cam có chữ trắng luôn dùng cam đất, không dùng cam cháy: cam cháy chỉ đạt 3.6:1 với trắng, dưới chuẩn 4.5:1.

**The One Signal Rule.** Cam là màu nhấn duy nhất. Không thêm màu thương hiệu thứ hai; cần nhấn mạnh thêm thì dùng xanh than đậm.

**The Status-Only Rule.** Xanh lá, hổ phách, đỏ, xanh dương chỉ xuất hiện để nói trạng thái, luôn theo cặp màu đậm (chữ/icon) trên nền nhạt cùng họ. Không dùng chúng để trang trí.

## Typography

**Body Font:** Roboto (dự phòng sans-serif)

**Character:** Một phông sans-serif trung tính, đọc rõ tiếng Việt có dấu, dùng độ đậm (400 đến 800) thay cho phông thứ hai để tạo cấp bậc. Đúng tinh thần giấy tờ: rõ ràng, không làm dáng.

Hiện trạng: cổng khách hàng tải Roboto; backoffice đang chỉ tải Inter nên thực tế hiển thị Inter. Chuẩn là Roboto cho cả hai; backoffice cần sửa cho khớp.

### Hierarchy
- **Display** (800, clamp(2rem, 4.5vw, 3.2rem), 1.15): chỉ dùng cho tiêu đề banner trang chủ.
- **Headline** (800, clamp(1.4rem, 2.5vw, 1.8rem)): tiêu đề trang (`page-header h1`). Tiêu đề khu vực trên trang chủ dùng cỡ lớn hơn một chút (clamp(1.5rem, 3vw, 2rem)), viết hoa, căn giữa. Backoffice dùng 700, 1.6rem, letter-spacing −0.01em.
- **Title** (700, 1rem): tiêu đề thẻ, thường đi kèm icon cam bên trái; tiêu đề hộp thoại 1.05rem.
- **Body** (400, 1rem, 1.55): nội dung chung.
- **Body dense** (400, 0.88rem): bảng, dòng thông tin, hộp thông báo. Giá trị trong dòng thông tin đậm 600, căn phải.
- **Label** (700, 0.72rem, 0.04em, viết hoa): tiêu đề cột bảng. Nhãn trạng thái dùng 600, 0.75rem, không viết hoa. Nhãn trường biểu mẫu dùng 600, 0.85rem, màu xanh than đêm.

### Named Rules
**The Weight-Not-Font Rule.** Cấp bậc tạo bằng độ đậm và cỡ chữ, không bằng phông thứ hai. Phông đơn cách (monospace) chỉ dùng cho mã và số trong bảng báo giá.

## Layout

Khung nội dung rộng tối đa 1200px, chừa 16px mỗi bên trên màn hẹp. Vùng nội dung trang có đệm trên 32px, dưới 56px, nền giấy (bg-page). Tiêu đề trang cách nội dung 24px. Thẻ có đệm trong 20px, hai thẻ liền nhau cách 16px; phần đầu thẻ có đường kẻ dưới và cách thân thẻ 16px.

Nhịp khoảng cách theo bội số 4: 4, 8, 12, 16, 20, 24, 32px. Ô số liệu xếp lưới tự co giãn, mỗi ô tối thiểu 200px, khoảng cách 16px. Bảng nằm trong khung cuộn ngang để không vỡ trang trên màn hẹp.

Backoffice có thanh bên cố định rộng 264px (thu gọn còn 76px, chỉ còn icon) dưới thanh trên cao 64px. Dưới 860px thanh bên chuyển thành dải menu ngang cuộn được phía trên nội dung. Các điểm ngắt hay gặp: 700, 720, 760, 820, 860, 960, 1080px; chưa có thang điểm ngắt chung.

## Elevation & Depth

Hệ thống chủ yếu phẳng: viền mảnh 1px gánh việc tách lớp. Ở cổng khách hàng, thẻ có bóng rất nhẹ gần như không thấy (`0 2px 6px`, độ mờ 5%), chỉ đủ nhấc thẻ khỏi nền giấy. Bóng rõ chỉ xuất hiện khi có lớp thật sự nổi lên: hộp thoại, ô tra cứu đè lên banner trang chủ, nút chính khi hover (bóng cam). Backoffice bỏ hẳn bóng thẻ.

### Shadow Vocabulary
- **Thẻ nghỉ** (`box-shadow: 0 2px 6px rgba(15, 23, 42, 0.05)`): thẻ ở cổng khách hàng.
- **Lớp nổi** (`box-shadow: 0 16px 32px rgba(15, 23, 42, 0.12)`): hộp thoại ở cổng khách hàng.
- **Hộp thoại văn phòng** (`box-shadow: 0 24px 60px rgba(15, 23, 42, 0.18)`): hộp thoại trong backoffice.
- **Nút chính hover** (`box-shadow: 0 8px 18px rgba(234, 88, 12, 0.3)`): đi cùng việc nút nhích lên 2px.
- **Vòng focus** (`box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.15)`): ô nhập khi focus.

### Named Rules
**The Hairline-First Rule.** Tách lớp bằng viền 1px trước, bóng sau. Một thẻ mới không cần bóng đậm hơn "Thẻ nghỉ"; trong backoffice thì không có bóng.

## Shapes

Góc bo vừa phải, không vuông sắc cũng không tròn trịa: 8px cho nút và ô nhập, 12px cho thẻ và hộp thoại. Backoffice bo tròn hơn: nút và ô nhập 12px, nút nhỏ 10px, thẻ 16px, hộp thoại 20px. Nhãn trạng thái, số đếm và chấm chỉ báo luôn là viên thuốc (999px). Đường kẻ giữa các dòng thông tin là nét đứt; mọi đường kẻ khác là nét liền.

## Components

### Buttons
Gọn gàng, chắc chắn: chữ đậm 600, cỡ 0.9rem, có icon thì cách chữ 8px.
- **Shape:** bo 8px (backoffice 12px), viền 2px trong suốt để mọi biến thể cùng kích thước.
- **Primary:** nền cam đất, chữ trắng, đệm 10px 20px. Hover chuyển sang cam gỗ, nhích lên 2px, có bóng cam.
- **Outline:** nền trắng, viền và chữ cam đất; hover nền kem cam.
- **Focus:** viền ngoài 2px xanh than đêm, cách nút 2px, cho mọi nút. Trong backoffice, outline và ghost cùng một kiểu: viền sương, chữ xanh than, hover viền và chữ chuyển cam.
- **Ghost:** nền trắng, viền sương, chữ thường; hover viền đậm thành xám đá.
- **Navy / Danger / Success:** nền đặc xanh than / đỏ / xanh lục, chữ trắng, cho hành động phụ tối màu, hủy/xóa, xác nhận hoàn tất.
- **Kích cỡ:** nhỏ đệm 6px 12px, chữ 0.8rem; lớn đệm 14px 28px, chữ 1rem; có biến thể rộng hết khung.
- **Disabled:** mờ 50%, con trỏ bị chặn, không nhích lên.

### Chips
- **Nhãn trạng thái:** viên thuốc, đệm 3px 10px, chữ 0.75rem đậm 600, nền nhạt và chữ đậm cùng họ màu trạng thái. Ở cổng khách hàng, nhãn chuyển sang nền giấy, viền sương, chữ xanh than đá và chỉ tô màu trạng thái lên icon.
- **Số đếm trên tab/menu:** viên thuốc nhỏ nền xám kẽm; tab đang chọn thì nền kem cam; việc gấp trên menu backoffice thì nền đỏ chữ trắng.

### Cards / Containers
- **Corner Style:** 12px (backoffice 16px).
- **Background:** trắng trên nền giấy.
- **Shadow Strategy:** "Thẻ nghỉ" ở cổng khách hàng, không bóng ở backoffice.
- **Border:** 1px viền sương.
- **Internal Padding:** 20px; phần đầu thẻ có tiêu đề kèm icon cam và đường kẻ dưới (backoffice bỏ đường kẻ này).

### Inputs / Fields
- **Style:** nền trắng, viền 1px sương, bo 8px (backoffice 12px), đệm 10px 12px. Nhãn phía trên, đậm 600, xanh than đêm; trường bắt buộc có dấu `*` đỏ.
- **Focus:** viền chuyển cam, thêm vòng focus cam mờ 3px.
- **Error:** viền đỏ, dòng lỗi đỏ 0.8rem bên dưới. Gợi ý dùng chữ xám 0.78rem.

### Navigation
- **Tab trạng thái:** chữ xám đá đậm 600, gạch chân 2px. Hover chữ cam đất; đang chọn chữ cam đất và gạch chân cam cháy. Dải tab cuộn ngang khi chật.
- **Thanh bên backoffice:** nền gần trắng, mục cao 42px bo 12px, icon xám rộng 20px. Hover nền xám rất nhạt; mục đang chọn nền xám đậm hơn, chữ đậm 700 xanh than. Tiêu đề nhóm viết hoa 0.74rem màu xám nhạt.

### Dòng nhãn–giá trị (Signature)
Đơn vị cơ bản của "vận đơn": nhãn xám đá bên trái, giá trị đậm 600 căn phải, chữ 0.88rem, đệm dọc 8px, ngăn nhau bằng đường kẻ đứt sương; dòng cuối không kẻ. Dùng cho chi tiết đơn, báo giá, quyết toán, hồ sơ.

### Bảng dữ liệu
Chữ 0.88rem. Tiêu đề cột viết hoa 0.72rem đậm 700 màu xám đá trên nền giấy. Ô đệm 12px, kẻ dưới 1px sương. Hàng hover nền kem cam. Dòng tổng cuối bảng nền xanh than đêm chữ trắng, số cuối cùng màu cam nhạt (#fdba74).

### Hộp thông báo
Bo 8px, đệm 14px 16px, chữ 0.88rem, icon bên trái. Bốn loại thông tin / cảnh báo / lỗi / thành công, mỗi loại nền nhạt, viền và chữ đậm cùng họ. Ở cổng khách hàng hộp chuyển sang nền trắng, viền sương, thanh màu trạng thái 3px ở cạnh trái.

### Khung đăng nhập
Hai cột trên máy tính. Cột trái nền xanh than đêm phẳng: thương hiệu, ảnh xe chở ngựa trong khung bo 12px (không phủ chữ lên ảnh), tiêu đề, một câu phụ và các dòng cam kết kẻ mảnh như vận đơn (chỉ ở trang khách). Cột phải nền giấy, form rộng tối đa 420px, tiêu đề form là `h1`. Dưới 820px cột trái thu thành dải tiêu đề, ẩn ảnh và các dòng cam kết.

### Hộp thoại
Nền trắng, rộng 560px (bản rộng 880px), cao tối đa vừa màn hình và tự cuộn. Phần đầu, thân, chân tách bằng đường kẻ sương; nút đặt ở chân, căn phải. Nền phủ xanh than mờ 45%. Xuất hiện bằng hiệu ứng mờ dần và trượt nhẹ (0.3s).

## Do's and Don'ts

### Do:
- **Do** dùng cam đất (`var(--orange-dark)`) cho mọi chữ cam và nền cam có chữ trắng.
- **Do** dùng token trong `tokens.css` (`var(--orange)`, `var(--line)`…) thay vì gõ mã hex.
- **Do** trình bày tiền, hạn và mốc thanh toán bằng dòng nhãn–giá trị, giá trị căn phải và đậm 600.
- **Do** dùng cặp màu trạng thái (đậm trên nền nhạt cùng họ) cho nhãn và hộp thông báo.
- **Do** giữ backoffice phẳng: viền 1px, không bóng thẻ, bo theo thang của backoffice.
- **Do** tắt mọi chuyển động khi người dùng bật giảm chuyển động (đã có sẵn trong `base.css`).

### Don't:
- **Don't** thêm màu nhấn thứ hai ngoài cam cháy.
- **Don't** dùng xanh lá, hổ phách, đỏ, xanh dương để trang trí.
- **Don't** dùng phông khác ngoài Roboto (trừ monospace cho mã và số trong báo giá).
- **Don't** đưa ảnh nền tối, gradient hay hiệu ứng chữ chạy ra ngoài trang chủ công khai.
- **Don't** làm bóng thẻ đậm hơn "Thẻ nghỉ".
