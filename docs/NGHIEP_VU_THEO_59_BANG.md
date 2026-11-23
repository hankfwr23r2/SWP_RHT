# Nghiệp vụ chi tiết theo mô hình 59 bảng

> Tài liệu mô tả **ai làm gì, lúc nào, ghi vào bảng nào** trong hệ thống mới. Phần cuối nêu những gì tận dụng được từ project cũ.
>
> - Đặc tả cột và ràng buộc: [DB_SCHEMA_59_BANG.md](DB_SCHEMA_59_BANG.md). Khi có mâu thuẫn, file đó đúng.
> - Tài liệu **tham khảo** (căn cứ pháp lý, cách làm của IRT/Peden), không phải yêu cầu: [MO_HINH_NGHIEP_VU_MOI.md](MO_HINH_NGHIEP_VU_MOI.md).
> - Vì sao thiết kế như vậy: [NGU_CANH_THIET_KE_LAI.md](NGU_CANH_THIET_KE_LAI.md).

## Mục lục

1. [Bức tranh tổng thể](#1-bức-tranh-tổng-thể)
2. [Các bên và vai trò](#2-các-bên-và-vai-trò)
3. [Dữ liệu nền do admin chuẩn bị](#3-dữ-liệu-nền-do-admin-chuẩn-bị)
4. [Vòng đời một đơn: 10 giai đoạn](#4-vòng-đời-một-đơn-10-giai-đoạn)
5. [Nhánh phụ và ngoại lệ](#5-nhánh-phụ-và-ngoại-lệ)
6. [Ví dụ xuyên suốt: một đơn đi Phnom Penh](#6-ví-dụ-xuyên-suốt-một-đơn-đi-phnom-penh)
7. [Bảng nào do ai ghi, ở giai đoạn nào](#7-bảng-nào-do-ai-ghi-ở-giai-đoạn-nào)
8. [Tận dụng từ project cũ](#8-tận-dụng-từ-project-cũ)

---

## 1. Bức tranh tổng thể

### 1.1 Định vị

Công ty là **đại lý tổ chức vận chuyển ngựa đua trọn gói** bằng đường bộ trong Việt Nam, Lào và Campuchia.
- Công ty là đầu mối duy nhất với khách, được tự chọn tuyến và phương tiện.
- Công ty được thuê lại bên khác thực hiện một phần dịch vụ: đại lý nước ngoài, trại cách ly, thú y.
- Đoạn trong Việt Nam do xe công ty chạy. Đoạn ở nước ngoài do đại lý chạy, ngựa được bàn giao tại cửa khẩu.

### 1.2 Mô hình gom hàng qua hub

```
 Chuồng khách A ─┐   chặng ĐÓN                      chặng TRỤC CHÍNH                        chặng GIAO
 Chuồng khách B ─┼──(vehicle_trip PICKUP)──► HUB ──(vehicle_trip LINEHAUL theo departure)──► ...
 Chuồng khách C ─┘                           │                                              │
                                     horse_stay ASSEMBLY                              Cửa khẩu: handover AGENT
                                     horse_stay QUARANTINE                            horse_leg do đại lý (agent_party_id)
                                     (trại cách ly)                                   horse_stay QUARANTINE (nước đến)
                                                                                            │
                                                                            (vehicle_trip DELIVERY hoặc đại lý)
                                                                                            ▼
                                                                                  Người nhận: handover DELIVERY
```

Có ba lớp khái niệm, không được trộn lẫn:

| Lớp | Câu hỏi | Bảng |
|---|---|---|
| **Thương mại** | Khách mua gì, giá bao nhiêu, đi chuyến nào | `booking`, `booking_horse`, `quotation`, `payment`, `departure` |
| **Vận hành xe** | Xe nào chạy, ai lái, dừng ở đâu | `vehicle_trip`, `trip_stop`, `crew_assignment` |
| **Theo dõi ngựa** | Từng con ngựa đi chặng nào, ở đâu, ai đang giữ | `horse_leg`, `horse_stay`, `handover`, `horse_health_log` |

Một **đơn** có nhiều ngựa. Một **lượt xe** chở ngựa của nhiều đơn. Bảng nối giữa hai phía là `horse_leg`: mỗi dòng là một con ngựa trên một lượt xe, hoặc trong tay một đại lý.

**Trục chính có cả tuyến nội địa và tuyến quốc tế:**
- Nội địa: hub ⇄ hub, ví dụ `HUB-HCM` ⇄ `HUB-DN`.
- Quốc tế chiều xuất: hub → cửa khẩu, ví dụ `HUB-HCM` → `GATE-MOCBAI`.
- Quốc tế chiều nhập: cửa khẩu → hub, ví dụ `GATE-MOCBAI` → `HUB-HCM`. Đại lý đưa ngựa tới cửa khẩu, bàn giao cho công ty, rồi ngựa đi chuyến trục về hub.

**Trụ sở và hub vệ tinh.** Mọi xe và nhân viên thuộc trụ sở (`HUB-HCM`). Hub vệ tinh (ví dụ `HUB-DN`) có vùng phục vụ và chỗ tập kết ngựa, nhưng không có xe hay nhân viên trực thuộc. Xe trục tới hub vệ tinh thì chạy luôn lượt giao ở vùng đó, rồi về trụ sở bằng chuyến trục chiều ngược.

**Chọn cách chạy theo hub, không theo loại đơn:**

| Điều kiện | Cách chạy | Chặng của mỗi con ngựa |
|---|---|---|
| Hub đón **trùng** hub giao | Chạy thẳng, không có `departure` | Một chặng `DIRECT`: địa chỉ đón → địa chỉ giao |
| Hub đón **khác** hub giao, hoặc đơn quốc tế | Đi trục, bắt buộc có `departure` | PICKUP → (lưu trú) → LINEHAUL → (lưu trú) → DELIVERY |

Hub đón và hub giao tra từ `hub_service_area` theo tỉnh của `booking_address`. Mỗi đơn có tối đa một chặng trục.

### 1.3 Loại đơn

| `booking_type` | Đặc điểm | Có tra `country_rule` | Có `departure` |
|---|---|---|---|
| `LOCAL` | Cùng tỉnh, chạy thẳng một chặng `DIRECT` | Không | Không |
| `INTERPROVINCE` | Liên tỉnh, cần giấy kiểm dịch vận chuyển | Có (`VN`) | Có nếu hub đón khác hub giao. Cùng hub thì chạy thẳng |
| `EXPORT` | VN → Lào hoặc Campuchia. Có thể cách ly trước khi xuất, bàn giao cho đại lý ở cửa khẩu | Có (nước đến) | Có |
| `IMPORT` | Lào hoặc Campuchia → VN. Chủ hàng bố trí nơi cách ly | Có (nước đi) | Có |

Đơn hồi hương (khứ hồi) là một đơn `IMPORT` có `original_booking_id` trỏ về đơn đi.

---

## 2. Các bên và vai trò

### 2.1 Người dùng hệ thống (`app_user` + `customer` hoặc `staff`)

| `role.code` | Người | Việc chính |
|---|---|---|
| `CUSTOMER` | Khách | Khai ngựa, đặt đơn, nộp giấy, chấp nhận báo giá, thanh toán, đánh giá |
| `SPECIALIST` | Chuyên viên | Duyệt hồ sơ ngựa, giấy tờ kiểm dịch, bảo hiểm |
| `COORDINATOR` | Điều phối viên | Gán đơn vào chuyến, lập chặng, lượt xe, lưu trú, đề xuất phương án sự cố |
| `MANAGER` | Quản lý | Duyệt báo giá, phương án sự cố, khoản chi, quyết toán |
| `DRIVER` | Tài xế | Chạy lượt xe, check-in điểm dừng, giao nhận |
| `ESCORT` | Nhân viên hộ tống | Chăm ngựa, ghi nhật ký sức khỏe, quét chip |
| `ADMIN` | Quản trị | Dữ liệu nền: tuyến, lịch, bảng giá, quy định, tài khoản |

Mỗi tài khoản có đúng một vai trò (`app_user.role_code`). Nhân viên có thêm dòng `staff` (1–1 với `app_user`) chứa mã nhân viên, hub trực thuộc và bằng lái. Khách hàng (cá nhân hoặc tổ chức) có thêm dòng `customer` (1–1 với `app_user`), **không** có dòng `staff`. Khách được tham chiếu ở `booking.customer_id` và `horse.owner_customer_id`.

### 2.2 Đối tác (`party` + `party_role`)

| `party_role.role_type` | Ví dụ | Xuất hiện ở |
|---|---|---|
| `DEST_AGENT` | Đại lý vận tải Campuchia | `horse_leg.agent_party_id`, `handover.to_party_id` |
| `QUARANTINE_FARM` | Trại cách ly đối tác | `horse_stay.operator_party_id` |
| `STABLE` | Chuồng gửi qua đêm | `horse_stay.operator_party_id`, `incident_plan.handler_party_id` |
| `VET` | Bác sĩ thú y | `incident_plan.handler_party_id` |
| `RESCUE` | Đội cứu hộ xe | `incident_plan.handler_party_id` |
| `INSURER` | Công ty bảo hiểm | `horse_insurance.insurer_party_id` |

Đối tác không có tài khoản. Khách, người gửi và người nhận **không** tạo dòng `party`.

### 2.3 Vai trò của các bên trong một đơn (`booking_party`)

Lấy theo mẫu phiếu yêu cầu SLI: **người trả tiền (PAYER)**, **người gửi (SHIPPER)**, **người nhận (CONSIGNEE)** và **người liên hệ khẩn cấp (EMERGENCY_CONTACT)** có thể là những người khác nhau. Mỗi vai trò là một dòng, chỉ là bản chụp tên, số điện thoại và giấy tờ tại thời điểm đặt đơn, không trỏ tới `party`. Người liên hệ tại điểm đón hoặc giao nằm ở `booking_address`.

---

## 3. Dữ liệu nền do admin chuẩn bị

Phải có dữ liệu nền trước khi nhận đơn đầu tiên. Nhóm nên viết dữ liệu này thành **Flyway seed** (file `R__` hoặc `V__seed`), thay cho các class `*Seeder.java` cũ.

> Cột "Ví dụ" chỉ minh họa, **chưa phải dữ liệu đầy đủ**. Danh sách đầy đủ (điểm mạng lưới, vùng phục vụ, tuyến, lịch, bảng giá, quy định) cần bổ sung riêng trước khi viết seed.

| Thứ tự | Dữ liệu | Bảng | Ví dụ |
|---|---|---|---|
| 1 | Quốc gia, tỉnh | `country`, `province` | VN, KH, LA; 34 tỉnh/thành VN (sắp xếp từ 01/07/2025), 25 tỉnh KH, 18 tỉnh LA |
| 2 | Điểm mạng lưới | `location` | `HUB-HCM` (trụ sở), `HUB-DN` (hub vệ tinh), `QF-CUCHI` (trại cách ly), `REST-TRANGBANG`, `GATE-MOCBAI`, `HUB-PNH` |
| 3 | Vùng phục vụ của hub (mỗi tỉnh thuộc đúng một hub) | `hub_service_area` | HUB-HCM phục vụ TP.HCM, Bình Dương, Đồng Nai…; HUB-DN phục vụ Đà Nẵng, Quảng Nam, Huế… |
| 4 | Vai trò, tài khoản nhân viên | `role`, `app_user`, `staff` | |
| 5 | Đối tác | `party`, `party_role` | Đại lý Campuchia, trại cách ly, thú y |
| 6 | Loại ngựa, hạng xe, loại khoang, xe | `horse_type`, `vehicle_class`, `stall_type`, `vehicle` | |
| 7 | Tuyến và điểm dừng (mỗi chiều là một tuyến) | `route`, `route_stop` | HCM-PNH với 5 điểm dừng; HCM-DN và DN-HCM (trục nội địa) |
| 8 | Lịch chạy | `departure_schedule`, `departure_schedule_day` | HCM-PNH, 06:00, thứ Hai và thứ Năm, 9 khoang; HCM-DN thứ Ba, DN-HCM thứ Năm để xe quay về |
| 9 | Dịch vụ, bảng giá | `billable_service`, `price_list`, `price_item` | Bảng giá 2027, cọc 30% |
| 10 | Quy định, giấy tờ | `country_rule`, `document_type`, `document_requirement` | KH/EXPORT: cách ly 7 ngày, cần giấy chứng nhận kiểm dịch xuất khẩu |
| 11 | Tham số kỹ thuật | `system_config` | `HANDOVER_FREE_WAIT_MINUTES = 120` |

### 3.1 Sinh chuyến từ lịch

Một **scheduler** chạy hằng ngày, lấy các `departure_schedule` ACTIVE và tạo trước các `departure` cho N tuần tới:
- `departure_date` là các ngày khớp `departure_schedule_day.day_of_week` và nằm trong thời hạn hiệu lực của lịch;
- `planned_departure_at = departure_date + departure_time`;
- `booking_cutoff_at = planned_departure_at − booking_cutoff_hours`;
- `stall_capacity` chép từ lịch;
- `status = OPEN`.

Ràng buộc `UNIQUE (departure_schedule_id, departure_date)` bảo đảm chạy lại scheduler cũng không sinh trùng. Quản lý có thể tạo thêm **chuyến đột xuất** với `departure_schedule_id = NULL`.

Một scheduler khác chuyển `departure` sang `CLOSED` khi đã qua `booking_cutoff_at`.

### 3.2 Cách bảng giá vận hành

```
service (đơn vị tính)  ──<  price_item (đơn giá + điều kiện)  >──  price_list (hiệu lực, biên lợi nhuận, % cọc)
```

| Thành phần báo giá | `billable_service.layer` | `billing_unit` | Điều kiện tra `price_item` |
|---|---|---|---|
| Cước trục chính | LINEHAUL | PER_STALL | `route_id` + `stall_type_code` |
| Cước đón | PICKUP | PER_KM | `vehicle_class_id` + bậc `km_from..km_to` |
| Cước giao | DELIVERY | PER_KM | `vehicle_class_id` + bậc km |
| Cước chạy thẳng (không qua trục) | DIRECT | PER_KM | `vehicle_class_id` + bậc km, tính từ địa chỉ đón tới địa chỉ giao |
| Phụ thu khoang rộng khi chạy thẳng | STALL | PER_HORSE | Tra theo `stall_type_code` WIDE, số lượng là số ngựa chọn khoang rộng |
| Phụ phí nhiên liệu | FUEL | PER_KM | `fuel_buffer_rate` của bảng giá |
| Lưu chuồng, cách ly | QUARANTINE | PER_HORSE_DAY | |
| Khám thú y trước chuyến | ADD_ON | PER_HORSE | Khách chọn ở `booking_horse_service`. Một lần, ở nơi đón hoặc hub; kết quả là một `document` |
| Thủ tục kiểm dịch | DOCS | PER_BOOKING | `is_auto_applied = true` với đơn không phải LOCAL. Lệ phí nhà nước có `is_pass_through` |

Số km của chặng đón và giao do backend tính bằng dịch vụ bản đồ (Valhalla), từ `booking_address` tới hub đón hoặc từ hub giao tới `booking_address`. Với đơn chạy thẳng, số km tính từ địa chỉ đón tới địa chỉ giao. **Frontend không tính giá**, chỉ gọi API xem trước giá.

---

## 4. Vòng đời một đơn: 10 giai đoạn

Trạng thái của đơn (`booking.status`) chỉ phản ánh **mốc thương mại**:

```
PENDING_INTAKE → UNDER_REVIEW ⇄ NEEDS_RESUBMISSION → QUOTING → AWAITING_DEPOSIT → CONFIRMED
   → IN_OPERATION → DELIVERED → COMPLETED
Nhánh phụ: QUOTE_EXPIRED, SCHEDULE_CHANGE_REQUIRED, CANCELLED, REJECTED
```

Mỗi lần đổi trạng thái đều ghi một dòng `booking_status_history`, trong cùng transaction.
Tiến độ vận hành (ngựa đang ở đâu, chặng nào, có sự cố không) đọc từ `horse_leg`, `horse_stay`, `handover` và `incident`, **không** đọc từ `booking.status`.

---

### GĐ1. Khách khai báo và gửi yêu cầu

**Ai:** khách. **Trạng thái mới:** `PENDING_INTAKE`.

| Bước | Hành động | Bảng ghi |
|---|---|---|
| 1 | Đăng ký, xác thực email bằng OTP | `app_user` (`role_code` = CUSTOMER), `customer` (customer_type PERSON), `otp` |
| 2 | Đồng ý điều khoản dịch vụ khi đăng ký | `terms_acceptance` SERVICE_TERMS (`booking_id` NULL) |
| 3 | Khai hồ sơ ngựa: tên, chip, hộ chiếu, giới tính, năm sinh, giá trị khai báo… | `horse` |
| 4 | Lưu sổ địa chỉ (tùy chọn) | `customer_address` |
| 5 | Chọn **loại đơn**, **điểm đón**, **điểm giao** và **chuyến** muốn đi. Hệ thống gợi ý các `departure` OPEN phù hợp | Chỉ đọc `departure` (`stall_capacity − booked_stall_count`) |
| 6 | Khai các bên: người trả tiền, người gửi, người nhận, người liên hệ khẩn cấp | `booking_party` × 4 |
| 7 | Chọn ngựa, loại khoang, chế độ ăn, dịch vụ thêm | `booking_horse`, `booking_horse_service` |
| 8 | Khai bảo hiểm cho từng con: tự có / nhờ công ty thu xếp / không mua | `horse_insurance` (DRAFT). Chọn NONE thì phải ký cam kết miễn trách: `terms_acceptance` LIABILITY_WAIVER |
| 9 | Tải giấy tờ đang có | `document` (UPLOADED), có `horse_id` hoặc `booking_horse_id` và `document_requirement_id` |
| 10 | Gửi đơn | `booking` (PENDING_INTAKE), `booking_address` × 2 (snapshot), `terms_acceptance` SERVICE_TERMS gắn `booking_id`, `booking_status_history` |

**Quy tắc:**
- Chỉ chọn được `departure` có `status = OPEN`, chưa qua `booking_cutoff_at`, còn đủ khoang (`booked_stall_count` cộng số ngựa của đơn không vượt `stall_capacity`). Khi ghi, `DepartureService` khóa dòng `departure` bằng `FOR UPDATE` rồi cộng `booked_stall_count` trong cùng transaction.
- `booking_cutoff_at` phải đủ xa so với hôm nay để kịp thẩm định, làm giấy (`permit_lead_days`, `certificate_lead_days`) và cách ly (`quarantine_days`) theo `country_rule`. Không đủ thì cảnh báo và gợi ý chuyến sớm nhất khả thi.
- Hệ thống hiển thị danh sách giấy phải nộp từ `document_requirement`, ghi rõ **ai nộp** (`submitter_type`). Ví dụ: giấy phép nhập khẩu của nước đến do người nhận hoặc đại lý nộp.
- Microchip được chuẩn hóa theo ISO 11784/11785 (15 chữ số) trước khi lưu.

---

### GĐ2. Tiếp nhận và thẩm định song song

**Ai:** chuyên viên và điều phối viên. **Trạng thái:** `UNDER_REVIEW`, hoặc `NEEDS_RESUBMISSION` nếu thiếu hồ sơ.

Hai nhánh chạy song song. Mỗi nhánh tự lưu trạng thái trên đối tượng của mình, **không có bảng duyệt riêng**.

**Nhánh hồ sơ (chuyên viên)**

| Việc | Bảng |
|---|---|
| Duyệt từng giấy: đạt / không đạt (bắt buộc ghi lý do) | `document.status`, `reviewed_by`, `reviewed_at`, `review_note` |
| Kiểm tra còn thiếu giấy nào | `DocumentCompletenessPolicy` (đọc `document_requirement` và `document`) |
| Bảo hiểm PERSONAL: kiểm hợp đồng. Thiếu thì yêu cầu khách bổ sung | `horse_insurance.status = REVISION_REQUESTED`, `revision_reason` |
| Bảo hiểm ARRANGED: lấy báo giá từ công ty bảo hiểm | `horse_insurance.insurer_party_id`, `premium_amount`, `quote_expires_at` |
| Xác nhận bảo hiểm | `horse_insurance.status = CONFIRMED`, `confirmed_by` |

**Nhánh kế hoạch (điều phối viên)**

| Việc | Bảng |
|---|---|
| Xác định cách chạy: tra hub đón và hub giao, quyết định chạy thẳng hay đi trục (mục 1.2) | Đọc `hub_service_area` |
| Đi trục: xác nhận chuyến trục chính, hoặc đổi sang chuyến khác | `booking.departure_id` |
| Tính **số km và ngày dự kiến** của từng chặng để báo giá: đón, trục, giao, hoặc một chặng chạy thẳng | Chỉ tính, chưa ghi `horse_leg` |
| Lập **các lần lưu trú dự kiến** | `horse_stay`: ASSEMBLY ở hub, QUARANTINE ở trại trước khi xuất, QUARANTINE ở nước đến |

Ở giai đoạn này **chưa tạo `horse_leg`**. Vì ràng buộc "xe hoặc đại lý, đúng một", chặng do xe công ty chạy chỉ được tạo khi đã có `vehicle_trip`, tức ở GĐ5. Riêng chặng ở nước ngoài do đại lý chạy (`agent_party_id`) có thể tạo sớm.

**Khi thiếu thông tin:** chuyên viên trả đơn về khách, ghi lý do vào `booking_status_history.reason` và chuyển trạng thái `NEEDS_RESUBMISSION`. Khách bổ sung xong thì đơn quay lại `UNDER_REVIEW`.

**Điều kiện sang GĐ3:** đủ giấy bắt buộc, mọi `horse_insurance` đã CONFIRMED, và đơn đi trục đã có `departure_id` (đơn chạy thẳng không cần). Hệ thống kiểm điều kiện này bằng truy vấn, không lưu thành cờ.

---

### GĐ3. Báo giá

**Ai:** hệ thống tính, quản lý duyệt. **Trạng thái:** `QUOTING` → `AWAITING_DEPOSIT`.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Backend chọn `price_list` ACTIVE có thời hạn hiệu lực chứa ngày báo giá | Đọc `price_list` |
| 2 | Sinh từng dòng báo giá từ `price_item` theo mục 3.2. Mỗi dòng có thể gắn với một con ngựa hoặc một chặng | `quotation_line` (snapshot `unit_price`, `is_pass_through`) |
| 3 | Tạo phiên bản báo giá, chốt lịch: ngày đón, ngày xuất phát, ngày giao dự kiến | `quotation` (DRAFT, `version` tăng dần, snapshot `deposit_rate`) |
| 4 | Quản lý có thể thêm dòng điều chỉnh giảm giá | `quotation_line` loại ADJUSTMENT |
| 5 | Quản lý gửi báo giá cho khách | `quotation.status = SENT`, `sent_by`, `sent_at`, `expires_at`. Các bản cũ chuyển SUPERSEDED |

`QuotationService` tính và ghi `quotation.total_amount`, `pass_through_amount`, `deposit_amount` khi báo giá còn DRAFT. Sau khi SENT thì các cột này không đổi. Tiền cọc gồm **toàn bộ** phần thu hộ (bảo hiểm, lệ phí): `deposit = round(deposit_rate × (tổng − thu hộ), 2) + thu hộ`.

---

### GĐ4. Khách chấp nhận và đặt cọc

**Ai:** khách. **Trạng thái:** `AWAITING_DEPOSIT` → `CONFIRMED`.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Khách chấp nhận báo giá | `quotation.status = ACCEPTED`, `accepted_at`. Mỗi đơn chỉ có một báo giá ACCEPTED (partial unique index) |
| 2 | Tạo khoản cọc | `payment` DEPOSIT, `amount = quotation.deposit_amount`, `due_at` |
| 3 | Khách trả qua VNPAY. Cổng gọi lại (callback và IPN) | `payment_transaction` (UK `gateway_code + gateway_transaction_number` chống ghi trùng), `payment.status = PAID` |
| 4 | Cấp vận đơn | `booking.waybill_number`, `status = CONFIRMED` |

Quá `expires_at` mà chưa cọc thì scheduler chuyển báo giá sang `EXPIRED` và đơn sang `QUOTE_EXPIRED`. Đơn hết hạn thì nhả khoang: trừ `departure.booked_stall_count` trong cùng transaction.

---

### GĐ5. Chuẩn bị

**Ai:** điều phối viên, chuyên viên, khách. **Trạng thái:** vẫn `CONFIRMED`.

| Việc | Bảng |
|---|---|
| Khách trả số dư trước ngày đón N ngày | `payment` BALANCE |
| Hoàn tất giấy tờ kiểm dịch, giấy phép nhập | `document` |
| **Tạo lượt xe đón**: gom các đơn có điểm đón gần nhau trong cùng ngày | `vehicle_trip` PICKUP, `trip_stop` (mỗi `booking_address` PICKUP là một điểm dừng, cuối cùng là hub) |
| **Tạo lượt xe trục chính** cho chuyến | `vehicle_trip` LINEHAUL có `departure_id`. `trip_stop` chép từ `route_stop`: giờ = `planned_departure_at + arrival_offset_minutes` |
| **Tạo lượt xe chạy thẳng** cho đơn không qua trục | `vehicle_trip` DIRECT: trụ sở → các địa chỉ đón → các địa chỉ giao → trụ sở. Có thể gom nhiều đơn cùng vùng |
| **Tạo lượt giao ở hub vệ tinh** | `vehicle_trip` DELIVERY bắt đầu tại hub vệ tinh, dùng chính xe vừa chạy trục tới. Sau đó xe về trụ sở bằng chuyến trục chiều ngược |
| Phân công tài xế, hộ tống | `crew_assignment` (ASSIGNED). Nhân viên bấm xác nhận thì thành ACKNOWLEDGED |
| Gắn ngựa vào lượt xe | `horse_leg.vehicle_trip_id`, `board_trip_stop_id`, `alight_trip_stop_id` |

**Điều kiện được đón ngựa**, kiểm bằng truy vấn khi xe xuất phát:
- đã trả đủ: số còn nợ do `SettlementPolicy` tính bằng 0;
- đủ giấy theo `DocumentCompletenessPolicy`;
- bảo hiểm của từng con đã CONFIRMED;
- lượt xe đã có tài xế ACKNOWLEDGED.

**Quy tắc phân xe:**
- Tổng ngựa trên xe tại mọi thời điểm không vượt số khoang của xe, theo đúng loại khoang.
- Một xe hoặc một nhân viên không được nằm trong hai lượt chạy trùng giờ.
- Một chuyến 9 khoang có thể chia cho hai xe, tức hai `vehicle_trip` LINEHAUL cùng `departure_id`.
- Lượt chạy mới của một xe phải bắt đầu ở nơi lượt chạy trước của xe đó kết thúc.
- Ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến.

---

### GĐ6. Nhận ngựa

**Ai:** tài xế, hộ tống. **Trạng thái:** `CONFIRMED` → `IN_OPERATION` khi con ngựa đầu tiên được nhận.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Xe xuất phát | `vehicle_trip.status = IN_PROGRESS`, `actual_start_at` |
| 2 | Tới điểm đón | `trip_stop.actual_arrival_at` |
| 3 | Tạo lần giao nhận tại điểm dừng | `handover` handover_type PICKUP, `from_party_id` NULL (người gửi là `booking_party` SHIPPER), `to_party_id` NULL (công ty nhận) |
| 4 | Quét chip từng con và so với hồ sơ | `handover_horse.scanned_microchip`, `is_microchip_matched` |
| 5 | Khám nhanh, ghi tình trạng, chụp ảnh | `horse_health_log` (`trip_stop_id`), `document` ảnh hiện trường (`horse_leg_id` hoặc `handover_id`) |
| 6 | Nhận giấy tờ gốc, ví dụ hộ chiếu ngựa | `handover_document` direction RECEIVED |
| 7 | Ký biên bản | `document` loại HANDOVER_RECORD (`handover_id`), `handover.status = DONE`, `handed_at` |
| 8 | Ngựa lên xe | `horse_leg` PICKUP: `status = IN_PROGRESS`, `actual_start_at` |
| 9 | Rời điểm đón | `trip_stop.actual_departure_at` |

**Ngoại lệ ở bước 4–5:**
- Chip không khớp: không nhận con đó, ghi `incident` loại DOCUMENT.
- Ngựa không đủ sức khỏe: loại khỏi đơn (`booking_horse.status = EXCLUDED`), hủy các `horse_leg` còn lại của con đó, trừ `departure.booked_stall_count`, và điều chỉnh báo giá bằng phiên bản mới (SUPERSEDED bản cũ và ACCEPTED bản mới trong cùng transaction).

**Mốc không hoàn tiền:** khi `handover` PICKUP DONE thì đơn không còn được hoàn tiền nếu khách hủy.

---

### GĐ7. Hành trình: các chặng và các lần lưu trú xen kẽ

**Ai:** tài xế, hộ tống, điều phối viên, đại lý. **Trạng thái đơn:** vẫn `IN_OPERATION`.

#### 7.1 Về hub và tập kết

| Việc | Bảng |
|---|---|
| Xe đón về tới hub | `trip_stop` (hub) `actual_arrival_at`. `horse_leg` PICKUP COMPLETED |
| Giao ngựa vào hub | `handover` HUB_IN (hoặc STAY_IN), gắn `horse_stay_id`. Người ở `handled_by` phụ trách ngựa tới HUB_OUT. Ở hub vệ tinh (không có nhân viên), tổ xe trục ở lại tới khi giao; qua đêm thì gửi đối tác `STABLE` |
| Ngựa ở hub chờ chuyến trục chính | `horse_stay` ASSEMBLY: `actual_start_at` |
| Lượt xe đón kết thúc, xe rảnh | `vehicle_trip.status = COMPLETED`, `actual_end_at` |

#### 7.2 Cách ly trước khi xuất (đơn EXPORT có `quarantine_days > 0`)

| Việc | Bảng |
|---|---|
| Chuyển từ hub sang trại cách ly | `vehicle_trip` TRANSFER, `horse_leg` leg_type TRANSFER |
| Nhập trại | `handover` STAY_IN, `horse_stay` QUARANTINE (`operator_party_id` = trại) |
| Hộ tống ghi sức khỏe và ăn uống hằng ngày theo chỉ dẫn của khách | `horse_health_log` (`horse_stay_id`, ăn uống ghi ở `note`) |
| Chuyên viên kết luận cách ly | `horse_stay.quarantine_result = PASSED` hoặc `FAILED`, `actual_end_at`. Có thể kéo dài: tăng `planned_end_at` và tính phụ phí ở `cost_item` |

Phí lưu chuồng, cách ly theo ngày đã gồm ăn uống.

Trong lúc ngựa cách ly, **xe không bị giữ**: chặng tiếp theo là một lượt xe khác.

#### 7.3 Chặng trục chính

| Việc | Bảng |
|---|---|
| Chuyến bắt đầu | `departure.status = IN_PROGRESS`, `vehicle_trip` LINEHAUL IN_PROGRESS |
| Ngựa xuất khỏi nơi lưu trú, lên xe | `handover` STAY_OUT hoặc HUB_OUT, `horse_stay.actual_end_at`, `horse_leg` LINEHAUL IN_PROGRESS |
| Check-in tại từng trạm nghỉ (cách nhau tối đa khoảng 4 giờ chạy) | `trip_stop.actual_arrival_at/actual_departure_at` |
| Kiểm tra sức khỏe tại trạm | `horse_health_log` (`trip_stop_id`) |
| Phí cầu đường, nhiên liệu | `cost_item` category TOLL hoặc FUEL, payer_type COMPANY, `vehicle_trip_id`. Biên lai là một `document` có `cost_item_id` |
| Ghi nhận niêm phong, khử trùng xe nếu có. Đợt này không có quy tắc kiểm tra | `vehicle_trip.sealed_at`, `disinfected_at` |

#### 7.4 Cửa khẩu và bàn giao cho đại lý

| Việc | Bảng |
|---|---|
| Tới cửa khẩu, làm thủ tục (tính bằng giờ, không phải lưu trú) | `trip_stop` stop_type BORDER |
| Cán bộ xác nhận giấy chứng nhận kiểm dịch | `document` liên quan được duyệt |
| Bàn giao cho đại lý | `handover` AGENT, `from_party_id` NULL (công ty), `to_party_id` = đại lý. Có `handover_horse` và biên bản |
| Chặng của công ty kết thúc, chặng của đại lý bắt đầu. Đại lý không có tài khoản: điều phối nhập hộ mốc và ảnh | `horse_leg` LINEHAUL COMPLETED. `horse_leg` kế tiếp có `agent_party_id`: IN_PROGRESS |
| Bị giữ qua đêm ở cửa khẩu | `horse_stay` OVERNIGHT |

Sau khi bàn giao cho đại lý, công ty **vẫn chịu trách nhiệm với khách**. Đại lý là bên được thuê lại.

#### 7.5 Cách ly sau khi đến

`horse_stay` QUARANTINE ở nước đến, `operator_party_id` là đại lý hoặc cơ sở của nhà nước. Đại lý báo kết quả, nhân viên công ty nhập vào hệ thống.

---

### GĐ8. Giao ngựa

Giao ngựa **đối xứng với đón ngựa**. **Ai:** tài xế và hộ tống (giao trong nước), hoặc đại lý (giao ở nước ngoài). **Trạng thái:** `IN_OPERATION` → `DELIVERED`.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Xuất ngựa khỏi nơi lưu trú hoặc hub | `handover` STAY_OUT/HUB_OUT, `horse_stay.actual_end_at` |
| 2 | Lượt xe giao: mỗi `booking_address` DELIVERY là một điểm dừng | `vehicle_trip` DELIVERY, `trip_stop`, `horse_leg` DELIVERY IN_PROGRESS |
| 3 | Tới nơi, bắt đầu chờ người nhận | `trip_stop.actual_arrival_at`, `handover` DELIVERY PENDING, `wait_started_at` |
| 4 | Quét chip, ghi tình trạng, người nhận ký | `handover_horse`, `document` biên bản. Hư hại nhìn thấy phải ghi ngay lúc giao |
| 5 | Trả giấy tờ gốc | `handover_document` direction RETURNED |
| 6 | Hoàn tất giao nhận | `handover.status = DONE`, `horse_leg` DELIVERY (hoặc DIRECT) COMPLETED, `booking_horse.status = DELIVERED` |
| 7 | Mọi con ngựa ACTIVE của đơn đã giao xong | `booking.status = DELIVERED` |

**Người nhận vắng mặt (hết thời gian chờ miễn phí, mặc định 120 phút trong `system_config`):**
1. `handover.status = TIMEOUT`. Ghi `incident` loại HANDOVER_TIMEOUT, nguyên nhân RECIPIENT_ABSENT.
2. Điều phối viên lập `incident_plan`: gửi chuồng gần đó (STABLE_NEARBY) hoặc đưa về hub giao của đơn (RETURN_TO_HUB). Hub giao có thể là hub vệ tinh.
3. Chặng giao hiện tại chuyển `FAILED`. Tạo `horse_leg` mới (RETURN hoặc TRANSFER) để đưa ngựa đi gửi.
4. Ngựa được giữ ở nơi gửi: `horse_stay` STABLING.
5. Giao lại bằng một `horse_leg` DELIVERY mới có `replaced_horse_leg_id` trỏ về chặng thất bại.
6. Phí chờ và phí gửi chuồng ghi vào `cost_item`, payer_type CONSIGNEE (người nhận không trả thì khách trả). Ghi `handover_id` hoặc `horse_stay_id` tương ứng.

---

### GĐ9. Quyết toán

**Ai:** quản lý. **Trạng thái:** vẫn `DELIVERED`.

| Việc | Bảng |
|---|---|
| Rà soát mọi chi phí phát sinh của đơn | `cost_item` theo `booking_id` |
| Duyệt từng khoản | `cost_item.approved_by`, `approved_at` |
| Khoản do khách hoặc người nhận chịu thì lập khoản thu thêm | `payment` EXTRA_CHARGE, gắn vào `cost_item.extra_charge_payment_id` |
| Đối chiếu số đã trả và số còn nợ | `SettlementPolicy`, từ tổng `payment` và `cost_item` (truy vấn tổng hợp ở repository) |
| Tách các khoản thu hộ (bảo hiểm, lệ phí nhà nước) khỏi doanh thu | `quotation_line.is_pass_through` |

**Ai chịu chi phí** do `incident.cause_type` quyết định:

| `cause_type` | Bên chịu (`cost_item.payer_type`) |
|---|---|
| `COMPANY_FAULT` | COMPANY |
| `AGENT_FAULT` | COMPANY (công ty đòi lại đại lý theo hợp đồng riêng) |
| `HORSE_CONDITION` | CUSTOMER |
| `CUSTOMER_DOCS` | CUSTOMER |
| `RECIPIENT_ABSENT` | CONSIGNEE, không trả thì CUSTOMER |
| `FORCE_MAJEURE` | CUSTOMER |

Phải hỏi khách trước khi chi (`is_customer_consulted = true`). Ngoại lệ: trường hợp khẩn cấp có số tiền nằm trong `booking.emergency_spending_limit`.

---

### GĐ10. Đóng đơn

| Việc | Bảng |
|---|---|
| Đã trả đủ: số còn nợ theo `SettlementPolicy` bằng 0 | `booking.status = COMPLETED` |
| Khách đánh giá | `booking_rating` |
| Thông báo cho các bên | `notification`, `notification_recipient` |

Hạn khiếu nại là 7 ngày kể từ `handover` DELIVERY DONE.

---

## 5. Nhánh phụ và ngoại lệ

### 5.1 Sự cố trên đường

| Bước | Ai | Bảng |
|---|---|---|
| Báo sự cố, kèm vị trí và ảnh | Tài xế/hộ tống | `incident` (REPORTED). Gắn `vehicle_trip_id` nếu cả xe bị ảnh hưởng, `horse_leg_id` nếu chỉ một con. `document` ảnh |
| Lập phương án | Điều phối viên | `incident_plan` (PROPOSED, version 1): loại xử lý, chỉ đạo, chi phí dự kiến, bên xử lý (`handler_party_id`) |
| Duyệt hoặc từ chối | Quản lý | `incident_plan` APPROVED / REJECTED (bắt buộc ghi lý do). Từ chối thì lập version 2 |
| Thực hiện phương án | Tổ xe | Tùy loại: VET_TREATMENT thì ghi `horse_health_log`; TRANSFER_VEHICLE thì tạo `vehicle_trip` mới và đổi `horse_leg`; RETURN_TO_HUB thì tạo chặng RETURN |
| Ghi chi phí | Tổ xe | `cost_item` gắn `incident_id`, `payer_type` theo `cause_type` |
| Xác nhận ngựa đủ sức khỏe đi tiếp | Thú y hoặc hộ tống | `incident.fit_confirmed_by`, `fit_confirmed_at` |
| Đóng sự cố | Điều phối viên | `incident.status = RESOLVED` → CLOSED, `incident_plan` DONE |

### 5.2 Chuyến bị hủy hoặc đổi

1. Quản lý hủy chuyến: `departure.status = CANCELLED`.
2. Các đơn đã gán vào chuyến chuyển sang `SCHEDULE_CHANGE_REQUIRED`.
3. Khách chọn chuyến khác (`booking.departure_id` mới, có thể cần báo giá mới) hoặc hủy đơn mà không mất phí. Đổi chuyến thì trừ `booked_stall_count` của chuyến cũ và cộng vào chuyến mới, giữ `FOR UPDATE` trên cả hai dòng.

### 5.3 Ngựa không đạt cách ly

1. `horse_stay.quarantine_result = FAILED`.
2. Con ngựa đó bị tách khỏi đơn: `booking_horse.status = EXCLUDED`, nhả khoang, hủy các chặng phía sau. Trả ngựa xong thì chuyển `RETURNED`.
3. Tạo chặng RETURN để chở ngựa về. Khách chịu chi phí (`cost_item`, cause_type HORSE_CONDITION).
4. Lập báo giá phiên bản mới cho các con ngựa còn lại.
5. Nếu ngựa đang ở nước ngoài, cơ quan nước đến quyết định. Phải đưa về Việt Nam thì tạo **đơn IMPORT liên kết** qua `original_booking_id`.

### 5.4 Khách hủy đơn

- Trước GĐ6: `booking.status = CANCELLED`. Hoàn tiền theo biểu phí hủy bằng `payment` REFUND có `refunded_payment_id`.
- Sau khi đã nhận ngựa: không hoàn tiền.

### 5.5 Từ chối đơn

Chuyên viên hoặc quản lý từ chối khi đơn không thể thực hiện, ví dụ quy định không cho phép: `booking.status = REJECTED`, lý do ghi ở `booking_status_history.reason`.

---

## 6. Ví dụ xuyên suốt: một đơn đi Phnom Penh

Khách ở Bình Dương gửi 2 con ngựa (Sao Mai, Bạch Long) tới Phnom Penh, đi chuyến thứ Hai 20/12.

| Thời điểm | Sự kiện | Dòng dữ liệu chính |
|---|---|---|
| 01/12 | Khách gửi đơn | `booking` #500 EXPORT, `departure_id` = 101 (HCM-PNH 20/12). `booking_horse` #1 Sao Mai, #2 Bạch Long. `booking_address` PICKUP (Bình Dương) và DELIVERY (Phnom Penh). `booking_party` × 4 |
| 02–04/12 | Thẩm định | `document` giấy kiểm dịch × 2 APPROVED. `horse_insurance` × 2 CONFIRMED |
| 05/12 | Báo giá | `quotation` v1: trục chính 2 khoang, đón 35 km, cách ly 2 ngựa × 7 ngày, thủ tục… Chốt: đón 13/12, xuất phát 20/12, giao dự kiến 25/12 |
| 06/12 | Cọc 30% | `payment` DEPOSIT PAID, `payment_transaction` VNPAY. `booking` CONFIRMED, có vận đơn |
| 10/12 | Trả số dư | `payment` BALANCE PAID |
| 13/12 | Đón ngựa | `vehicle_trip` PICKUP #T1 (đón cả 2 đơn khác cùng khu vực). `handover` PICKUP DONE. `horse_leg` #1.1, #2.1 PICKUP COMPLETED. `booking` IN_OPERATION |
| 13/12 | Vào trại cách ly | `horse_stay` QUARANTINE × 2 tại `QF-CUCHI`. `handover` STAY_IN |
| 13–19/12 | Cách ly | `horse_health_log` mỗi ngày. Ngày 19: `quarantine_result = PASSED` |
| 20/12 | Chặng trục chính | `vehicle_trip` LINEHAUL #T7 (`departure_id` 101). `trip_stop`: hub → Trảng Bàng → Mộc Bài. `horse_leg` #1.2, #2.2 LINEHAUL |
| 20/12 | Cửa khẩu Mộc Bài | `handover` AGENT cho đại lý Campuchia. `horse_leg` #1.3, #2.3 có `agent_party_id` |
| 20–24/12 | Cách ly ở nước đến | `horse_stay` QUARANTINE do đại lý vận hành |
| 25/12 | Giao ngựa | `handover` DELIVERY DONE (đại lý giao, người nhận ký). `booking` DELIVERED |
| 26/12 | Quyết toán | `cost_item` phí chờ ở cửa khẩu (FORCE_MAJEURE, khách chịu) → `payment` EXTRA_CHARGE |
| 28/12 | Đóng đơn | `booking` COMPLETED, `booking_rating` 5 sao |

Chuỗi chặng của Sao Mai:

| `sequence_number` | `leg_type` | Từ | Đến | Ai chở |
|---|---|---|---|---|
| 1 | PICKUP | `booking_address` PICKUP | `location` QF-CUCHI | `vehicle_trip` #T1 |
| 2 | LINEHAUL | `location` QF-CUCHI | `location` GATE-MOCBAI | `vehicle_trip` #T7 |
| 3 | DELIVERY | `location` GATE-MOCBAI | `booking_address` DELIVERY | đại lý (`agent_party_id`) |

### 6.2 Ví dụ trục nội địa: Đồng Nai đi Huế

Khách ở Đồng Nai gửi 1 con ngựa ra Huế. Đồng Nai thuộc `HUB-HCM`, Huế thuộc `HUB-DN`. Hai hub khác nhau nên đơn đi trục, `booking_type = INTERPROVINCE`, `departure_id` là chuyến HCM-DN thứ Ba.

| `sequence_number` | `leg_type` | Từ | Đến | Ai chở |
|---|---|---|---|---|
| 1 | PICKUP | `booking_address` PICKUP (Đồng Nai) | `location` HUB-HCM | `vehicle_trip` PICKUP, xe A |
| 2 | LINEHAUL | `location` HUB-HCM | `location` HUB-DN | `vehicle_trip` LINEHAUL, xe B |
| 3 | DELIVERY | `location` HUB-DN | `booking_address` DELIVERY (Huế) | `vehicle_trip` DELIVERY, vẫn là xe B |

Giữa chặng 1 và 2, ngựa ở `horse_stay` ASSEMBLY tại HUB-HCM. Nếu xe B tới HUB-DN chiều tối, ngựa có thể ở `horse_stay` OVERNIGHT tại HUB-DN trước chặng 3. Giao xong, xe B về trụ sở bằng lượt LINEHAUL của chuyến DN-HCM thứ Năm. Lượt đó cũng có thể chở ngựa của các đơn đi từ miền Trung vào miền Nam.

Báo giá gồm: cước trục HCM-DN theo khoang (`price_item.route_id`), cước đón theo km Đồng Nai → HUB-HCM, cước giao theo km HUB-DN → Huế, và phí thủ tục kiểm dịch vận chuyển.

Nếu cùng khách đó gửi ngựa từ Đồng Nai sang Bình Dương thì cả hai tỉnh đều thuộc HUB-HCM. Đơn chạy thẳng bằng một chặng `DIRECT`, không có `departure`, và tính cước DIRECT theo km.

---

## 7. Bảng nào do ai ghi, ở giai đoạn nào

| Nhóm | Bảng | Ai ghi | Khi nào |
|---|---|---|---|
| 1 | `country`, `province`, `location`, `hub_service_area` | Admin | Dữ liệu nền |
| 2 | `app_user`, `otp` | Khách tự đăng ký. Admin tạo tài khoản nhân viên | GĐ1 / dữ liệu nền |
| 2 | `role` | Seed | Dữ liệu nền |
| 2 | `staff` | Admin | Dữ liệu nền |
| 2 | `customer`, `customer_address` | Khách | GĐ1 |
| 2 | `party`, `party_role` | Admin hoặc điều phối viên | Dữ liệu nền |
| 3 | `horse_type` | Admin | Dữ liệu nền |
| 3 | `horse` | Khách. Chuyên viên sửa khi duyệt | GĐ1–2 |
| 4 | `route`, `route_stop`, `departure_schedule`, `departure_schedule_day` | Admin | Dữ liệu nền |
| 4 | `departure` | Scheduler. Quản lý tạo chuyến đột xuất hoặc hủy chuyến | Dữ liệu nền, 5.2 |
| 5 | `vehicle_class`, `stall_type`, `vehicle` | Admin | Dữ liệu nền |
| 6 | `billable_service`, `price_list`, `price_item` | Admin hoặc quản lý | Dữ liệu nền |
| 7 | `country_rule`, `document_type`, `document_requirement` | Admin hoặc chuyên viên | Dữ liệu nền |
| 8 | `booking`, `booking_party`, `booking_address`, `booking_horse`, `booking_horse_service` | Khách tạo. Nhân viên đổi trạng thái | GĐ1 → GĐ10 |
| 8 | `booking_status_history` | Service tự ghi mỗi lần đổi trạng thái | Mọi giai đoạn |
| 8 | `terms_acceptance` | Khách | GĐ1 |
| 8 | `booking_rating` | Khách | GĐ10 |
| 9 | `vehicle_trip`, `trip_stop`, `crew_assignment` | Điều phối viên tạo. Tổ xe cập nhật giờ thực tế | GĐ5–8 |
| 9 | `horse_leg`, `horse_stay` | Điều phối viên lập kế hoạch. Tổ xe hoặc đại lý cập nhật | GĐ5–8 |
| 9 | `horse_health_log` | Hộ tống, thú y | GĐ6–8 |
| 10 | `handover`, `handover_horse`, `handover_document` | Tài xế, hộ tống | GĐ6–8 |
| 11 | `horse_insurance` | Khách khai. Chuyên viên báo giá và xác nhận | GĐ1–2 |
| 12 | `quotation`, `quotation_line` | Hệ thống tính. Quản lý duyệt và gửi | GĐ3 |
| 12 | `payment`, `payment_transaction` | Hệ thống tạo. Cổng VNPAY gọi lại | GĐ4, GĐ5, GĐ9 |
| 13 | `incident`, `incident_plan` | Tổ xe báo. Điều phối viên đề xuất. Quản lý duyệt | 5.1, GĐ8 |
| 13 | `cost_item` | Tổ xe ghi. Quản lý duyệt | GĐ7–9 |
| 14 | `document` | Khách, nhân viên, đại lý (nhân viên nhập thay) | Mọi giai đoạn |
| 15 | `notification`, `notification_recipient` | Service | Mọi giai đoạn |
| 15 | `audit_log` | Service (thay đổi dữ liệu nền, duyệt, tiền) | Mọi giai đoạn |
| 15 | `system_config` | Admin | Dữ liệu nền |
| 15 | `route_cache` | Service bản đồ | Khi tính lộ trình |

---

## 8. Tận dụng từ project cũ

> Đường dẫn backend tính từ `backend/src/main/java/com/example/racehorse_transport/`. Đường dẫn frontend tính từ `frontend/src/`.
> Bảng đối chiếu dựa trên **tên class, các trường và chú thích trong code**. Trước khi dùng lại phải đọc kỹ từng file.

Mức tận dụng:
- **Giữ:** dùng gần như nguyên.
- **Sửa:** giữ logic, đổi nguồn dữ liệu sang bảng mới.
- **Viết lại:** chỉ giữ ý tưởng.
- **Bỏ.**

### 8.1 Hạ tầng (gần như giữ nguyên)

| Thành phần | File | Mức | Ghi chú |
|---|---|---|---|
| JWT, filter, xử lý 401/403 | `security/JwtUtils`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `JwtAccessDeniedHandler` | Giữ | Vai trò lấy từ `app_user.role_code` |
| Cấu hình bảo mật, CORS | `config/SecurityConfig`, `CorsConfig` | Giữ | Cập nhật lại các đường dẫn API |
| Chống bot (Cloudflare Turnstile) | `security/TurnstileVerifier` | Giữ | |
| Chặn dò số điện thoại khi tra cứu đơn | `security/PublicTrackingGuard` | Giữ | |
| Đăng nhập, quên mật khẩu, xác thực email | `AuthServiceImpl`, `PasswordResetServiceImpl`, `EmailVerificationServiceImpl`, `scheduler/ExpiredOtpCleanup` | Sửa | Dùng bảng `otp` (khóa `email + purpose`). Khi đăng ký thì tạo thêm `customer` |
| Gửi mail | `MailDeliveryServiceImpl`, `BillMailServiceImpl` | Giữ / Sửa | Số liệu trong hóa đơn lấy từ các cột tiền của `quotation` và `SettlementPolicy` |
| Lưu file (Cloudinary) | `CloudinaryStorageServiceImpl`, `config/CloudinaryConfig`, `FileUploadController` | Giữ | Sau khi tải lên thì ghi `document` |
| Đồng hồ hệ thống (Clock) | `config/ClockConfiguration` | Giữ | Cần cho test scheduler và các hạn chót |
| Cache, Jackson, OpenAPI | `config/CacheConfig`, `JacksonConfig`, `OpenApiConfig` | Giữ | Dùng OpenAPI làm hợp đồng và sinh code client cho frontend |
| VNPAY | `VnpayServiceImpl`, `util/VnpayUtil`, `config/VnpayProperties`, `VnpayController` | Sửa | Callback ghi `payment_transaction`. Chống ghi trùng bằng UK |
| Xử lý ngoại lệ | `exception/*` | Giữ | |
| Health check | `HealthCheckController` | Giữ | |

### 8.2 Bản đồ và lộ trình (tài sản giá trị nhất, giữ lại)

| Thành phần | File | Mức | Dùng cho |
|---|---|---|---|
| Gọi Valhalla lấy đường đi của xe tải | `RoadRoutingServiceImpl` | Giữ | Tính km chặng đón và giao để tra `price_item` theo bậc km |
| Bộ đệm lộ trình | `RouteCacheServiceImpl` | Giữ | Bảng `route_cache` |
| Kiểm tra lãnh thổ quốc gia của từng chặng | `TerritoryLookupServiceImpl` | Giữ | Xác định nước đối tác của đơn để tra `country_rule` |
| Giải mã polyline, chiếu điểm lên đường | `util/Polyline`, `util/RouteGeometry` | Giữ | |
| Lập lịch trình có nghỉ bắt buộc, nghỉ đêm, chờ cửa khẩu | `util/JourneyPlanner` | Sửa | Dùng khi admin dựng `route_stop` (`arrival_offset_minutes`, `dwell_minutes`) và khi tạo `trip_stop` cho lượt đón và giao |
| Đặt tên chỗ nghỉ theo trạm gần nhất | `util/RestPlaceNamer` | Giữ | |
| Xem trước lộ trình | `RoutePreviewServiceImpl`, `CoordinatorRouteController` | Sửa | |
| Frontend: bản đồ, chọn địa chỉ | `shared/ui/AddressPicker.tsx`, `shared/lib/baseMapStyle.ts`, `shared/services/useRoadRoute.ts`, `routing.ts`, `shared/lib/waypoints.ts`, `routePreview.ts` | Giữ | Leaflet và MapLibre |
| Frontend: khớp đơn vào tuyến cố định | `shared/lib/fixedRouteMatch.ts` (+ test) | Chuyển xuống backend | Gợi ý `departure` phù hợp theo hành lang tuyến (≤ 80 km, đi vòng ≤ 1,5 lần) |

### 8.3 Quy tắc nghiệp vụ (`util/*Policy`) đưa vào package `policy`

| File cũ | Mức | Dùng cho |
|---|---|---|
| `MicrochipPolicy` (ISO 11784/11785, 15 chữ số) | Giữ | `horse.microchip`, `handover_horse.scanned_microchip` |
| `DepositPaymentWindow` (hạn VNPAY, thời gian chờ xác nhận) | Giữ | `payment.due_at` với khoản cọc |
| `PaymentAmountLimits` | Giữ | `payment.amount` |
| `ContactPatterns` | Giữ | Kiểm tra số điện thoại và email ở `app_user`, `customer`, `party`, `booking_party` |
| `BookingCodes` | Giữ | `booking.code`, `vehicle_trip.code` |
| `BookingSchedulePolicy` (ngày theo giờ Việt Nam) | Sửa | Hạn chót tính theo `departure.booking_cutoff_at` |
| `HorseInsurancePolicy` (kiểm khai báo, bản nháp, ngày hiệu lực, yêu cầu bổ sung) | Sửa | Chuyển từ JSON sang cột của `horse_insurance`. Giữ quy tắc chỉ yêu cầu bổ sung với PERSONAL |
| `DocumentRequirementPolicy`, `ClearanceDocumentPolicy` | Viết lại | Thay bằng `country_rule` + `document_requirement` + `DocumentCompletenessPolicy` |
| `TermsDocuments` (văn bản và phiên bản điều khoản) | Sửa | Cấp giá trị cho `terms_acceptance.terms_type/terms_version` |
| `IncidentCostPolicy` (mọi chi phí sự cố do công ty chịu) | **Viết lại** | Chia theo `incident.cause_type` (mục GĐ9) |
| `QuoteLinePolicy`, `QuoteTotals` | **Bỏ** | Thay bằng `PricingService`, `DepositPolicy` và các cột tiền của `quotation` |
| `BookingDetailsJson`, `BookingHistory` | **Bỏ** | Dữ liệu chuyển ra các bảng riêng và `booking_status_history` |
| `BookingStatusLabels` | Sửa | Còn 12 trạng thái |
| `BookingTripRules`, `CustomerBookingRules` | Viết lại | Theo các quy tắc của GĐ1 và GĐ5 |

### 8.4 Scheduler

| File cũ | Mức | Việc mới |
|---|---|---|
| `QuotationExpiryScheduler` | Sửa | Báo giá quá `expires_at` → EXPIRED, đơn → QUOTE_EXPIRED |
| `BookingScheduleScheduler` | Sửa | Đổi thành: sinh `departure` từ lịch, đóng chuyến khi qua hạn chốt |
| `SettlementExpiryScheduler` | Sửa | Nhắc hạn trả số dư và khoản thu thêm |
| `ExpiredOtpCleanup` | Giữ | |

### 8.5 Seeder đổi thành dữ liệu Flyway

| File cũ | Dữ liệu chuyển sang |
|---|---|
| `config/FixedRouteSeeder` (3 tuyến: HCM–Đà Nẵng, HCM–Phnom Penh qua Mộc Bài, HCM–Viêng Chăn qua Lao Bảo) | `route`, `route_stop`, `location` |
| `config/DocumentRequirementSeeder` | `country_rule`, `document_type`, `document_requirement` |
| `config/ReferenceDataSeeder` | `billable_service`, `horse_type`, `vehicle_class`, `stall_type`. Bỏ các gói thức ăn và cữ nước cũ |
| `config/DataInitializer` | `role` và tài khoản mẫu |
| Khóa giá trong `SystemConfigServiceImpl` (`TRIP_OPEN_FEE`, `KM_TIER_*`, `CLASS_FACTOR_*`, `MARGIN_RATE`, `CLEARANCE_FEE_INTERNATIONAL`) | `price_list` (biên lợi nhuận, % cọc) và `price_item` (đơn giá, bậc km) |
| Hằng số `FUEL_BOT_PER_KM`… ở `frontend/src/shared/config/booking-rules.ts` | `price_item` của dịch vụ FUEL |

Các class migration một lần sẽ **bỏ** khi chuyển sang Flyway: `BookingCodeMigration`, `CoordinatorStaffCodeMigration`, `OperationalStaffCodeMigration`, `SpecialistStaffCodeMigration`, `InternalExpenseReviewMigration`, `HorseMicrochipUniqueIndex`. Bỏ luôn `ModelMapperConfig`, vì quy tắc mới chuyển đổi bằng các method trên DTO.

### 8.6 Entity cũ → bảng mới

| Entity cũ | Bảng mới |
|---|---|
| `User` | `app_user` |
| `Customer` | `customer` |
| `Staff` | `staff` |
| `EmailOtp`, `OtpPurpose` | `otp` |
| `Horse`, `HorseType` | `horse`, `horse_type` |
| `Location` | `location`. Địa chỉ của khách chuyển sang `booking_address` và `customer_address` |
| `Route`, `LocationRoute` | `route`, `route_stop` |
| `Vehicle` | `vehicle` + `vehicle_class`. Bỏ việc đọc sức chứa từ chuỗi `vehicle_type` |
| `Category`, `Service` | `billable_service` (`layer` thay cho `Category`) + `price_item` |
| `Booking` (cùng `DetailsJson`) | `booking` + `booking_party` + `booking_address` + `horse_insurance` + `quotation` |
| `BookingHorse` | `booking_horse` |
| `ServiceBooking` | `booking_horse_service` |
| `BookingVehicle` | `vehicle_trip` + `horse_leg` |
| `BookingAssignment` | `crew_assignment` |
| `HorseJourneyLog` | `trip_stop` (giờ thực tế) + `horse_leg` + `handover` |
| `HorseHealthLog` | `horse_health_log` |
| `HorseDocument`, `TripDocument`, `CaptureAsset` | `document` (một bảng, chủ sở hữu bằng cột FK) |
| `DocumentRequirement` | `document_requirement` + `country_rule` + `document_type` |
| `TermsAcceptance` | `terms_acceptance` |
| `Quotation` (`quote_lines_json`) | `quotation` + `quotation_line` |
| `Payment` | `payment` + `payment_transaction` |
| `Incident` | `incident` + `incident_plan` + `cost_item` |
| `Notification` | `notification` + `notification_recipient` |
| `Log`, `SystemLog` | `audit_log`. Phần nhật ký của đơn chuyển sang `booking_status_history` |
| `SystemConfig` | `system_config`, chỉ giữ tham số kỹ thuật |
| `RouteCacheEntry` | `route_cache` |

### 8.7 Service cũ → service mới

| Service cũ | Hướng xử lý |
|---|---|
| `HorseService`, `HorseTypeService`, `LocationService`, `RouteService`, `CustomerProfileService`, `AdminAccountService`, `NotificationService`, `StaffNotificationService` | **Sửa** theo bảng mới, logic CRUD giữ được nhiều |
| `BookingService` (rất lớn) | **Tách** thành `BookingService` (tạo và đổi trạng thái), `QuotationService`, `PricingService` (mới), `InsuranceService`, `BookingPartyService` |
| `BookingQueryService`, `BookingVisibilityService` | **Sửa**. Giữ quy tắc ẩn chi phí nội bộ (phí cầu đường) với khách |
| `SpecialistService`, `HorseDocumentService`, `DocumentRequirementService` | **Sửa** thành `DocumentReviewService`, dựa trên `document` và `DocumentCompletenessPolicy` |
| `FleetService` (giữ xe theo khoảng ngày cố định cho từng đơn) | **Viết lại** thành `DispatchService`: tạo `vehicle_trip`, `crew_assignment`, kiểm trùng giờ |
| `TripProgressService` (switch khoảng 21 action) | **Viết lại** thành `TripStopService`, `HandoverService`, `HealthLogService`, với endpoint theo tài nguyên |
| `DeliveryService` | **Gộp** vào `HandoverService` (handover_type DELIVERY). Giữ quy tắc hết thời gian chờ thì gửi chuồng gần hoặc về hub |
| `BookingScheduleService` | **Viết lại** thành `DepartureService` (sinh chuyến, đóng chuyến, hủy chuyến) |
| `IncidentService` | **Sửa**: thêm phương án có phiên bản và chia chi phí theo nguyên nhân |
| `SettlementService` | **Sửa** thành `SettlementService` dùng `cost_item`, `payment` EXTRA_CHARGE và `SettlementPolicy`. Phần đánh giá chuyển sang `RatingService` |
| `PaymentService`, `VnpayService` | **Sửa** theo `payment`/`payment_transaction` |
| `CaptureService` | **Gộp** vào `DocumentService` (ảnh hiện trường là `document` có `captured_at` và tọa độ) |
| `TripReportService` | **Sửa**. Dựng báo cáo từ `trip_stop`, `horse_health_log`, `incident`, `cost_item`, `booking_rating` |
| `AdminAuditService`, `AdminSystemLogService`, `OperationalLogService` | **Gộp** thành `AuditService` trên `audit_log` |
| `ServiceCatalogService`, `SystemConfigService` | **Sửa**. Thêm `PriceListService` cho bảng giá |
| `HorseDeliveryLocationService` | **Viết lại**: vị trí hiện tại của ngựa lấy từ `handover` DONE mới nhất (truy vấn ở repository) |

### 8.8 Frontend

| Thành phần | File | Mức | Ghi chú |
|---|---|---|---|
| Cấu trúc 2 ứng dụng (khách và nhân viên), phân theo vai trò | `apps/customer`, `apps/backoffice/features/{admin,coordinator,driver,escort,manager,specialist}` | Giữ | Thêm màn hình quản lý tuyến, lịch chạy, chuyến, bảng giá |
| Gọi API, refresh token | `shared/api/client.ts` | Giữ | Thay các kiểu dữ liệu viết tay bằng kiểu sinh từ OpenAPI |
| Đa ngôn ngữ | `shared/i18n`, i18next | Giữ | |
| Đăng nhập, phân quyền route | `shared/auth`, `shared/routing` | Giữ | |
| Giao diện chung | `shared/ui` | Giữ | |
| Chụp ảnh hiện trường, tải giấy tờ | `shared/lib/capture.ts`, `captureScope.ts`, `documentUpload.ts` | Sửa | Gửi kèm chủ sở hữu của `document` |
| Kiểm tra đầu vào | `shared/lib/contactValidation.ts`, `locationValidation.ts`, `microchip.ts` | Giữ | Chỉ để báo lỗi sớm. Backend vẫn kiểm lại |
| Định dạng ngày, tiền, văn bản | `shared/lib/dates.ts`, `format.ts`, `text.ts` | Giữ | |
| **Tính giá ở frontend** | `shared/lib/pricing.ts`, `booking.ts` (quoteLines), `shared/config/booking-rules.ts`, `shared/config/public-pricing.ts`, `insuranceMoney.ts`, `incidentCosts.ts` | **Bỏ** | Thay bằng API xem trước giá. Frontend chỉ hiển thị |
| Hạn chót, lịch đơn | `shared/lib/deadlines.ts`, `bookingSchedule.ts`, `effectiveArrival.ts` | Sửa | Hiển thị giá trị backend trả về, không tự tính lại |
| Theo dõi ngựa và chuyến | `shared/lib/horseLocation.ts`, `trip.ts`, `shared/services/tracking.ts` | Viết lại | Theo `horse_leg`, `trip_stop`, `handover` |
| Chuyển đổi dữ liệu đơn | `shared/services/bookingMapper.ts` | Viết lại | Dữ liệu đơn không còn nằm trong `DetailsJson` |
| Bộ test hiện có | `shared/lib/*.test.ts` | Giữ một phần | Test của phần bản đồ, kiểm tra đầu vào và định dạng vẫn dùng được. Test của phần giá chuyển thành test backend cho `PricingService` |
| Test E2E | `frontend/e2e/*` | Sửa | Viết lại theo luồng mới |

### 8.9 Tóm tắt

| Mức | Phần |
|---|---|
| **Giữ** | Bảo mật, xác thực, mail, lưu file, VNPAY (phần giao tiếp với cổng), bản đồ và lộ trình (Valhalla, kiểm tra lãnh thổ, polyline), quy tắc microchip, liên hệ, mã đơn, khung giao diện, đa ngôn ngữ, gọi API |
| **Sửa** | Các service CRUD, bảo hiểm, thanh toán, sự cố, quyết toán, báo cáo, scheduler, seeder (thành dữ liệu Flyway) |
| **Viết lại** | Lập lịch và giữ xe (`FleetService`, `BookingScheduleService`), tiến trình chuyến (`TripProgressService`), giao nhận, yêu cầu giấy tờ, chia chi phí sự cố, theo dõi ngựa |
| **Bỏ** | `DetailsJson`, `quote_lines_json`, tính giá ở frontend, khóa giá trong `system_config`, ModelMapper, các class migration một lần |
