# Mô hình nghiệp vụ – phạm vi thay đổi mức 1 và mức 2

Tài liệu này rút từ [MO_HINH_NGHIEP_VU_MOI.md](MO_HINH_NGHIEP_VU_MOI.md), **chỉ giữ những gì sẽ thay đổi ở mức 1 (nền móng) và mức 2 (sửa nghiệp vụ)**. Mục đích là để cân nhắc thiết kế DB cho đợt cải thiện này. Những gì đang chạy đúng thì tận dụng tối đa.

- Trạng thái: **bản phân tích**, chưa triển khai.
- Mã lỗi (A1, B2, F1…) tham chiếu [DANH_SACH_LOI_THIET_KE.md](DANH_SACH_LOI_THIET_KE.md).
- Mức 3 và giai đoạn 2 nằm ngoài phạm vi. Mục 6 liệt kê chúng và chỉ ra chỗ cần chừa sẵn trong DB.

---

## 1. Phạm vi

| Mức | # | Thay đổi | Loại |
|---|---|---|---|
| 1 | 1 | Quản lý phiên bản lược đồ bằng Flyway | Kỹ thuật |
| 1 | 2 | Đưa dữ liệu cốt lõi ra khỏi `BOOKING.DetailsJson` | Kỹ thuật và dữ liệu |
| 1 | 3 | Chuyến xe (`trip`) và đoạn (`shipment_leg`) thành bảng | Nghiệp vụ và dữ liệu |
| 1 | 4 | Backend tự tính giá, có dòng báo giá và bảng giá theo hiệu lực | Nghiệp vụ và bảo mật |
| 1 | 5 | Kiểm dịch vận chuyển cho đơn liên tỉnh | Nghiệp vụ (pháp lý) |
| 2 | 6 | Chi phí phát sinh chia theo nguyên nhân | Nghiệp vụ |
| 2 | 7 | Hiệu lực báo giá và hạn thanh toán | Nghiệp vụ (tham số) |
| 2 | 8 | Bảo hiểm thành bảng riêng, có xác nhận từ chối bảo hiểm | Nghiệp vụ và dữ liệu |
| 2 | 9 | Tách người gửi và người nhận thành các bên (`party`) | Dữ liệu |
| 2 | 10 | Giấy tờ đối chiếu với yêu cầu, quy định theo nước và chiều đi | Nghiệp vụ và dữ liệu |
| 2 | 11 | Lịch sử trạng thái đơn | Dữ liệu |
| 2 | 12 | Bỏ gói thức ăn, thay bằng chỉ dẫn chế độ ăn | Nghiệp vụ |

**Giữ nguyên, không đổi trong đợt này:**
- Đăng nhập: JWT, refresh token dạng cookie HttpOnly, phân quyền, Turnstile.
- Luồng VNPAY và cơ chế đối soát cọc sát hạn ([DEPOSIT_PAYMENT_DEADLINE.md](../frontend/docs/DEPOSIT_PAYMENT_DEADLINE.md)).
- Thẩm định song song.
- Tuyến cố định và logic khớp hành lang.
- Luồng SOS và bản đồ.
- Check-in của tài xế và hộ tống, chụp ảnh chứng từ.
- Thông báo, nhật ký hệ thống, báo cáo.
- **Mô hình trụ sở duy nhất:** khai báo trụ sở là hub duy nhất.

---

## 2. Mô hình nghiệp vụ trong phạm vi

### 2.1 Định vị
- Công ty tự chạy các đoạn trong Việt Nam. Đoạn ở Lào và Campuchia có thể giao cho **đại lý** và bàn giao tại cửa khẩu.
  - Trong đợt này, đại lý chỉ được ghi nhận như một bên chạy đoạn.
  - Cách ly, lưu trú và cổng thông tin cho đại lý để sau.
- Rủi ro với ngựa thuộc chủ ngựa. Trách nhiệm của công ty có trần. Khách ký xác nhận điều khoản khi đặt đơn.

### 2.2 Loại đơn

| Loại | Thay đổi |
|---|---|
| `LOCAL` – nội địa cùng tỉnh | Mới tách ra. Không cần giấy kiểm dịch vận chuyển |
| `INTERPROVINCE` – nội địa liên tỉnh | Mới tách ra. **Bắt buộc giấy chứng nhận kiểm dịch vận chuyển ra khỏi tỉnh** (TT 01/2026, Điều 5) |
| `EXPORT` – xuất sang Lào/Campuchia | Giữ như đơn quốc tế hiện tại, thêm quy định theo nước |
| `IMPORT` – nhập từ Lào/Campuchia | Giữ như đơn quốc tế hiện tại, thêm quy định theo nước |

Hiện tại `booking_type` chỉ có `domestic` và `international`.

### 2.3 Luồng đơn
Luồng và **các trạng thái hiện tại giữ nguyên** (xem mục 3.11). Thay đổi chỉ nằm ở **dữ liệu sinh ra** trong mỗi bước:

