# PRD — Hệ thống Vận chuyển Ngựa đua

> Tài liệu nghiệp vụ gốc. Nội dung lấy từ bộ quy trình do chủ dự án chốt (Flow 1–6, quy định hồ sơ, hạng xe, gói cước). Quy trình đặt trước tiên; các mục sau (hạng xe, gói cước, giấy tờ) phải tuân thủ quy trình.
> Cập nhật lần cuối: 06/10/2026.

## Mục lục

1. Tổng quan quy trình, vai trò và nguyên tắc
2. Flow 1 — Tạo đơn, gán xe, báo giá & đặt cọc
3. Flow 2 — Thủ tục kiểm dịch & hải quan (nhà xe làm)
4. Flow 3 — Phát Lệnh điều xe & bàn giao xuống app
5. Flow 4 — Cập nhật trạng thái & nhật ký lộ trình
6. Flow 5 — Xử lý sự cố & điều chỉnh khẩn cấp
7. Flow 6 — Bàn giao, quyết toán phát sinh & đóng đơn
8. Ngoại lệ: người nhận từ chối, khách chậm thanh toán, khách và Manager hủy đơn
9. Dịch vụ thủ tục kiểm dịch & hải quan trọn gói
10. Hạng xe & chọn xe, tài xế, hộ tống
11. Gói cước, biểu phí & chính sách chi phí sự cố
12. Hồ sơ, giấy tờ & SOP check-in
13. Trạng thái đơn hàng
14. Điểm cần chốt
15. Báo cáo của Manager (doanh thu, chi phí vận hành, hiệu suất)

---

## 1. Tổng quan quy trình, vai trò và nguyên tắc

### 1.1. Phạm vi

- Vận chuyển **ngựa đua đường bộ**, bằng phương tiện chuyên dụng chở ngựa.
- Hai loại tuyến: **Nội địa** và **Quốc tế liên vận CLV** (Việt Nam ⇄ Campuchia ⇄ Lào).

### 1.2. Chuỗi quy trình

```
Flow 1  Tạo đơn, chọn xe, báo giá → Coordinator chọn xe và lập lộ trình, Manager chọn Driver/Escort theo số xe rồi duyệt báo giá cố định, khách cọc 30% → Vận đơn
Flow 2  Thủ tục kiểm dịch & HQ   → Specialist làm giấy ngoài hệ thống, cập nhật tiến độ có ảnh cho khách và Manager
Flow 3  Lệnh điều xe & bàn giao   → hệ thống tự phát Lệnh điều xe (mỗi xe một lệnh) ngay sau cọc, đẩy xuống app Driver/Escort
Flow 4  Nhật ký lộ trình         → ngày D khách trả 70% còn lại, check-in từng mốc có ảnh, nhật ký an sinh, thông quan, giao ngựa
Flow 5  Sự cố khẩn cấp           → SOS, phương án xử lý, Manager duyệt, tiếp tục hành trình
Flow 6  Bàn giao & đóng đơn      → khoản phát sinh có chứng từ (nếu có), thanh toán, đóng đơn
```

D = ngày khởi hành.

### 1.3. Vai trò

| Vai trò | Việc chính |
|---|---|
| Khách hàng (Customer) | Khai Hồ sơ ngựa, đặt đơn, đặt cọc, theo dõi tiến độ giấy tờ và chuyến đi, trả số dư ngày D, giao hồ sơ gốc của ngựa cho tài xế, thanh toán khoản phát sinh (nếu có), đánh giá. **Không** làm thủ tục thông quan, không tải giấy thông quan |
| Quản trị viên (Admin) | Quản lý **tài khoản hệ thống**: tạo tài khoản nhân viên (Manager, Specialist, Coordinator, Driver, Escort, Admin), sửa thông tin, khóa / mở khóa tài khoản (kể cả khách hàng), đặt lại mật khẩu, xem lịch sử thao tác. **Không** tham gia nghiệp vụ vận chuyển |
| Logistics Manager | Tiếp nhận đơn, **chọn Driver và Escort cho từng xe** (sau khi Coordinator chọn xe), duyệt báo giá (gồm xe và lộ trình), **trả đơn về Specialist hoặc Coordinator làm lại** khi chưa duyệt, xem trang Tiến độ đơn (trạng thái, bước tiến độ của mọi đơn và chi tiết giấy tờ, lộ trình, xe, nhân sự, chỉ xem), duyệt phương án khẩn cấp, **người duy nhất liên hệ làm việc với Khách hàng khi có sự cố**, duyệt khoản phát sinh, xử lý ngoại lệ |
| Transport Specialist (Kiểm dịch viên) | **Người gác cổng duy nhất** về hồ sơ ngựa, kiểm dịch và hải quan. Duyệt Hồ sơ ngựa ở Flow 1. **Trực tiếp làm** giấy kiểm dịch, hải quan và giấy pháp lý chuyến đi (làm bên ngoài hệ thống), chụp ảnh và cập nhật tiến độ lên hệ thống. Không can thiệp thao tác sơ cứu lâm sàng |
| Fleet & Route Coordinator (Điều phối viên) | **Tự chọn xe** (một hoặc nhiều xe tùy số ngựa) và chia ngựa lên xe (hệ thống khóa xe trùng lịch); **không chọn** Driver, Escort; lập lộ trình chi tiết; nhập bộ giấy cho Driver; giám sát chuyến; lập phương án sự cố |
| Driver (Tài xế) | Lái xe, check-in từng mốc có ảnh chụp trực tiếp, thu và trả bản gốc chứng từ, xuất trình giấy tại cửa khẩu, ký biên bản giao nhận, kê khai chi phí có chứng từ, báo sự cố |
| Escort (Nhân viên chăm sóc) | **Người duy nhất** khám lâm sàng, sơ cứu và xử lý sức khỏe ngựa tại hiện trường; quét microchip; ghi nhật ký an sinh |

### 1.3a. Tài khoản hệ thống

- Khách hàng **tự đăng ký**; nhân viên do Admin tạo. Admin cấp **mật khẩu tạm** (chỉ hiện một lần khi tạo hoặc đặt lại).
- **Khóa tài khoản:** người bị khóa không đăng nhập được; nhân viên đang đăng nhập dùng được đến khi đăng xuất. Khóa Specialist hoặc Coordinator thì họ chuyển sang trạng thái nghỉ trong danh bạ nên hệ thống không phân công nữa. Khách bị khóa tự động khi **Payment Overdue** (mục 8.2); Admin có thể mở khóa sau khi khách thanh toán.
- Admin **không tự khóa mình** và không khóa Admin cuối cùng. **Vai trò không đổi sau khi tạo** (cần vai trò khác thì tạo tài khoản mới); họ tên nhân viên vận hành không đổi vì gắn với đơn và lịch phân công.
- Tạo Specialist, Coordinator, Driver, Escort thì người đó **tự vào danh bạ** để được phân công.
- Mỗi thao tác (tạo, sửa, khóa, mở khóa, đặt lại mật khẩu) ghi vào lịch sử của tài khoản.

### 1.4. Nguyên tắc vận hành bắt buộc

1. **Charter độc quyền:** các xe của một đơn chỉ chở ngựa của đơn đó. Không ghép ngựa của đơn khác (bảo đảm an toàn sinh học). **Một đơn có thể có nhiều xe** (mục 10.2).
2. **Định biên cứng:** mỗi xe đúng **01 Driver + 01 Escort**. Coordinator chọn xe trước; **Manager** nhìn số xe đã chọn rồi chọn Driver và Escort cho từng xe, hệ thống không gán tự động. **Tài xế không gắn cố định với xe**: xe, Driver và Escort là ba danh sách riêng.
3. **Lead time 30 ngày:** khách đặt trước ngày khởi hành tối thiểu 30 ngày (phục vụ cách ly, xét nghiệm dịch tễ, làm thủ tục).
4. **Nhà xe làm thủ tục:** khách chỉ cần biết ngựa được chở tới nơi. Giấy kiểm dịch, hải quan và giấy pháp lý chuyến đi do nhà xe (Specialist) làm; khách chỉ xem tiến độ (mục 9).
5. **Báo giá cố định, không phụ thu:** phí nào cố định được thì cố định; phí không cố định được (nhiên liệu, BOT) ước tính theo lộ trình và đưa thẳng vào báo giá; nhà xe chịu chênh lệch. Chỉ phát sinh thêm khi có sự cố thuộc phần khách chịu (mục 11.5) hoặc dịch vụ khách chọn thêm.
6. **Biển số cố định:** không điều xe thay thế giữa chặng, để giữ tính pháp lý của giấy kiểm dịch và tờ khai hải quan.
7. **Cửa khẩu cố định (Fixed Border Policy):** cửa khẩu do **Coordinator chọn** khi lập lộ trình (khách không chọn, vì thủ tục do nhà xe làm). Chốt lộ trình xong thì cửa khẩu bị khóa, phương tiện không tự ý đổi sang cửa khẩu khác.
8. **Khách chỉ nộp Hồ sơ ngựa:** khách tải Hồ sơ ngựa (hộ chiếu, sổ tiêm, xét nghiệm) lúc tạo đơn. Mọi giấy pháp lý và thông quan do nhà xe làm. **Bản gốc** hồ sơ ngựa được đối soát và thu tại điểm đón trước khi xe xuất phát.
9. **Chuyên môn thú y:** chỉ Escort khám lâm sàng và sơ cứu tại hiện trường.
10. **Thẩm quyền:** chỉ Logistics Manager duyệt thay đổi phương án di chuyển và trực tiếp làm việc với khách khi có sự cố.
11. **Xe cứu hộ** chỉ sửa xe tại chỗ xe gặp sự cố, không phải xe thay thế để chạy tiếp qua biên giới. Ngựa được đưa riêng tới trạm nghỉ gần nhất.
12. **Evidence-First:** chưa có ảnh chứng từ hợp lệ thì ô nhập số tiền bị khóa (áp dụng cho chi phí sự cố, mục 7.3).
13. **Lộ trình lập một lần:** Coordinator lập lộ trình chi tiết ngay ở Flow 1. Lộ trình chỉ sửa khi có sự cố (Flow 5).
14. **Thanh toán hai đợt:** cọc 30% để có Vận đơn; 70% còn lại trả ngày D. Chưa trả đủ thì xe không được bắt đầu hành trình.

### 1.5. Thông báo đẩy

Mọi vai trò có chuông thông báo trên thanh menu (số chưa đọc, bấm một thông báo để mở đơn liên quan). Thông báo chỉ **đẩy xuống**, người nhận không phải trả lời và hệ thống không ghi thêm vào nhật ký đơn.

| Sự việc | Người nhận |
|---|---|
| Khách gửi đơn | Manager |
| Manager tiếp nhận, giao việc | Specialist và Coordinator được giao (nhiệm vụ mới) |
| Specialist yêu cầu bổ sung hồ sơ ngựa | Khách (không gửi Manager) |
| Khách bổ sung hồ sơ gửi lại | Specialist |
| Specialist duyệt hồ sơ ngựa; Coordinator chốt xe và lộ trình | Manager |
| Manager trả đơn về làm lại | Specialist (duyệt lại hồ sơ ngựa) hoặc Coordinator (làm lại xe và lộ trình), kèm lý do |
| Manager gửi báo giá | Khách |
| Báo giá hết hạn | Khách, Manager |
| Khách đặt cọc | Manager, Specialist, Coordinator; Driver và Escort của từng xe nhận "chuyến mới" |
| Specialist tiếp nhận Vận đơn; hoàn tất giấy tờ | Khách; khách và Manager (báo tin) |
| Nhà xe từ chối đơn (Manager lúc tiếp nhận, hoặc Coordinator không duyệt lộ trình) | Khách (kèm lý do); nếu do Coordinator thì thêm Manager và Specialist |
| Khách trả 70% | Manager, Coordinator |
| Khách từ chối báo giá | Manager, Specialist, Coordinator |
| Khách hủy đơn | Manager, Specialist, Coordinator, Driver và Escort của các xe |
| Manager hủy đơn | Khách (kèm số tiền hoàn), Specialist, Coordinator, Driver và Escort của các xe |
| Xe bắt đầu hành trình | Khách, Manager, Coordinator |
| Xe giao ngựa xong | Khách, Manager |

