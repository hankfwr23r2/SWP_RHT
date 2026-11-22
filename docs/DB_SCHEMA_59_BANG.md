# Đặc tả cơ sở dữ liệu: 59 bảng

> Tài liệu dùng để tạo DB (viết Flyway migration). Bản này thay cho mô hình 64 bảng trong
> `ERD_ENTITY_MERMAID.md`, `ERD_ALL_IN_ONE.md`, `ERD_ENTITY_BOXES.md` (các file đó chưa cập nhật).
> Hệ quản trị: PostgreSQL 15 trở lên.

## Mục lục

0. [Quy ước chung](#0-quy-ước-chung)
1. [Thay đổi so với bản 64 bảng](#thay-đổi-so-với-bản-64-bảng)
2. [Danh sách 59 bảng](#danh-sách-59-bảng)
3. [Thứ tự tạo bảng](#thứ-tự-tạo-bảng)
4. Chi tiết từng nhóm: [1](#nhóm-1-danh-mục-địa-lý) · [2](#nhóm-2-tài-khoản-nhân-sự-các-bên) · [3](#nhóm-3-ngựa) · [4](#nhóm-4-mạng-lưới-và-lịch-chạy) · [5](#nhóm-5-xe) · [6](#nhóm-6-dịch-vụ-và-bảng-giá) · [7](#nhóm-7-quy-định-và-giấy-tờ-yêu-cầu) · [8](#nhóm-8-đơn-hàng) · [9](#nhóm-9-vận-hành) · [10](#nhóm-10-chuỗi-giao-nhận) · [11](#nhóm-11-bảo-hiểm) · [12](#nhóm-12-báo-giá-và-thanh-toán) · [13](#nhóm-13-sự-cố-và-chi-phí) · [14](#nhóm-14-chứng-từ) · [15](#nhóm-15-hỗ-trợ)
5. [View thay cho cột dẫn xuất](#view-thay-cho-cột-dẫn-xuất)
6. [Ràng buộc phải kiểm ở service](#ràng-buộc-phải-kiểm-ở-service)

---

## 0. Quy ước chung

### Kiểu dữ liệu

| Loại giá trị | Kiểu | Ghi chú |
|---|---|---|
| Khóa tự tăng | `bigint GENERATED ALWAYS AS IDENTITY` | Mọi bảng có `id` |
| Mã danh mục | `varchar(30)` | Ví dụ `code` của `role`, `stall_type` |
| Tên, tiêu đề | `varchar(200)` | |
| Địa chỉ | `varchar(500)` | |
| Số điện thoại | `varchar(20)` | |
| Email | `varchar(254)` | |
| Mô tả dài, ghi chú | `text` | |
| Tiền | `numeric(15,2)` | Không dùng `float`/`double` |
| Tỷ lệ (0–1) | `numeric(5,4)` | Ví dụ `0.3000` = 30% |
| Số km | `numeric(8,2)` | |
| Tọa độ | `numeric(9,6)` | |
| Mã tiền tệ | `char(3)` | ISO 4217, `CHECK (currency ~ '^[A-Z]{3}$')` |
| Mã quốc gia | `char(2)` | ISO 3166-1 alpha-2 |
| Thời điểm | `timestamptz` | Lưu UTC |
| Ngày | `date` | |
| Giờ trong ngày | `time` | |
| Giá trị liệt kê (enum) | `varchar(30)` + `CHECK (col IN (...))` | Danh mục admin tự thêm được thì làm bảng riêng |

### Đặt tên

- Bảng và cột dùng `snake_case`, tên bảng số ít (`booking`, không phải `bookings`).
- Khóa ngoại đặt tên theo `<bảng đích>_id`. Nếu một bảng có nhiều FK tới cùng bảng thì thêm vai trò: `from_location_id`, `to_location_id`.
- Tên ràng buộc: `pk_<bảng>`, `fk_<bảng>_<cột>`, `uk_<bảng>_<cột>`, `ck_<bảng>_<ý nghĩa>`, `ix_<bảng>_<cột>`.

### Khóa ngoại

- Mặc định `ON DELETE RESTRICT`: dữ liệu nghiệp vụ không bị xóa dây chuyền.
- `ON DELETE CASCADE` chỉ dùng cho **bảng con thuần** (chỉ tồn tại trong cha). Các bảng này được ghi rõ ở từng nơi.
- PostgreSQL **không tự tạo index cho cột FK**. Mọi cột FK cần `CREATE INDEX`, trừ cột đã là phần đầu của PK hoặc UK.

### Ký hiệu trong bảng cột

| Ký hiệu | Nghĩa |
|---|---|
| `PK` | Khóa chính |
| `FK → x` | Khóa ngoại tới bảng `x` |
| `UK` | Duy nhất |
| Null = `có` | Cho phép NULL. Để trống = `NOT NULL` |
| `snapshot` | Bản chụp có chủ đích: chép giá trị tại thời điểm giao dịch, không cập nhật theo bảng gốc |

### Nguyên tắc chung

1. **Không lưu dữ liệu nghiệp vụ dạng JSON.** Chỉ có 2 ngoại lệ: `payment_transaction.raw_payload` (phản hồi thô của cổng thanh toán) và `audit_log.old_data/new_data` (bản chụp kiểm toán).
2. **Không lưu cột dẫn xuất** như tổng tiền, số đã trả, số khoang còn trống. Các giá trị này tính bằng view (mục 5).
3. **Snapshot chỉ dùng có chủ đích**, cho các giá trị phải giữ nguyên theo thời điểm: đơn giá, tỷ lệ cọc, thông tin các bên trong đơn, địa chỉ đón và giao.
4. Thời gian hiệu lực dùng cặp `valid_from` (bắt buộc) và `valid_to` (NULL = vô thời hạn), tính khoảng đóng `[valid_from, valid_to]`.
5. Mọi thay đổi schema đi qua Flyway. JPA chạy với `ddl-auto=validate`.

### Extension cần bật

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;   -- cho EXCLUDE chống trùng thời hạn hiệu lực
```

---

## Thay đổi so với bản 64 bảng

| # | Thay đổi | Lý do |
|---|---|---|
| 1 | Bỏ `terms_version`. `terms_acceptance` lưu thẳng `terms_kind` và `terms_version` | Bản điều khoản chỉ cần mã và số phiên bản. Nội dung lưu thành file theo đường dẫn cố định |
| 2 | Bỏ `insurance_revision`. Thêm `revision_reason`, `revision_requested_by`, `revision_requested_at` vào `horse_insurance`. Thêm trạng thái `REVISION_REQUESTED` | Mỗi lúc chỉ có một yêu cầu sửa đang mở. Lịch sử sửa đổi đã có trong `audit_log` |
| 3 | Gộp `document_link` vào `document`: chủ sở hữu là các cột FK với `CHECK num_nonnulls(...) = 1`, thêm `requirement_id` | Mỗi chứng từ thuộc đúng một đối tượng. Bảng nối là thừa |
| 4 | Bỏ `incident_party` | Bên xử lý sự cố là `incident_plan.handler_party_id`, vai trò của bên đó lấy từ `party_role` |
| 5 | Bỏ `booking_review` | Trạng thái duyệt đã nằm ở `document.status`, `horse_insurance.status`, và việc đơn đã có `departure`/`horse_leg` |
| 6 | Bỏ vai trò `PICKUP_CONTACT` khỏi `booking_party` | Người liên hệ tại chỗ đã nằm ở `booking_address.contact_name/contact_phone` |
| 7 | `booking.status` rút từ 27 xuống 12 trạng thái | Chỉ giữ các mốc thương mại. Trạng thái vận hành lấy từ `horse_leg`, `incident` và `handover` |
| 8 | `country_rule.direction` còn INTERPROVINCE, EXPORT, IMPORT. Bỏ REIMPORT | Đơn hồi hương là đơn IMPORT có `return_of_booking_id` |
| 9 | `country_rule`: `*_lead_workdays` đổi thành `*_lead_days` (ngày lịch). Thêm EXCLUDE chống trùng thời hạn hiệu lực | Không cần bảng ngày lễ |
| 10 | `document_requirement`: bỏ `status` và `per_horse`. Thêm UK `NULLS NOT DISTINCT` | `status` trùng với thời hạn hiệu lực của `country_rule`. `per_horse` suy ra được từ `document_type.subject` |
| 11 | Bỏ `handover.signed_document_id` và `cost_item.invoice_document_id` | Trùng với `document.handover_id` và `document.cost_item_id`, và tạo vòng FK |
| 12 | `document_type.subject` mở rộng để khớp với từng cột chủ sở hữu trong `document` | Mỗi giá trị subject ứng với đúng một cột FK |
| 13 | Thêm `DIRECT` vào `vehicle_trip.kind`, `horse_leg.stage`, `service.layer` và `quotation_line.line_type` | Đơn có hub đón trùng hub giao (LOCAL hoặc liên tỉnh gần) chạy thẳng từ địa chỉ tới địa chỉ, không qua trục chính |
| 14 | `hub_service_area`: thêm `UNIQUE (province_code)` | Mỗi tỉnh thuộc đúng một hub, để tra được hub đón và hub giao |
| 15 | Có **trục nội địa** (ví dụ HCM ⇄ Đà Nẵng) với **hub vệ tinh** | Hub vệ tinh là location loại HUB, có vùng phục vụ nhưng không có xe hay nhân viên trực thuộc. `vehicle.home_hub_id` và `staff.home_hub_id` vẫn là trụ sở |

---

## Danh sách 59 bảng

| Nhóm | Bảng | Số bảng |
|---|---|---|
| 1. Địa lý | `country`, `province`, `location`, `hub_service_area` | 4 |
| 2. Tài khoản, các bên | `app_user`, `role`, `user_role`, `staff`, `party`, `party_role`, `party_address`, `otp` | 8 |
| 3. Ngựa | `horse_type`, `horse` | 2 |
| 4. Mạng lưới, lịch chạy | `route`, `route_stop`, `departure_schedule`, `departure_schedule_day`, `departure` | 5 |
| 5. Xe | `vehicle_class`, `stall_type`, `vehicle` | 3 |
| 6. Dịch vụ, bảng giá | `service`, `price_list`, `price_item` | 3 |
| 7. Quy định, giấy tờ yêu cầu | `country_rule`, `document_type`, `document_requirement` | 3 |
| 8. Đơn hàng | `booking`, `booking_party`, `booking_address`, `booking_horse`, `booking_horse_service`, `booking_status_history`, `terms_acceptance`, `booking_rating` | 8 |
| 9. Vận hành | `vehicle_trip`, `trip_stop`, `crew_assignment`, `horse_leg`, `horse_stay`, `horse_health_log` | 6 |
| 10. Giao nhận | `handover`, `handover_horse`, `handover_item` | 3 |
| 11. Bảo hiểm | `horse_insurance` | 1 |
| 12. Báo giá, thanh toán | `quotation`, `quotation_line`, `payment`, `payment_transaction` | 4 |
| 13. Sự cố, chi phí | `incident`, `incident_plan`, `cost_item` | 3 |
| 14. Chứng từ | `document` | 1 |
| 15. Hỗ trợ | `notification`, `notification_recipient`, `audit_log`, `system_config`, `route_cache` | 5 |
| | **Tổng** | **59** |

---

## Thứ tự tạo bảng

Thứ tự này bảo đảm bảng cha luôn được tạo trước bảng con, nên mô hình không có vòng FK. Có thể tách thành nhiều file Flyway theo từng bậc.

| Bậc | Bảng |
|---|---|
| 1 | `country`, `role`, `horse_type`, `vehicle_class`, `stall_type`, `service`, `price_list`, `document_type`, `app_user`, `route_cache` |
| 2 | `province`, `user_role`, `otp`, `country_rule` |
| 3 | `location`, `party` |
| 4 | `hub_service_area`, `staff`, `party_role`, `party_address`, `horse`, `route`, `vehicle`, `document_requirement` |
| 5 | `route_stop`, `departure_schedule`, `price_item`, `system_config` |
| 6 | `departure_schedule_day`, `departure` |
| 7 | `booking` |
| 8 | `booking_party`, `booking_address`, `booking_horse`, `booking_status_history`, `terms_acceptance`, `booking_rating`, `vehicle_trip`, `quotation`, `notification`, `audit_log` |
| 9 | `booking_horse_service`, `trip_stop`, `crew_assignment`, `horse_stay`, `horse_insurance`, `payment`, `notification_recipient` |
| 10 | `horse_leg`, `handover`, `payment_transaction` |
| 11 | `horse_health_log`, `handover_horse`, `quotation_line`, `incident` |
| 12 | `incident_plan`, `cost_item` |
| 13 | `document` |
| 14 | `handover_item` (cần `document`) |

---

## Nhóm 1. Danh mục địa lý

### 1.1 `country`: quốc gia

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `char(2)` | | PK | Mã ISO: VN, KH, TH |
| `name` | `varchar(200)` | | | Tên quốc gia |
| `default_currency` | `char(3)` | | CK định dạng ISO | Tiền tệ mặc định |

### 1.2 `province`: tỉnh/thành

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `varchar(10)` | | PK | Mã tỉnh, ví dụ `VN-SG`, `KH-12` |
| `country_code` | `char(2)` | | FK → country | |
| `name` | `varchar(200)` | | | |

Index: `ix_province_country_code`.

### 1.3 `location`: điểm thuộc mạng lưới của công ty

Gồm hub, trại cách ly, trạm nghỉ, cửa khẩu, chuồng và trường đua cố định. **Không chứa** địa chỉ đón hoặc giao của khách: các địa chỉ đó nằm ở `booking_address`.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `HUB-HCM`, `GATE-MOCBAI` |
| `name` | `varchar(200)` | | | |
| `type` | `varchar(30)` | | CK `HUB, QUARANTINE_FARM, REST_STOP, BORDER_GATE, STABLE, RACETRACK` | Loại điểm |
| `country_code` | `char(2)` | | FK → country | |
| `province_code` | `varchar(10)` | có | FK → province | |
| `address` | `varchar(500)` | | | |
| `latitude` | `numeric(9,6)` | có | CK −90..90 | |
| `longitude` | `numeric(9,6)` | có | CK −180..180 | |
| `opening_minutes` | `int` | có | CK 0..1439 | Giờ mở cửa, tính bằng phút từ 0 giờ |
| `closing_minutes` | `int` | có | CK 0..1439 | Giờ đóng cửa |
| `contact_phone` | `varchar(20)` | có | | |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Ràng buộc bảng: `CHECK ((latitude IS NULL) = (longitude IS NULL))`.
Index: `country_code`, `province_code`, `type`.

### 1.4 `hub_service_area`: tỉnh nào do hub nào phục vụ

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `hub_location_id` | `bigint` | | PK, FK → location, CASCADE | Phải là location loại HUB (kiểm ở service) |
| `province_code` | `varchar(10)` | | PK, FK → province, UK | Mỗi tỉnh thuộc đúng một hub |

Ràng buộc bảng: `UNIQUE (province_code)`, đồng thời là index để tra "tỉnh này thuộc hub nào".

Hub đón của đơn là hub phục vụ tỉnh của `booking_address` PICKUP. Hub giao là hub phục vụ tỉnh của `booking_address` DELIVERY. Với đơn quốc tế, phía nước ngoài dùng cửa khẩu thay cho hub.

---

## Nhóm 2. Tài khoản, nhân sự, các bên

### 2.1 `app_user`: tài khoản đăng nhập

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `username` | `varchar(50)` | | UK | |
| `email` | `varchar(254)` | | UK (so sánh không phân biệt hoa thường: `UNIQUE (lower(email))`) | |
| `password_hash` | `varchar(100)` | | | BCrypt |
| `email_verified` | `boolean` | | default `false` | |
| `preferred_language` | `varchar(5)` | | default `'vi'`, CK `vi, en` | |
| `status` | `varchar(30)` | | CK `ACTIVE, LOCKED, DISABLED` | |
| `created_at` | `timestamptz` | | default `now()` | |

### 2.2 `role`: vai trò hệ thống

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `varchar(30)` | | PK | `CUSTOMER, ADMIN, MANAGER, COORDINATOR, SPECIALIST, DRIVER, ESCORT` |
| `name` | `varchar(200)` | | | |

### 2.3 `user_role`: tài khoản có vai trò nào (N–N)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `user_id` | `bigint` | | PK, FK → app_user, CASCADE | |
| `role_code` | `varchar(30)` | | PK, FK → role | |

Index: `role_code`.

### 2.4 `staff`: hồ sơ nhân viên (1–1 với app_user)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `user_id` | `bigint` | | PK, FK → app_user | Kế thừa khóa của tài khoản |
| `staff_code` | `varchar(30)` | | UK | |
| `full_name` | `varchar(200)` | | | |
| `phone` | `varchar(20)` | | | |
| `hire_date` | `date` | | | |
| `employment_status` | `varchar(30)` | | CK `ACTIVE, ON_LEAVE, TERMINATED` | |
| `home_hub_id` | `bigint` | | FK → location | Hub trực thuộc. Đợt này mọi nhân viên thuộc trụ sở. Hub vệ tinh không có nhân viên trực thuộc |
| `driver_license_class` | `varchar(10)` | có | | Chỉ có với tài xế |
| `driver_license_expiry` | `date` | có | | |

Ràng buộc bảng: `CHECK ((driver_license_class IS NULL) = (driver_license_expiry IS NULL))`.
Index: `home_hub_id`.

### 2.5 `party`: một bên (cá nhân hoặc tổ chức)

Khách hàng, người nhận, đại lý nước ngoài, trại cách ly, bác sĩ thú y, đội cứu hộ, công ty bảo hiểm… đều là `party`. Vai trò của từng bên nằm ở `party_role`.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `kind` | `varchar(30)` | | CK `PERSON, ORGANIZATION` | |
| `display_name` | `varchar(200)` | | | |
| `phone` | `varchar(20)` | có | | |
| `email` | `varchar(254)` | có | | |
| `address` | `varchar(500)` | có | | Địa chỉ liên lạc hoặc xuất hóa đơn |
| `country_code` | `char(2)` | | FK → country | |
| `id_type` | `varchar(30)` | có | CK `CCCD, PASSPORT, BUSINESS_REG` | |
| `id_number` | `varchar(50)` | có | | |
| `id_country` | `char(2)` | có | FK → country | Nước cấp giấy tờ |
| `tax_code` | `varchar(30)` | có | | Mã số thuế, với tổ chức |
| `user_id` | `bigint` | có | UK, FK → app_user | Bên này có tài khoản đăng nhập (khách hàng) |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Ràng buộc bảng:
- `CHECK (num_nonnulls(id_type, id_number, id_country) IN (0, 3))`: giấy tờ hoặc khai đủ cả 3 cột, hoặc bỏ trống cả 3.
- `UNIQUE (id_type, id_number, id_country)`.

Index: `country_code`.

### 2.6 `party_role`: vai trò của một bên

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `party_id` | `bigint` | | PK, FK → party, CASCADE | |
| `role_type` | `varchar(30)` | | PK, CK `CUSTOMER, CONSIGNEE, DEST_AGENT, QUARANTINE_FARM, STABLE, VET, RESCUE, INSURER` | |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `valid_to >= valid_from` | |

Index: `role_type`, dùng để tra danh sách bác sĩ thú y, đội cứu hộ…

### 2.7 `party_address`: sổ địa chỉ của một bên

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `party_id` | `bigint` | | FK → party, CASCADE | |
| `label` | `varchar(100)` | | | Ví dụ "Trang trại Bình Dương" |
| `address_line` | `varchar(500)` | | | |
| `ward` | `varchar(100)` | có | | |
| `province_code` | `varchar(10)` | | FK → province | |
| `country_code` | `char(2)` | | FK → country | |
| `latitude` | `numeric(9,6)` | có | | |
| `longitude` | `numeric(9,6)` | có | | |
| `is_default` | `boolean` | | default `false` | |

Ràng buộc bảng: mỗi bên chỉ có một địa chỉ mặc định, `CREATE UNIQUE INDEX uk_party_address_default ON party_address(party_id) WHERE is_default`.
Index: `party_id`, `province_code`.

### 2.8 `otp`: mã xác thực một lần

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `email` | `varchar(254)` | | UK cùng `purpose` | |
| `user_id` | `bigint` | có | FK → app_user, CASCADE | NULL khi đang đăng ký, chưa có tài khoản |
| `purpose` | `varchar(30)` | | CK `VERIFY_EMAIL, RESET_PASSWORD` | |
| `otp_hash` | `varchar(100)` | | | Chỉ lưu hash, không lưu mã gốc |
| `expires_at` | `timestamptz` | | | |
| `last_sent_at` | `timestamptz` | | | Dùng để giới hạn tần suất gửi lại |
| `attempts` | `int` | | default 0, CK `>= 0` | |

Ràng buộc bảng: `UNIQUE (email, purpose)`.

---

## Nhóm 3. Ngựa

### 3.1 `horse_type`: loại ngựa

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `name` | `varchar(100)` | | UK | Ví dụ "Ngựa đua Thoroughbred" |
| `description` | `text` | có | | |

### 3.2 `horse`: hồ sơ ngựa

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `owner_party_id` | `bigint` | | FK → party | Chủ ngựa |
| `name` | `varchar(100)` | | | |
| `microchip` | `varchar(30)` | có | UK | Dùng để quét chip khi giao nhận |
| `passport_no` | `varchar(50)` | có | UK | |
| `gender` | `varchar(30)` | | CK `STALLION, MARE, GELDING` | |
| `birth_year` | `smallint` | có | CK `1980..2100` | |
| `color` | `varchar(50)` | có | | |
| `breed` | `varchar(100)` | có | | |
| `sire` | `varchar(100)` | có | | Tên ngựa bố |
| `dam` | `varchar(100)` | có | | Tên ngựa mẹ |
| `horse_type_id` | `bigint` | | FK → horse_type | |
| `identification_marks` | `text` | có | | Đặc điểm nhận dạng |
| `weight_kg` | `numeric(6,1)` | có | CK `> 0` | |
| `declared_value` | `numeric(15,2)` | có | CK `>= 0` | Giá trị khai báo, dùng cho bảo hiểm |
| `declared_currency` | `char(3)` | có | | |
| `status` | `varchar(30)` | | CK `ACTIVE, RETIRED, DECEASED` | Trạng thái của hồ sơ. Ngựa đang ở đâu lấy từ `v_horse_current_custody` |
| `created_at` | `timestamptz` | | default `now()` | |
| `updated_at` | `timestamptz` | | | |

Ràng buộc bảng: `CHECK ((declared_value IS NULL) = (declared_currency IS NULL))`.
Index: `owner_party_id`, `horse_type_id`.

---

## Nhóm 4. Mạng lưới và lịch chạy

### 4.1 `route`: tuyến trục chính cố định

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `HCM-PNH` |
| `name` | `varchar(200)` | | | |
| `origin_hub_id` | `bigint` | | FK → location | Điểm đầu, phải là HUB |
| `destination_location_id` | `bigint` | | FK → location | Điểm cuối: hub, trại cách ly hoặc cửa khẩu |
| `distance_km` | `numeric(8,2)` | | CK `> 0` | |
| `est_duration_minutes` | `int` | | CK `> 0` | |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Ràng buộc bảng: `CHECK (origin_hub_id <> destination_location_id)`.
Index: `origin_hub_id`, `destination_location_id`.

### 4.2 `route_stop`: các điểm dừng trên tuyến

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `route_id` | `bigint` | | PK, FK → route, CASCADE | |
| `seq` | `int` | | PK, CK `>= 1` | Thứ tự dừng |
| `location_id` | `bigint` | | FK → location | |
| `stop_type` | `varchar(30)` | | CK `HUB, REST, VITAL_CHECK, BORDER, QUARANTINE` | |
| `offset_minutes` | `int` | | CK `>= 0` | Số phút tính từ lúc xuất phát |
| `dwell_minutes` | `int` | | CK `>= 0` | Số phút dừng tại điểm |

PK là `(route_id, seq)` nên một tuyến có thể ghé cùng một điểm hai lần.
Index: `location_id`.

### 4.3 `departure_schedule`: lịch chạy định kỳ

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `route_id` | `bigint` | | FK → route | |
| `departure_time` | `time` | | | Giờ xuất phát |
| `booking_cutoff_hours` | `int` | | CK `> 0` | Chốt nhận đơn trước giờ xuất phát bao lâu |
| `capacity_stalls` | `int` | | CK `> 0` | Số khoang tối đa mỗi chuyến |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |
| `status` | `varchar(30)` | | CK `ACTIVE, PAUSED, ENDED` | |

Index: `route_id`.

### 4.4 `departure_schedule_day`: lịch chạy vào thứ mấy

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `schedule_id` | `bigint` | | PK, FK → departure_schedule, CASCADE | |
| `weekday` | `smallint` | | PK, CK `1..7` | Theo ISO: 1 = thứ Hai, 7 = Chủ nhật |

### 4.5 `departure`: chuyến cụ thể theo ngày (khách đặt chỗ vào đây)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `schedule_id` | `bigint` | có | FK → departure_schedule | NULL = chuyến đột xuất |
| `route_id` | `bigint` | | FK → route | Bắt buộc để chuyến đột xuất cũng biết tuyến |
| `departure_date` | `date` | | | |
| `planned_depart_at` | `timestamptz` | | | |
| `booking_cutoff_at` | `timestamptz` | | | |
| `capacity_stalls` | `int` | | CK `> 0` | Snapshot từ lịch, sửa được cho từng chuyến |
| `status` | `varchar(30)` | | CK `OPEN, CLOSED, IN_PROGRESS, COMPLETED, CANCELLED` | |

Ràng buộc bảng:
- `UNIQUE (schedule_id, departure_date)`: mỗi lịch sinh tối đa một chuyến mỗi ngày. Chuyến đột xuất có `schedule_id` NULL nên không bị giới hạn.
- `CHECK (booking_cutoff_at < planned_depart_at)`.

Index: `route_id`, `(status, planned_depart_at)`.

---

## Nhóm 5. Xe

### 5.1 `vehicle_class`: hạng xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | `LIGHT, MEDIUM, HEAVY` |
| `name` | `varchar(200)` | | | |
| `max_stalls` | `int` | | CK `> 0` | |

### 5.2 `stall_type`: loại khoang

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `varchar(30)` | | PK | `STANDARD, WIDE` |
| `name` | `varchar(200)` | | | |

### 5.3 `vehicle`: xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `license_plate` | `varchar(20)` | | UK | |
| `vehicle_class_id` | `bigint` | | FK → vehicle_class | |
| `standard_stalls` | `int` | | CK `>= 0` | |
| `wide_stalls` | `int` | | CK `>= 0` | |
| `home_hub_id` | `bigint` | | FK → location | Hub gốc. Đợt này mọi xe thuộc trụ sở |
| `registration_expiry` | `date` | có | | |
| `status` | `varchar(30)` | | CK `ACTIVE, MAINTENANCE, RETIRED` | Xe đang chạy hay rảnh suy ra từ `vehicle_trip` |

Ràng buộc bảng: `CHECK (standard_stalls + wide_stalls > 0)`. Điều kiện tổng khoang ≤ `vehicle_class.max_stalls` cần so hai bảng nên kiểm ở service.
Index: `vehicle_class_id`, `home_hub_id`.

---

## Nhóm 6. Dịch vụ và bảng giá

### 6.1 `service`: danh mục dịch vụ tính tiền

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `LINEHAUL_STALL`, `PICKUP_KM`, `FEED_PACK` |
| `name` | `varchar(200)` | | | |
| `layer` | `varchar(30)` | | CK `LINEHAUL, PICKUP, DELIVERY, DIRECT, FUEL, STALL, QUARANTINE, ADD_ON, DOCS, SURCHARGE` | Nhóm cước. `DIRECT` = cước chạy thẳng theo km × hạng xe |
| `billing_unit` | `varchar(30)` | | CK `PER_STALL, PER_KM, PER_HORSE, PER_HORSE_DAY, PER_BOOKING` | Đơn vị tính |
| `is_pass_through` | `boolean` | | default `false` | Thu hộ, không tính biên lợi nhuận |
| `auto_apply` | `boolean` | | default `false` | Tự thêm vào báo giá, khách không phải chọn |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

### 6.2 `price_list`: bảng giá có hiệu lực

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `name` | `varchar(200)` | | | Ví dụ "Bảng giá 2027" |
| `currency` | `char(3)` | | | |
| `margin_rate` | `numeric(5,4)` | | CK `0..1` | Biên lợi nhuận |
| `fuel_buffer_rate` | `numeric(5,4)` | | CK `0..1` | Dự phòng biến động giá nhiên liệu |
| `deposit_rate` | `numeric(5,4)` | | CK `0..1` | Tỷ lệ đặt cọc |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |
| `status` | `varchar(30)` | | CK `DRAFT, ACTIVE, ARCHIVED` | |

Ràng buộc bảng: các bảng giá ACTIVE cùng tiền tệ không được trùng thời hạn hiệu lực.
```sql
EXCLUDE USING gist (currency WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
  WHERE (status = 'ACTIVE')
```

### 6.3 `price_item`: đơn giá trong bảng giá

Mỗi dòng là một đơn giá của một dịch vụ, áp theo các điều kiện tùy chọn: tuyến, hạng xe, loại khoang, khoảng km.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `price_list_id` | `bigint` | | FK → price_list, CASCADE | |
| `service_id` | `bigint` | | FK → service | |
| `route_id` | `bigint` | có | FK → route | Giá riêng theo tuyến, dùng cho cước trục chính |
| `vehicle_class_id` | `bigint` | có | FK → vehicle_class | Giá theo hạng xe, dùng cho cước đón và giao |
| `stall_type` | `varchar(30)` | có | FK → stall_type | |
| `km_from` | `numeric(8,2)` | có | | Bậc km: từ |
| `km_to` | `numeric(8,2)` | có | | Bậc km: đến |
| `unit_price` | `numeric(15,2)` | | CK `>= 0` | |

Ràng buộc bảng:
- `CHECK ((km_from IS NULL) = (km_to IS NULL))`
- `CHECK (km_from IS NULL OR km_from < km_to)`
- `UNIQUE NULLS NOT DISTINCT (price_list_id, service_id, route_id, vehicle_class_id, stall_type, km_from)`

Index: `service_id`, `route_id`, `vehicle_class_id`.

Cách tra giá: chọn `price_list` ACTIVE có thời hạn hiệu lực chứa ngày báo giá, rồi lọc `price_item` theo `service_id`. Điều kiện nào có giá trị thì phải khớp, điều kiện NULL nghĩa là áp cho mọi giá trị. Riêng `km_from`/`km_to` thì chọn bậc chứa số km.

---

## Nhóm 7. Quy định và giấy tờ yêu cầu

### 7.1 `country_rule`: quy định theo nước và hướng đi

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `country_code` | `char(2)` | | FK → country | **Nước đối tác ngoài VN.** Với INTERPROVINCE dùng `VN` |
| `direction` | `varchar(30)` | | CK `INTERPROVINCE, EXPORT, IMPORT` | Cùng bộ giá trị với `booking.booking_type`, trừ LOCAL |
| `permit_lead_days` | `int` | | CK `>= 0` | Số ngày lịch cần để xin giấy phép |
| `cert_lead_days` | `int` | | CK `>= 0` | Số ngày lịch cần để làm giấy chứng nhận |
| `quarantine_days` | `int` | | CK `>= 0` | Số ngày cách ly |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |

Ràng buộc bảng: cùng nước và cùng hướng thì không được trùng thời hạn hiệu lực.
```sql
EXCLUDE USING gist (country_code WITH =, direction WITH =,
                    daterange(valid_from, valid_to, '[]') WITH &&)
```

Đơn LOCAL không cần tra quy định.

### 7.2 `document_type`: loại giấy tờ

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `HEALTH_CERT`, `EXPORT_PERMIT`, `HANDOVER_RECORD`, `TOLL_RECEIPT` |
| `name` | `varchar(200)` | | | |
| `subject` | `varchar(30)` | | CK `HORSE, BOOKING, BOOKING_HORSE, HORSE_LEG, VEHICLE_TRIP, INSURANCE, INCIDENT, COST_ITEM, HANDOVER, VEHICLE, STAFF, PARTY` | Loại giấy này gắn với đối tượng nào. Mỗi giá trị ứng với đúng một cột chủ sở hữu trong `document` |

Giấy cấp **cho từng con ngựa** có `subject = HORSE` hoặc `BOOKING_HORSE`. Giấy cấp **cho cả đơn** có `subject = BOOKING`.

### 7.3 `document_requirement`: theo quy định thì cần giấy gì

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `rule_id` | `bigint` | | FK → country_rule, CASCADE | |
| `document_type_id` | `bigint` | | FK → document_type | |
| `gate_location_id` | `bigint` | có | FK → location | Chỉ áp cho một cửa khẩu. NULL = mọi cửa khẩu |
| `submitted_by` | `varchar(30)` | | CK `CUSTOMER, CONSIGNEE, COMPANY, AGENT` | Ai chịu trách nhiệm nộp |
| `is_mandatory` | `boolean` | | default `true` | |

Ràng buộc bảng: `UNIQUE NULLS NOT DISTINCT (rule_id, document_type_id, gate_location_id)`.
Index: `document_type_id`, `gate_location_id`.

---

## Nhóm 8. Đơn hàng

### 8.1 `booking`: phần đầu đơn

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Mã đơn hiển thị cho khách |
| `customer_party_id` | `bigint` | | FK → party | Người đặt đơn |
| `booking_type` | `varchar(30)` | | CK `LOCAL, INTERPROVINCE, EXPORT, IMPORT` | |
| `departure_id` | `bigint` | có | FK → departure | Chuyến trục chính. NULL khi chưa gán chuyến, hoặc khi đơn chạy thẳng (hub đón trùng hub giao). Mỗi đơn có tối đa một chặng trục |
| `requested_pickup_date` | `date` | | | Ngày khách muốn đón |
| `waybill_no` | `varchar(30)` | có | UK | Số vận đơn, cấp sau khi khách đặt cọc |
| `emergency_spend_limit` | `numeric(15,2)` | có | CK `>= 0` | Hạn mức chi khẩn cấp không cần hỏi khách |
| `return_of_booking_id` | `bigint` | có | FK → booking | Đơn này là chuyến hồi hương của đơn nào |
| `status` | `varchar(30)` | | CK, xem bên dưới | |
| `created_by` | `bigint` | | FK → app_user | |
| `created_at` | `timestamptz` | | default `now()` | |

Các giá trị `status`, chỉ gồm mốc thương mại:

| Trạng thái | Nghĩa |
|---|---|
| `PENDING_INTAKE` | Khách vừa gửi đơn |
| `UNDER_REVIEW` | Đang thẩm định giấy tờ và bảo hiểm |
| `NEEDS_RESUBMISSION` | Chờ khách bổ sung hồ sơ |
| `QUOTING` | Đang lập báo giá |
| `AWAITING_DEPOSIT` | Đã gửi báo giá, chờ khách đặt cọc |
| `QUOTE_EXPIRED` | Báo giá hết hạn |
| `CONFIRMED` | Đã cọc, đã cấp vận đơn |
| `SCHEDULE_CHANGE_REQUIRED` | Chuyến bị hủy hoặc đổi, cần khách xác nhận lại lịch |
| `IN_OPERATION` | Ngựa đã được nhận, đơn đang vận hành. Chi tiết ngựa đang ở đâu xem `horse_leg`, `horse_stay` và `incident` |
| `DELIVERED` | Mọi `horse_leg` giao hàng đã xong, chờ quyết toán |
| `COMPLETED` | Đã quyết toán xong |
| `CANCELLED` / `REJECTED` | Bị hủy / bị từ chối |

Index: `customer_party_id`, `departure_id`, `return_of_booking_id`, `(status, created_at)`.

### 8.2 `booking_party`: các bên trong đơn (snapshot)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `booking_id` | `bigint` | | PK, FK → booking, CASCADE | |
| `role` | `varchar(30)` | | PK, CK `PAYER, SHIPPER, CONSIGNEE, EMERGENCY_CONTACT` | |
| `party_id` | `bigint` | có | FK → party | Bên đã có hồ sơ. NULL nếu khách chỉ nhập tên |
| `full_name` | `varchar(200)` | | snapshot | |
| `phone` | `varchar(20)` | | snapshot | |
| `id_type` | `varchar(30)` | có | snapshot | |
| `id_number` | `varchar(50)` | có | snapshot | |
| `id_country` | `char(2)` | có | snapshot | |
| `address` | `varchar(500)` | | snapshot | Địa chỉ liên lạc hoặc xuất hóa đơn, khác nơi đón ngựa |

Index: `party_id`.

### 8.3 `booking_address`: điểm đón và giao của đơn (snapshot)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | Là đích FK của `trip_stop` và `horse_leg` |
| `booking_id` | `bigint` | | FK → booking, CASCADE | |
| `role` | `varchar(30)` | | CK `PICKUP, DELIVERY` | |
| `address_line` | `varchar(500)` | | snapshot | |
| `province_code` | `varchar(10)` | | FK → province, snapshot | |
| `country_code` | `char(2)` | | FK → country, snapshot | |
| `latitude` | `numeric(9,6)` | | snapshot | Bắt buộc, để tính km và vẽ lộ trình |
| `longitude` | `numeric(9,6)` | | snapshot | |
| `contact_name` | `varchar(200)` | | snapshot | Người liên hệ tại chỗ |
| `contact_phone` | `varchar(20)` | | snapshot | |
| `access_note` | `text` | có | | Hướng dẫn vào cổng, chỗ đỗ xe |
| `location_id` | `bigint` | có | FK → location | Nếu điểm này trùng một location của mạng lưới, ví dụ trường đua |
| `source_address_id` | `bigint` | có | FK → party_address, ON DELETE SET NULL | Chép từ sổ địa chỉ nào |

Ràng buộc bảng: `UNIQUE (booking_id, role)`.
Index: `province_code`, `location_id`.

### 8.4 `booking_horse`: dòng đơn (mỗi con ngựa một dòng)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking, CASCADE | |
| `horse_id` | `bigint` | | FK → horse | |
| `stall_type` | `varchar(30)` | | FK → stall_type | Loại khoang cần đặt |
| `diet_note` | `text` | có | | |
| `feed_times_per_day` | `smallint` | có | CK `1..10` | |
| `owner_supplies_feed` | `boolean` | | default `false` | |
| `status` | `varchar(30)` | | CK `ACTIVE, EXCLUDED, CANCELLED, DELIVERED, RETURNED` | `EXCLUDED` = bị loại vì không đủ sức khỏe khi đón |
| `added_at` | `timestamptz` | | default `now()` | |

Ràng buộc bảng: `UNIQUE (booking_id, horse_id)`.
Index: `horse_id`.

### 8.5 `booking_horse_service`: dịch vụ thêm cho từng con

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `booking_horse_id` | `bigint` | | PK, FK → booking_horse, CASCADE | |
| `service_id` | `bigint` | | PK, FK → service | Chỉ chọn được dịch vụ có `layer = ADD_ON` (kiểm ở service) |
| `quantity` | `int` | | CK `> 0` | |

### 8.6 `booking_status_history`: nhật ký đổi trạng thái đơn

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking, CASCADE | |
| `from_status` | `varchar(30)` | có | | NULL ở dòng đầu tiên |
| `to_status` | `varchar(30)` | | | |
| `changed_by` | `bigint` | | FK → app_user | |
| `changed_at` | `timestamptz` | | default `now()` | |
| `reason` | `text` | có | | Lý do từ chối, lý do yêu cầu bổ sung hồ sơ… |

Index: `(booking_id, changed_at)`.

### 8.7 `terms_acceptance`: bằng chứng khách đồng ý điều khoản

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `terms_kind` | `varchar(30)` | | CK `SERVICE_TERMS, LIABILITY_WAIVER, PRIVACY` | |
| `terms_version` | `varchar(20)` | | | Ví dụ `2027.1`. Nội dung nằm ở file `terms/<kind>/<version>.md` |
| `user_id` | `bigint` | | FK → app_user | |
| `booking_id` | `bigint` | có | FK → booking | NULL nếu đồng ý lúc đăng ký tài khoản |
| `accepted_at` | `timestamptz` | | default `now()` | |
| `ip_address` | `varchar(45)` | có | | Đủ dài cho IPv6 |

Index: `user_id`, `booking_id`.

### 8.8 `booking_rating`: khách đánh giá đơn (1–0..1)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `booking_id` | `bigint` | | PK, FK → booking, CASCADE | |
| `score` | `smallint` | | CK `1..5` | |
| `comment` | `text` | có | | |
| `rated_at` | `timestamptz` | | default `now()` | |

Chỉ đánh giá được khi đơn ở trạng thái `DELIVERED` hoặc `COMPLETED` (kiểm ở service).

---

## Nhóm 9. Vận hành

### 9.1 `vehicle_trip`: một lượt chạy thật của một xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | |
| `kind` | `varchar(30)` | | CK `PICKUP, LINEHAUL, DELIVERY, TRANSFER, DIRECT` | `DIRECT` = chạy thẳng từ địa chỉ đón tới địa chỉ giao, không qua hub |
| `departure_id` | `bigint` | có | FK → departure | Bắt buộc khi `LINEHAUL` |
| `vehicle_id` | `bigint` | | FK → vehicle | |
| `start_location_id` | `bigint` | | FK → location | Hub xuất phát. Phải là nơi xe kết thúc lượt chạy trước đó |
| `end_location_id` | `bigint` | | FK → location | Nơi kết thúc lượt chạy |
| `planned_start` | `timestamptz` | | | |
| `planned_end` | `timestamptz` | | | |
| `actual_start` | `timestamptz` | có | | |
| `actual_end` | `timestamptz` | có | | |
| `sealed_at` | `timestamptz` | có | | Thời điểm niêm phong xe. Chỉ ghi nhận, đợt này không có quy tắc kiểm tra |
| `disinfected_at` | `timestamptz` | có | | Thời điểm khử trùng xe. Chỉ ghi nhận |
| `status` | `varchar(30)` | | CK `PLANNED, DISPATCHED, IN_PROGRESS, COMPLETED, CANCELLED` | |

Ràng buộc bảng:
- `CHECK (kind <> 'LINEHAUL' OR departure_id IS NOT NULL)`
- `CHECK (planned_end > planned_start)`
- `CHECK (actual_end IS NULL OR actual_start IS NOT NULL)`

Index: `departure_id`, `vehicle_id`, `(status, planned_start)`.

### 9.2 `trip_stop`: các điểm dừng của một lượt chạy

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `vehicle_trip_id` | `bigint` | | FK → vehicle_trip, CASCADE | |
| `seq` | `int` | | CK `>= 1` | |
| `location_id` | `bigint` | có | FK → location | Điểm thuộc mạng lưới |
| `booking_address_id` | `bigint` | có | FK → booking_address | Điểm đón hoặc giao của khách |
| `stop_type` | `varchar(30)` | | CK `PICKUP, DELIVERY, HUB, REST, VITAL_CHECK, BORDER, QUARANTINE` | |
| `route_id` | `bigint` | có | FK cặp `(route_id, route_seq)` → route_stop | Điểm này chép từ điểm nào của tuyến |
| `route_seq` | `int` | có | | |
| `planned_arrive_at` | `timestamptz` | | snapshot | Chép từ `route_stop.offset_minutes` |
| `planned_depart_at` | `timestamptz` | | snapshot | |
| `actual_arrive_at` | `timestamptz` | có | | |
| `actual_depart_at` | `timestamptz` | có | | |

Ràng buộc bảng:
- `UNIQUE (vehicle_trip_id, seq)`
- `CHECK (num_nonnulls(location_id, booking_address_id) = 1)`: điểm dừng là location của mạng lưới hoặc địa chỉ của khách, chỉ một trong hai.
- `CHECK ((route_id IS NULL) = (route_seq IS NULL))`
- `FOREIGN KEY (route_id, route_seq) REFERENCES route_stop(route_id, seq)`
- `CHECK (planned_depart_at >= planned_arrive_at)`

Index: `location_id`, `booking_address_id`, `(route_id, route_seq)`.

### 9.3 `crew_assignment`: phân công tổ lái cho lượt chạy

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `vehicle_trip_id` | `bigint` | | FK → vehicle_trip, CASCADE | |
| `staff_id` | `bigint` | | FK → staff(user_id) | |
| `role` | `varchar(30)` | | CK `DRIVER, ESCORT, VET` | |
| `status` | `varchar(30)` | | CK `ASSIGNED, ACKNOWLEDGED, RELEASED` | `ACKNOWLEDGED` = nhân viên đã xác nhận nhận việc |
| `assigned_at` | `timestamptz` | | default `now()` | |
| `note` | `text` | có | | |

Ràng buộc bảng: `UNIQUE (vehicle_trip_id, staff_id)`.
Index: `staff_id`.

### 9.4 `horse_leg`: một chặng di chuyển của một con ngựa

Mỗi con ngựa trong đơn có một chuỗi chặng: đón → hub → trục chính → trại cách ly → giao. Đơn chạy thẳng chỉ có một chặng `DIRECT` từ địa chỉ đón tới địa chỉ giao. Một chặng do xe công ty chở (`vehicle_trip_id`) hoặc do đại lý đảm nhận (`agent_party_id`).

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | FK → booking_horse | |
| `seq` | `int` | | CK `>= 1` | Thứ tự chặng của con ngựa |
| `stage` | `varchar(30)` | | CK `PICKUP, LINEHAUL, DELIVERY, TRANSFER, RETURN, DIRECT` | |
| `from_location_id` | `bigint` | có | FK → location | |
| `from_address_id` | `bigint` | có | FK → booking_address | |
| `to_location_id` | `bigint` | có | FK → location | |
| `to_address_id` | `bigint` | có | FK → booking_address | |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | Xe công ty chở |
| `agent_party_id` | `bigint` | có | FK → party | Đại lý chở |
| `board_stop_id` | `bigint` | có | FK → trip_stop | Lên xe ở điểm dừng nào |
| `alight_stop_id` | `bigint` | có | FK → trip_stop | Xuống xe ở điểm dừng nào |
| `replaces_leg_id` | `bigint` | có | FK → horse_leg | Chặng thay thế cho chặng thất bại, ví dụ giao lại lần hai |
| `planned_start` | `timestamptz` | | | |
| `planned_end` | `timestamptz` | | | |
| `actual_start` | `timestamptz` | có | | |
| `actual_end` | `timestamptz` | có | | |
| `status` | `varchar(30)` | | CK `PLANNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED` | |

Ràng buộc bảng:
- `UNIQUE (booking_horse_id, seq)`
- `CHECK (num_nonnulls(from_location_id, from_address_id) = 1)`
- `CHECK (num_nonnulls(to_location_id, to_address_id) = 1)`
- `CHECK (num_nonnulls(vehicle_trip_id, agent_party_id) = 1)`
- `CHECK (agent_party_id IS NULL OR (board_stop_id IS NULL AND alight_stop_id IS NULL))`: chặng do đại lý chở thì không có điểm lên xuống trên xe công ty.
- `CHECK (planned_end > planned_start)`

Index: `vehicle_trip_id`, `agent_party_id`, `board_stop_id`, `alight_stop_id`, `replaces_leg_id`, `status`.

### 9.5 `horse_stay`: thời gian ngựa ở một nơi, không trên xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | FK → booking_horse | |
| `kind` | `varchar(30)` | | CK `ASSEMBLY, QUARANTINE, OVERNIGHT, STABLING` | Tập kết ở hub / cách ly / nghỉ qua đêm / gửi chuồng |
| `location_id` | `bigint` | | FK → location | |
| `operator_party_id` | `bigint` | có | FK → party | Đơn vị vận hành nơi đó, ví dụ trại cách ly đối tác |
| `planned_from` | `timestamptz` | | | |
| `planned_to` | `timestamptz` | | | |
| `actual_from` | `timestamptz` | có | | |
| `actual_to` | `timestamptz` | có | | |
| `result` | `varchar(30)` | có | CK `PASSED, FAILED` | Chỉ dùng cho QUARANTINE |

Ràng buộc bảng:
- `CHECK (planned_to > planned_from)`
- `CHECK (result IS NULL OR kind = 'QUARANTINE')`
- `CHECK (actual_to IS NULL OR actual_from IS NOT NULL)`

Index: `booking_horse_id`, `location_id`, `operator_party_id`.

### 9.6 `horse_health_log`: lần đo sức khỏe ngựa

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | FK → booking_horse | |
| `recorded_by` | `bigint` | | FK → staff(user_id) | |
| `recorded_at` | `timestamptz` | | | |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | Đo trên đường, không ở điểm dừng |
| `trip_stop_id` | `bigint` | có | FK → trip_stop | Đo tại một điểm dừng |
| `horse_stay_id` | `bigint` | có | FK → horse_stay | Đo trong thời gian lưu trú hoặc cách ly |
| `health_status` | `varchar(30)` | | CK `NORMAL, WATCH, SICK, CRITICAL` | |
| `temperature_c` | `numeric(4,1)` | có | CK `30..45` | |
| `heart_rate` | `smallint` | có | CK `10..200` | |
| `respiration_rate` | `smallint` | có | CK `4..100` | |
| `weight_kg` | `numeric(6,1)` | có | CK `> 0` | |
| `symptoms` | `text` | có | | |
| `note` | `text` | có | | |

Ràng buộc bảng: `CHECK (num_nonnulls(vehicle_trip_id, trip_stop_id, horse_stay_id) <= 1)`. Khi đo tại `trip_stop` thì lượt chạy đã suy ra được từ điểm dừng, nên không ghi `vehicle_trip_id`.
Index: `(booking_horse_id, recorded_at)`, `trip_stop_id`, `horse_stay_id`, `vehicle_trip_id`.

---

## Nhóm 10. Chuỗi giao nhận

### 10.1 `handover`: một lần chuyển giao ngựa giữa các bên

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `kind` | `varchar(30)` | | CK `PICKUP, HUB_IN, HUB_OUT, STAY_IN, STAY_OUT, AGENT, DELIVERY, RETURN` | |
| `trip_stop_id` | `bigint` | có | FK → trip_stop | Giao nhận tại điểm dừng của xe |
| `horse_stay_id` | `bigint` | có | FK → horse_stay | Giao nhận khi nhập hoặc xuất trại, chuồng |
| `from_party_id` | `bigint` | có | FK → party | NULL = công ty |
| `to_party_id` | `bigint` | có | FK → party | NULL = công ty |
| `staff_id` | `bigint` | | FK → staff(user_id) | Nhân viên thực hiện |
| `wait_started_at` | `timestamptz` | có | | Bắt đầu chờ người nhận |
| `handed_at` | `timestamptz` | có | | |
| `status` | `varchar(30)` | | CK `PENDING, DONE, TIMEOUT, FAILED` | |
| `note` | `text` | có | | |

Biên bản ký là một `document` có `handover_id` trỏ về dòng này.

Ràng buộc bảng:
- `CHECK (num_nonnulls(trip_stop_id, horse_stay_id) = 1)`
- `CHECK (from_party_id IS DISTINCT FROM to_party_id)`
- `CHECK (status <> 'DONE' OR handed_at IS NOT NULL)`

Index: `trip_stop_id`, `horse_stay_id`, `from_party_id`, `to_party_id`, `staff_id`.

### 10.2 `handover_horse`: ngựa nào được giao trong lần giao nhận

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `handover_id` | `bigint` | | PK, FK → handover, CASCADE | |
| `horse_leg_id` | `bigint` | | PK, FK → horse_leg | |
| `chip_scanned` | `varchar(30)` | có | | Mã chip đọc được |
| `chip_matched` | `boolean` | | | So với `horse.microchip` |
| `condition_note` | `text` | có | | Tình trạng ngựa lúc giao nhận |

Index: `horse_leg_id`.

### 10.3 `handover_item`: giấy tờ gốc nhận hoặc trả trong lần giao nhận

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `handover_id` | `bigint` | | PK, FK → handover, CASCADE | |
| `document_id` | `bigint` | | PK, FK → document | Ví dụ hộ chiếu ngựa bản gốc |
| `direction` | `varchar(30)` | | CK `RECEIVED, RETURNED` | |

Index: `document_id`.

---

## Nhóm 11. Bảo hiểm

### 11.1 `horse_insurance`: bảo hiểm cho từng con trong đơn (1–1 với booking_horse)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | UK, FK → booking_horse, CASCADE | |
| `option` | `varchar(30)` | | CK `PERSONAL, ARRANGED, NONE` | Khách tự mua / công ty mua giúp / không mua |
| `insurer_party_id` | `bigint` | có | FK → party | Công ty bảo hiểm |
| `coverage_amount` | `numeric(15,2)` | có | CK `> 0` | |
| `premium` | `numeric(15,2)` | có | CK `>= 0` | Phí bảo hiểm |
| `currency` | `char(3)` | có | | |
| `quote_expires_at` | `timestamptz` | có | | Hạn của báo giá bảo hiểm |
| `policy_no` | `varchar(50)` | có | | Số hợp đồng |
| `valid_from` | `date` | có | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |
| `waiver_acceptance_id` | `bigint` | có | FK → terms_acceptance | Bản cam kết miễn trách khi không mua bảo hiểm |
| `status` | `varchar(30)` | | CK `DRAFT, REVISION_REQUESTED, CONFIRMED` | |
| `revision_reason` | `text` | có | | Lý do yêu cầu khách bổ sung |
| `revision_requested_by` | `bigint` | có | FK → staff(user_id) | |
| `revision_requested_at` | `timestamptz` | có | | |
| `confirmed_by` | `bigint` | có | FK → staff(user_id) | |
| `confirmed_at` | `timestamptz` | có | | |

Ràng buộc bảng:
- `CHECK (option <> 'ARRANGED' OR insurer_party_id IS NOT NULL)`
- `CHECK (option <> 'NONE' OR waiver_acceptance_id IS NOT NULL)`
- `CHECK (option = 'NONE' OR (coverage_amount IS NOT NULL AND currency IS NOT NULL))`
- `CHECK (status <> 'REVISION_REQUESTED' OR (revision_reason IS NOT NULL AND revision_requested_at IS NOT NULL))`. Chỉ được yêu cầu bổ sung khi `option = PERSONAL` (kiểm ở service).
- `CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))`

Index: `insurer_party_id`, `waiver_acceptance_id`.

---

## Nhóm 12. Báo giá và thanh toán

### 12.1 `quotation`: phiên bản báo giá

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking | |
| `version` | `int` | | CK `>= 1` | |
| `price_list_id` | `bigint` | | FK → price_list | Báo giá này tính theo bảng giá nào |
| `currency` | `char(3)` | | | |
| `deposit_rate` | `numeric(5,4)` | | CK `0..1`, snapshot | Chép từ `price_list` |
| `valid_until` | `timestamptz` | | | |
| `confirmed_pickup_date` | `date` | | | |
| `confirmed_departure_date` | `date` | | | |
| `estimated_delivery_date` | `date` | | CK `>= confirmed_departure_date` | |
| `status` | `varchar(30)` | | CK `DRAFT, SENT, ACCEPTED, REJECTED, EXPIRED, SUPERSEDED` | |
| `sent_by` | `bigint` | có | FK → staff(user_id) | NULL khi còn DRAFT |
| `sent_at` | `timestamptz` | có | | |
| `accepted_at` | `timestamptz` | có | | |

Ràng buộc bảng:
- `UNIQUE (booking_id, version)`
- `CREATE UNIQUE INDEX uk_quotation_accepted ON quotation(booking_id) WHERE status = 'ACCEPTED'`: mỗi đơn chỉ có một báo giá được chấp nhận.
- `CHECK (status = 'DRAFT' OR (sent_by IS NOT NULL AND sent_at IS NOT NULL))`

Index: `price_list_id`.

### 12.2 `quotation_line`: dòng báo giá

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `quotation_id` | `bigint` | | FK → quotation, CASCADE | |
| `line_no` | `int` | | CK `>= 1` | |
| `line_type` | `varchar(30)` | | CK `LINEHAUL, PICKUP, DELIVERY, DIRECT, FUEL, STALL, QUARANTINE, SERVICE, DOCS, INSURANCE, ADJUSTMENT` | |
| `service_id` | `bigint` | có | FK → service | |
| `price_item_id` | `bigint` | có | FK → price_item | Đơn giá lấy từ dòng giá nào. NULL với dòng điều chỉnh tay |
| `booking_horse_id` | `bigint` | có | FK → booking_horse | Dòng này tính cho con ngựa nào |
| `horse_leg_id` | `bigint` | có | FK → horse_leg | Dòng này tính cho chặng nào |
| `description` | `varchar(500)` | | | |
| `quantity` | `numeric(10,2)` | | CK `> 0` | |
| `unit` | `varchar(30)` | | | |
| `unit_price` | `numeric(15,2)` | | snapshot | |
| `amount` | `numeric(15,2)` | | `GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED` | |
| `is_pass_through` | `boolean` | | snapshot | |

Ràng buộc bảng:
- `UNIQUE (quotation_id, line_no)`
- `CHECK (unit_price >= 0 OR line_type = 'ADJUSTMENT')`: chỉ dòng điều chỉnh được có đơn giá âm (giảm giá).
- `CHECK (line_type = 'ADJUSTMENT' OR service_id IS NOT NULL)`

Index: `service_id`, `price_item_id`, `booking_horse_id`, `horse_leg_id`.

Tổng tiền **không lưu**, tính bằng `v_quotation_total`.

### 12.3 `payment`: khoản phải thu hoặc phải hoàn

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking | |
| `quotation_id` | `bigint` | có | FK → quotation | Cọc và số dư gắn với báo giá đã chấp nhận |
| `kind` | `varchar(30)` | | CK `DEPOSIT, BALANCE, EXTRA_CHARGE, REFUND` | |
| `amount` | `numeric(15,2)` | | CK `> 0` | Luôn dương. Chiều tiền xác định bằng `kind` |
| `currency` | `char(3)` | | | |
| `method` | `varchar(30)` | | CK `VNPAY, BANK_TRANSFER, CASH` | |
| `status` | `varchar(30)` | | CK `PENDING, PAID, FAILED, CANCELLED` | |
| `due_at` | `timestamptz` | có | | |
| `paid_at` | `timestamptz` | có | | |
| `refund_of_payment_id` | `bigint` | có | FK → payment | |

Ràng buộc bảng:
- `CHECK ((kind = 'REFUND') = (refund_of_payment_id IS NOT NULL))`
- `CHECK (kind NOT IN ('DEPOSIT','BALANCE') OR quotation_id IS NOT NULL)`
- `CHECK (status <> 'PAID' OR paid_at IS NOT NULL)`

Index: `booking_id`, `quotation_id`, `refund_of_payment_id`.

### 12.4 `payment_transaction`: giao dịch với cổng thanh toán

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `payment_id` | `bigint` | | FK → payment | |
| `gateway` | `varchar(30)` | | | Ví dụ `VNPAY` |
| `gateway_txn_no` | `varchar(100)` | | | Mã giao dịch phía cổng |
| `bank_code` | `varchar(30)` | có | | |
| `response_code` | `varchar(10)` | | | |
| `amount` | `numeric(15,2)` | | CK `> 0` | Số tiền cổng báo về, dùng để đối chiếu |
| `status` | `varchar(30)` | | CK `PENDING, SUCCESS, FAILED` | |
| `gateway_paid_at` | `timestamptz` | có | | |
| `received_at` | `timestamptz` | | default `now()` | |
| `raw_payload` | `jsonb` | | | Phản hồi thô của cổng. Ngoại lệ được phép dùng JSON |

Ràng buộc bảng: `UNIQUE (gateway, gateway_txn_no)`. Cổng gửi lại callback nhiều lần cũng không tạo bản ghi trùng.
Index: `payment_id`.

---

## Nhóm 13. Sự cố và chi phí

### 13.1 `incident`: sự cố

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | có | FK → booking | |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | Một sự cố xe có thể ảnh hưởng nhiều đơn |
| `horse_leg_id` | `bigint` | có | FK → horse_leg | Sự cố của riêng một con ngựa |
| `incident_type` | `varchar(30)` | | CK `HEALTH, VEHICLE_BREAKDOWN, ACCIDENT, DELAY, DOCUMENT, HANDOVER_TIMEOUT, OTHER` | |
| `cause` | `varchar(30)` | | CK `COMPANY_FAULT, HORSE_CONDITION, CUSTOMER_DOCS, AGENT_FAULT, FORCE_MAJEURE, RECIPIENT_ABSENT` | Nguyên nhân, quyết định ai chịu chi phí |
| `name` | `varchar(200)` | | | |
| `description` | `text` | | | |
| `latitude` | `numeric(9,6)` | có | | |
| `longitude` | `numeric(9,6)` | có | | |
| `reported_by` | `bigint` | | FK → staff(user_id) | |
| `reported_at` | `timestamptz` | | default `now()` | |
| `status` | `varchar(30)` | | CK `REPORTED, PLAN_PENDING, PLAN_ACTIVE, RESOLVED, CLOSED` | |
| `fit_confirmed_by` | `bigint` | có | FK → staff(user_id) | Người xác nhận ngựa đủ sức khỏe đi tiếp |
| `fit_confirmed_at` | `timestamptz` | có | | |
| `resolved_at` | `timestamptz` | có | | |

Ràng buộc bảng:
- `CHECK (num_nonnulls(booking_id, vehicle_trip_id, horse_leg_id) >= 1)`
- `CHECK ((fit_confirmed_by IS NULL) = (fit_confirmed_at IS NULL))`

Index: `booking_id`, `vehicle_trip_id`, `horse_leg_id`, `status`.

### 13.2 `incident_plan`: phương án xử lý sự cố (có phiên bản)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `incident_id` | `bigint` | | FK → incident, CASCADE | |
| `version` | `int` | | CK `>= 1` | |
| `resolution_type` | `varchar(30)` | | CK `CONTINUE, VET_TREATMENT, STABLE_NEARBY, TRANSFER_VEHICLE, RETURN_TO_HUB, CANCEL` | |
| `directive` | `text` | | | Chỉ đạo cụ thể |
| `estimated_cost` | `numeric(15,2)` | có | CK `>= 0` | |
| `handler_party_id` | `bigint` | có | FK → party | Bên xử lý: thú y, cứu hộ, chuồng gửi. Vai trò của bên này lấy từ `party_role` |
| `status` | `varchar(30)` | | CK `PROPOSED, APPROVED, REJECTED, SUPERSEDED, DONE` | |
| `proposed_by` | `bigint` | | FK → staff(user_id) | |
| `proposed_at` | `timestamptz` | | default `now()` | |
| `decided_by` | `bigint` | có | FK → staff(user_id) | |
| `decided_at` | `timestamptz` | có | | |
| `rejection_reason` | `text` | có | | |

Ràng buộc bảng:
- `UNIQUE (incident_id, version)`
- `CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)`
- `CHECK (status IN ('PROPOSED') OR (decided_by IS NOT NULL AND decided_at IS NOT NULL))`
- `CREATE UNIQUE INDEX uk_incident_plan_active ON incident_plan(incident_id) WHERE status = 'APPROVED'`: mỗi sự cố chỉ có một phương án đang hiệu lực.

Index: `handler_party_id`.

### 13.3 `cost_item`: chi phí phát sinh

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `category` | `varchar(30)` | | CK `TOLL, FUEL, STABLING, VET, RESCUE, REPAIR, WAITING, OTHER` | |
| `description` | `varchar(500)` | | | |
| `amount` | `numeric(15,2)` | | CK `> 0` | |
| `currency` | `char(3)` | | | |
| `payer` | `varchar(30)` | | CK `COMPANY, CUSTOMER, CONSIGNEE, AGENT` | Ai chịu khoản này |
| `booking_id` | `bigint` | có | FK → booking | |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | |
| `incident_id` | `bigint` | có | FK → incident | |
| `handover_id` | `bigint` | có | FK → handover | Ví dụ phí chờ khi người nhận vắng mặt |
| `horse_stay_id` | `bigint` | có | FK → horse_stay | Ví dụ phí gửi chuồng |
| `customer_consulted` | `boolean` | | default `false` | Đã hỏi ý kiến khách trước khi chi |
| `incurred_at` | `timestamptz` | | | |
| `recorded_by` | `bigint` | | FK → staff(user_id) | |
| `approved_by` | `bigint` | có | FK → staff(user_id) | |
| `approved_at` | `timestamptz` | có | | |
| `extra_payment_id` | `bigint` | có | FK → payment | Khoản thu thêm từ khách cho chi phí này |

Hóa đơn hoặc biên lai là một `document` có `cost_item_id` trỏ về dòng này.

Ràng buộc bảng:
- `CHECK (payer = 'COMPANY' OR booking_id IS NOT NULL)`
- `CHECK (num_nonnulls(booking_id, vehicle_trip_id, incident_id) >= 1)`
- `CHECK ((approved_by IS NULL) = (approved_at IS NULL))`
- `CHECK (extra_payment_id IS NULL OR payer <> 'COMPANY')`

Index: `booking_id`, `vehicle_trip_id`, `incident_id`, `handover_id`, `horse_stay_id`, `extra_payment_id`.

---

## Nhóm 14. Chứng từ

### 14.1 `document`: tệp chứng từ

Mỗi chứng từ thuộc **đúng một** đối tượng, được chỉ bằng một trong các cột `*_id` ở phần dưới của bảng. Cột nào được điền phải khớp với `document_type.subject`.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `document_type_id` | `bigint` | | FK → document_type | |
| `requirement_id` | `bigint` | có | FK → document_requirement | Chứng từ này đáp ứng yêu cầu giấy tờ nào |
| `file_url` | `varchar(1000)` | | UK | |
| `file_name` | `varchar(255)` | | | |
| `document_number` | `varchar(100)` | có | | Số giấy |
| `issue_date` | `date` | có | | |
| `expiry_date` | `date` | có | CK `>= issue_date` | |
| `uploaded_by` | `bigint` | | FK → app_user | |
| `uploaded_at` | `timestamptz` | | default `now()` | |
| `captured_at` | `timestamptz` | có | | Thời điểm chụp, với ảnh hiện trường |
| `latitude` | `numeric(9,6)` | có | | |
| `longitude` | `numeric(9,6)` | có | | |
| `status` | `varchar(30)` | | CK `UPLOADED, APPROVED, REJECTED, EXPIRED` | |
| `reviewed_by` | `bigint` | có | FK → staff(user_id) | |
| `reviewed_at` | `timestamptz` | có | | |
| `review_note` | `text` | có | | |
| **Chủ sở hữu (đúng 1 cột có giá trị)** | | | | |
| `horse_id` | `bigint` | có | FK → horse | subject `HORSE` |
| `booking_id` | `bigint` | có | FK → booking | subject `BOOKING` |
| `booking_horse_id` | `bigint` | có | FK → booking_horse | subject `BOOKING_HORSE` |
| `horse_leg_id` | `bigint` | có | FK → horse_leg | subject `HORSE_LEG` |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | subject `VEHICLE_TRIP` |
| `horse_insurance_id` | `bigint` | có | FK → horse_insurance | subject `INSURANCE` |
| `incident_id` | `bigint` | có | FK → incident | subject `INCIDENT` |
| `cost_item_id` | `bigint` | có | FK → cost_item | subject `COST_ITEM` |
| `handover_id` | `bigint` | có | FK → handover | subject `HANDOVER` |
| `vehicle_id` | `bigint` | có | FK → vehicle | subject `VEHICLE` |
| `staff_id` | `bigint` | có | FK → staff(user_id) | subject `STAFF` |
| `party_id` | `bigint` | có | FK → party | subject `PARTY` |

Ràng buộc bảng:
```sql
CHECK (num_nonnulls(horse_id, booking_id, booking_horse_id, horse_leg_id, vehicle_trip_id,
                    horse_insurance_id, incident_id, cost_item_id, handover_id,
                    vehicle_id, staff_id, party_id) = 1)
CHECK (status NOT IN ('APPROVED','REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
CHECK (status <> 'REJECTED' OR review_note IS NOT NULL)
```

Index: mỗi cột chủ sở hữu một partial index, ví dụ `CREATE INDEX ix_document_booking ON document(booking_id) WHERE booking_id IS NOT NULL`. Thêm index cho `document_type_id` và `requirement_id`.

---

## Nhóm 15. Hỗ trợ

### 15.1 `notification`: nội dung thông báo

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | có | FK → booking | |
| `title` | `varchar(200)` | | | |
| `message` | `text` | | | |
| `link` | `varchar(500)` | có | | Đường dẫn trong ứng dụng |
| `created_at` | `timestamptz` | | default `now()` | |

Index: `booking_id`.

### 15.2 `notification_recipient`: ai nhận thông báo, đã đọc chưa

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `notification_id` | `bigint` | | PK, FK → notification, CASCADE | |
| `user_id` | `bigint` | | PK, FK → app_user, CASCADE | |
| `read_at` | `timestamptz` | có | | NULL = chưa đọc |

Index: `(user_id, read_at)`, dùng để đếm số thông báo chưa đọc.

### 15.3 `audit_log`: nhật ký kiểm toán

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `actor_user_id` | `bigint` | có | FK → app_user | NULL = hệ thống tự chạy (scheduler) |
| `action` | `varchar(50)` | | | Ví dụ `UPDATE_PRICE_LIST`, `CONFIRM_INSURANCE` |
| `target_table` | `varchar(50)` | | | |
| `target_id` | `bigint` | | | Không đặt FK vì trỏ tới nhiều bảng khác nhau |
| `old_data` | `jsonb` | có | | Bản chụp kiểm toán |
| `new_data` | `jsonb` | có | | |
| `created_at` | `timestamptz` | | default `now()` | |

Index: `(target_table, target_id)`, `actor_user_id`, `created_at`.

### 15.4 `system_config`: tham số kỹ thuật

Chỉ chứa tham số **không phải giá tiền**, ví dụ thời gian chờ giao (phút), số giờ ân hạn, hạn hiệu lực OTP. Giá cước và tỷ lệ đã chuyển sang `price_list`/`price_item`.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `key` | `varchar(100)` | | PK | Ví dụ `HANDOVER_WAIT_MINUTES` |
| `value` | `varchar(500)` | | | |
| `unit` | `varchar(30)` | có | | `MINUTE`, `HOUR`, `COUNT` |
| `config_group` | `varchar(50)` | | | |
| `description` | `text` | có | | |
| `updated_by` | `bigint` | có | FK → staff(user_id) | |
| `updated_at` | `timestamptz` | | default `now()` | |

### 15.5 `route_cache`: bộ đệm kết quả tính lộ trình

Không phải dữ liệu nghiệp vụ. Có thể xóa toàn bộ bất kỳ lúc nào.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `cache_key` | `varchar(200)` | | PK | Hash của điểm đi, điểm đến và tham số |
| `payload` | `text` | | | Kết quả trả về từ dịch vụ bản đồ |
| `created_at` | `timestamptz` | | default `now()` | Dọn bản ghi cũ theo cột này |

Index: `created_at`.

---

## View thay cho cột dẫn xuất

### `v_quotation_total`: tổng tiền của báo giá

```sql
CREATE VIEW v_quotation_total AS
SELECT q.id                                             AS quotation_id,
       q.booking_id,
       q.currency,
       COALESCE(SUM(l.amount), 0)                       AS total_amount,
       COALESCE(SUM(l.amount) FILTER (WHERE l.is_pass_through), 0) AS pass_through_amount,
       ROUND(COALESCE(SUM(l.amount), 0) * q.deposit_rate, 2)        AS deposit_amount
FROM quotation q
LEFT JOIN quotation_line l ON l.quotation_id = q.id
GROUP BY q.id;
```

### `v_booking_paid`: đơn đã trả bao nhiêu, còn phải trả bao nhiêu

```sql
CREATE VIEW v_booking_paid AS
WITH paid AS (
    SELECT booking_id,
           SUM(CASE WHEN kind = 'REFUND' THEN -amount ELSE amount END) AS paid_amount
    FROM payment
    WHERE status = 'PAID'
    GROUP BY booking_id
), extra AS (
    SELECT booking_id, SUM(amount) AS extra_amount
    FROM cost_item
    WHERE payer <> 'COMPANY' AND approved_at IS NOT NULL
    GROUP BY booking_id
)
SELECT b.id                                              AS booking_id,
       t.total_amount,
       COALESCE(e.extra_amount, 0)                       AS extra_amount,
       COALESCE(p.paid_amount, 0)                        AS paid_amount,
       COALESCE(t.total_amount, 0) + COALESCE(e.extra_amount, 0)
         - COALESCE(p.paid_amount, 0)                    AS outstanding_amount
FROM booking b
LEFT JOIN quotation q        ON q.booking_id = b.id AND q.status = 'ACCEPTED'
LEFT JOIN v_quotation_total t ON t.quotation_id = q.id
LEFT JOIN paid p             ON p.booking_id = b.id
LEFT JOIN extra e            ON e.booking_id = b.id;
```

### `v_departure_capacity`: chuyến còn bao nhiêu khoang

```sql
CREATE VIEW v_departure_capacity AS
SELECT d.id                                   AS departure_id,
       d.capacity_stalls,
       COUNT(bh.id)                           AS booked_stalls,
       d.capacity_stalls - COUNT(bh.id)       AS available_stalls
FROM departure d
LEFT JOIN booking b        ON b.departure_id = d.id
                          AND b.status NOT IN ('CANCELLED', 'REJECTED', 'QUOTE_EXPIRED')
LEFT JOIN booking_horse bh ON bh.booking_id = b.id
                          AND bh.status NOT IN ('CANCELLED', 'EXCLUDED')
GROUP BY d.id;
```

Khi khách đặt chỗ, service phải khóa dòng `departure` (`SELECT ... FOR UPDATE`) trước khi đọc view này. Nếu không, hai đơn đặt cùng lúc có thể cùng lấy khoang cuối.

### `v_horse_current_custody`: ngựa đang ở trong tay ai

```sql
CREATE VIEW v_horse_current_custody AS
SELECT DISTINCT ON (hl.booking_horse_id)
       hl.booking_horse_id,
       h.id            AS handover_id,
       h.kind,
       h.to_party_id   AS holder_party_id,     -- NULL = công ty đang giữ
       h.trip_stop_id,
       h.horse_stay_id,
       h.handed_at
FROM handover_horse hh
JOIN handover  h  ON h.id  = hh.handover_id AND h.status = 'DONE'
JOIN horse_leg hl ON hl.id = hh.horse_leg_id
ORDER BY hl.booking_horse_id, h.handed_at DESC;
```

### `v_booking_document_status`: đơn đã đủ giấy bắt buộc chưa

Cách tính:
1. Xác định nước đối tác của đơn. Với EXPORT là `country_code` của `booking_address` DELIVERY, với IMPORT là của PICKUP, với INTERPROVINCE là `VN`.
2. Tìm `country_rule` cùng nước, có `direction = booking_type` và có thời hạn hiệu lực chứa `requested_pickup_date`.
3. Lấy các `document_requirement` có `is_mandatory` của quy định đó.
4. Với mỗi yêu cầu:
   - nếu `document_type.subject` là `HORSE` hoặc `BOOKING_HORSE` thì **mỗi** `booking_horse` ACTIVE phải có một `document` APPROVED, còn hạn, có `requirement_id` này;
   - nếu là `BOOKING` thì chỉ cần một `document` cho cả đơn.
5. Kết quả mỗi dòng gồm `booking_id`, `requirement_id`, `booking_horse_id` (NULL với giấy của đơn) và `is_satisfied`.

Nên viết view này trong một migration riêng và kiểm thử với dữ liệu mẫu, vì đây là view phức tạp nhất.

---

## Ràng buộc phải kiểm ở service

Các quy tắc dưới đây cần so dữ liệu ở nhiều bảng nên CHECK không làm được. Đặt chúng trong lớp policy hoặc service, theo `AGENTS.md`.

| # | Quy tắc | Bảng liên quan |
|---|---|---|
| 1 | `location` được tham chiếu phải đúng `type`: `hub_service_area`, `staff.home_hub_id`, `vehicle.home_hub_id`, `route.origin_hub_id` là HUB. `document_requirement.gate_location_id` là BORDER_GATE | location |
| 2 | `departure.route_id` phải bằng `departure_schedule.route_id` khi có `schedule_id` | departure |
| 3 | Chỉ nhận đặt chỗ khi `departure.status = OPEN`, `now() < booking_cutoff_at` và còn đủ khoang | booking, v_departure_capacity |
| 4 | `booking_cutoff_at` phải đủ xa: lấy `permit_lead_days`, `cert_lead_days` và `quarantine_days` từ `country_rule` | departure, country_rule |
| 5 | Tổng khoang của xe ≤ `vehicle_class.max_stalls` | vehicle |
| 6 | Các `trip_stop` của một lượt LINEHAUL phải chép từ `route_stop` của đúng tuyến của `departure` | trip_stop |
| 7 | Chuỗi `horse_leg` của một con ngựa phải liền mạch: `to_*` của chặng n bằng `from_*` của chặng n+1 | horse_leg |
| 8 | `board_stop_id`/`alight_stop_id` phải thuộc đúng `vehicle_trip_id` của chặng, và `board.seq < alight.seq` | horse_leg, trip_stop |
| 9 | Tại một thời điểm, số ngựa trên xe ≤ số khoang của xe, theo đúng loại khoang | horse_leg, vehicle |
| 10 | Một nhân viên hoặc một xe không được nằm trong hai lượt chạy trùng thời gian | crew_assignment, vehicle_trip |
| 11 | Cột chủ sở hữu được điền trong `document` phải khớp `document_type.subject` | document |
| 12 | `document.requirement_id` phải cùng `document_type_id` với yêu cầu | document |
| 13 | `incident_plan.handler_party_id` phải có `party_role` phù hợp với `resolution_type`, ví dụ VET_TREATMENT thì bên xử lý có vai trò VET | incident_plan |
| 14 | `horse_insurance` chỉ chuyển sang REVISION_REQUESTED khi `option = PERSONAL` | horse_insurance |
| 15 | `booking_horse_service.service_id` phải là dịch vụ có `layer = ADD_ON` | booking_horse_service |
| 16 | `booking` chỉ chuyển sang DELIVERED khi mọi `booking_horse` ACTIVE đã có `horse_leg` stage DELIVERY hoặc DIRECT ở trạng thái COMPLETED | booking, horse_leg |
| 17 | Đơn giá trong `quotation_line` phải lấy từ `price_item` thuộc đúng `quotation.price_list_id` | quotation_line |
| 18 | Mỗi lần đổi `booking.status` phải ghi một dòng `booking_status_history`, trong cùng transaction | booking |
| 19 | Chọn cách chạy theo hub, không theo `booking_type`: hub đón trùng hub giao thì chạy thẳng (`DIRECT`, `departure_id` NULL); khác hub thì đi trục (bắt buộc có `departure_id`, `route` nối hai hub hoặc nối hub với cửa khẩu) | booking, hub_service_area, route |
| 20 | `vehicle_trip.start_location_id` phải bằng `end_location_id` của lượt chạy liền trước của cùng xe. Xe tới hub vệ tinh thì phải có lượt chạy về trụ sở, ví dụ chuyến trục chiều ngược | vehicle_trip |
| 21 | Đợt này ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến. Vì vậy `v_departure_capacity` đếm theo cả chuyến là đúng | horse_leg, trip_stop, departure |