| Bước | Trạng thái hiện tại | Dữ liệu mới sinh ra |
|---|---|---|
| Khách gửi đơn | `pending_intake` | `booking`, `booking_party`, `booking_horse` (kèm chỉ dẫn chế độ ăn), `booking_horse_service`, `horse_insurance` (bản nháp), `terms_acceptance` |
| Quản lý tiếp nhận | `under_review` hoặc `rejected` | `booking_status_history` |
| Thẩm định song song | `under_review` | `booking_review` (y tế/giấy tờ, bảo hiểm, kế hoạch). Điều phối viên tạo `trip` và `shipment_leg` |
| Lập và duyệt báo giá | `pending_commercial` | Backend tạo `quotation` và `quotation_line` từ bảng giá |
| Chờ đặt cọc | `awaiting_payment`, `quote_expired` | `payment` (DEPOSIT) và `payment_transaction` |
| Cấp vận đơn | `waybill_issued` | `booking.waybill_no` |
| Làm giấy tờ | `clearance_in_progress`, `clearance_done` | `document`, `document_link` gắn với `document_requirement` |
| Sẵn sàng và đón ngựa | `ready_for_pickup`, `en_route_to_pickup`, `pickup_failed` | `payment` (BALANCE). `trip` và `shipment_leg` chuyển sang chạy |
| Vận chuyển và sự cố | `in_transit`, `incident_reported`, `pending_emergency_approval`, `emergency_plan_active` | `leg_checkpoint`, `horse_health_log`, `incident`, `incident_plan`, `cost_item` |
| Giao ngựa | `arrived`, `handover_timeout`, `returning_to_hq`, `delivered_pending_settlement` | `handover` |
| Quyết toán | `expenses_submitted`, `settlement_issued`, `completed` | `cost_item` đã duyệt; `payment` (EXTRA_CHARGE) cho phần khách chịu |

---

## 3. Chi tiết từng thay đổi

Mỗi mục trình bày bốn ý: hiện trạng, mô hình mới, dữ liệu cần thêm hoặc sửa, và phần tận dụng được.

### 3.1 Quản lý phiên bản lược đồ (mức 1, #1)
- **Hiện trạng** (lỗi A4):
  - `spring.jpa.hibernate.ddl-auto=update`;
  - 5 class `*Migration.java` và `HorseMicrochipUniqueIndex` chạy lúc khởi động;
  - các script `backend/scripts/migrations/001–004`.

  Không có lược đồ chuẩn để đối chiếu.
- **Mới:**
  - Dùng Flyway. Lấy DB hiện tại làm `V1` (mốc ban đầu), mỗi thay đổi sau là một file `V{n}__*.sql`.
  - Đổi `ddl-auto` sang `validate`.
- **Tận dụng:** nội dung các script và class migration hiện có thành các bản migration đầu tiên.
- **Ảnh hưởng tới ERD:** ERD vẽ từ thư mục migration là chính xác. Không vẽ từ entity nữa.

### 3.2 Đưa dữ liệu ra khỏi `DetailsJson` (mức 1, #2)
- **Hiện trạng:** `BOOKING.DetailsJson` chứa 22 nhóm dữ liệu (lỗi A1). Bảng `BOOKING_HORSE` và `SERVICE_BOOKING` có trên ERD nhưng không bao giờ được ghi (A2). Xe, báo giá, thanh toán, lịch sử lưu ở 2–3 nơi (A3).
- **Mới:** mỗi nhóm dữ liệu có bảng riêng. Sau khi chuyển xong, `DetailsJson` không còn chứa dữ liệu nghiệp vụ.

| Nhóm trong `DetailsJson` | Chuyển sang | Mục |
|---|---|---|
| `horses` (cùng `selectedServices`, `insurance`) | `booking_horse`, `booking_horse_service`, `horse_insurance` | 3.2, 3.8, 3.12 |
| `trips`, `route`, `plan`, `gate` | `trip`, `shipment_leg`, `crew_assignment` | 3.3 |
| `quote` | `quotation`, `quotation_line` | 3.4 |
| `payment`, `settlement` | `payment`, `payment_transaction`, `cost_item` | 3.4, 3.6 |
| `documentRequirements`, `medical`, `clearance` | `document`, `document_link`, `booking_review` | 3.10 |
| `history`, `rejection`, `sendBack` | `booking_status_history` | 3.11 |
| `arrival`, `handover` | `handover` | 3.3 |
| `photoEvidence` | `document` + `document_link` | 3.10 |
| `rating` | `booking_rating` (bảng nhỏ) | |
| `intake`, `scheduleChange` | Cột trên `booking` hoặc `booking_status_history` | 3.11 |

- **Cách chuyển (strangler):** thêm bảng bên cạnh JSON → ghi vào cả hai nơi → chuyển phần đọc sang bảng → bỏ trường JSON. Mỗi nhóm là một PR.
- **Bắt đầu từ `booking_horse`**, vì bảng đã có sẵn.

### 3.3 Chuyến xe và đoạn (mức 1, #3)
- **Hiện trạng** (lỗi B1):
  - Chuyến chỉ là phần tử JSON có `tripId` dạng chuỗi.
  - Xe và nhân sự lưu ở `BOOKING_VEHICLE`, `BOOKING_ASSIGNMENT` **và** `DetailsJson.trips[]`.
  - `INCIDENT.trip_id`, `CAPTURE_ASSET.tripId`, `HORSE_JOURNEY_LOG.trip_code` trỏ vào thứ không tồn tại.
