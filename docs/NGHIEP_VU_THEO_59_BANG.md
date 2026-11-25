# Nghiệp vụ chi tiết theo mô hình 59 bảng

> Tài liệu mô tả **ai làm gì, lúc nào, ghi vào bảng nào** trong hệ thống mới. Phần cuối nêu những gì tận dụng được từ project cũ.
>
> - Đặc tả cột và ràng buộc: [DB_SCHEMA_59_BANG.md](DB_SCHEMA_59_BANG.md). Khi có mâu thuẫn, file đó đúng.
> - Tài liệu **tham khảo** (căn cứ pháp lý, cách làm của IRT/Peden), không phải yêu cầu: [CAN_CU_PHAP_LY_VA_IRT.md](CAN_CU_PHAP_LY_VA_IRT.md).
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
- Công ty được thuê lại bên khác thực hiện một phần dịch vụ: đại lý nước ngoài, thú y, chuồng gửi.
- **Trọn gói vận chuyển và thủ tục, không gồm nơi cách ly.** Công ty lo hồ sơ kiểm dịch, giấy phép, hải quan và thú y lấy mẫu tại chuồng. Chủ ngựa hoặc bên nhận lo nơi cách ly. Công ty chỉ chở ngựa **đã đủ điều kiện**, thể hiện bằng giấy.
- Đoạn trong Việt Nam do xe công ty chạy. Đoạn ở nước ngoài do đại lý chạy, ngựa được bàn giao tại cửa khẩu.

### 1.2 Mô hình gom hàng qua hub

Đơn đi trục có hai cách đưa và nhận ngựa tại mỗi đầu trong nước. Công ty có thể đón hoặc giao tận nơi nếu một chiều không quá nửa giới hạn lái mỗi ngày (5 giờ theo cấu hình 10 giờ). Khách cũng có thể đưa ngựa tới hub hoặc người nhận đến hub vào giờ đã hẹn. Ngựa phải có mặt trước giờ chuyến trục chạy theo `HUB_CHECKIN_HOURS` (mặc định 3 giờ). Quá hạn nhận, ngựa được gửi chuồng và người nhận trả phí. Trách nhiệm của công ty bắt đầu khi ký nhận ngựa tại hub và kết thúc khi bên nhận ký nhận. Đơn chạy thẳng và đầu nước ngoài dùng `COMPANY`. Cả hai địa chỉ gốc vẫn được khai để chọn hub và lập giấy tờ.