---

## 2. Flow 1 — Tạo đơn, gán xe, báo giá & đặt cọc

```
[Khách hàng]            [Manager]              [Hệ thống]        [Specialist & Coordinator]
     |-- 1. Tạo đơn -------->|
     |                       |-- 2. Tiếp nhận --> giao Specialist, Coordinator
     |                       |                                   |-- 3. Duyệt hồ sơ ngựa ∥ chỉnh xe, lập lộ trình
     |                       |<-- hoàn tất thẩm định ------------|
     |<-- 4. Báo giá --------|
     |-- 5. Cọc 30% ---> Vận đơn
```

### 2.1. Kho Hồ sơ ngựa (Horse Profile Repository)

Trang "Hồ sơ ngựa" trên Cổng khách hàng. Khách khai báo một lần; các lần đặt sau chỉ cần tích chọn.

**Thông tin định danh cố định:** Tên ngựa, **Mã microchip** (định danh bắt buộc, **không thể chỉnh sửa sau khi lưu**), Giống, Giới tính (Đực / Cái / Thiến), Màu lông, Năm sinh, Đặc điểm nhận dạng.

**Hồ sơ ngựa đính kèm** (khách tải; ảnh có xem trước): bản scan Hộ chiếu ngựa (FEI / National Passport); Sổ tiêm phòng; Phiếu xét nghiệm máu (Coggins/EIA âm tính).

**Trạng thái hiển thị cá thể:**

| Trạng thái | Ý nghĩa |
|---|---|
| Sẵn sàng đặt | Đủ Hộ chiếu, Sổ tiêm phòng và xét nghiệm hợp lệ; chọn được ngay khi tạo đơn |
| Thiếu giấy | Chưa đủ giấy hoặc giấy hết hạn; hệ thống tạm khóa, khách bấm "Sửa" để cập nhật trước khi đặt chuyến |

### 2.2. Bước 1 — Khách tạo đơn từ kho hồ sơ

- **Khóa lịch tự động (Lead Time Validation):** vô hiệu hóa mọi ngày trong vòng 30 ngày kể từ ngày hiện tại. Ngày khởi hành ≥ ngày hiện tại + 30 ngày.
- **Tuyến đường & chủ thể:**
  - Phân loại tuyến: Nội địa hoặc Quốc tế liên vận CLV.
  - Người gửi (Consignor) và Người nhận (Consignee): tên, SĐT, CCCD/MST/Hộ chiếu, địa chỉ chi tiết.
  - Hành trình: Điểm bốc → Điểm trả. Khách **chọn trên bản đồ**: tìm theo tên, bấm điểm trên bản đồ hoặc trong danh sách. Chỉ các kho và CLB có trong hệ thống (không nhập địa chỉ tự do). Khách **không** chọn cửa khẩu: Coordinator chọn khi lập lộ trình (mục 2.4).
- **Chọn ngựa (Horse Selector):**
  - Chỉ tích chọn được cá thể ở trạng thái Sẵn sàng đặt.
  - Cá thể Thiếu giấy: bấm "Sửa" để tải bổ sung giấy mới; dữ liệu cập nhật đè vào kho.
  - Bấm "+ Thêm ngựa" để khai nhanh cá thể mới qua popup ngay trong lúc tạo đơn.
- **Cấu hình dịch vụ & bảo hiểm theo từng cá thể:**
  - Khoang tiêu chuẩn hoặc Khoang đơn mở rộng (Single Stall).
  - **Gói thức ăn** (chọn một cho từng ngựa, không nhập tự do): **Cơ bản** (cỏ khô thường, đã gồm trong cước), **Nâng cao** (cỏ Timothy cao cấp, yến mạch), **Thể thao** (như Nâng cao, thêm thức ăn bổ sung vitamin, khoáng).
  - **Cữ nước** (chọn riêng, tùy nhu cầu từng ngựa, phí thấp): **mỗi 3 giờ** (mặc định tại trạm, đã gồm trong cước), **mỗi 2 giờ**, **mỗi giờ**. Không có ô ghi chú chăm sóc tự do.
  - Gói và cữ trả phí có giá cố định theo ngựa, cộng vào báo giá. Hệ thống **không** quản lý nhiệt độ khoang: khách không chọn, Escort không nhập.
  - Bảo hiểm Động vật Sống (Live Animal Transit Insurance), chọn cho từng cá thể:
    - **MUA BẢO HIỂM:** khách không tự nhập giá trị. Hệ thống tính phí theo giống ngựa (mỗi giống có giá trị bảo hiểm cố định, phí = 2% giá trị đó) và hiện phí cho khách trước khi chọn. Icon dấu chấm than (rê chuột vào thì xổ ra) hiện bảng phí bảo hiểm của từng giống; khách chỉ thấy phí, không thấy giá trị ngựa theo giống.
    - **TỪ CHỐI:** khách đồng ý Điều khoản Trách nhiệm Hạn chế của nhà xe (bấm liên kết để đọc ở trang điều khoản, mục 11.6).
- Khách **không** tải giấy thông quan, giấy kiểm dịch vận chuyển hay Import Permit; các giấy này do nhà xe làm (Flow 2).
- Bấm "Gửi yêu cầu đặt đơn". → **Pending Manager Intake**.

### 2.3. Bước 2 — Manager tiếp nhận

- Đơn vào hàng đợi của Manager. Manager kiểm tra tổng quan, bấm **"Tiếp nhận & Kích hoạt Thẩm định"**. → **Under Internal Review**.
- **Từ chối đơn:** Manager có thể không nhận đơn, bắt buộc ghi lý do. → **Order Rejected**; xe và nhân sự không bị giữ; khách nhận lý do và đặt chuyến mới (đơn cũ đóng, không sửa lại).
- Manager giao 01 **Specialist** (duyệt hồ sơ ngựa) và 01 **Coordinator** (xe, lộ trình); Manager chọn trong popup thẻ; mỗi thẻ chỉ cho biết người đó **đang hoạt động** trên hệ thống và giao việc được, hay **đang nghỉ** (bị khóa). Manager **không xem số đơn** từng người đang nhận và hệ thống không gợi ý theo khối lượng việc. **Hệ thống không tự gán xe, Driver, Escort**: Coordinator chọn xe ở bước sau, Manager chọn Driver, Escort sau khi có xe, nên việc tiếp nhận không phụ thuộc đội xe rảnh.
- Dữ liệu được đẩy song song đến Specialist và Coordinator.

### 2.4. Bước 3 — Thẩm định song song

**Nhánh A — Specialist (duyệt hồ sơ ngựa):**
- Hệ thống đẩy task "Duyệt hồ sơ ngựa" cho Specialist ngay khi đơn được tiếp nhận.
- Đối chiếu tính xác thực của Hộ chiếu, số Microchip, Sổ tiêm và thời hạn xét nghiệm máu (EIA/EVA) theo quy định kiểm dịch. Specialist **xem được từng giấy khách đã nộp** (tên tệp, ngày nộp, hạn, bấm ảnh để phóng to) ngay trên trang thẩm định, không phải hỏi lại khách.
- Thao tác: **"Duyệt hồ sơ ngựa (Approve Horse Profile)"**, hoặc Yêu cầu bổ sung nếu giấy mờ / hết hạn.

**Nhánh B — Coordinator (xe và lộ trình chi tiết):**
- **Tự chọn xe** cho từng chuyến và chia ngựa lên các xe. Ưu tiên **ít xe nhất**: có xe đủ chỗ thì dùng một xe; nếu không (ví dụ 6 ngựa mà không còn xe 6 ngăn) thì chọn **nhiều xe** và chia ngựa (2 xe, mỗi xe 3 ngựa). Coordinator **không chọn** Driver và Escort (việc của Manager, mục 2.5). Khi chọn, hệ thống cho biết xe đó đang bận đơn nào và **khóa** xe có đơn trùng lịch. Hệ thống kiểm tra lại sức chứa, lịch rảnh, hạn Đăng kiểm và Giấy phép liên vận CLV (tuyến quốc tế) khi sửa.
- **Lập lộ trình chi tiết ngay bước này** (một lần cho cả đơn; mỗi xe có thể có giờ chạy riêng): **chọn cửa khẩu** (tuyến quốc tế; hệ thống gợi ý cửa khẩu có tổng quãng đường ngắn nhất trong các cửa khẩu của nước đến), chọn các **trạm nghỉ** dọc đường đi từ điểm đón qua cửa khẩu tới điểm trả: Coordinator mở **bản đồ** hiện điểm đón, điểm trả, cửa khẩu và mọi trạm của hệ thống, bấm vào trạm để chọn và đặt **thời gian nghỉ** của trạm đó (hệ thống tự xếp thứ tự trạm và vẽ đường đi qua các điểm; nút gợi ý chọn sẵn các trạm gần đường đi nhất theo cửa khẩu đã chọn), giờ đón ngựa (ETD), giờ tới cửa khẩu (ETA). **Đường đi và thời gian lái lấy theo đường giao thông thật** (Google Routes API có tính giao thông theo giờ khởi hành; không có khóa hoặc lỗi thì dùng OSRM không tính giao thông; vẫn không được thì ước lượng theo quãng đường và tốc độ trung bình). Thời gian lái này cộng với thời gian nghỉ ở các trạm cho ra **giờ đến dự kiến** của từng chặng và của cả chuyến (khách thấy ở bước chờ đặt cọc). Đổi cửa khẩu thì hệ thống gợi ý lại các trạm. Quy tắc chia chặng ở mục 4.2. Lộ trình **không** quản lý trạm thú y dọc tuyến.
- Thao tác: **"Xác nhận Phương án Xe & Lộ trình (Confirm Fleet & Route)"**, hoặc **"Không duyệt, từ chối đơn"** (bắt buộc ghi lý do) khi không xếp được xe hoặc lộ trình. → **Order Rejected**, xử lý như khi Manager từ chối (mục 2.3).

Cả hai bộ phận duyệt xong, kết quả chuyển về Dashboard của Manager. → **Pending Final Commercial Approval**.

### 2.5. Bước 4 — Manager duyệt đơn và phát hành báo giá

Báo giá **cố định, có bảng chi tiết từng khoản**, không phụ thu ngoài những gì ghi trong phiếu. Khách đọc chính sách chi phí sự cố và điều khoản vận chuyển bằng liên kết mở trang điều khoản riêng (mục 11.6).