- **Mới:**
  - Một đơn gồm **nhiều đoạn**. Mỗi đoạn do **đội xe công ty** (gắn với một chuyến) hoặc **đại lý** chạy.
  - Một chuyến = một xe + kíp xe, xuất phát từ trụ sở và về trụ sở.
  - Một đơn nhiều xe thì có nhiều chuyến. Ngựa nào đi xe nào được ghi rõ.
  - Đơn quốc tế có đại lý: đoạn Việt Nam → cửa khẩu do công ty chạy; đoạn cửa khẩu → điểm giao do đại lý chạy; bàn giao tại cửa khẩu có biên bản.
  - Cấu trúc cho phép một chuyến chở đoạn của nhiều đơn, nhưng **đợt này chưa mở ghép chuyến**. Một chuyến thuộc một đơn, kiểm tra ở tầng nghiệp vụ.
- **Quy tắc:**
  - Một xe không có hai chuyến chồng thời gian. Giữ logic khoảng giữ xe hiện tại: nội địa từ 1 ngày trước đến 2 ngày sau ngày đi, quốc tế từ 2 ngày trước đến 4 ngày sau.
  - Xe chuyển sang `AVAILABLE` khi chuyến `RETURNED_TO_HUB`.
- **Tận dụng:**
  - Logic giữ xe và kiểm tra trùng lịch trong [FleetServiceImpl.java](../backend/src/main/java/com/example/racehorse_transport/service/impl/FleetServiceImpl.java).
  - Lộ trình và trạm nghỉ: tuyến cố định cùng `route` và `location_route`.
  - Check-in hiện tại chuyển thành `leg_checkpoint`.

### 3.4 Báo giá do backend tính (mức 1, #4)
- **Hiện trạng** (lỗi B2, B3, F1, G1):
  - Frontend tính giá trong `booking.ts` (`quoteLines`) và `pricing.ts` (`truckCost`), rồi gửi số tiền lên. Backend chỉ đối chiếu dòng khoang và gói thức ăn, nên **sửa request là đổi được giá**.
  - Tham số giá lưu dạng key-value trong `SYSTEM_CONFIG`, một số khác là hằng số cứng ở frontend.
  - Kết quả lưu trong `QUOTATION.quote_lines_json`.
  - Ví dụ về lệch dữ liệu: phí khoang đơn mở rộng là 1.500.000 ở frontend nhưng 2.000.000 ở seed backend.
- **Mới:**
  - Backend là nơi **duy nhất** tính giá. Frontend gọi API xem trước giá.
  - Báo giá có phiên bản. Mỗi dòng lưu lại số lượng, đơn giá và mục giá nguồn tại thời điểm chốt.
  - Báo giá đã phát hành thì không sửa, muốn đổi phải tạo phiên bản mới.
- **Công thức giữ nguyên, chỉ đổi nơi lưu tham số:**

  ```
  Cước mỗi xe     = (phí mở chuyến + Σ km từng bậc × đơn giá bậc) × hệ số hạng xe × (1 + biên lợi nhuận)
  Nhiên liệu+BOT  = km × đơn giá nhiên liệu × (1 + tỷ lệ dự phòng)
  Tổng            = Σ cước xe + Σ nhiên liệu + Σ dịch vụ + phí thủ tục + Σ thu hộ (bảo hiểm) ± điều chỉnh
  ```

| Tham số | Hiện tại | Mới |
|---|---|---|
| Phí mở chuyến, bậc km, đơn giá bậc | `SYSTEM_CONFIG` (`KM_TIER_*`) | `price_item` (`km_from`, `km_to`, `unit_price`) |
| Hệ số hạng xe | `SYSTEM_CONFIG` (`CLASS_FACTOR_*`); hạng xe tách từ chuỗi `vehicle_type` | `vehicle_class.price_factor`, với `vehicle.vehicle_class_id` FK |
| Biên lợi nhuận, dự phòng nhiên liệu | `SYSTEM_CONFIG` và hằng số frontend | Cột của `price_list` |
| Đơn giá nhiên liệu + BOT (9.000 đ/km) | Hằng số frontend | `price_item` của dịch vụ `FUEL_TOLL` |
| Phí thủ tục (300.000 đ cho đơn quốc tế) | Biến frontend | `price_item` của dịch vụ `QUARANTINE_DOCS` |
| Phí khoang, dịch vụ | `SERVICE.price` | `price_item` |
| Điều chỉnh của quản lý | `QUOTATION.adjustment_amount`, `adjustment_note` | Một dòng `quotation_line` loại ADJUSTMENT |
| Tổng, cọc, số dư | Cột lưu sẵn trên `QUOTATION` | Tính từ các dòng, không lưu |

- **Tận dụng:** công thức, màn hình admin cấu hình giá ([AdminPricingPage.tsx](../frontend/src/apps/backoffice/features/admin/pricing/AdminPricingPage.tsx)), luồng duyệt báo giá của quản lý.