```
 Chuồng khách A ─┐   chặng ĐÓN                      chặng TRỤC CHÍNH                        chặng GIAO
 Chuồng khách B ─┼──(vehicle_trip PICKUP)──► HUB ──(vehicle_trip LINEHAUL theo departure)──► ...
 Chuồng khách C ─┘                           │                                              │
                                     horse_stay ASSEMBLY                              Cửa khẩu: handover AGENT
                                     (tối đa một đêm)                                 horse_leg do đại lý (agent_party_id)
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

**Các tuyến và nhịp chạy.** Mỗi tuyến chạy **một chuyến mỗi tuần**, 6 tuyến một chiều: HCM ⇄ Mộc Bài (Campuchia), HCM ⇄ Đà Nẵng, Đà Nẵng ⇄ Lao Bảo (Lào). Một xe chạy nối nhiều tuyến trong tuần:

| Ngày (mẫu) | Xe A | Xe B |
|---|---|---|
| Thứ Hai | HCM → Mộc Bài, rồi Mộc Bài → HCM | |
| Thứ Ba – Tư | | HCM → Đà Nẵng, nghỉ đêm dọc đường |
| Thứ Năm | | Giao và đón quanh Đà Nẵng |
| Thứ Sáu | | Đà Nẵng → Lao Bảo, nghỉ tại Lao Bảo |
| Thứ Bảy | | Lao Bảo → Đà Nẵng |
| Chủ nhật – thứ Hai | | Đà Nẵng → HCM, nghỉ đêm dọc đường |

Xe B đi từ thứ Ba tới thứ Hai tuần sau nên cần hai xe nặng và hai tổ xe luân phiên. Giờ chạy và quãng đường là số mẫu, tính bằng Valhalla ở 80 km/h (`R__dev_seed_base.sql`). Nhu cầu gấp hoặc số lượng lớn đi **chuyến đột xuất**; đơn cùng hub chạy thẳng bất kỳ ngày nào.

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
| `EXPORT` | VN → Lào hoặc Campuchia. Ngựa đã đủ điều kiện cách ly, công ty bàn giao cho đại lý ở cửa khẩu | Có (nước đến) | Có |
| `IMPORT` | Lào hoặc Campuchia → VN. Ngược chiều đơn xuất: đại lý liên hệ cửa khẩu Việt Nam và bàn giao cho công ty ở đó. Chủ hàng bố trí nơi cách ly | Có (nước đi) | Có |

Đơn hồi hương (khứ hồi) là một đơn `IMPORT` có `original_booking_id` trỏ về đơn đi.

Đơn quốc tế chỉ chọn được tuyến có cửa khẩu khớp nước đối tác (`location.counterpart_country_code`). Vì mỗi đơn có một chặng trục, Đà Nẵng → Campuchia và TP.HCM → Lào chưa có tuyến định kỳ: hệ thống báo chưa có tuyến và cho xin chuyến đột xuất.

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
| `STABLE` | Chuồng gửi qua đêm | `horse_stay.operator_party_id`, `incident_plan.handler_party_id` |
| `VET` | Bác sĩ thú y, gồm khám và lấy mẫu tại chuồng | `incident_plan.handler_party_id` |
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
| 2 | Điểm mạng lưới | `location` | `HUB-HCM` (trụ sở), `HUB-DN` (hub vệ tinh), `REST-TRANGBANG`, `GATE-MOCBAI` (nước đối diện KH), `GATE-LAOBAO` (LA) |
| 3 | Vùng phục vụ của hub (mỗi tỉnh thuộc đúng một hub) | `hub_service_area` | HUB-HCM phục vụ TP.HCM, Bình Dương, Đồng Nai…; HUB-DN phục vụ Đà Nẵng, Quảng Nam, Huế… |
| 4 | Vai trò, tài khoản nhân viên | `role`, `app_user`, `staff` | |
| 5 | Đối tác | `party`, `party_role` | Đại lý Campuchia, đại lý Lào, thú y, chuồng gửi |
| 6 | Loại ngựa, hạng xe, loại khoang, xe | `horse_type`, `vehicle_class`, `stall_type`, `vehicle` | |
| 7 | Tuyến và điểm dừng (mỗi chiều là một tuyến) | `route`, `route_stop` | HCM-PNH, MOCBAI-HCM, HCM-DN, DN-HCM, DN-LAOBAO, LAOBAO-DN. Điểm dừng cách nhau tối đa 4 giờ chạy |
| 8 | Lịch chạy | `departure_schedule`, `departure_schedule_day` | Mỗi tuyến một ngày trong tuần, 9 chỗ, chốt đơn trước 72 giờ |
| 9 | Dịch vụ, bảng giá | `billable_service`, `price_list`, `price_item` | Bảng giá 2027, cọc 30% |
| 10 | Quy định, giấy tờ | `country_rule`, `document_type`, `document_requirement` | KH/EXPORT: cần giấy chứng nhận kiểm dịch xuất khẩu và `QUARANTINE_CLEARANCE` |
| 11 | Tham số kỹ thuật | `system_config` | `HANDOVER_FREE_WAIT_MINUTES = 120`; danh sách đầy đủ ở `DB_SCHEMA_59_BANG.md` mục 15.4 |

### 3.1 Sinh chuyến từ lịch

Một **scheduler** chạy hằng ngày, lấy các `departure_schedule` ACTIVE và tạo trước các `departure` cho N tuần tới:
- `departure_date` là các ngày khớp `departure_schedule_day.day_of_week` và nằm trong thời hạn hiệu lực của lịch;
- `planned_departure_at = departure_date + departure_time`;
- `booking_cutoff_at = planned_departure_at − booking_cutoff_hours`;
- `stall_capacity` chép từ lịch;
- `status = OPEN`.

Ràng buộc `UNIQUE (departure_schedule_id, departure_date)` bảo đảm chạy lại scheduler cũng không sinh trùng. Quản lý có thể tạo thêm **chuyến đột xuất** với `departure_schedule_id = NULL`.

Một scheduler khác chuyển `departure` sang `CLOSED` khi đã qua `booking_cutoff_at`.

**Có lịch không có nghĩa là chắc chắn chạy:**

| Mốc trước giờ xuất phát | Việc |
|---|---|
| 8 tuần | Mở bán (`OPEN`) |
| 72 giờ | Chốt nhận đơn (`CLOSED`) |
| 48 giờ (`DEPARTURE_DECISION_HOURS`) | Đơn chưa gửi báo giá nhả chỗ và chuyển `SCHEDULE_CHANGE_REQUIRED`. Đơn `AWAITING_DEPOSIT` giữ chỗ đến khi báo giá hết hạn. Quản lý quyết định chạy |
| 1 ngày | Ngày đưa ngựa tới hub chuẩn của đơn đi trục INTERPROVINCE, EXPORT. Dùng cho cả COMPANY và SELF. Lưu chuồng sớm hơn tính theo số ngựa và số ngày |
| 3 giờ (`HUB_CHECKIN_HOURS`) | Ngựa phải có mặt ở hub. Trễ thì điều phối chờ hoặc dời chuyến; chi phí do bên có lỗi chịu |

Khi quyết định chỉ đếm chỗ của đơn CONFIRMED hoặc IN_OPERATION (đã cọc). Dưới `DEPARTURE_MIN_FILL_PERCENT` (30%) thì cảnh báo, quản lý vẫn là người quyết định:

| Lựa chọn | Với khách |
|---|---|
| Chạy xe nhỏ hơn (ưu tiên) | Không đổi ngày, không đổi giá |
| Dồn sang chuyến gần nhất cùng tuyến | Phải đồng ý, giữ giá |
| Một khách thuê cả chuyến | Báo giá chuyến đột xuất |
| Hủy chuyến | Đổi chuyến miễn phí hoặc hoàn 100% |

Xe ra hub vệ tinh phải về trụ sở: chuyến chiều về bị hủy thì vẫn có lượt về không chở ngựa. Bỏ một tuyến mà tuyến sau trong tuần cần xe đó thì hệ thống cảnh báo.

### 3.2 Cách bảng giá vận hành

```
service (đơn vị tính)  ──<  price_item (đơn giá + điều kiện)  >──  price_list (hiệu lực, biên lợi nhuận, % cọc)
```

| Thành phần báo giá | `billable_service.layer` | `billing_unit` | Điều kiện tra `price_item` |
|---|---|---|---|
| Cước trục chính | LINEHAUL | PER_STALL | `route_id` + `stall_type_code` |
| Cước đón do công ty chở | PICKUP | PER_KM | `vehicle_class_id` + bậc `km_from..km_to` |
| Cước giao do công ty chở | DELIVERY | PER_KM | `vehicle_class_id` + bậc km |
| Cước chạy thẳng (không qua trục) | DIRECT | PER_KM | `vehicle_class_id` + bậc km, tính từ địa chỉ đón tới địa chỉ giao |
| Phụ thu khoang rộng khi chạy thẳng | STALL | PER_HORSE | Tra theo `stall_type_code` WIDE, số lượng là số ngựa chọn khoang rộng |
| Phụ phí nhiên liệu | FUEL | PER_KM | `fuel_buffer_rate` của bảng giá |
| Lưu chuồng theo ngày | QUARANTINE (tên cũ, giữ mã) | PER_HORSE_DAY | Chỉ số ngày khách xin đón sớm hơn lịch đón chuẩn. Không phải phí cách ly |
| Thú y tại chuồng: khám và lấy mẫu | ADD_ON | PER_HORSE | Tự thêm khi đơn bắt buộc có `LAB_TEST` do công ty nộp. Trường hợp khác khách chọn ở `booking_horse_service`. Kết quả là một `document` |
| Thủ tục kiểm dịch | DOCS | PER_BOOKING | `is_auto_applied = true` với đơn không phải LOCAL. Lệ phí nhà nước có `is_pass_through` |

Số km của chặng đón và giao do backend tính bằng dịch vụ bản đồ (Valhalla), từ `booking_address` tới hub đón hoặc từ hub giao tới `booking_address`. Với đơn chạy thẳng, số km tính từ địa chỉ đón tới địa chỉ giao. **Frontend không tính giá**, chỉ gọi API xem trước giá.

**Nhóm dịch vụ (theo IRT):**

| Nhóm | Gồm | Khách thao tác | Tính tiền |
|---|---|---|---|
| Chuẩn | Hộ tống, ăn uống, đo sức khỏe, theo dõi từng con | Chỉ ghi chỉ dẫn | Trong cước |
| Bắt buộc | Thủ tục kiểm dịch, hải quan, lệ phí, xét nghiệm nếu nước đến yêu cầu | Không chọn, chỉ thấy | Dòng riêng, tự thêm |
| Khách chọn | Loại khoang, bảo hiểm, thú y (đơn không bắt buộc), đón sớm | Chọn | Dòng riêng |

| Phát sinh | Thú y khẩn cấp, gửi chuồng do trễ | Đồng ý hoặc từ chối | `cost_item` (GĐ9) |

Dịch vụ khách mua không làm đổi việc của hộ tống: mọi con trên xe được chăm cùng một chuẩn, chỉ khác chỉ dẫn riêng.

Ghép đơn không làm đổi giá: mỗi khách trả theo khoang và km của mình.

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
| 5 | Chọn **loại đơn**, **điểm đón**, **điểm giao**, cách đưa và nhận ngựa, và **chuyến** muốn đi. Hệ thống gợi ý các `departure` OPEN phù hợp | Chỉ đọc `departure` (`stall_capacity − booked_stall_count`) |
| 6 | Khai các bên: người trả tiền, người gửi, người nhận, người liên hệ khẩn cấp | `booking_party` × 4 |
| 7 | Chọn ngựa, loại khoang, chế độ ăn và dịch vụ thêm | `booking_horse`, `booking_horse_service` |
| 8 | Khai bảo hiểm cho từng con: tự có / nhờ công ty thu xếp / không mua | `horse_insurance` (DRAFT). Chọn NONE thì phải ký cam kết miễn trách: `terms_acceptance` LIABILITY_WAIVER |
| 9 | Tải giấy tờ đang có | `document` (UPLOADED), có `horse_id` hoặc `booking_horse_id` và `document_requirement_id` |
| 10 | Gửi đơn | `booking` (PENDING_INTAKE), `booking_address` × 2 (snapshot), `terms_acceptance` SERVICE_TERMS gắn `booking_id`, `booking_status_history`, `notification` cho khách và chuyên viên |

**Quy tắc:**
- Chỉ chọn được `departure` có `status = OPEN`, chưa qua `booking_cutoff_at`, còn đủ chỗ (`booked_stall_count` cộng số chỗ của đơn không vượt `stall_capacity`; khoang thường 1 chỗ, khoang rộng 2 chỗ). Khách tự chọn loại khoang cho từng con; không bắt buộc theo giới tính. Khi ghi, `DepartureService` khóa dòng `departure` bằng `FOR UPDATE` rồi cộng `booked_stall_count` trong cùng transaction.
- `booking_cutoff_at` phải đủ xa so với hôm nay để kịp thẩm định, làm giấy (`permit_lead_days`, `certificate_lead_days`) theo `country_rule`. Không đủ thì cảnh báo và gợi ý chuyến sớm nhất khả thi.
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
| Lập **các lần lưu trú dự kiến** | `horse_stay`: ASSEMBLY ở hub, gồm số ngày lưu chuồng theo lịch đưa ngựa đã hẹn |

Ở giai đoạn này **chưa tạo `horse_leg`**. Vì ràng buộc "xe hoặc đại lý, đúng một", chặng do xe công ty chạy chỉ được tạo khi đã có `vehicle_trip`, tức ở GĐ5. Riêng chặng ở nước ngoài do đại lý chạy (`agent_party_id`) có thể tạo sớm.

**Khi thiếu thông tin:** chuyên viên trả đơn về khách, ghi lý do vào `booking_status_history.reason` và chuyển trạng thái `NEEDS_RESUBMISSION`. Khách bổ sung xong thì đơn quay lại `UNDER_REVIEW`.

**Điều kiện sang GĐ3:** đủ giấy bắt buộc có `submitter_type = CUSTOMER` và đã xác định yêu cầu phụ thuộc cửa khẩu. Mỗi ngựa ACTIVE phải có bảo hiểm CONFIRMED. Hợp đồng PERSONAL phải phủ ngày đón và ngày xuất phát hiện tại của đơn nếu có chuyến trục. Báo giá ARRANGED phải còn hạn. Đơn đi trục phải có `departure_id`. Hệ thống kiểm các điều kiện khi bắt đầu báo giá và tính tình trạng giấy tờ khi đọc checklist.

---

### GĐ3. Báo giá

**Ai:** hệ thống tính, quản lý duyệt. **Trạng thái:** `QUOTING` → `AWAITING_DEPOSIT`.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Backend chọn `price_list` ACTIVE có thời hạn hiệu lực chứa ngày báo giá | Đọc `price_list` |
| 2 | Sinh từng dòng báo giá từ `price_item` theo mục 3.2. Mỗi dòng có thể gắn với một con ngựa hoặc một chặng. Phí bảo hiểm mua hộ lấy trọn từ báo giá của hãng cho từng con | `quotation_line` (snapshot `unit_price`, `is_pass_through`, `price_item_id` NULL cho phí bảo hiểm mua hộ) |
| 3 | Tạo phiên bản báo giá, chốt lịch: ngày đón, ngày xuất phát, ngày giao dự kiến | `quotation` (DRAFT, `version` tăng dần, snapshot `deposit_rate`) |
| 4 | Quản lý có thể thêm dòng điều chỉnh tăng hoặc giảm bằng số nguyên đồng, kèm lý do. Tổng phải đủ phần thu hộ | `quotation_line` loại ADJUSTMENT |
| 5 | Quản lý gửi báo giá cho khách khi tổng và tiền cọc đều dương, báo giá bảo hiểm mua hộ còn hạn | `quotation.status = SENT`, `sent_by`, `sent_at`, `expires_at`. Các bản cũ đã gửi chuyển SUPERSEDED. Hạn là mốc sớm nhất trong thời gian `QUOTATION_VALID_HOURS` sau khi gửi (mặc định 24 giờ), hết ngày đón theo giờ Việt Nam và hạn báo giá bảo hiểm mua hộ (nếu có) |

`QuotationService` tính và ghi `quotation.total_amount`, `pass_through_amount`, `deposit_amount` khi báo giá còn DRAFT. Sau khi SENT thì các cột này không đổi. Tiền cọc gồm **toàn bộ** phần thu hộ (bảo hiểm, lệ phí): `deposit = deposit_rate × (tổng − thu hộ) + thu hộ`, làm tròn **lên nghìn đồng** và không vượt tổng; tổng không đổi, số dư = tổng − cọc. Km tính tiền lấy km gần nhất, đơn giá sau hệ số tới đồng nên mọi dòng giá là số nguyên đồng.

---

### GĐ4. Khách chấp nhận và đặt cọc

**Ai:** khách. **Trạng thái:** `AWAITING_DEPOSIT` → `CONFIRMED`.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Khách chấp nhận báo giá | `quotation.status = ACCEPTED`, `accepted_at`. Mỗi đơn chỉ có một báo giá ACCEPTED (partial unique index) |
| 2 | Tạo khoản cọc | `payment` DEPOSIT, `amount = quotation.deposit_amount`, `due_at = quotation.expires_at` |
| 3 | Khách trả qua VNPAY. Cổng gọi lại (callback và IPN) | `payment_transaction` (UK `gateway_code + gateway_transaction_number` chống ghi trùng), `payment.status = PAID` |
| 4 | Cấp vận đơn | `booking.waybill_number`, `status = CONFIRMED` |

Khách có thể từ chối báo giá đang chờ phản hồi và ghi lý do. Báo giá sang `REJECTED`, đơn về `QUOTING` và giữ chỗ.

Quá `expires_at` mà chưa cọc thì scheduler chuyển báo giá sang `EXPIRED` và đơn sang `QUOTE_EXPIRED`, rồi báo khách. Khách cũng nhận thông báo khi hệ thống xác nhận đã nhận cọc. Quản lý nhận thông báo khi đơn sang Đang báo giá và khi khách chấp nhận hoặc từ chối báo giá. Nếu có phiên VNPAY đang chờ xác nhận, scheduler đợi thêm theo cấu hình VNPAY để ghi nhận khoản tiền đã trả trước hạn. Đơn hết hạn thì nhả chỗ và rời chuyến trong cùng transaction. Tiền về sai thời điểm hoặc sau khi hủy: lưu giao dịch, báo quản lý số tiền, mã giao dịch và lý do để quản lý hoàn ngoài hệ thống; khách thấy "Đã nhận tiền, cần đối soát" và không phải trả lại. Hệ thống không tự khôi phục chỗ.

---

### GĐ5. Chuẩn bị

Với đầu `SELF`, điều phối hẹn giờ tại hub và chuẩn bị `horse_stay` để gắn biên bản giao nhận. Đầu tự chở không có chặng xe đón hoặc giao tương ứng.

**Ai:** điều phối viên, chuyên viên, khách. **Trạng thái:** vẫn `CONFIRMED`.

| Việc | Bảng |
|---|---|
| Hoàn tất giấy tờ kiểm dịch, giấy phép nhập. Chuyên viên tải giấy của COMPANY, CONSIGNEE, AGENT và duyệt hoặc từ chối từng giấy. Khách có thể nộp lại giấy CUSTOMER bắt buộc khi thiếu bản còn hiệu lực | `document` |
| **Tạo lượt xe đón**: hệ thống gợi ý gom các đơn cùng hub, cùng ngày, đủ chỗ, đi vòng ≤ 1,5 lần, cộng giờ nghỉ theo quy tắc #31; điều phối duyệt | `vehicle_trip` PICKUP, `trip_stop` (mỗi `booking_address` PICKUP là một điểm dừng, cuối cùng là hub) |
| **Tạo lượt xe trục chính** cho chuyến | `vehicle_trip` LINEHAUL có `departure_id`. `trip_stop` chép từ `route_stop`: giờ = `planned_departure_at + arrival_offset_minutes` |
| **Tạo lượt xe chạy thẳng** cho đơn không qua trục | `vehicle_trip` DIRECT: trụ sở → các địa chỉ đón → các địa chỉ giao → trụ sở. Có thể gom nhiều đơn cùng vùng |
| **Tạo lượt giao ở hub vệ tinh** | `vehicle_trip` DELIVERY bắt đầu tại hub vệ tinh, dùng chính xe vừa chạy trục tới. Sau đó xe về trụ sở bằng chuyến trục chiều ngược |
| Phân công tài xế, hộ tống | `crew_assignment` (ASSIGNED). Nhân viên bấm xác nhận thì thành ACKNOWLEDGED |
| Gắn ngựa vào lượt xe | `horse_leg.vehicle_trip_id`, `board_trip_stop_id`, `alight_trip_stop_id` |

**Điều kiện xe xuất phát đón**, kiểm bằng truy vấn:
- đủ tất cả giấy bắt buộc của CUSTOMER, COMPANY, CONSIGNEE và AGENT theo `DocumentCompletenessService.isComplete`, gồm `QUARANTINE_CLEARANCE` của từng con với đơn EXPORT. Đầu SELF kiểm khi nhận ngựa ở hub. Đơn IMPORT kiểm khi nhận ngựa ở cửa khẩu (7.4);
- bảo hiểm của từng con đã CONFIRMED;
- lượt xe đã có tài xế ACKNOWLEDGED;
- mọi con của lượt xe đã có vị trí khoang (quy tắc #40).

Chưa trả đủ **không** chặn xe đi đón. Số dư thu sau khi nhận ngựa (GĐ6).

**Đã cọc mà giấy không kịp (A8).** Danh sách của chuyên viên gồm đơn có chuyến và đơn liên tỉnh chạy thẳng. Hệ thống kiểm giấy theo ngày chạy hoặc ngày đón, và nhắc theo cửa sổ cấu hình trước ngày đó. Giấy khách nộp thì báo khách, giấy của công ty, người nhận hoặc đại lý thì báo chuyên viên. Khi từ chối giấy cũng báo đúng bên nộp. Với đơn có chuyến, tới mốc 48 giờ quản lý chọn nguyên nhân kèm lý do, đơn chuyển `SCHEDULE_CHANGE_REQUIRED` và nhả chỗ:

| Nguyên nhân | Đổi chuyến | Không đi nữa |
|---|---|---|
| Khách | Miễn phí 1 lần | Mất cọc, gồm cả phần thu hộ (5.4) |
| Công ty | Miễn phí | Hoàn 100% |
| Cơ quan cấp chậm | Miễn phí mỗi lần, không tính vào lượt của khách | Khi chưa đủ `AUTHORITY_DELAY_REFUND_MISSES` lần lỡ chuyến thì xử lý như khách hủy. Từ ngưỡng này hoàn cọc vận chuyển, không hoàn khoản thu hộ và `VET_CHECK` |

Giá cũ giữ trong `PRICE_HOLD_DAYS` ngày tính từ ngày chạy của chuyến vừa lỡ. Lịch sử chuyển sang `SCHEDULE_CHANGE_REQUIRED` ghi chuyến vào `previous_departure_id`, nguyên nhân vào `cause_type` và lý do vào `reason`. Đếm lượt đổi do khách và số lần lỡ chuyến do cơ quan từ loại nguyên nhân trong lịch sử. Các dòng thu hộ không hoàn và không chọn hoàn riêng từng dòng (24/11/2026). Công ty hủy thì hoàn toàn bộ tiền đã trả, gồm cả khoản thu hộ.

**Giờ lượt đón, giao (người dùng chốt 24/11/2026):** hệ thống tính theo đường thật, điều phối chỉ sửa giờ xuất phát.
- Đón trong khung 06:00–18:00 đúng ngày khách chọn, tới địa chỉ đầu lúc mở khung, ngựa về hub trong khung và nghỉ đêm ở hub. Hạn cuối là giờ chạy trừ `HUB_CHECKIN_HOURS`; quá hạn thì không cho xếp.
- Giao trong khung 06:00–18:00 sau khi xe trục tới hub. Không kịp trong ngày thì dời sang sáng hôm sau, ngựa nghỉ ở hub; hub vệ tinh gửi chuồng đối tác, công ty chịu chi phí.
- Mỗi điểm dừng 20 phút cộng 10 phút mỗi con. Lái liên tục quá `MAX_CONTINUOUS_HOURS` thì cộng thời gian nghỉ dọc đường.
- Mỗi con ở trên xe đón hoặc giao không quá nửa giờ lái mỗi ngày, tính cả đoạn đi vòng.
- Tài xế không lái quá `MAX_DRIVE_HOURS_PER_DAY` mỗi ngày, gộp mọi lượt, mỗi tài xế tính đủ dù xe có hai người.
- Dịch vụ bản đồ hỏng thì điều phối nhập giờ tay.

**Quy tắc phân xe:**
- Tổng chỗ ngựa trên xe chiếm không vượt tổng chỗ của xe; khoang rộng 2 chỗ.
- Ngựa của một đơn đi cùng một xe trục. Giấy chung của đơn chỉ có một bản gốc.
- Chỉ gán xe ACTIVE. Xe bảo dưỡng, ngừng sử dụng hoặc trùng lịch bị khóa kèm lý do.
- Một xe hoặc một nhân viên không được nằm trong hai lượt chạy trùng giờ.
- Một chuyến 9 chỗ có thể chia cho hai xe, tức hai `vehicle_trip` LINEHAUL cùng `departure_id`.
- Lượt chạy mới của một xe, một tài xế hoặc một hộ tống phải bắt đầu ở nơi lượt trước kết thúc.
- Ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến.

**Xếp vị trí khoang** (`horse_leg.stall_position`):
- Khách chọn **loại khoang**, không chọn vị trí. Hệ thống tự xếp ngay khi gán ngựa vào xe, điều phối xem sơ đồ và đổi chỗ khi cần. "Xếp lại tự động" tính lại cả xe.
- Bắt buộc: quy tắc #39, #40. Đủ chỗ nhưng lẻ vị trí thì hệ thống dồn các con khác; dồn không được thì từ chối và nêu con vướng.
- Ưu tiên khi tự xếp, không chặn: con xuống trước đứng gần cửa sau; ngựa đực và ngựa cái không cạnh nhau, đực về phía đầu xe; ngựa cùng đơn cạnh nhau. Không đạt thì hiện cảnh báo, điều phối quyết định.
- Kế hoạch đón và sơ đồ chốt **trước khi lượt xe bắt đầu**: mọi con, kể cả con đón ở điểm sau, đã có vị trí thì lượt xe mới được bắt đầu. Lượt xe bắt đầu thì khóa cả sơ đồ. Con không lên xe được thì để trống vị trí đó, không xếp lại. Chuyển ngựa sang xe khác là lượt xe mới, có sơ đồ mới.
- Lưu sơ đồ gửi kèm sơ đồ đang nhìn (mọi `horse_leg` của lượt xe và vị trí hiện tại). Server khóa `vehicle_trip` rồi so với dữ liệu thật: khác vị trí, thêm hoặc bớt ngựa thì trả 409, điều phối xem bản mới. Sơ đồ mới phải đủ đúng các con đó.
- Đổi xe hoặc loại ngựa trước khi lượt xe bắt đầu thì xếp lại cả xe. Chuồng ở hub chưa quản lý vị trí ở đợt này.

---

### GĐ6. Nhận ngựa

Với `pickup_method = SELF`, kiểm giấy trước khi ký nhận tại hub, ghi `handover` PICKUP gắn với `horse_stay` và mở thời gian lưu chuồng thực tế. Lập khoản số dư sau khi nhận. Kiểm tra đã trả đủ trước khi xe rời nơi nhận ngựa.

**Ai:** tài xế, hộ tống, khách trả số dư. **Trạng thái:** `CONFIRMED` → `IN_OPERATION` khi khách đã trả đủ và xe rời nơi nhận.

| Bước | Hành động | Bảng |
|---|---|---|
| 1 | Xe xuất phát | `vehicle_trip.status = IN_PROGRESS`, `actual_start_at` |
| 2 | Tới điểm đón | `trip_stop.actual_arrival_at` |
| 3 | Tạo lần giao nhận tại điểm dừng | `handover` handover_type PICKUP, `from_party_id` NULL (người gửi là `booking_party` SHIPPER), `to_party_id` NULL (công ty nhận) |
| 4 | Quét chip từng con và so với hồ sơ | `handover_horse.scanned_microchip`, `is_microchip_matched` |
| 5 | Khám nhanh, ghi tình trạng, chụp ảnh | `horse_health_log` (`trip_stop_id`), `document` ảnh hiện trường (`horse_leg_id` hoặc `handover_id`) |
| 6 | Nhận giấy tờ gốc, ví dụ hộ chiếu ngựa | `handover_document` direction RECEIVED |
| 7 | Ký biên bản | `document` loại HANDOVER_RECORD (`handover_id`), `handover.status = DONE`, `handed_at` |
| 8 | Lập khoản số dư bằng tổng báo giá ACCEPTED trừ số đã trả (thường gọi là "70%"), khách trả qua VNPAY | `payment` BALANCE, `due_at` = lúc nhận + `BALANCE_WAIT_MINUTES` (30 phút) |
| 9 | Trả đủ: ngựa lên đúng vị trí `stall_position` (màn hình hiện "Khoang số…") | `horse_leg` PICKUP: `status = IN_PROGRESS`, `actual_start_at` |
| 10 | Rời điểm đón | `trip_stop.actual_departure_at`, `booking.status = IN_OPERATION` |

**Không trả đủ trong 30 phút:** tổ xe trả ngựa và giấy gốc tại chỗ (`handover` RETURN), quản lý hủy đơn do khách (5.4), nhả chỗ. Các đơn khác trên xe đi tiếp. Link VNPAY mở trước hạn mà chưa có kết quả thì chờ thêm 5 phút sau hạn. Với đơn IMPORT, nơi nhận là cửa khẩu và quy tắc như trên, ngựa trả lại đại lý.

**Ngoại lệ ở bước 4–5:**
- Chip không khớp: không nhận con đó, ghi `incident` loại DOCUMENT.
- Ngựa không đủ sức khỏe: loại khỏi đơn (`booking_horse.status = EXCLUDED`), hủy các `horse_leg` còn lại của con đó, trừ `departure.booked_stall_count`, và điều chỉnh báo giá bằng phiên bản mới (SUPERSEDED bản cũ và ACCEPTED bản mới trong cùng transaction). Nếu đã trả dư, kể cả khi VNPAY xác nhận số dư theo link cũ sau khi thay báo giá, lập khoản hoàn phần dư.

**Mốc không hoàn tiền:** khi khách đã trả đủ hoặc xe đã rời nơi nhận thì khách không tự hủy được (5.4).

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

#### 7.2 Nghỉ và cho ăn

| Lúc | Cho ăn, uống | Hộ tống ghi |
|---|---|---|
| Trước khi lên xe | Cỏ khô, nước; không ngũ cốc | Tình trạng lúc nhận |
| Trạm nghỉ (tối đa 4 giờ một lần, ≥ 30 phút) | Nước và cỏ | Uống, ăn, sức khỏe từng con: `horse_health_log` (`trip_stop_id`, `water_intake`, `feed_intake`) |
| Nghỉ đêm và ở hub | Bữa tối, bữa sáng theo chỉ dẫn của khách (`diet_note`, `feedings_per_day`) | Như trên, với `horse_stay_id` nếu ngựa xuống xe |
| Lúc giao | Nước | Tình trạng bàn giao |

- Chặng đón và giao do công ty chở tối đa 5 giờ lái một chiều với cấu hình 10 giờ lái mỗi ngày. Vượt ngưỡng lái liên tục thì bố trí điểm nghỉ theo quy tắc #31.
- Tối đa 10 giờ lái mỗi ngày, nghỉ đêm 10 giờ.
- Xe chỉ rời trạm khi mọi con đã được ghi và đã nghỉ đủ. Rời sớm phải xác nhận kèm lý do.
- Bỏ uống ở 2 trạm liền thì ghi `WATCH` và báo điều phối.
- Ở hub, hộ tống nhận ngựa ở HUB_IN phụ trách tới **lần ký nhận kế tiếp** (SHIFT hoặc HUB_OUT).

**Phân công khác phụ trách:**

| | Phân công | Phụ trách |
|---|---|---|
| Câu hỏi | Ai dự kiến đi lượt xe này | Ai đang giữ con ngựa này |
| Gắn với | Lượt xe (`crew_assignment`) | Từng con ngựa (`handover` DONE mới nhất) |
| Đổi khi | Điều phối đổi kế hoạch | Người mới quét chip và ký nhận. Không sửa tay |

1. Sổ sức khỏe theo con ngựa. Mỗi dòng `horse_health_log` ghi ai đo, lúc nào, ở đâu; qua nhiều hộ tống vẫn là một dòng thời gian.
2. Không ai được buông ngựa khi chưa có người nhận: còn con chưa bàn giao thì không kết thúc được ca hay lượt xe.
3. Nhận ngựa là nhận hồ sơ: người mới xem phiếu tóm tắt (chỉ số gần nhất, ăn uống, cảnh báo, chỉ dẫn) rồi mới ký.
4. Xe có nhiều hộ tống thì người nào quét nhận con nào thì phụ trách con đó.

Việc chăm sóc suy ra từ nơi ngựa đang ở, nên đổi người thì việc tự chuyển theo: vừa nhận thì khám; trên xe thì ghi ở mỗi điểm dừng; ở hub thì khám lúc tới và 2 lần mỗi ngày cùng các bữa theo chỉ dẫn; `WATCH` thì đo lại ở lần kế tiếp.

| Tình huống | Xử lý |
|---|---|
| Đổi ca ở hub | `handover` SHIFT gắn lưu trú |
| Hộ tống ốm hoặc thay giữa đường | Điều phối cử người; người thay ký SHIFT tại điểm dừng. Người cũ không ký được thì quản lý xác nhận thay kèm lý do |
| Đổi xe do sự cố | Hộ tống xe mới ký SHIFT tại chỗ |
| Hub vệ tinh không có nhân viên | Tổ xe trục giữ tới khi giao; gửi qua đêm thì ký sang đối tác `STABLE` |

#### 7.3 Chặng trục chính

| Việc | Bảng |
|---|---|
| Chuyến bắt đầu | `departure.status = IN_PROGRESS`, `vehicle_trip` LINEHAUL IN_PROGRESS |
| Ngựa xuất khỏi nơi lưu trú, lên xe | `handover` STAY_OUT hoặc HUB_OUT, `horse_stay.actual_end_at`, `horse_leg` LINEHAUL IN_PROGRESS |
| Check-in tại từng trạm nghỉ (cách nhau tối đa 4 giờ chạy) | `trip_stop.actual_arrival_at/actual_departure_at` |
| Kiểm tra sức khỏe, cho ăn uống tại trạm (7.2) | `horse_health_log` (`trip_stop_id`) |
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

**Đơn nhập làm ngược chiều đơn xuất:**

| Bước | Đơn xuất | Đơn nhập |
|---|---|---|
| Trước chuyến | Giấy kiểm dịch xuất khẩu, `QUARANTINE_CLEARANCE` | Văn bản đồng ý của Cục, giấy kiểm dịch nước xuất, khai báo đã xác nhận |
| Liên hệ cửa khẩu Việt Nam | Công ty | Đại lý nước ngoài; công ty hỗ trợ giấy |
| Tại cửa khẩu | Công ty giao cho đại lý: `handover` AGENT, `to_party_id` = đại lý | Đại lý giao cho công ty: `handover` AGENT, `from_party_id` = đại lý. **Thiếu giấy vận chuyển mẫu 14a (`IMPORT_TRANSPORT_CERT`) thì không cho nhận** |
| Sau đó | Nước đến tự xử lý, không nằm trong trách nhiệm nơi cách ly của công ty | Xe trục chở về hub rồi giao tới địa chỉ chủ ngựa |

Giả định của đợt này: địa chỉ giao của đơn nhập là nơi chủ ngựa cách ly. Luật yêu cầu niêm phong xe về nơi cách ly nên ghé hub là đơn giản hóa; quy tắc niêm phong đã bỏ qua (`NGU_CANH_THIET_KE_LAI.md` mục 4).

#### 7.5 Ở nước đến (đơn EXPORT)

Đại lý báo mốc, điều phối viên nhập hộ. Nơi cách ly ở nước đến do người nhận lo, công ty không vận hành và không ghi `horse_stay`.

---

### GĐ8. Giao ngựa

Với `delivery_method = SELF`, trả ngựa tại hub và ghi `handover` DELIVERY gắn với `horse_stay`. Quá giờ hẹn thì gửi chuồng, báo khách khi bắt đầu giữ và tính phí theo `STABLING_DAILY_FEE` khi rời hub.

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
2. Chặng giao chuyển `FAILED`. Xe đi thẳng tới hub cuối lượt, không cần thao tác Đưa về hub.
3. Khi giao ngựa vào hub, mở `horse_stay` STABLING và thông báo mức thu theo ngày. Không thu phí chờ tại nhà.
4. Giao lại bằng chặng DELIVERY có `replaced_horse_leg_id` trỏ về chặng thất bại. Khi lập, chốt phí đến giờ lên xe dự kiến, tự duyệt và lập khoản thu thêm. Chặn HUB_OUT nếu còn phí đã chốt chưa trả.
5. Lúc rời hub, lập thêm khoản chênh nếu số ngày thực tế lớn hơn số đã tính. Người nhận tự tới hub nhận thì giao ngựa được ngay. Khách trả phần còn thiếu qua khoản thu thêm.
6. Gắn phí vào `horse_stay_id`, đặt bên chịu `CONSIGNEE`. Trong hệ thống khách thanh toán khoản thu thêm.

---

### GĐ9. Quyết toán

**Ai:** quản lý. Có thể lập và thanh toán khoản thu thêm tại `DELIVERED`, `COMPLETED` và, riêng phí giữ ngựa, tại `IN_OPERATION`. Hóa đơn muộn giữ nguyên trạng thái đơn.

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
| `FORCE_MAJEURE` | COMPANY |

Khoản khách chịu luôn gắn với một con hoặc một đơn, nên **không chia chi phí theo đầu ngựa**. Quản lý chọn các khoản đã duyệt chưa gắn khoản thu, backend lấy tổng theo chi phí đã ghi và khách trả `EXTRA_CHARGE` qua VNPAY.

Phải hỏi khách trước khi chi khoản khách chịu (`is_customer_consulted = true`). Ngoại lệ gồm chi trong hạn mức khẩn cấp và phí hệ thống tính theo biểu phí gắn lưu trú STABLING. Phí biểu phí đã báo khi bắt đầu giữ và không chiếm hạn mức khẩn cấp.

Khoản thu thêm đến hạn sau `EXTRA_CHARGE_DUE_DAYS` kể từ lúc lập. Hệ thống báo khách khi lập, nhắc mỗi khách lúc 08:00 giờ Việt Nam mỗi ngày còn nợ quá hạn và chặn gửi đơn mới tới khi trả.

---

### GĐ10. Đóng đơn

| Việc | Bảng |
|---|---|
| Đã trả đủ: số còn nợ theo `SettlementPolicy` bằng 0. Đơn không có khoản phát sinh tự đóng sau 7 ngày kể từ khi giao | `booking.status = COMPLETED` |
| Khách đánh giá một lần khi đơn ở `DELIVERED` hoặc `COMPLETED` | `booking_rating` |
| Thông báo cho các bên | `notification`, `notification_recipient` |

Hạn khiếu nại là 7 ngày kể từ `handover` DELIVERY DONE.

---

## 5. Nhánh phụ và ngoại lệ

### 5.1 Sự cố trên đường

Quy trình theo bản cũ của nhóm. Khác bản cũ: sự cố gắn lượt xe hoặc từng con, **không** đổi `booking.status`.

| Bước | Ai | Bảng |
|---|---|---|
| Báo sự cố, kèm vị trí và ảnh | Tài xế/hộ tống | Chỉ báo khi lượt IN_PROGRESS. HEALTH bắt buộc chọn chặng ngựa. ACCIDENT có thể gắn cả xe hoặc một con. |
| Lập phương án | Điều phối viên | `incident_plan` (PROPOSED, version 1): loại xử lý, chỉ đạo, chi phí dự kiến, bên xử lý (`handler_party_id`) |
| Duyệt hoặc từ chối | Quản lý | `incident_plan` APPROVED / REJECTED (bắt buộc ghi lý do). Từ chối thì lập version 2 |
| Thực hiện phương án | Tổ xe | Nghỉ và chăm sóc ≥ 30 phút. Tùy loại: VET_TREATMENT thì ghi `horse_health_log`; TRANSFER_VEHICLE thì tạo `vehicle_trip` mới và đổi `horse_leg`; RETURN_TO_HUB thì tạo chặng RETURN |
| Ghi chi phí | Tổ xe và điều phối | Tổ xe vẫn ghi chi phí và tải ảnh sau khi lượt kết thúc nếu sự cố chưa đóng. `cost_item` gắn `incident_id`, bên chịu theo `cause_type`. |
| Xác nhận đủ sức (HEALTH và ACCIDENT có chặng ngựa) | Tổ xe hoặc điều phối xác nhận thay | Điều phối cần tải kết quả thú y `VET_REPORT`. HEALTH cũ thiếu chặng vẫn xác nhận được. |
| Tiếp tục hành trình | Tài xế | Mở lại thao tác chặng |
| Đóng sự cố | Điều phối viên | `incident.status = RESOLVED` → CLOSED, `incident_plan` DONE |

### 5.2 Chuyến bị hủy hoặc đổi

1. Quản lý hủy hoặc dồn chuyến kèm lý do: `departure.status = CANCELLED`.
2. Các đơn trên chuyến chuyển sang `SCHEDULE_CHANGE_REQUIRED`.
3. Xe trục đầu tiên rời điểm đầu thì chuyến sang `IN_PROGRESS`. Mọi lượt trục chưa hủy hoàn thành thì chuyến sang `COMPLETED`. Cấm hủy ở cả hai trạng thái hoặc khi còn đơn `IN_OPERATION`.
4. Lịch sử ghi nguyên nhân COMPANY và chuyến vừa hủy. Khách chọn chuyến khác (miễn phí; đơn đã cọc thì giữ giá trong `PRICE_HOLD_DAYS`) hoặc hủy và được **hoàn 100%**. Đổi chuyến thì trừ `booked_stall_count` của chuyến cũ và cộng vào chuyến mới, giữ `FOR UPDATE` trên cả hai dòng.

Khi đơn rời chuyến, gỡ các chặng PLANNED gồm cả chặng đại lý, trả vị trí khoang và bỏ lịch hẹn hub chưa bắt đầu trong cùng giao dịch. Nếu có gỡ, báo điều phối.

### 5.3 Ngựa không đủ điều kiện

1. Trước khi đón: thiếu hoặc không đạt `QUARANTINE_CLEARANCE` hay giấy bắt buộc khác thì xe không đi đón. Xử lý theo bảng "Đã cọc mà giấy không kịp" ở GĐ5.
2. Lúc nhận: chip sai hoặc không đủ sức khỏe thì con đó bị loại (`booking_horse.status = EXCLUDED`), nhả chỗ, hủy các chặng phía sau, lập báo giá phiên bản mới cho các con còn lại.
3. Đơn nhập không có giấy vận chuyển mẫu 14a: không nhận ngựa ở cửa khẩu, ngựa ở lại với đại lý.
4. Ngựa đang ở nước ngoài mà phải đưa về Việt Nam thì tạo **đơn IMPORT liên kết** qua `original_booking_id`.

### 5.4 Khách hủy đơn

Theo điều khoản của bản cũ:

| Thời điểm | Kết quả |
|---|---|
| Trước khi cọc | Hủy miễn phí |
| Đã cọc, chưa nhận ngựa xong | Khách tự hủy, mất cọc, gồm cả phần thu hộ |
| Đã nhận ngựa, chưa trả đủ, xe chưa rời nơi nhận | Quản lý hủy do khách: mất cọc, gồm cả phần thu hộ, trả ngựa tại chỗ |
| Đã trả đủ hoặc xe đã rời nơi nhận | Khách không tự hủy được |

Nguyên nhân thuộc công ty thì hoàn 100%, gồm cả khoản thu hộ. Nếu đang chờ đổi chuyến do giấy không kịp, áp dụng bảng ở GĐ5. Khách hủy hoặc hủy theo nguyên nhân khách, cơ quan thì khoản thu hộ **không hoàn**, kể cả phần chưa chi; phí thú y (`VET_CHECK`) xử lý như cước dịch vụ (24/11/2026). Trả ngựa tại nơi nhận khi quá hạn số dư; có link VNPAY mở trước hạn chưa có kết quả thì chờ thêm 5 phút sau hạn. Link bỏ dở không chặn khách hoặc công ty hủy đơn. Xe chạy trễ và sự cố trên đường xử lý theo quy trình vận hành. Hoàn tiền ghi bằng `payment` REFUND có `refunded_payment_id`. Quản lý chuyển khoản ngoài hệ thống rồi ghi mã chuyển khoản và chứng từ nếu có.

### 5.5 Từ chối đơn

Chuyên viên hoặc quản lý từ chối khi đơn không thể thực hiện, ví dụ quy định không cho phép: `booking.status = REJECTED`, lý do ghi ở `booking_status_history.reason`.

---

## 6. Ví dụ xuyên suốt: một đơn đi Phnom Penh

Khách ở Bình Dương gửi 2 con ngựa (Sao Mai, Bạch Long) tới Phnom Penh, đi chuyến HCM-PNH thứ Hai 21/12/2026. Hai con chọn khoang thường nên chiếm 2 chỗ.

| Thời điểm | Sự kiện | Dòng dữ liệu chính |
|---|---|---|
| 01/12 | Khách gửi đơn | `booking` #500 EXPORT, `departure_id` = 101 (HCM-PNH 21/12). `booking_horse` #1 Sao Mai, #2 Bạch Long. `booking_address` PICKUP (Bình Dương) và DELIVERY (Phnom Penh). `booking_party` × 4. Giữ 2 chỗ |
| 02–04/12 | Thẩm định giấy của khách, bảo hiểm | `document` hộ chiếu, sổ tiêm × 2 APPROVED. `horse_insurance` × 2 CONFIRMED |
| 05/12 | Báo giá, hạn 24 giờ | `quotation` v1: trục chính 2 khoang, đón 35 km, thú y tại chuồng × 2, thủ tục… Chốt: đón 20/12, xuất phát 21/12 |
| 06/12 | Cọc 30% | `payment` DEPOSIT PAID, `payment_transaction` VNPAY. `booking` CONFIRMED, có vận đơn |
| 08–17/12 | Thú y lấy mẫu tại chuồng; chuyên viên tải giấy | `document` LAB_TEST, QUARANTINE_CLEARANCE, EXPORT_QUARANTINE_CERT × 2 APPROVED; giấy phép nhập, tờ khai hải quan |
| 19/12 | Quản lý quyết định chạy: 5/9 chỗ đã cọc | Điều phối tạo `vehicle_trip` LINEHAUL #T7 (`departure_id` 101) |
| 20/12 | Đón ngựa | `vehicle_trip` PICKUP #T1 (ghép đơn cùng khu vực). `handover` PICKUP DONE. Khách trả số dư: `payment` BALANCE PAID. Xe rời: `booking` IN_OPERATION |
| 20/12 | Vào hub, qua một đêm | `handover` HUB_IN, `horse_stay` ASSEMBLY × 2 tại `HUB-HCM`. Bữa tối và bữa sáng theo chỉ dẫn |
| 21/12 | Chặng trục chính | `handover` HUB_OUT. `trip_stop`: hub → Trảng Bàng (nước, cỏ) → Mộc Bài. `horse_leg` #1.2, #2.2 LINEHAUL |
| 21/12 | Cửa khẩu Mộc Bài | `handover` AGENT cho đại lý Campuchia. `horse_leg` #1.3, #2.3 có `agent_party_id` |
| 22/12 | Đại lý giao ngựa, điều phối nhập hộ | `handover` DELIVERY DONE. `booking` DELIVERED |
| 29/12 | Không phát sinh, tự đóng sau 7 ngày | `booking` COMPLETED, khách đánh giá |

Chuỗi chặng của Sao Mai:

| `sequence_number` | `leg_type` | Từ | Đến | Ai chở |
|---|---|---|---|---|
| 1 | PICKUP | `booking_address` PICKUP | `location` HUB-HCM | `vehicle_trip` #T1 |
| 2 | LINEHAUL | `location` HUB-HCM | `location` GATE-MOCBAI | `vehicle_trip` #T7 |
| 3 | DELIVERY | `location` GATE-MOCBAI | `booking_address` DELIVERY | đại lý (`agent_party_id`) |

### 6.2 Ví dụ trục nội địa: Đồng Nai đi Huế

Khách ở Đồng Nai gửi 1 con ngựa ra Huế. Đồng Nai thuộc `HUB-HCM`, Huế thuộc `HUB-DN`. Hai hub khác nhau nên đơn đi trục, `booking_type = INTERPROVINCE`, `departure_id` là chuyến HCM-DN thứ Ba.

| `sequence_number` | `leg_type` | Từ | Đến | Ai chở |
|---|---|---|---|---|
| 1 | PICKUP | `booking_address` PICKUP (Đồng Nai) | `location` HUB-HCM | `vehicle_trip` PICKUP, xe A |
| 2 | LINEHAUL | `location` HUB-HCM | `location` HUB-DN | `vehicle_trip` LINEHAUL, xe B |
| 3 | DELIVERY | `location` HUB-DN | `booking_address` DELIVERY (Huế) | `vehicle_trip` DELIVERY, vẫn là xe B |

Giữa chặng 1 và 2, ngựa ở `horse_stay` ASSEMBLY tại HUB-HCM. Nếu xe B tới HUB-DN chiều tối, ngựa có thể ở `horse_stay` OVERNIGHT tại HUB-DN trước chặng 3. Giao xong, xe B về trụ sở bằng lượt LINEHAUL của chuyến DN-HCM cuối tuần (có thể chạy thêm tuyến Đà Nẵng ⇄ Lao Bảo trước đó). Lượt đó cũng có thể chở ngựa của các đơn đi từ miền Trung vào miền Nam.

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
| 5 | `vehicle_class`, `stall_type`, `vehicle` | Admin, gồm đổi trạng thái xe (bảo dưỡng, hoạt động lại, ngừng sử dụng). Luồng B báo "cần bảo dưỡng" khi xe về hub gốc | Dữ liệu nền |
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
| 12 | `payment`, `payment_transaction` | Hệ thống tạo. Cổng VNPAY gọi lại | GĐ4, GĐ6, GĐ9 |
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
| `IncidentCostPolicy` (mọi chi phí sự cố do công ty chịu) | **Viết lại** | Theo `incident.cause_type` (mục GĐ9); bất khả kháng vẫn do công ty chịu |
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

### Đơn liên tỉnh có nhiều nơi giao

Khách chọn nơi giao và người nhận tại từng nơi. Mọi nơi thuộc cùng hub đích, đơn đi trục và công ty giao. Service kiểm `MAX_DELIVERY_PLACES` (mặc định 3, cấu hình 1–5), hạng xe đủ khoang, tuyến xe tải và giới hạn giờ vận chuyển. Một lượt giao đi qua các nơi của đơn. Tính cước giao và nhiên liệu theo phương án rẻ hơn giữa vòng giao thực tế và tính riêng từng nơi. Giấy của từng con trả tại nơi của con đó và giấy chung trả tại điểm giao cuối của đơn. Nơi giao trung gian áp dụng `INTERMEDIATE_WAIT_MINUTES` (mặc định 60). Nơi giao cuối của lượt xe áp dụng `HANDOVER_FREE_WAIT_MINUTES`.