```text
================================================================================
                    PHIẾU BÁO GIÁ ĐƠN HÀNG VẬN TẢI (QUOTATION SHEET)
================================================================================
1. THÔNG TIN CHUNG
   * Mã đơn hàng: ORD-2026-XXXX                  * Ngày lập: DD/MM/YYYY
   * Loại hình tuyến: [ NỘI ĐỊA ] / [ QUỐC TẾ LIÊN VẬN ]
   * Phương thức: NGUYÊN XE CHUYÊN DỤNG (CHARTER ONLY - KHÔNG GHÉP)
   * Số xe: [N] xe

2. CHỦ THỂ GIAO NHẬN
   * Người gửi (Consignor) / Người nhận (Consignee): [Tên, SĐT, Địa chỉ]

3. NGỰA, DỊCH VỤ VÀ XE (ĐÃ DUYỆT)
   Xe 1 [BKS ...] | Driver [...] | Escort [...]
     #1. [Tên ngựa] | Microchip | Giống | Khoang | Bảo hiểm: [ĐỒNG Ý / TỪ CHỐI] -> Phí
   Xe 2 [BKS ...] | ...
   * Specialist phụ trách: [Tên] - Kết luận hồ sơ ngựa: ĐẠT
   * Lộ trình: [Điểm bốc → ... → Điểm trả], ETD / ETA, [Cửa khẩu]

4. BẢNG GIÁ CHI TIẾT (TẤT CẢ CỐ ĐỊNH)
   a. Cước vận chuyển nguyên chuyến (theo cự ly & hạng xe, từng xe):    ... VNĐ
   b. Nhân sự kỹ thuật (01 Driver + 01 Escort mỗi xe, theo số ngày):    ... VNĐ
   c. Phí khoang đặc thù và gói thức ăn, cữ nước theo từng ngựa: ... VNĐ
   d. Phí thủ tục kiểm dịch & hải quan (nhà xe làm trọn gói):           ... VNĐ
   e. Nhiên liệu & BOT (ước tính theo lộ trình, đã gồm dự phòng):       ... VNĐ
   f. Bảo hiểm Động vật Sống (cá thể đồng ý mua):                       ... VNĐ
   -----------------------------------------------------------------------------
   TỔNG GIÁ TRỊ (CHƯA VAT):                                             ... VNĐ
   ĐẶT CỌC 30% ĐỂ NHẬN VẬN ĐƠN:                                         ... VNĐ
   THANH TOÁN 70% CÒN LẠI VÀO NGÀY BỐC NGỰA (D):                        ... VNĐ
   THỜI HẠN GIỮ BÁO GIÁ: 48 Giờ

5. CHÍNH SÁCH CHI PHÍ SỰ CỐ (liên kết sang trang điều khoản): mục 11.5, 11.6
================================================================================
```

Giấy tờ chỉ để Manager xem, **không gửi kèm khách**: bấm "Approve & Send Quotation" thì khách chỉ nhận chi tiết đơn và bảng giá. Trước khi duyệt, Manager **xem được bằng chứng do hai bộ phận đẩy lên**: giấy từng ngựa Specialist đã duyệt (ảnh, ngày nộp, hạn, kết luận), lộ trình Coordinator đã lập trên bản đồ (trạm, cửa khẩu, giờ đi / đến), và các xe Coordinator đã chọn cho từng chuyến.

**Trả lại để làm lại:** nếu thấy chưa đạt, Manager không duyệt mà bấm **Trả lại Kiểm dịch viên** (duyệt lại hồ sơ ngựa; xe và lộ trình giữ nguyên) hoặc **Trả lại Điều phối viên** (chọn lại xe và lập lại lộ trình; Driver, Escort đã chọn bị bỏ), bắt buộc ghi lý do. Đơn quay về **Under Internal Review**, người nhận thấy lý do trên đơn và nhận thông báo; làm xong thì đơn lại về chờ Manager duyệt báo giá. Chỉ trả lại được khi đơn đang `Pending Final Commercial Approval`.

**Chọn Driver và Escort:** trước khi gửi báo giá, Manager **nhìn số xe** Coordinator đã chọn rồi chọn **01 Driver và 01 Escort cho từng xe** (popup thẻ). Driver và Escort quản lý theo kiểu **giao việc (assign task)**: người đã được giao vào một chuyến của đơn khác thì **bị khóa, không chọn được** (không xét ngày đi), cho tới khi đơn đó giao ngựa xong. Chưa chọn đủ cho mọi xe thì chưa gửi được báo giá. Driver và Escort chỉ nhận "chuyến mới" sau khi khách cọc (mục 1.5).

Manager rà soát, điều chỉnh chiết khấu thương mại (nếu có), bấm **"Approve & Send Quotation"**. → **Awaiting Payment**.

### 2.6. Bước 5 — Khách đặt cọc và nhận Vận đơn

- Khách có **48 giờ** để đặt cọc **30%** trên hệ thống. Quá hạn: đơn tự hủy (**Quote Expired**), xe và nhân sự được nhả cho đơn khác.
- Ở bước chờ đặt cọc, khách thấy **ngày giờ khởi hành và ngày giờ đến nơi dự kiến** (giờ đến của chặng cuối trong lộ trình đã duyệt) trên thẻ đặt cọc, dòng thông báo của đơn và danh sách đơn. Đây là dự kiến, có thể đổi nếu gặp sự cố (Flow 5).
- Khách không đồng ý báo giá thì bấm **Từ chối báo giá** (ghi lý do nếu muốn) thay vì chờ hết hạn: đơn → **Cancelled**, chưa cọc nên không mất phí, xe và nhân sự được nhả. Ở bước này khách chỉ dùng **Từ chối báo giá**, không có nút Hủy đơn riêng (mục 8.3).
- Sau khi cọc, đơn chuyển sang giai đoạn **Vận đơn**: hệ thống cấp **mã Vận đơn**, giữ chỗ xe và nhân sự. Không có hợp đồng ký số.
- Khách theo dõi trên app: danh sách xe và ngựa trên từng xe, tiến độ giấy tờ do Specialist cập nhật, lộ trình, và **bản đồ theo dõi** (mục 5.7).
- → **Waybill Issued** → Specialist tiếp nhận Vận đơn (Flow 2).

---

## 3. Flow 2 — Thủ tục kiểm dịch & hải quan (nhà xe làm)

### 3.1. Phân quyền

- **Transport Specialist:** người gác cổng duy nhất, **trực tiếp làm** thủ tục kiểm dịch, hải quan và giấy pháp lý chuyến đi; báo tiến độ.
- **Fleet & Route Coordinator:** nhập bộ giấy cho từng Driver để xuất trình tại hải quan (bản in, bản gốc nhà xe có); không làm thủ tục với cơ quan chức năng.
- **Logistics Manager:** người đứng đầu, **chỉ xem, không sửa**: trạng thái và tiến độ của đơn, và ở trang Tiến độ đơn bấm "Xem giấy tờ, xe" để xem giấy kiểm dịch, hải quan Specialist đã làm (từng hạng mục, ảnh chụp, ghi chú), giấy ngựa đã duyệt, lộ trình trên bản đồ, xe, Driver, Escort. Xử lý ngoại lệ.
- **Khách hàng:** **chỉ xem** tiến độ và giấy Specialist đã nộp, không sửa và không báo sai thông tin. Khách chỉ cần biết ngựa được chở tới nơi; nghiệp vụ giấy tờ do nhà xe làm.

### 3.2. Tiến trình

| Bước | Chủ thể | Hành động chính | Trạng thái |
|---|---|---|---|
| 1 | Hệ thống | Cấp Vận đơn sau cọc, giao việc cho Specialist | Waybill Issued |
| 2 | Specialist | Tiếp nhận Vận đơn; làm giấy (ngoài hệ thống); cập nhật từng hạng mục + ảnh chụp | Clearance In Progress |
| 3 | Coordinator | Nhập bộ giấy cho từng Driver | Clearance In Progress |
| 4 | Specialist | Xong mọi hạng mục, ghi nhận từng ngựa đã có giấy thông quan (quốc tế) | Clearance Done |
| 5 | Driver / Escort | Nhận lệnh trên app (Lệnh điều xe đã phát từ lúc cọc); đủ điều kiện thì sẵn sàng đón ngựa (Flow 3) | Ready for Pickup |

### 3.3. Hạng mục giấy tờ

Giấy được chia **hai nhóm**:

1. **Hồ sơ ngựa:** Hộ chiếu, Sổ tiêm, xét nghiệm. Khách tải lúc tạo đơn, Specialist đã duyệt ở Flow 1.
2. **Giấy pháp lý chuyến đi:** nhà xe làm hoàn toàn. Hạng mục cụ thể phụ thuộc tuyến:
   - Kiểm dịch: Health Cert (cả nội địa và quốc tế); Giấy cách ly trước xuất phát (nếu nước đến yêu cầu).
   - Hải quan (chỉ quốc tế): Tờ khai hải quan điện tử; Import Permit; ATA Carnet, Hóa đơn thương mại (nếu có, xem mục 14).
   - Vận tải: PoA (Giấy ủy quyền áp tải); giấy phép liên vận CLV của xe (quốc tế).

Mỗi hạng mục chỉ có hai trạng thái: **Chưa nộp / Đã nộp** (nộp cho cơ quan chức năng), kèm ghi chú và ảnh chụp. Specialist **không chỉnh tay** trạng thái: tải ảnh giấy lên thì hạng mục tự chuyển **Đã nộp**; xóa hết ảnh thì về **Chưa nộp**. Cần giấy nào ở giai đoạn nào thì Specialist bổ sung hạng mục đó. Specialist điền tờ khai thay khách.

### 3.4. Báo cáo tiến độ

- Mỗi lần Specialist cập nhật (đổi trạng thái hoặc thêm ảnh), khách thấy ngay trên đơn; Manager thấy trạng thái tổng quan của đơn.
- **Cảnh báo nội bộ:** quá 18:00 D-1 mà chưa `Clearance Done`, hệ thống cảnh báo Specialist phụ trách (Manager thấy qua trạng thái đơn). Khách không bị tính phí chậm vì giấy này.

### 3.5. Ghi nhận thông quan theo ngựa

Với tuyến quốc tế, hệ thống chỉ **ghi nhận từng ngựa đã có giấy thông quan** (cờ đánh dấu và ảnh). Giấy lấy từ đâu do vận hành xử lý. Chưa đủ cờ cho mọi ngựa thì đơn chưa sang `Clearance Done`.

---

## 4. Flow 3 — Phát Lệnh điều xe & bàn giao xuống app

**Mục tiêu:** lộ trình đã lập ở Flow 1, và Manager đã duyệt xe và lộ trình cùng báo giá, nên **không duyệt lại**. Ngay khi khách cọc, hệ thống tự phát Lệnh điều xe và đẩy xuống Driver và Escort. **Mỗi xe có một Lệnh điều xe riêng.**

### 4.1. Điều kiện

- Đơn ở `Clearance Done`. Hệ thống khóa thông tin xe, nhân sự và mã Microchip (không cho sửa).
- Lộ trình lấy từ Flow 1, không lập lại. Chỉ sửa khi có sự cố (Flow 5).

### 4.2. Quy tắc chia chặng (áp dụng khi Coordinator lập lộ trình ở Flow 1)

- **Chia chặng (Staging Rule):** ngựa không di chuyển liên tục quá **3 – 4 giờ**. Chia thành các chặng ngắn xen điểm dừng tại trạm nghỉ tối thiểu **30 – 45 phút**.
- **Các điểm mốc:**
  - **Trạm nghỉ (checkpoint):** các điểm xe đi qua giữa điểm đón và điểm trả, theo cửa khẩu đã chọn. Tại trạm Escort kiểm tra thể trạng và cho ngựa uống nước; mỗi trạm Driver check-in và Escort ghi nhật ký an sinh. Thời gian nghỉ do Coordinator đặt cho từng trạm (tối thiểu 30 phút).
  - **Không** gán trước trạm thú y khẩn cấp dọc tuyến; khi có sự cố sức khỏe ngựa, Coordinator định vị phòng khám gần nhất tại thời điểm đó (Flow 5).
  - **Cửa khẩu (Border Crossing):** ETA cửa khẩu rơi vào **07:30 – 16:30**, làm thủ tục thông quan và khám lâm sàng trong ngày.

### 4.3. Lệnh điều xe (Trip Manifest, hệ thống tự phát sau cọc)