### 3.5 Kiểm dịch vận chuyển liên tỉnh (mức 1, #5)
- **Hiện trạng:**
  - Đã có yêu cầu giấy `health_cert` ("Giấy chứng nhận kiểm dịch động vật vận chuyển"), áp dụng cho mọi đơn nội địa.
  - Chưa phân biệt cùng tỉnh và liên tỉnh.
  - Chưa tính thời gian làm giấy vào lịch.
  - Chưa ghi việc niêm phong và khử trùng xe.
- **Mới:**
  - Đơn `INTERPROVINCE` bắt buộc có giấy chứng nhận kiểm dịch vận chuyển ra khỏi tỉnh.
  - Ngày đi sớm nhất = hôm nay + thời gian thẩm định + thời gian làm giấy (1 ngày làm việc xem hồ sơ và tới 5 ngày làm việc cấp giấy chứng nhận).
  - Ghi nhận xe đã được niêm phong và khử trùng.
  - Đơn `LOCAL` không cần giấy này.
- **Dữ liệu:**
  - `country_rule` với `direction = INTERPROVINCE` cho Việt Nam;
  - `document_requirement` gắn với quy định đó;
  - `trip.sealed_at` và `trip.disinfected_at`.
- **Tận dụng:** `DocumentRequirementSeeder`, màn hình Chuyên viên và khách theo dõi tiến độ giấy tờ.

### 3.6 Chi phí phát sinh theo nguyên nhân (mức 2, #6)
- **Hiện trạng:**
  - Công ty chịu toàn bộ chi phí sự cố và chi phí chờ bàn giao.
  - `INCIDENT` có 32 cột, gồm cả `plan_json` và `expenses_json`. Thông tin đối tác là chuỗi (`partner_name`, `partner_phone`).
- **Mới:** mỗi khoản chi ghi **nguyên nhân** và **bên chịu**.

| Tình huống | Bên chịu |
|---|---|
| Hỏng xe, tai nạn do công ty | Công ty (thiệt hại cho ngựa tính trong giới hạn trần) |
| Ngựa ốm hoặc bị thương không do lỗi công ty | Khách |
| Chờ người nhận trong số giờ miễn phí (hiện tại 2 giờ) | Công ty |
| Chờ quá mốc, phải lưu chuồng hoặc chở về trụ sở | Người nhận, không trả thì khách |
| Giấy tờ khách cung cấp sai | Khách |
| Thủ tục do công ty hoặc đại lý làm sai | Công ty |
| Thiên tai, đóng cửa khẩu | Khách (chi phí phát sinh) |

- **Quy tắc:**
  - Phải hỏi khách trước khi chi, trừ trường hợp khẩn cấp.
  - Khẩn cấp thì chi trong **hạn mức ủy quyền** của đơn.
  - Khoản do khách hoặc người nhận chịu, sau khi quản lý duyệt, sinh ra một `payment` loại EXTRA_CHARGE.
- **Dữ liệu:** tách `incident` thành ba bảng:
  - `incident`: báo cáo, kèm `cause`;
  - `incident_plan`: phương án, duyệt hoặc từ chối;
  - `cost_item`: khoản chi.

  Thêm `partner` thay cho các cột đối tác dạng chuỗi.
- **Tận dụng:** toàn bộ luồng SOS, bản đồ, duyệt phương án, xác nhận ngựa đủ sức đi tiếp (`fit_confirmed_*`), đối soát chi phí nội bộ.

### 3.7 Hiệu lực báo giá và hạn thanh toán (mức 2, #7)
- **Hiện trạng:** báo giá hiệu lực 24 giờ. Khách trả 70% còn lại trước khi xuất phát.
- **Mới:**
  - Báo giá hiệu lực N ngày, có điều khoản: cước hoặc phí bảo hiểm tăng thì khách trả phần chênh.
  - Số dư phải trả **trước ngày đón N ngày**. Chưa trả đủ thì không nhận ngựa.
  - Sau khi đã nhận ngựa thì không hoàn tiền. Trước đó áp dụng biểu phí hủy.
- **Dữ liệu:**
  - Phần lớn là tham số trong `system_config`.
  - `quotation` lưu lịch đã chốt: `confirmed_pickup_date`, `confirmed_departure_date`, `estimated_delivery_date`.
  - `payment.due_at`.
- **Tận dụng:** toàn bộ luồng VNPAY, khóa booking, khoảng chờ xác nhận 5 phút.

### 3.8 Bảo hiểm thành bảng riêng (mức 2, #8)
- **Hiện trạng** (lỗi B5): bảo hiểm nằm trong `horses[].insurance` (JSON).
- **Mới:**
  - Mỗi ngựa trong đơn có tối đa một bản ghi bảo hiểm: **cá nhân**, **hỗ trợ**, hoặc **không bảo hiểm**.
  - **Bổ sung:** chọn "không bảo hiểm" thì khách phải **xác nhận điều khoản trách nhiệm có giới hạn**, gắn với phiên bản điều khoản.
  - Công ty chỉ thu xếp khi khách yêu cầu trước khi giao ngựa.
  - Giá trị khai báo của ngựa không có nghĩa là khách đã mua bảo hiểm.
- **Tận dụng:** **toàn bộ logic** trong [BAO_HIEM_BEN_THU_BA.md](BAO_HIEM_BEN_THU_BA.md) và nhánh `feat/insurance-resubmission`:
  - lưu nháp và xác nhận;
  - khóa khi khách đang cân nhắc báo giá;
  - bản chụp báo giá bảo hiểm;
  - bổ sung hợp đồng sau khi đặt cọc;
  - yêu cầu khách bổ sung hồ sơ.

  Chỉ đổi chỗ lưu. Bản chụp `insuranceSnapshot` thành các dòng `quotation_line` có cờ thu hộ.

### 3.9 Tách các bên (mức 2, #9)
- **Hiện trạng:** `BOOKING` có 12 cột `consignor_*` và `consignee_*` (tên, điện thoại, địa chỉ, loại, số và nước cấp giấy tờ tùy thân). `CUSTOMER` tách riêng.
- **Mới:**
  - Mọi bên liên quan đến đơn là một `party`.
  - Một đơn gắn với các bên theo vai trò: người thanh toán, người gửi, người nhận, người liên hệ ở điểm đón.
  - Khách có tài khoản thì `party` gắn với `app_user`. Người nhận không cần tài khoản.
- **Tận dụng:** form nhập người gửi và người nhận, gồm cả giấy tờ tùy thân dùng cho hải quan.

### 3.10 Giấy tờ đối chiếu với yêu cầu (mức 2, #10)
- **Hiện trạng** (lỗi B6):
  - `HORSE_DOCUMENT.document_type` là chuỗi, không có FK tới yêu cầu.
  - Có 3 bảng tệp với cấu trúc giống nhau: `HORSE_DOCUMENT`, `TRIP_DOCUMENT` (chưa bao giờ được ghi), `CAPTURE_ASSET`.
  - `REGULATION` và `DOCUMENT_REQUIREMENT` trùng vai trò.
- **Mới:**
  - Quy định theo **nước và chiều đi** (liên tỉnh, xuất, nhập, tái nhập). Mỗi quy định có danh sách giấy tờ, **bên phải nộp** (khách / người nhận / công ty / đại lý), thời gian làm giấy và số ngày cách ly (tham số, dùng để tính lịch).
  - Mỗi tệp gắn với một đối tượng và với yêu cầu mà nó đáp ứng. Hệ thống trả lời được "đơn này đã đủ giấy chưa".
- **Tận dụng:**
  - `DocumentRequirementSeeder` và các loại giấy hiện có (`health_cert`, `poa`, `customs_declaration`, `import_permit`, `quarantine_cert`, `ata_carnet`, `commercial_invoice`);
  - màn hình admin quản lý yêu cầu giấy tờ;
  - các cột duyệt (`review_note`, `reviewed_at`, người duyệt);
  - các cột `document_number`, `issue_date`, `expiry_date` của `TRIP_DOCUMENT`.

### 3.11 Lịch sử trạng thái (mức 2, #11)
- **Hiện trạng** (lỗi B7): `BOOKING.status` là chuỗi tự do. Lịch sử nằm ở `DetailsJson.history` và bảng `LOG`.
- **Mới:**
  - Giữ nguyên 27 trạng thái đang dùng trong [booking-rules.ts](../frontend/src/shared/config/booking-rules.ts), thêm `CHECK` trên DB.
  - Mọi lần chuyển trạng thái ghi vào `booking_status_history`, kèm người thực hiện và lý do (từ chối, trả về khách, đổi lịch).
- **Danh sách trạng thái giữ:**
  - `schedule_change_required`
  - `pending_intake`, `under_review`, `pending_commercial`, `awaiting_payment`, `quote_expired`
  - `waybill_issued`, `clearance_in_progress`, `clearance_done`
  - `ready_for_pickup`, `en_route_to_pickup`, `pickup_failed`, `returning_horses`
  - `in_transit`, `incident_reported`, `pending_emergency_approval`, `emergency_plan_active`
  - `arrived`, `handover_timeout`, `returning_to_hq`, `delivered_pending_settlement`
  - `expenses_submitted`, `settlement_issued`, `payment_overdue`, `completed`
  - `cancelled`, `rejected`
- `payment_overdue` hiện chỉ để đọc dữ liệu cũ (legacy). Có thể chuyển dữ liệu rồi bỏ khỏi `CHECK`.

### 3.12 Chỉ dẫn chế độ ăn (mức 2, #12)
- **Hiện trạng:** 3 gói thức ăn (0 / 300.000 / 500.000 đ mỗi ngựa) và 3 mức cữ nước (đang ngừng dùng).
- **Mới:**
  - Mỗi ngựa trong đơn có chỉ dẫn chế độ ăn: khẩu phần, số lần cho ăn, thức ăn cấm hoặc dị ứng, khách có tự gửi thức ăn không.
  - Cho ăn uống theo yêu cầu là dịch vụ `FEEDING`, tính theo ngựa theo ngày. Đúng theo IRT Anh điều 15: chỉ làm khi khách yêu cầu, chi phí do khách.
- **Dữ liệu:** cột trên `booking_horse`, một dòng trong `service`. Đơn cũ vẫn hiển thị gói đã chọn như dữ liệu lịch sử.

---

## 4. DB trong phạm vi

Quy ước:
- Khóa chính `bigint identity`.
- Tiền `numeric(15,2)` kèm `currency`.
- Thời điểm `timestamptz`.
- Trạng thái và loại là `varchar` có `CHECK`.
- Không lưu cột dẫn xuất. Mọi tham chiếu có FK.