```text
================================================================================
                    LỆNH ĐIỀU XE (TRIP MANIFEST)
================================================================================
1. THÔNG TIN CHUYẾN ĐI
   * Mã đơn hàng: ORD-2026-XXXX         * Mã chuyến (mỗi xe một mã): TRP-8892
   * Loại tuyến: [ NỘI ĐỊA ] / [ QUỐC TẾ LIÊN VẬN ]
   * Phương tiện: 01 Xe chuyên dụng BKS [51D - 892.45] (xe [i]/[N] của đơn)

2. ĐỘI NGŨ VẬN HÀNH (của xe này)
   * Tài xế (Driver): [Tên, SĐT, Số GPLX, Số Hộ chiếu]
   * Chăm sóc (Escort): [Tên, SĐT, Số CCCD/Hộ chiếu]

3. DANH SÁCH NGỰA TRÊN XE NÀY
   * #1: [Tên ngựa] | Microchip | Khoang | Đã có giấy thông quan: [x]

4. LỊCH TRÌNH TỪNG CHẶNG
   - Chặng, trạm nghỉ, cửa khẩu (ETA), điểm giao đích

5. BỘ GIẤY HỆ THỐNG CẤP CHO TÀI XẾ MANG THEO (Coordinator nhập)
   [x] Lệnh điều xe                          [x] Vận đơn (Waybill)
   [x] Giấy kiểm dịch, tờ khai, PoA nhà xe đã làm (bản in)
   [x] Giấy phép liên vận CLV, Đăng kiểm, Bảo hiểm trách nhiệm dân sự
   [x] 02 Biên bản Giao nhận Động vật sống & Chứng từ gốc (ký tay với người gửi)
   [x] 02 Biên bản Bàn giao & Hoàn tất chuyến đi (ký tay với người nhận)

6. BẢN GỐC TÀI XẾ THU TỪ KHÁCH TẠI ĐIỂM ĐÓN (đối soát với bản đã tải lên)
   [ ] Hộ chiếu ngựa bản gốc
   [ ] Sổ tiêm phòng / Phiếu xét nghiệm EIA/EVA (bản gốc + 02 bản sao công chứng)
   [ ] Giấy khách có sẵn khác nếu có (xem mục 14)
================================================================================
```

Không có bước Manager duyệt riêng: nội dung Lệnh điều xe lấy từ phương án xe và lộ trình đã được Manager duyệt cùng báo giá (Flow 1). Khi lộ trình đổi do sự cố (Flow 5), Lệnh điều xe của xe đó được cập nhật.

### 4.4. Bàn giao lệnh xuống thiết bị di động

- **Mobile App Driver và Escort** nhận: lộ trình chi tiết, tọa độ các trạm nghỉ, danh bạ hỗ trợ; Physical Document Checklist để Driver tích chọn khi gặp khách.
- **Customer Tracking:** thông báo kế hoạch đã duyệt; nhắc: "Vui lòng chuẩn bị bản gốc hồ sơ ngựa (Hộ chiếu, Sổ tiêm, xét nghiệm) để giao cho tài xế."
- Driver và Escort **của xe đó** nhận lệnh trên app (được nhận ngay từ khi Lệnh điều xe phát). Xe sẵn sàng khi cả hai đã nhận lệnh **và** đơn đã `Clearance Done`. Đơn → **Ready for Pickup** khi mọi xe đã sẵn sàng.
- Mỗi xe bắt đầu di chuyển đến điểm bốc ngựa khi Driver bấm; đơn → **En Route to Pickup** khi có xe đầu tiên đi.

---

## 5. Flow 4 — Cập nhật trạng thái & nhật ký lộ trình

**Mục tiêu:** minh bạch hóa hành trình. Driver cập nhật các mốc bằng ảnh chụp trực tiếp kèm Timestamp; Escort ghi nhật ký an sinh; pháp lý hai đầu bảo đảm bằng **Biên bản giấy ký tay** được chụp ảnh số hóa. Dữ liệu đồng bộ tức thời đến Coordinator và Customer. **Mỗi xe có chuỗi mốc, nhật ký và biên bản riêng.**

### 5.1. Các mốc chặng

| Mốc | Chủ thể | Thao tác | Khách & Coordinator thấy |
|---|---|---|---|
| 1. Tiếp nhận ngựa tại điểm đón | Driver & Escort | Chụp ảnh điểm đón; quét Microchip; đối soát và thu đủ chứng từ gốc; ký tay Biên bản giao nhận 02 bản; chụp ảnh biên bản tải lên | Xe đã tiếp nhận ngựa, thu đủ chứng từ gốc và lăn bánh (kèm ảnh biên bản ký tay) |
| 2. Tới trạm nghỉ | Driver & Escort | Driver: chụp biển hiệu trạm / cây xăng (có Timestamp), bấm xác nhận. Escort: nhập nhật ký an sinh | Xe đang dừng tại trạm nghỉ [tên / Km]; tình trạng ngựa |
| 3. Tới cửa khẩu quốc tế | Driver | Chụp barie / cổng trạm kiểm soát, bấm xác nhận có mặt | Xe đã tới cửa khẩu [tên], đang làm thủ tục kiểm dịch & thông quan |
| 4. Hoàn tất thông quan | Driver | Chụp Giấy kiểm dịch / cuống ATA Carnet đã có mộc đỏ | Ngựa và phương tiện đã thông quan |
| 5. Bàn giao đích & hoàn tất | Driver & Escort | Chụp điểm đích; trả hồ sơ gốc; ký tay Biên bản bàn giao hoàn tất 02 bản; chụp ảnh tải lên | Chuyến hoàn thành; ngựa và hồ sơ gốc đã bàn giao cho Consignee |

### 5.2. Bước 1 — Tiếp nhận ngựa, thu hồ sơ gốc, ký biên bản tại điểm đón

1. Xe tới trang trại, Driver bấm "Đã tới điểm đón" và chụp 01 ảnh thực tế tại cổng / khu chuồng.
2. Escort dùng máy quét chip cầm tay rà cổ ngựa, đối soát số hiển thị với Microchip trên Hộ chiếu gốc.
3. Driver mở Checklist chứng từ gốc, nhận đủ từ người gửi: Hộ chiếu gốc, Sổ tiêm, Phiếu xét nghiệm, và giấy khách có sẵn khác nếu có. Các giấy nhà xe làm đã nằm trong bộ giấy Driver mang theo.
4. Driver lấy 02 bản in "Biên bản Giao nhận Động vật sống & Chứng từ gốc". Consignor và Driver cùng kiểm tra, tích nhận từng ngựa và từng hồ sơ gốc, ký bút mực (đóng mộc nếu là trang trại / doanh nghiệp). Người giao giữ 01 bản; Driver giữ 01 bản mang theo xe.
5. **Cổng thanh toán:** nút **Bắt đầu hành trình (Start Journey)** chỉ mở khi khách đã trả đủ 70% còn lại của cả đơn (ngày D).
6. Driver chụp biên bản đủ chữ ký, tải lên, bấm **Bắt đầu hành trình**.
7. Đồng bộ: Customer App báo xe đã tiếp nhận ngựa và bắt đầu di chuyển, khách xem được ảnh biên bản; Coordinator Dashboard chuyển chuyến sang **In Transit - Leg 1**. Đơn → **In Transit - Leg 1** khi có xe đầu tiên chạy.

### 5.3. Bước 2 — Cập nhật tại các trạm nghỉ

Định kỳ mỗi 3 – 4 giờ theo lịch lộ trình:

- **Driver check-in:** chọn mốc dừng tương ứng; chụp ảnh trực tiếp bằng camera App (**Live Capture**) rõ biển hiệu trạm / cây xăng / bảng địa danh; hệ thống tự gắn Timestamp; bấm "Xác nhận đã đến trạm dừng".
- **Escort ghi Nhật ký An sinh (Welfare Log)** trong lúc dừng 30 – 45 phút:
  - Thể trạng: Bình thường / Căng thẳng (Stress) / Đổ mồ hôi nhiều.
  - Chăm sóc: đã cấp nước (số lít ước tính), đã bổ sung cỏ khô.
  - 01 ảnh thực tế cá thể ngựa trong khoang.
  - Bấm "Gửi nhật ký an sinh".
- Hết thời gian dừng, **Driver** bấm "Tiếp tục hành trình" để sang chặng tiếp theo.

### 5.4. Bước 3 — Cửa khẩu (chỉ tuyến Quốc tế CLV)

- Khi vào khu vực kiểm soát biên giới, Driver chụp cổng / barie, bấm "Đã tới cửa khẩu [tên]". Hệ thống thông báo Customer và Coordinator.
- Driver và Escort xuất trình bộ hồ sơ (do nhà xe chuẩn bị) cho cán bộ Thú y và Hải quan, đưa ngựa vào luồng khám lâm sàng.
- Sau khi được đóng dấu thông quan: Driver chụp trang mộc đỏ kiểm dịch trên Health Cert và cuống ATA Carnet có dấu Hải quan (nếu có), bấm **Đã thông quan thành công (Customs Cleared)**. Xe qua barie tiếp tục sang nước bạn.
- Sau thông quan xe vẫn **ở cửa khẩu** cho đến khi Driver bấm **Rời cửa khẩu, tiếp tục hành trình**; chưa bấm thì không xác nhận được mốc kế tiếp.
- Customer nhận Push Notification: ngựa đã hoàn tất thông quan tại cửa khẩu [tên] lúc [giờ:phút].
- Nếu cơ quan chức năng yêu cầu bổ sung hoặc giải trình, Driver kích hoạt liên lạc trực tuyến với Specialist và Manager để xử lý tức thời; khách không phải làm thủ tục.

### 5.5. Bước 4 — Màn hình giám sát của Coordinator

- **Visual Timeline:** mốc chuyển từ xám (chưa tới) sang xanh lá (đã check-in, có ảnh và Timestamp), theo từng xe.
- **Delay Alert:** nếu quá 30 – 45 phút so với ETA của một trạm mà Driver chưa check-in, hệ thống gắn cờ vàng **Delayed Check-in**. Coordinator chủ động liên hệ Driver / Escort.
- **Sức khỏe ngựa:** nhận cảnh báo tức thời nếu Escort báo ngựa mệt mỏi / bỏ ăn, sẵn sàng kích hoạt phương án rẽ vào phòng khám thú y gần nhất (Coordinator định vị tại thời điểm đó).
- **Xe và ngựa:** xem được xe nào chở những ngựa nào, và ngựa nào đang ở xe nào.

### 5.6. Bước 5 — Bàn giao tại điểm đích

1. Tới nơi, Driver chụp cổng cơ sở tiếp nhận, bấm "Đã tới điểm giao đích".
2. Escort cùng Consignee đưa ngựa ra khỏi khoang, kiểm tra thể trạng lần cuối.
3. Driver trả toàn bộ chứng từ gốc của ngựa (Hộ chiếu ngựa, Sổ tiêm, Health Cert, cuống ATA Carnet nếu có) cho Consignee.
4. Hai bên ký bút mực 02 bản "Biên bản Bàn giao & Hoàn tất chuyến đi". Consignee xác nhận đã nhận đủ số lượng ngựa, thể trạng an toàn, đã nhận đủ hồ sơ gốc. Mỗi bên giữ 01 bản.
5. Driver chụp biên bản có đủ chữ ký, tải lên, bấm **Hoàn tất giao ngựa (Complete Delivery)**.
6. Customer nhận thông báo giao thành công và tải được ảnh biên bản. Đơn → **Delivered - Pending Settlement** khi **mọi xe** đã giao xong.

### 5.7. Theo dõi vị trí xe trên bản đồ (thủ công, không GPS)

Vị trí xe suy từ các xác nhận của Driver ở mục 5.1, không dùng GPS:

- Driver xác nhận **tới** một mốc (điểm đón, trạm nghỉ, cửa khẩu, điểm giao): xe nằm đúng chỗ mốc đó.
- Driver bấm **tiếp tục hành trình** (bắt đầu hành trình, rời trạm, rời cửa khẩu): xe đang chạy, hiển thị **ở giữa** mốc vừa rời và mốc kế tiếp.
- Khách xem bản đồ lộ trình (điểm đón, trạm, cửa khẩu, điểm giao, đoạn đã đi, xe), mỗi xe một biểu tượng. Driver thấy dòng trạng thái "Đang ở …" hoặc "Đang trên đường từ … tới …".

---

## 6. Flow 5 — Xử lý sự cố & điều chỉnh khẩn cấp

### 6.1. Nguyên tắc bắt buộc

- **Chuyên môn y tế:** Escort là người duy nhất khám lâm sàng, sơ cứu, xử lý sức khỏe ngựa tại hiện trường. Specialist chỉ phụ trách pháp lý, kiểm dịch và quy chế cửa khẩu.
- **Cố định cửa khẩu:** không tự ý đổi cửa khẩu (do ràng buộc của Health Cert mộc đỏ và Tờ khai hải quan). Khi đổi lộ trình vì tắc nghẽn, chỉ đổi đường đi tới điểm kế tiếp, vẫn qua đúng cửa khẩu đã chốt.
- **Xe cứu hộ:** chỉ sửa xe tại chỗ xe gặp sự cố, không thay xe chạy tiếp (sai biển số so với hồ sơ hải quan và kiểm dịch). Ngựa được đưa riêng tới trạm nghỉ gần nhất để bảo đảm sức khỏe.
- **Thẩm quyền:** Logistics Manager là cấp duy nhất duyệt thay đổi phương án di chuyển và trực tiếp liên hệ khách.
- **Lộ trình:** chỉ khi có sự cố mới sửa lộ trình đã lập ở Flow 1; sự cố ở xe nào thì sửa lộ trình của xe đó.
- **Ai chịu chi phí:** theo chính sách mục 11.5.

### 6.2. Phân công theo nhóm sự cố

| Nhóm sự cố | Phát hiện & xử lý tại chỗ | Lập phương án | Phê duyệt cuối |
|---|---|---|---|
| 1. Sức khỏe ngựa (đau bụng Colic, sốt, mất nước, chấn thương) | Escort sơ cứu, đánh giá lâm sàng | Coordinator lập lại lộ trình **đưa ngựa tới trạm nghỉ gần nhất** (trên bản đồ) | Manager duyệt; gọi khách thông báo tình trạng |
| 2. Xe gặp sự cố (hỏng điều hòa thùng, nổ lốp, sự cố động cơ, tai nạn) | Driver đặt cảnh báo an toàn; Escort mở quạt đối lưu, xịt nước làm mát thùng | Coordinator **gọi cứu hộ gần chỗ xe nhất** để sửa tại chỗ, đồng thời **đưa ngựa tới trạm nghỉ gần nhất** | Manager duyệt phương án |
| 3. Giao thông tắc nghẽn (kẹt xe, tắc đường, đường bị chặn) | Driver báo cáo, giữ nguyên vị trí | Coordinator chọn **lộ trình khác** (đường thay thế trên bản đồ) tới điểm kế tiếp, giữ nguyên cửa khẩu | Manager duyệt |

### 6.3. Bước 1 — Kích hoạt SOS

- Driver / Escort mở App, bấm **Báo cáo Sự cố Khẩn cấp (SOS Alert)**, chọn nhóm sự cố (Sức khỏe ngựa / Kỹ thuật phương tiện / Giao thông tắc nghẽn), chụp ảnh hoặc quay video ngắn, nhập ghi chú vắn tắt, bấm "Gửi Báo động Khẩn cấp". Hệ thống ghi vị trí xe lúc báo (bản thử mô phỏng theo tiến độ hành trình; có app thật thì lấy GPS).
- Hệ thống báo động còi + nhấp nháy đỏ trên màn hình Coordinator và Manager. → **Incident Reported - Action Required**.

### 6.4. Bước 2 — Ứng phó hiện trường

- **Sức khỏe ngựa:** Driver tấp xe vào nơi an toàn. Escort đo thân nhiệt, kiểm tra niêm mạc mắt / nướu, cho uống dung dịch bù điện giải, tiêm / cho uống thuốc chống co thắt giảm đau nếu có dấu hiệu Colic (theo tủ thuốc thú y trên xe).
- **Hỏng điều hòa khoang:** Driver khởi động máy phát điện phụ và quạt hút đối lưu. Escort mở ô thoáng tự nhiên, xịt nước hạ nhiệt chân và cổ ngựa để ngăn sốc nhiệt.

### 6.5. Bước 3 — Coordinator lập kế hoạch điều chỉnh

Coordinator lập phương án **trên bản đồ**, thấy vị trí xe, điểm đón, điểm trả, cửa khẩu; hệ thống tự vẽ đường bộ theo lựa chọn và gợi ý giờ đến mới (Coordinator sửa được):
- **Sức khỏe ngựa:** chọn trạm nghỉ trong danh mục và thời gian nghỉ ngựa (tối thiểu 30 phút). Bản đồ và danh sách **hiện các trạm nghỉ gần chỗ xe nhất, đánh số từ gần tới xa** (như lúc lập lộ trình), xem thêm được mọi trạm; bấm trên bản đồ hoặc trong danh sách đều chọn được. Hệ thống vẽ đường đưa ngựa từ chỗ xe tới trạm.
- **Xe gặp sự cố:** chọn điểm cứu hộ gần chỗ xe nhất (hệ thống gợi ý, kèm số điện thoại) để sửa tại chỗ giữ nguyên xe chính; đồng thời chọn trạm nghỉ đưa ngựa tới như trên. Xe sửa xong thì ngựa đủ sức mới đi tiếp.
- **Giao thông tắc nghẽn:** hệ thống đưa các đường thay thế tới điểm kế tiếp (có thời gian lái, theo giao thông nếu có khóa Google); Coordinator chọn một đường. Cửa khẩu giữ nguyên.
- Không có xe thay thế chạy tiếp; xe hỏng hoàn toàn không khắc phục được (hoặc tai nạn) thuộc phương án hủy chuyến bất khả kháng, chưa làm (mục 14.7).
- Coordinator hoàn thành **Phiếu Kế hoạch Xử lý Sự cố (Incident Action Plan)** trình Manager. → **Pending Emergency Approval**.

### 6.6. Bước 4 — Manager duyệt phương án và làm việc với khách

- Xem lại phương án trên bản đồ (trạm nghỉ, điểm cứu hộ, lộ trình mới) và giờ đến mới.
- Bấm **Phê duyệt Phương án Khẩn cấp (Approve Emergency Plan)**.
- Manager **trực tiếp gọi khách** (hệ thống không có ô xác nhận đã gọi): giải thích nguyên nhân, cập nhật tình trạng an toàn của ngựa, thông báo phương án đã duyệt, ETA mới, và khoản nào khách phải chịu theo mục 11.5.
- → **Emergency Plan Active**.

### 6.7. Bước 5 — Minh bạch trạng thái và tiếp tục hành trình

- **Customer Interface:** bản đồ cập nhật lộ trình rẽ nhánh; nhật ký hiển thị: "Chuyến xe đang tạm dừng tại [Trạm thú y X / Trại đệm Cửa khẩu Y] để chăm sóc sức khỏe cho ngựa. Đội ngũ Escort đang túc trực. Thời gian dự kiến hoàn thành mới: [Giờ, Ngày]."
- **Khôi phục hành trình:** khi ngựa ổn định, hoặc xe chính đã đến đón lại ngựa, hoặc cửa khẩu mở luồng lại: Escort xác nhận thể trạng ngựa đủ điều kiện tiếp tục; Driver bấm **Tiếp tục Hành trình Chính (Resume Journey)**. → **In Transit**.
- Toàn bộ hóa đơn chi tại chỗ được chụp tải lên mục **Chi phí phát sinh thực tế (Actual Incurred Expenses)**, tách theo bên chịu (mục 11.5).

---

## 7. Flow 6 — Bàn giao, quyết toán phát sinh & đóng đơn

**Mục tiêu:** khép vòng đời đơn. Vì giá đã cố định và khách đã trả đủ ngày D, quyết toán chỉ còn **khoản phát sinh thuộc phần khách chịu** (mục 11.5) và dịch vụ khách chọn thêm. Nhiên liệu và BOT **không** quyết toán lại với khách.

1. **Nghiệm thu thực địa & pháp lý:** kiểm tra lâm sàng ngựa, trả bản gốc, ký Biên bản bàn giao.
2. **Quyết toán phát sinh (Evidence-First):** chỉ khi có chi phí khách chịu; có chứng từ.
3. **Lưu trữ & đồng bộ:** cập nhật lịch sử chuyến vào Kho Hồ sơ ngựa, giải phóng xe và nhân sự.

### 7.1. Tiến trình

| Bước | Chủ thể | Hành động chính | Trạng thái |
|---|---|---|---|
| 1 | Driver & Escort | Dắt ngựa xuống an toàn; kiểm tra thể trạng; trả hồ sơ gốc; ký tay 02 bản Biên bản; chụp ảnh gửi App | Delivered - Pending Settlement |
| 2 | Driver | Chụp chứng từ chi phí sự cố thuộc phần khách chịu (nếu có) | Expenses Submitted - Pending Audit |
| 3 | Manager | Đối chiếu chứng từ chi phí sự cố; lập và phát hành Bảng quyết toán phát sinh | Settlement Issued - Awaiting Final Payment |
| 4 | Customer | Xem đối soát; thanh toán (nếu có); chấm điểm | Fully Paid |
| 5 | Hệ thống | Đóng đơn (Order Completed); cập nhật kho hồ sơ ngựa; giải phóng xe & nhân sự | Archived / Completed |

Đơn không có khoản phát sinh nào: khi Driver gửi bảng kê, hệ thống tự phát hành Bảng quyết toán 0 đồng (bỏ bước đối soát của Manager); khách chỉ cần xác nhận và đánh giá.

### 7.2. Bước 1 — Nghiệm thu thực địa và ký biên bản bàn giao

- Escort cùng Consignee hướng dẫn ngựa bước xuống cầu dốc; hai bên kiểm tra lâm sàng: khớp chân, mắt, dấu hiệu sốt / mất nước, vết trầy xước (nếu có).
- Driver trả tận tay người nhận hồ sơ gốc của ngựa.
- Driver xuất trình 02 bản "Biên bản Bàn giao & Hoàn tất Chuyến đi". Consignee xác nhận ngựa đúng mã chip, thể trạng an toàn, đã nhận đủ hồ sơ gốc. Hai bên ký bút mực; mỗi bên giữ 01 bản.
- Driver chụp biên bản đủ chữ ký, bấm **Xác nhận Hoàn tất Giao ngựa**. Hệ thống gửi Push cho khách.
- → **Delivered - Pending Settlement** (khi mọi xe đã giao).

### 7.3. Bước 2 — Kê khai chi phí và khóa nhập liệu (Evidence-First)

**Chứng từ:** hóa đơn viện phí thú y, hóa đơn thuốc cấp cứu, biên lai tiền chuồng đệm có mộc / chữ ký cơ sở tiếp nhận, hóa đơn cứu hộ.

**Luồng kiểm soát trên App của Driver:**
1. Ô nhập số tiền bị khóa mặc định.
2. Driver mở camera ứng dụng chụp chứng từ thực tế, tải lên trước.
3. Tải lên thành công thì ô "Số tiền trên hóa đơn (VNĐ)" mới mở khóa để điền.
4. Không có ảnh chứng từ hợp lệ thì nút "Gửi Bảng Kê Chi Phí" bị vô hiệu hóa hoàn toàn.