Ký hiệu: **Giữ** (đổi tên hoặc thêm vài cột), **Sửa** (cấu trúc thay đổi), **Mới**, **Bỏ**.

### 4.1 Tài khoản, các bên, ngựa

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `app_user` | Giữ (`users`) | id, username, email UQ, password_hash, email_verified, preferred_language, role, status |
| `staff` | Giữ | user_id PK/FK, staff_code UQ, hire_date, employment_status, phone |
| `party` | Sửa (từ `customer` và các cột `consignor_*`, `consignee_*`) | id, kind (PERSON/ORGANIZATION), name, phone, email, address, country_code FK, id_type, id_number, id_country, user_id? FK UQ |
| `horse_type` | Giữ | id, type_name, description. **Bỏ `insured_value`** |
| `horse` | Sửa | id, owner_party_id FK, horse_name, sire?, dam?, birth_year (thay `age`), color, gender, breed, horse_type_id FK, microchip UQ?, identification_marks, weight, allergies?, declared_value?, declared_currency?, status. **Bỏ** các cột vị trí và trạng thái lưu sẵn (tính qua view) |

### 4.2 Danh mục và quy định

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `country` | Mới | code PK, name, default_currency |
| `location` | Giữ | id, location_code UQ, location_name, location_type, country_code FK, province, address, latitude, longitude, opening_minutes, closing_minutes, contact_phone, neighbor_country_code? (cửa khẩu), status |
| `route`, `route_stop` | Giữ (`route`, `location_route`) | route_stop: route_id, stop_order, location_id, arrival_time, departure_time. **Bỏ** lat/lng trùng với `location` |
| `partner` | Mới | id, type (DEST_AGENT, INSURER, VET, STABLE…), name, country_code FK, contact_name, phone, email, status |
| `country_rule` | Mới (thay `regulation`) | id, country_code FK, direction (INTERPROVINCE/EXPORT/IMPORT/REIMPORT), requires_import_permit, permit_lead_workdays, cert_lead_workdays, quarantine_days, valid_from, valid_to? |
| `document_type` | Mới | id, code UQ, name, subject |
| `document_requirement` | Sửa | id, rule_id FK, document_type_id FK, gate_location_id? FK, submitted_by, is_mandatory, per_horse, status |
| `terms_version` | Mới | id, kind, version, effective_from, content_url. UQ(kind, version) |
| `system_config` | Giữ | Chỉ còn tham số vận hành (hiệu lực báo giá, hạn số dư, giờ chờ miễn phí, hạn mức ủy quyền mặc định…). **Bỏ** các key bảng giá |

### 4.3 Bảng giá

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `service` | Sửa | id, service_code UQ, service_name, layer (TRANSPORT/STALL/ADD_ON/SURCHARGE), billing_unit, scope, auto_apply, is_pass_through, status. **Bỏ `price`, `categoryid`** |
| `vehicle_class` | Mới | id, code (LIGHT/MEDIUM/HEAVY), name, price_factor |
| `price_list` | Mới | id, name, currency, margin_rate, fuel_buffer_rate, valid_from, valid_to?, status |
| `price_item` | Mới | id, price_list_id FK, service_id FK, vehicle_class_id? FK, km_from?, km_to?, unit_price |
| `category` | **Bỏ** | Thay bằng `service.layer` |

### 4.4 Đơn

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `booking` | Sửa | id, code UQ, waybill_no UQ?, customer_party_id FK, booking_type (LOCAL/INTERPROVINCE/EXPORT/IMPORT), origin_location_id FK, destination_location_id FK, route_id? FK, proposed_departure_date, emergency_spend_limit?, status (CHECK), booking_date. **Bỏ** `details_json` (sau khi chuyển xong), `total_horses`, `total_booking_price`, các cột `consignor_*` và `consignee_*` |
| `booking_party` | Mới | booking_id, role (PAYER/SHIPPER/CONSIGNEE/PICKUP_CONTACT), party_id. PK(booking_id, role) |
| `booking_review` | Mới | booking_id, branch (DOCUMENT/INSURANCE/PLAN), reviewer_id?, status, completed_at? PK(booking_id, branch) |
| `booking_horse` | Sửa | id, booking_id, horse_id, stall_service_id FK, diet_note, feed_times_per_day, owner_supplies_feed, status, added_date. UQ(booking_id, horse_id). **Bỏ** `total_horse_service_price` |
| `booking_horse_service` | Sửa (`service_booking`) | booking_horse_id, service_id, quantity. **Bỏ** `unit_price`, `subtotal` (chuyển sang dòng báo giá) |
| `booking_status_history` | Mới (thay `log`) | id, booking_id, from_status, to_status, changed_by, changed_at, reason |
| `terms_acceptance` | Giữ | id, booking_id, terms_version_id FK, accepted_by, accepted_at |
| `booking_rating` | Mới (từ `DetailsJson.rating`) | booking_id PK, score, comment, rated_at |

### 4.5 Vận hành

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `vehicle` | Sửa | id, license_plate UQ, vehicle_class_id FK, stall_count, wide_stall_count, status |
| `trip` | Mới (thay `booking_vehicle` và `DetailsJson.trips`) | id, code UQ, vehicle_id FK, planned_depart_at, planned_return_at, actual_depart_at?, actual_return_at?, sealed_at?, disinfected_at?, status |
| `shipment_leg` | Mới | id, booking_id FK, seq, from_location_id FK, to_location_id FK, operator (OWN_FLEET/PARTNER), trip_id? FK, partner_id? FK, distance_km, planned_start, planned_end, actual_start?, actual_end?, status. UQ(booking_id, seq). CHECK theo operator |
| `leg_horse` | Mới | leg_id, booking_horse_id. PK ghép. Ngựa nào đi đoạn hoặc xe nào |
| `crew_assignment` | Sửa (`booking_assignment`) | id, trip_id FK, staff_id FK, assign_role (DRIVER/ESCORT), status, note, assigned_at |
| `leg_checkpoint` | Sửa (`horse_journey_log`) | id, leg_id FK, location_id? FK, depart_at?, arrived_at?, recorded_by FK, source, notes |
| `handover` | Mới (từ `DetailsJson.arrival` và `handover`) | id, leg_id FK, kind (PICKUP/AGENT_HANDOVER/DELIVERY), receiver_party_id?, receiver_partner_id?, receiver_name, wait_started_at?, handed_at?, condition_note? |
| `horse_health_log` | Giữ | id, booking_horse_id FK, leg_id? FK, health_status, symptoms, temperature, weight, report_by FK, recorded_at |

### 4.6 Bảo hiểm

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `horse_insurance` | Mới (từ JSON) | id, booking_horse_id FK UQ, option (PERSONAL/ARRANGED/NONE), insurer_partner_id?, premium?, currency?, quote_expires_at?, policy_no?, valid_from?, valid_to?, status (DRAFT/CONFIRMED), revision_reason?, waiver_acceptance_id? FK, confirmed_by?, confirmed_at?. CHECK: NONE thì phải có waiver; ARRANGED thì phải có insurer |

### 4.7 Báo giá và thanh toán

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `quotation` | Sửa | id, booking_id FK, version, status, currency, valid_until, confirmed_pickup_date, confirmed_departure_date, estimated_delivery_date, price_list_id FK, sent_by FK, sent_at, accepted_at?. UQ(booking_id, version). Một báo giá ACCEPTED mỗi đơn. **Bỏ** `quote_lines_json`, `subtotal`, `total_amount`, `deposit_amount`, `balance_amount`, `adjustment_*` |
| `quotation_line` | Mới | id, quotation_id FK, line_type (FREIGHT/FUEL/STALL/SERVICE/DOCS/INSURANCE/ADJUSTMENT), service_id?, price_item_id?, trip_id?, booking_horse_id?, description, quantity, unit_price, amount (generated), is_pass_through |
| `payment` | Sửa | id, booking_id FK, quotation_id? FK, kind (DEPOSIT/BALANCE/EXTRA_CHARGE/REFUND), amount, currency, payment_method, status, transaction_code, gateway_transaction_no, bank_code, due_at?, paid_at?, refund_of_payment_id? FK |
| `payment_transaction` | Sửa (`transaction_log`) | id, payment_id FK, response_code, gateway_paid_at, received_at, amount, status |

### 4.8 Sự cố và chi phí

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `incident` | Sửa | id, booking_id FK, leg_id? FK, incident_name, incident_type, cause, description, location_lat, location_lng, reported_by FK, created_at, status, fit_confirmed_at?, fit_confirmed_by? |
| `incident_plan` | Mới (từ `plan_json` và các cột duyệt) | id, incident_id FK, resolution_type, directive, estimated_cost, partner_id? FK, status, approved_by?, approved_at?, rejection_by?, rejection_at?, rejection_reason? |
| `cost_item` | Mới (từ `expenses_json`, `actual_cost`, `cost_bearer`) | id, booking_id FK, incident_id? FK, trip_id? FK, handover_id? FK, category, description, amount, currency, payer (COMPANY/CUSTOMER/CONSIGNEE/AGENT), customer_consulted, approved_by?, approved_at?, extra_payment_id? FK |

### 4.9 Chứng từ

| Bảng | Tình trạng | Cột chính |
|---|---|---|
| `document` | Mới (gộp `horse_document`, `trip_document`, `capture_asset`) | id, document_type_id FK, file_url, file_name, document_number?, issue_date?, expiry_date?, uploaded_by FK, uploaded_at, captured_at?, status, reviewed_by?, reviewed_at?, review_note? |
| `document_link` | Mới | id, document_id FK, requirement_id? FK, horse_id?, booking_id?, booking_horse_id?, leg_id?, insurance_id?, incident_id?, cost_item_id?, handover_id? (tất cả FK). **CHECK num_nonnulls(...) = 1** |

### 4.10 Hỗ trợ

| Bảng | Tình trạng |
|---|---|
| `notification` | Giữ, thêm FK `recipient_userid` và `bookingid` |
| `system_log` | Giữ, gộp `location_change_log` vào |
| `otp` | Sửa: gộp `email_verification_otps` và `password_reset_otps`, thêm cột `purpose` |
| `log`, `location_change_log`, `booking_vehicle`, `category`, `regulation`, `horse_document`, `trip_document`, `capture_asset` | **Bỏ** sau khi chuyển dữ liệu |