→ **Expenses Submitted - Pending Audit**.

### 7.4. Bước 3 — Đối soát và lập Bảng quyết toán phát sinh

- Manager đối chiếu từng chứng từ chi phí sự cố và phân loại bên chịu theo mục 11.5. Làm ở trang **Tiến độ đơn** (nút "Đối soát chi phí" ở đơn `Expenses Submitted - Pending Audit`), không có tab riêng ở trang Sự cố; trang Sự cố chỉ có duyệt phương án và sự cố đang xử lý.
- Chỉ khoản **khách chịu** vào bảng; khoản nhà xe chịu không tính cho khách.

```text
================================================================================
                    BẢNG QUYẾT TOÁN PHÁT SINH (FINAL SETTLEMENT SHEET)
================================================================================
* Mã đơn hàng: ORD-2026-XXXX        * Mã chuyến: TRP-... (các xe)   * Ngày: DD/MM/YYYY
--------------------------------------------------------------------------------
I. GIÁ TRỊ ĐƠN ĐÃ THANH TOÁN (cọc 30% + 70% ngày D):                 ... VNĐ
II. KHOẢN PHÁT SINH THUỘC PHẦN KHÁCH CHỊU (CÓ CHỨNG TỪ)
   1. Thuốc, viện phí thú y:                                         ... VNĐ
   2. Chuồng đệm, cỏ, nước trong thời gian chờ (nếu thuộc phần khách):... VNĐ
   3. Dịch vụ khách chọn thêm:                                       ... VNĐ
   CỘNG MỤC II:                                                      ... VNĐ
--------------------------------------------------------------------------------
SỐ TIỀN KHÁCH CẦN THANH TOÁN:                                        ... VNĐ
================================================================================
```

Manager ký duyệt, bấm **Phê duyệt & Phát hành Quyết toán (Approve & Issue Final Invoice)**. → **Settlement Issued - Awaiting Final Payment**.

### 7.5. Bước 4 — Khách thanh toán và đánh giá

- Khách nhận thông báo Bảng quyết toán trên App trong trang chi tiết đơn (không có trang Nghiệm thu riêng): xem sự cố (nếu có), bảng quyết toán; bấm từng khoản để xem ảnh chứng từ.
- Thanh toán số tiền qua cổng thanh toán tích hợp (chuyển khoản ngân hàng / thẻ tín dụng).
- Chấm điểm 1 – 5 sao cho chuyến đi; gửi đánh giá riêng về độ êm ái / an toàn của xe (Driver) và mức độ chuyên nghiệp / sức khỏe của ngựa (Escort).
- Thanh toán và đánh giá là **một thao tác** của khách: bấm xong thì đơn chuyển thẳng sang đã hoàn tất (`Fully Paid`, `Order Completed`, `Archived / Completed` xảy ra cùng lúc).
- → **Fully Paid**.

### 7.6. Bước 5 — Đóng đơn và đồng bộ Kho Hồ sơ ngựa

- Hệ thống chuyển đơn sang **Order Completed**.
- Tăng số chuyến đi thành công của cá thể ngựa trong kho (ví dụ hiển thị "Đã có 6 đơn"); cập nhật nhật ký hành trình vào lý lịch cá thể ngựa.
- Chuyển mọi xe chuyên dụng sang **Available**; chuyển các Driver và Escort sang **Available**.
- → **Archived / Completed**.

---

## 8. Ngoại lệ

### 8.1. Người nhận (Consignee) từ chối tiếp nhận ngựa tại điểm đích

- Driver tuyệt đối không dắt ngựa xuống xe khi chưa có đại diện tiếp nhận ký biên bản. Driver bấm **"Consignee Refused Delivery"** trên App.
- Coordinator điều xe đưa ngựa về Holding Stable gần nhất đã liên kết để hạ ngựa nghỉ, cấp nước sạch và cỏ khô, theo dõi.
- Chi phí lưu xe (Demurrage), tiền thuê chuồng đệm và chi phí thức ăn / nước uống trong thời gian chờ là **lỗi từ phía khách / người nhận**, cộng vào Bảng quyết toán của khách (mục 11.3).

**Sau khi đã thông quan (tuyến Quốc tế, xe đã qua barie biên giới), áp dụng cho Consignee từ chối nhận tại nước bạn:**
- Đã qua barie biên giới = đã hoàn tất xuất khẩu. Tuyệt đối **không tự ý quay đầu xe** về Việt Nam. Ưu tiên an sinh: chuyển ngay về cơ sở đệm sở tại.
- Không giao ngựa nếu người nhận từ chối ký biên bản. Không lưu ngựa trên xe quá **02 giờ**. Coordinator điều xe về **Holding Stable** liên kết tại nước bạn để hạ ngựa chăm sóc.
- **Chế tài tài chính:** khách thanh toán 100% cước chiều đi (khoản đã trả được tính vào số tiền này, khách trả phần còn lại nếu thiếu), và chịu toàn bộ chi phí lưu chuồng, tiền cỏ nước và phí lưu xe tại nước bạn.
- **Hồi hương:** muốn đưa ngựa về Việt Nam thì đóng đơn cũ và mở **Đơn hàng Hồi hương độc lập (Return Transit)**; làm lại thủ tục kiểm dịch nhập khẩu ngược từ đầu (chờ 7 – 10 ngày tại trạm đệm).
- Khách chối bỏ trách nhiệm quá **48 giờ**: áp dụng quyền cầm giữ động vật theo hợp đồng (Lien on Cargo).

### 8.2. Khách chậm trễ hoặc từ chối thanh toán

- **Số dư 70% ngày D:** chưa trả đủ thì xe không được bắt đầu hành trình (mục 5.2). Xe chờ do khách chưa trả được tính theo mục 11.3.
- **Khoản phát sinh sau chuyến (Flow 6):** hạn đệm **24 giờ** kể từ lúc Manager phát hành Bảng quyết toán.
- **Sau 24 giờ:** trạng thái chuyển **Payment Overdue**. Hệ thống:
  - Khóa tài khoản và bảo lưu dữ liệu: tự động khóa quyền đặt đơn mới, tạm ngừng toàn bộ quyền truy cập / trích xuất dữ liệu các cá thể ngựa trong Kho Hồ sơ ngựa.
- **Cầm giữ:** nếu ngựa đang ở Holding Stable, nhà xe thực thi quyền cầm giữ theo hợp đồng vận chuyển cho đến khi khách thanh toán đủ chi phí vận tải và chăm sóc lưu trú.
- **Quá 07 ngày:** hệ thống trích xuất toàn bộ gói hồ sơ điện tử (Báo giá, Vận đơn, Lệnh điều vận, ảnh Biên bản ký tay, chứng từ chi phí) chuyển Bộ phận Pháp lý để khởi kiện hoặc yêu cầu cơ quan thẩm quyền xử lý theo pháp luật.

### 8.3. Hủy đơn

Chỉ hủy được khi **xe chưa nhận ngựa**. Từ lúc xe đã nhận ngựa hoặc đã qua cửa khẩu thì xử lý theo mục 8.1. Đơn bị hủy chuyển `Cancelled`, xe, Driver và Escort được nhả.

| Ai hủy | Khi nào | Tiền |
|---|---|---|
| Khách | Chưa có báo giá (`Pending Manager Intake`, `Under Internal Review`, `Pending Final Commercial Approval`) | **Không hủy được**: đang thẩm định để báo giá thì phải đợi có báo giá |
| Khách | Đang chờ đặt cọc (`Awaiting Payment`) | Không có nút Hủy đơn: dùng **Từ chối báo giá** (mục 2.6), không mất phí |
| Khách | Đã cọc, xe chưa nhận ngựa (`Waybill Issued` đến `En Route to Pickup`) | **Mất 100% tiền cọc**. Số dư 70% đã trả (nếu có) hoàn 100% |
| Manager | Đã cọc, xe chưa nhận ngựa (như dòng trên); nút **Hủy đơn** ở trang Tiến độ đơn, bắt buộc ghi lý do | Hệ thống hủy nên **hoàn đủ tiền cọc và số dư 70% đã trả** cho khách |

Giao diện hủy đơn của khách hiện rõ số tiền mất và số tiền hoàn. Khách nhận thông báo hoàn tiền khi Manager hủy.

---

## 9. Dịch vụ thủ tục kiểm dịch & hải quan trọn gói

### 9.1. Mô hình

Nhà xe làm trọn gói thủ tục kiểm dịch, hải quan và giấy pháp lý chuyến đi cho khách. Khách không cần biết giấy thông quan: khách chỉ cần biết ngựa được chở tới nơi mình muốn. Việc để ngựa tới được nơi đó là nghiệp vụ của nhà xe. Specialist là người làm giấy, nhà xe nhập thông tin.

### 9.2. Phạm vi trách nhiệm

- **Nhà xe (Specialist, Coordinator):** làm tờ khai hải quan, giấy kiểm dịch, PoA, giấy phép liên vận; xuất trình tại cửa khẩu; báo cáo tiến độ lên hệ thống.
- **Khách:** cung cấp Hồ sơ ngựa chính xác và giao bản gốc cho tài xế tại điểm đón; xem tiến độ giấy tờ (chỉ xem).
- Phí dịch vụ thủ tục nằm trong báo giá cố định (mục 11.1).

### 9.3. Chính sách phương tiện và sự cố kỹ thuật

- **Biển số cố định:** không điều xe thay thế giữa chặng (xem mục 1.4).
- **Sự cố kỹ thuật thông thường:** sửa tại chỗ bằng kỹ thuật lưu động / garage liên kết. Trong lúc sửa, nguồn điện phụ trợ duy trì máy lạnh và quạt hút để giữ nhiệt độ chuẩn. Hệ thống cập nhật ETA cửa khẩu cho khách.
- **Sự cố bất khả kháng (hỏng không sửa được, tai nạn):** kích hoạt đội cứu nạn chuyển ngựa về trạm lưu trú / chăm sóc thú y an toàn gần nhất. Đơn vị lập Biên bản xác nhận sự cố bất khả kháng gửi khách và xử lý bồi thường theo điều khoản vận chuyển. Chi tiết ở Flow 5.

---

## 10. Hạng xe & gán xe tự động

### 10.1. Hạng xe

Căn cứ tiêu chuẩn chế tạo xe vận chuyển ngựa của các hãng lớn (Stephex Horseboxes, Bloomfields), hệ thống có 3 hạng xe:

| Hạng | Loại xe | Sức chứa |
|---|---|---|
| **Light** | Xe tải nhẹ / Van | 2 Stalls |
| **Medium** | Xe tải trung | 4 đến 6 Stalls |
| **Heavy** | Xe tải nặng | 9 Stalls |

### 10.2. Chọn xe, tài xế, hộ tống