### 4.11 Quan hệ chính

```
party 1─N booking 1─N booking_party N─1 party
booking 1─N booking_horse N─1 horse
booking_horse 1─N booking_horse_service N─1 service
booking_horse 1─0..1 horse_insurance
booking 1─N shipment_leg N─0..1 trip N─1 vehicle
shipment_leg N─0..1 partner
shipment_leg N─N booking_horse (leg_horse)
trip 1─N crew_assignment N─1 staff
booking 1─N quotation 1─N quotation_line
quotation 1─N payment 1─N payment_transaction
booking 1─N incident 1─N incident_plan
incident 1─N cost_item N─0..1 payment (EXTRA_CHARGE)
country 1─N country_rule 1─N document_requirement N─1 document_type
document 1─N document_link N─0..1 document_requirement
price_list 1─N price_item N─1 service
```

### 4.12 Quy tắc ở tầng ứng dụng
Các quy tắc sau phụ thuộc nhiều bảng nên không đặt được ràng buộc DB:
1. Chỉ duyệt báo giá khi đủ 3 nhánh `booking_review` đã DONE.
2. Chỉ cho đón ngựa khi khoản BALANCE đã PAID, giấy tờ bắt buộc đã VERIFIED và bảo hiểm (nếu chọn) đã CONFIRMED.
3. Đợt này mỗi chuyến chỉ thuộc một đơn.
4. Ngày đi đề xuất phải lớn hơn hoặc bằng hôm nay + thời gian thẩm định + thời gian làm giấy + số ngày cách ly (lấy từ `country_rule`).
5. Tổng số ngựa trên một chuyến không vượt số khoang của xe.

---

## 5. Thứ tự triển khai

| Bước | Nội dung | Mục |
|---|---|---|
| 1 | Flyway: lấy DB hiện tại làm mốc ban đầu, `ddl-auto=validate` | 3.1 |
| 2 | Ghi `booking_horse`, `booking_horse_service`; chỉ dẫn chế độ ăn | 3.2, 3.12 |
| 3 | Bảng giá, `vehicle_class`, `quotation_line`, backend tự tính giá | 3.4 |
| 4 | `trip`, `shipment_leg`, `leg_horse`, `crew_assignment`, `partner`, `handover` | 3.3 |
| 5 | `party`, `booking_party`, `booking_status_history`, `CHECK` trạng thái | 3.9, 3.11 |
| 6 | `horse_insurance`, `terms_version`, xác nhận từ chối bảo hiểm | 3.8 |
| 7 | `country_rule`, `document_type`, `document`, `document_link`; loại đơn liên tỉnh | 3.5, 3.10 |
| 8 | `incident_plan`, `cost_item`, `payment` mở rộng; đổi tham số thanh toán | 3.6, 3.7 |
| 9 | Bỏ các trường JSON và các bảng cũ đã thay thế | 3.2 |

Mỗi bước là một hoặc vài PR nhỏ, chạy đủ Quality Gate (`mvnw verify`, `npm run lint`, `npx tsc -b`, `npm test`, `npm run build`).

---

## 6. Ngoài phạm vi và chỗ cần chừa

| Nội dung | Mức | Chừa sẵn trong DB |
|---|---|---|
| Hub ngoài trụ sở | 3 | `location_type` đã có. Thêm `vehicle.home_hub_id` sau |
| Cách ly trước xuất và sau khi đến dưới dạng lưu trú (`horse_stay`) | 3 | `quarantine_days` trong `country_rule` đã dùng để tính lịch. Bảng `horse_stay` thêm sau, FK tới `booking_horse` |
| Sự kiện thông quan tại cửa khẩu (`border_clearance`) | 3 | `handover` loại AGENT_HANDOVER và chứng từ đã ghi được mức tối thiểu |
| Ghép chuyến | Giai đoạn 2 | `trip` và `shipment_leg` đã tách. Thêm `scheduled_departure` sau |
| Khứ hồi | Giai đoạn 2 | Có thể thêm `booking.return_of_booking_id` nullable ngay bây giờ |
| Khiếu nại (`claim`) | Giai đoạn 2 | Không cần chừa |
| Giấy phép xe, người đi kèm | Giai đoạn 2 | Không cần chừa |
| Tái cấu trúc package (`util/`, `config/`, service quá lớn) | 3 | Làm dần khi sửa từng service |

---

## 7. Cần quyết trước khi vẽ DB

1. Giá trị các tham số: hiệu lực báo giá, hạn trả số dư, biểu phí hủy, trần trách nhiệm, hạn mức ủy quyền mặc định.
2. Có thêm `booking.return_of_booking_id` ngay đợt này không (chi phí gần như bằng 0).
3. Tham số giá đang ở `SYSTEM_CONFIG`: chuyển một lần sang `price_list` và `price_item`, hay giữ song song một thời gian.
4. Cách chuyển dữ liệu cũ: chuyển toàn bộ đơn cũ sang bảng mới, hay chỉ áp dụng cho đơn tạo sau ngày chuyển. Đơn cũ khi đó giữ JSON ở chế độ chỉ đọc.