- Mỗi đơn có **một hoặc nhiều xe**. **Coordinator tự chọn xe**; hệ thống không gán tự động. Khuyến nghị dùng **ít xe nhất**: nếu có xe chứa đủ số ngựa thì dùng một xe; đơn đông ngựa thì chia ngựa cho nhiều xe (ví dụ 6 ngựa mà không có xe 6 chỗ: 2 xe, mỗi xe 3 ngựa). Số ngựa trên mỗi xe không vượt sức chứa của xe.
- Mỗi xe có **01 Driver + 01 Escort do Manager chọn**, sau khi Coordinator đã chọn xe và nhìn theo số xe đó (xe không có tài xế riêng). Xe, Driver, Escort nằm ở ba popup riêng; mỗi thẻ hiện lịch các đơn khác mà xe hoặc người đó đang giữ.
- **Khóa xe trùng lịch:** xe đang giữ cho đơn khác có **ngày đi cách ngày đi này dưới 3 ngày** thì bị khóa, nêu rõ trùng với đơn nào. **Driver và Escort khóa theo giao việc:** đã được giao vào một chuyến của đơn chưa kết thúc thì không chọn được (bất kể ngày đi), thẻ ghi rõ đã giao việc cho đơn nào; xe giao ngựa xong thì họ được mở lại. Cách này thay cho việc tự suy ra "có đơn cùng ngày" hay "về kịp". Xe thiếu giấy đăng kiểm (và giấy phép liên vận với tuyến quốc tế) cũng bị khóa. Một xe, Driver hoặc Escort không được chọn hai lần trong cùng một đơn. Manager chỉ biết nhân viên (Specialist, Coordinator) đang hoạt động hay nghỉ, không quản lý số đơn họ đang nhận. Hệ thống kiểm tra lại khi xác nhận phương án.
- **Không quản lý bảo dưỡng xe.** Trạng thái của xe chỉ có hai: **Có đơn** (đang giữ chỗ cho một đơn chưa kết thúc) hoặc **Chưa có đơn**, hệ thống tự suy ra từ các đơn. Điều kiện để một xe được gán vẫn là xe rảnh vào ngày D và đủ giấy đăng kiểm (và giấy phép liên vận với tuyến quốc tế).
- Mỗi ngựa thuộc đúng một xe. Dù xe còn chỗ trống, **không** ghép ngựa của đơn khác (Charter độc quyền).
- Mỗi xe có chuyến riêng (mã chuyến, Lệnh điều xe, check-in, nhật ký an sinh, biên bản); đơn hoàn tất khi mọi xe giao xong.

---

## 11. Gói cước, biểu phí & chính sách chi phí sự cố

### 11.1. Báo giá (chi tiết ở mục 2.5)

- **Tất cả khoản trong báo giá là cố định:** cước vận chuyển (theo cự ly và hạng xe, từng xe); nhân sự (01 Driver + 01 Escort mỗi xe); phí khoang đặc thù và gói thức ăn và cữ nước khách chọn theo ngựa; phí thủ tục kiểm dịch & hải quan; nhiên liệu và BOT; phí bảo hiểm Động vật Sống (ngựa đồng ý mua).
- **Nhiên liệu và BOT:** ước tính theo quãng đường của lộ trình đã lập, cộng **dự phòng 5%**, đưa vào báo giá. Nhà xe chịu chênh lệch so với thực tế.
- **Lợi nhuận:** biên lợi nhuận **5%** gộp vào đơn giá, không hiện thành dòng riêng. Giá báo phải bảo đảm nhà xe không lỗ khi chịu chênh lệch.
- **Thanh toán:** cọc **30%** tổng giá trị (chưa VAT) để nhận Vận đơn; **70%** còn lại trả ngày D. Hủy đơn sau cọc theo mục 8.3.
- **Thời hạn giữ báo giá:** 48 giờ.

### 11.2. Phí thủ tục kiểm dịch & hải quan

Một khoản **cố định** trong báo giá, nhà xe làm trọn gói. Thay cho Carrier Info Sheet / Carrier Data Package ở bản cũ.

### 11.3. Phí lưu xe chờ (Demurrage)

**300.000đ – 500.000đ / giờ.** **Không** nằm trong báo giá. Chỉ áp dụng khi xe phải chờ do **lỗi phía khách / người nhận**: Consignee từ chối nhận ngựa (mục 8.1), hoặc khách chưa trả 70% số dư đúng giờ ngày D làm xe không đi được. Không áp dụng cho chậm trễ giấy tờ do nhà xe làm.

### 11.4. Chi phí thực tế

Nhiên liệu và BOT **không** quyết toán lại với khách (đã nằm trong báo giá). Chỉ các khoản phát sinh thuộc phần khách chịu (mục 11.5) mới có Bảng quyết toán sau chuyến, và phải có chứng từ (mục 7.3).

### 11.5. Chính sách chi phí sự cố

Nguyên tắc: **liên quan đến ngựa thì khách chịu; liên quan đến vận chuyển thì nhà xe chịu.** Mọi khoản nhà xe tính cho khách đều cần Manager duyệt và có chứng từ.

| Nhóm | Bên chịu | Ví dụ |
|---|---|---|
| Liên quan **ngựa** | **Khách** | Thuốc, viện phí thú y; chuồng đệm, cỏ và nước trong lúc chờ; ngựa ốm hoặc chấn thương; hồ sơ ngựa sai / hết hạn; người nhận từ chối; hồi hương; lưu xe do lỗi khách (mục 11.3) |
| Liên quan **vận chuyển** | **Nhà xe** | Hỏng xe, cứu hộ cơ khí, hỏng điều hòa thùng, tai nạn do xe hoặc tài xế, chậm do nhà xe, giấy do nhà xe làm sai, chênh lệch nhiên liệu và BOT |
| **Giao thông tắc nghẽn** (bên ngoài) | **Nhà xe chịu toàn bộ** | Phí đường tránh, chi phí chờ, chăm sóc ngựa trong lúc chờ. Điều khoản: khách chấp nhận giao trễ khi tắc nghẽn và không yêu cầu bồi thường |

Nội dung chính sách xem ở trang điều khoản (mục 11.6), liên kết có trong phiếu báo giá và trang đơn.

### 11.6. Trang điều khoản và chính sách

- Điều khoản vận chuyển, chính sách chi phí khi có sự cố và Điều khoản Trách nhiệm Hạn chế **không nằm trong từng trang**: khách bấm liên kết (mở tab mới) sang **một trang riêng** `/terms`, công khai, có ba mục chuyển qua lại.
- Mỗi văn bản là **một tệp ảnh hoặc PDF** do chủ dự án cung cấp, đặt ở `public/policies` và khai báo ở `POLICY_DOCS` (`src/shared/config/business-rules.ts`). Nhà xe sửa nội dung bằng cách thay tệp, không sửa code. Tệp hiện tại là bản mẫu.
- Liên kết có ở: phiếu báo giá, bước đặt cọc (điều khoản vận chuyển), bước chọn bảo hiểm khi khách từ chối (Điều khoản Trách nhiệm Hạn chế), trang đơn và Bảng quyết toán (chính sách chi phí sự cố).

---

## 12. Hồ sơ, giấy tờ & SOP check-in

**Phạm vi:** mục 12.1 – 12.3 áp dụng cho tuyến vận chuyển đường bộ liên vận quốc tế Việt Nam ⇄ Campuchia ⇄ Lào. Tuyến Nội địa có danh mục riêng ở mục 12.4.

**Phân định trách nhiệm:** khách chỉ chuẩn bị **hồ sơ ngựa** (Bộ A, phần khách). Mọi giấy thú y chuyến đi, hải quan, vận tải do **nhà xe** chuẩn bị, làm thủ tục và xuất trình tại barie biên phòng, hải quan và trạm kiểm dịch cửa khẩu.

### 12.1. Bộ A — Hồ sơ kỹ thuật thú y & động vật sống

Escort giữ trực tiếp, làm thủ tục tại Trạm Kiểm dịch Động vật cửa khẩu hai đầu.

**Khách cung cấp (tải lúc tạo đơn, giao bản gốc tại điểm đón):**
1. **Hộ chiếu ngựa (Equine Passport / FEI Passport):** bản gốc. Có sơ đồ nhận dạng chi tiết và mã Microchip trùng khớp tuyệt đối với chip cấy trên thân ngựa.
2. **Sổ tiêm phòng:** Cúm ngựa (Equine Influenza), Dại (Rabies) theo quy định liên vận.
3. **Hồ sơ xét nghiệm dịch tễ (Equine Lab Tests):** bản gốc kèm 02 bản sao công chứng. Âm tính EIA / Coggins, EVA.

**Nhà xe làm:**
4. **Giấy chứng nhận kiểm dịch động vật xuất khẩu (Veterinary Health Certificate):** mộc đỏ của Cơ quan Thú y có thẩm quyền nước xuất khẩu.
5. **Giấy phép nhập khẩu (Import Permit):** do Cục Thú y / Bộ Nông nghiệp nước nhập khẩu phê duyệt.
6. **Chứng nhận cách ly kiểm dịch trước xuất phát (Pre-export Quarantine Certificate):** nếu nước nhập khẩu yêu cầu.
7. **Nhật ký chăm sóc & An sinh động vật dọc đường (Animal Welfare & Feeding Log):** mẫu do hệ thống cấp.

### 12.2. Bộ B — Cặp hồ sơ phương tiện, vận tải & thủ tục hải quan

Lưu trong cabin xe, do Tài xế chính quản lý để xuất trình tại luồng Hải quan, Biên phòng. **Nhà xe chuẩn bị**, Coordinator nhập bộ giấy cho từng Driver.

1. **Văn bản ủy quyền áp tải (PoA):** song ngữ Việt – Anh, nhà xe soạn.
2. **Vận đơn (Waybill):** bản gốc do hệ thống phát hành, có chữ ký xác nhận của đại diện giao nhận hai bên.
3. **Tờ khai hải quan điện tử:** Specialist điền, khách chỉ xem tiến độ.
4. **Hóa đơn thương mại / ATA Carnet:** chỉ khi áp dụng (xem mục 14).
5. **Hồ sơ phương tiện vận tải liên vận quốc tế:** giấy phép vận tải liên vận (CLV hoặc song phương), đăng kiểm còn hạn, bảo hiểm trách nhiệm dân sự có hiệu lực tại các quốc gia trong lộ trình, hộ chiếu và GPLX quốc tế / liên vận của tài xế và phụ xe.

### 12.3. Quy trình tiếp nhận & kiểm tra chứng từ (Check-in SOP)

1. **Phân định nguồn giấy:**

   | Nguồn | Giấy | Thời điểm |
   |---|---|---|
   | **Khách tải** | Hộ chiếu ngựa; Sổ tiêm phòng; Hồ sơ xét nghiệm dịch tễ | Lúc tạo đơn (Flow 1); Specialist duyệt |
   | **Nhà xe làm** | Health Cert; Tờ khai hải quan; Import Permit; PoA; Giấy cách ly; giấy phép liên vận; Hóa đơn thương mại / ATA Carnet (nếu có) | Sau cọc (Flow 2); Specialist cập nhật tiến độ |
   | **Hệ thống / nhà xe cấp** | Vận đơn; Lệnh điều xe; hồ sơ phương tiện (đăng kiểm, bảo hiểm xe, hộ chiếu / bằng lái tài xế và phụ xe) | Flow 1 – 3 |

   Hệ thống đối soát mã chip và thông tin tài xế / xe.
2. **Bàn giao thực tế tại điểm bốc hàng:** người giao ngựa ký biên bản giao nhận chứng từ vật lý với tài xế. Mọi thiếu sót hoặc sai lệch thông tin trên bản gốc so với bản đã tải lên dẫn đến **đình chỉ xuất phát tạm thời**.
3. **Sự cố giấy tờ tại cửa khẩu:** nếu cơ quan chức năng yêu cầu bổ sung hoặc giải trình, tài xế liên lạc trực tuyến với Specialist và Manager để xử lý ngay.

### 12.4. Danh mục giấy tờ tuyến Nội địa

Nội địa = đi và đến đều trong Việt Nam, theo quy định của Chi cục Chăn nuôi & Thú y cấp tỉnh. Không qua cửa khẩu, nên bỏ toàn bộ giấy xuất / nhập khẩu và thủ tục hải quan.

| Nguồn | Giấy |
|---|---|
| **Khách tải lúc tạo đơn** | Hộ chiếu ngựa (bản gốc có Microchip trùng chip trên thân ngựa); Sổ tiêm phòng, gồm Tiêm phòng Cúm ngựa (còn hạn trong 6 – 12 tháng) và Tiêm phòng Uốn ván (còn hiệu lực); Xét nghiệm EIA / Coggins âm tính trong vòng 6 – 12 tháng |
| **Nhà xe làm** | Health Cert nội địa (mộc đỏ, Chi cục Chăn nuôi & Thú y cấp tỉnh cấp, hiệu lực 15 – 30 ngày); **PoA** (đối soát với CSGT và Quản lý thị trường liên tỉnh, tránh bị giữ xe vì nghi vấn vận chuyển động vật không rõ nguồn gốc) |
| **Hệ thống / nhà xe cấp** | Vận đơn; Lệnh điều xe; Đăng kiểm xe và Bảo hiểm trách nhiệm dân sự của xe; CCCD và GPLX của Driver, CCCD của Escort |

**Khám lâm sàng:** ngựa không sốt, không lở loét móng, không có triệu chứng hô hấp.

**Đơn đi và đến trong cùng một tỉnh:** tạm thời áp dụng như đơn Nội địa thông thường. Danh mục giản lược cho đơn cùng tỉnh chốt sau.

**Không áp dụng cho nội địa:** Import Permit, Chứng nhận cách ly trước xuất phát, ATA Carnet, Hóa đơn thương mại / Kê khai giá trị, Tờ khai hải quan, Giấy phép vận tải liên vận quốc tế, hộ chiếu tài xế và phụ xe.

**Bản gốc Driver thu tại điểm đón (Flow 4):** Hộ chiếu ngựa, Sổ tiêm và phiếu xét nghiệm. Trả lại cho Consignee ở điểm giao như tuyến quốc tế.

**Khác với tuyến Quốc tế:** Flow 4 bỏ mốc 3 và mốc 4 (cửa khẩu, thông quan); Specialist không làm Tờ khai hải quan và không chọn cửa khẩu.

---

## 13. Trạng thái đơn hàng

Theo thứ tự xuất hiện trong quy trình:

| Giai đoạn | Trạng thái |
|---|---|
| Flow 1 | `Pending Manager Intake` → `Under Internal Review` → `Pending Final Commercial Approval` → `Awaiting Payment` (hoặc `Quote Expired`) |
| Flow 2 | `Waybill Issued` → `Clearance In Progress` → `Clearance Done` |
| Flow 3 | `Ready for Pickup` → `En Route to Pickup` |
| Flow 4 | `In Transit - Leg 1` → `Delayed Check-in` (cờ cảnh báo của Coordinator) → `Delivered - Pending Settlement` |
| Flow 5 | `Incident Reported - Action Required` → `Pending Emergency Approval` → `Emergency Plan Active` → `In Transit` |
| Flow 6 | `Expenses Submitted - Pending Audit` → `Settlement Issued - Awaiting Final Payment` → `Fully Paid` → `Order Completed` → `Archived / Completed` (ba trạng thái cuối chuyển cùng lúc khi khách thanh toán và đánh giá, hệ thống ghi một trạng thái `Order Completed`); quá hạn: `Payment Overdue` |
| Hủy đơn | `Cancelled` (khách từ chối báo giá hoặc hủy đơn, hoặc Manager hủy đơn; tiền theo mục 8.3) |
| Từ chối đơn | `Order Rejected` (Manager không tiếp nhận ở `Pending Manager Intake`, hoặc Coordinator không duyệt xe và lộ trình ở `Under Internal Review`; chưa có cọc nên không hoàn / mất tiền) |

Trạng thái sự cố (Flow 5) của đơn suy từ các sự cố đang mở của các xe, ưu tiên `Incident Reported - Action Required`, rồi `Pending Emergency Approval`, rồi `Emergency Plan Active`. Mỗi xe có tối đa một sự cố đang mở; xe có sự cố mở thì không check-in hay đi tiếp được.

**Bước tiến độ hiển thị** (khách và Manager) khớp quy trình: Gửi đơn → Thẩm định → Báo giá → Đặt cọc → Giấy tờ → Sẵn sàng (gồm `Clearance Done`, `Ready for Pickup`, `En Route to Pickup`) → Vận chuyển (gồm `In Transit` và các trạng thái sự cố) → Quyết toán (từ `Delivered - Pending Settlement` đến khi khách thanh toán) → hoàn tất. Trạm nghỉ, cửa khẩu chỉ là **mốc nhỏ** bên trong bước Vận chuyển, hiển thị theo từng xe.

Đơn nhiều xe: các trạng thái Flow 3 – 4 có ở **từng chuyến (xe)**. Trạng thái của đơn lấy từ các chuyến: `Ready for Pickup` khi mọi xe đã sẵn sàng; `En Route to Pickup` và `In Transit - Leg 1` khi có xe đầu tiên chạy; `Delivered - Pending Settlement` khi mọi xe đã giao.

---

## 14. Điểm cần chốt

Các điểm dưới đây là **giả định tạm** cần chủ dự án xác nhận hoặc thay số thật.

1. **Biểu giá chưa có công thức chính thức.** Đang dùng số mẫu trong `src/shared/config/booking-rules.ts`: cước theo km và hạng xe (hệ số 1,0 / 1,4 / 1,9 cho Light / Medium / Heavy), nhân sự 1.800.000 đ/ngày mỗi xe, khoang đơn mở rộng 1.500.000 đ/ngựa, gói thức ăn Nâng cao 300.000 đ và Thể thao 500.000 đ/ngựa, gói Cơ bản 0 đ (`FEED_PACKAGE`), cữ nước mỗi 2 giờ 50.000 đ và mỗi giờ 100.000 đ/ngựa, cữ 3 giờ 0 đ (`WATER_PLAN`), phí thủ tục kiểm dịch & hải quan 300.000 đ (quốc tế) và 0 đ (nội địa), bảo hiểm 2% giá trị theo giống (`BREED_INSURED_VALUE`: Thoroughbred 1 tỷ, Arabian 800 triệu, Warmblood 900 triệu, Quarter Horse 600 triệu, Appaloosa 500 triệu, Khác 400 triệu), phí lưu xe 400.000 đ/giờ (chỉ khi lỗi khách). **Mới (số mẫu):** cọc 30%, dự phòng nhiên liệu và BOT 5%, biên lợi nhuận 5%, đơn giá nhiên liệu và BOT theo km (chưa có).
2. **Dữ liệu danh bạ mẫu:** danh mục trạm nghỉ (tên, tọa độ), số khung, số đăng kiểm, giấy phép liên vận của xe, CCCD và GPLX của nhân sự đều là số sinh mẫu.
4. **Mốc nhận lệnh:** xe sẵn sàng khi cả Driver và Escort của xe đó bấm nhận lệnh; xe đi đến điểm đón khi Driver bấm bắt đầu.
5. **Chia chặng nội địa:** tuyến nội địa trong bộ địa điểm hiện có đều ngắn (dưới 4 giờ lái) nên thường không cần trạm nghỉ; quy tắc 3–4 giờ vẫn áp dụng và có kiểm tra.
6. **Giấy chỉ khách có (ATA Carnet, Hóa đơn thương mại):** nhà xe không tự lập được. Tạm thời: khách giao bản gốc cho Driver tại điểm đón, Specialist ghi nhận vào hạng mục giấy tờ. Cần chủ dự án chốt cách xử lý.
7. **Flow 5, 6 đã dựng, còn các phần chưa làm:** hủy chuyến bất khả kháng khi xe hỏng hoàn toàn (mục 6.5), người nhận từ chối (mục 8.1), cầm giữ ngựa và chuyển hồ sơ Pháp lý sau 7 ngày (mục 8.2), nhiều sự cố cùng lúc trên một xe. Khi Payment Overdue hệ thống mới chặn đặt đơn mới, chưa khóa xem Kho Hồ sơ ngựa. Các trang cũ Bảng điều khiển, Báo cáo chuyến đi (Manager), Giám sát sự cố và Đội xe (Điều phối) vẫn chạy trên dữ liệu cũ.
7a. **Giả định khi dựng Flow 5, 6:** Không có hạn mức chi khẩn cấp (Coordinator không đề nghị, Manager không duyệt), Driver / Escort kê chi phí có chứng từ; mỗi khoản chi có bên chịu mặc định theo mục 11.5 và Manager sửa được lúc đối soát; Payment Overdue chỉ áp dụng khi bảng quyết toán có khoản phải trả; cứu hộ, sửa xe và tắc nghẽn giao thông thì mọi khoản mặc định nhà xe chịu (chỉ thuốc, viện phí, phí trạm nghỉ do ngựa thì khách); sự cố sức khỏe ngựa và xe gặp sự cố cần Escort xác nhận ngựa đủ sức trước khi Driver tiếp tục hành trình, tắc nghẽn thì không. Danh mục điểm cứu hộ (`RESCUE_POINTS`) là số mẫu; vị trí xe lúc báo sự cố là mô phỏng; đường thay thế chỉ tính giữa vị trí xe và điểm kế tiếp (giới hạn của dịch vụ bản đồ).
9. **Báo cáo (mục 15):** chi phí vận hành là giá vốn ước tính từ báo giá (giả định biên 5%), chưa có chi phí thực tế; doanh thu ghi nhận theo ngày đặt cọc (chưa theo ngày giao). Cần chủ dự án chốt cách ghi nhận doanh thu và chi phí thật.
8. **Giao diện:** danh mục xe cần có số đăng kiểm (và giấy phép liên vận cho tuyến quốc tế), thiếu thì hệ thống không gán xe. Xem trước ảnh dạng popup chỉ hiện ảnh vừa chọn trong phiên (chưa có kho tệp), ảnh mẫu hiện ô thay thế.

---

## 15. Báo cáo của Manager

Trang **Báo cáo doanh thu** của Manager (chỉ xem), chọn khoảng 7 / 30 / 90 ngày, so với kỳ liền trước cùng độ dài, có xuất CSV theo đơn.

**Tài chính** (theo ngày đặt cọc của đơn):
- **Doanh thu:** giá trị đơn đã đặt cọc (tổng báo giá cộng khoản phát sinh khách chịu ở Bảng quyết toán). Đơn đã hủy: chỉ tính phần tiền khách đã trả mà không được hoàn (mục 8.3). Đơn chưa cọc, bị từ chối hoặc hết hạn báo giá thì không có doanh thu.
- **Chi phí vận hành:** giá vốn theo báo giá, tức bỏ biên lợi nhuận 5% khỏi các dòng cước, nhân sự, nhiên liệu và BOT (các dòng khoang, thủ tục, bảo hiểm tính nguyên) **cộng** chi phí sự cố nhà xe chịu (mục 11.5). Đơn hủy: chỉ tính chi phí sự cố nếu có.
- **Lợi nhuận** = doanh thu − chi phí; **biên lợi nhuận** = lợi nhuận / doanh thu. **Đã thu** = cọc + 70% + khoản quyết toán khách đã trả − tiền hoàn.

**Hiệu suất hoàn thành chuyến** (theo ngày xuất phát của từng xe):
- **Tỷ lệ hoàn thành:** số xe đã giao ngựa / số xe đã xuất phát.
- **Giao đúng giờ:** xe tới điểm giao không trễ quá 30 phút so với giờ dự kiến (cùng ngưỡng cờ Delayed Check-in, mục 5.5).
- **Chuyến có sự cố:** số xe từng có sự cố / số xe đã xuất phát.
- **Thời gian giao trung bình:** từ lúc bắt đầu hành trình đến lúc giao xong; kèm điểm đánh giá trung bình của khách.

Số tiền là **số mẫu** theo biểu giá mẫu (mục 14.1). Chi phí thật (nhiên liệu, BOT thực tế, lương) chưa được ghi nhận nên chưa đối chiếu được với giá vốn ước tính theo báo giá.
