# Đặc tả cơ sở dữ liệu: 59 bảng

> Tài liệu dùng để tạo DB (viết Flyway migration). Bản này thay cho mô hình 64 bảng trong
> `ERD_ENTITY_MERMAID.md`, `ERD_ALL_IN_ONE.md`, `ERD_ENTITY_BOXES.md` (các file đó chưa cập nhật).
> Hệ quản trị: PostgreSQL 15 trở lên.

> Migration `V202611231300__sync_names.sql` đồng bộ tên bảng, cột và các đối tượng liên quan.

## Mục lục

0. [Quy ước chung](#0-quy-ước-chung)
1. [Thay đổi so với bản 64 bảng](#thay-đổi-so-với-bản-64-bảng)
2. [Danh sách 59 bảng](#danh-sách-59-bảng)
3. [Thứ tự tạo bảng](#thứ-tự-tạo-bảng)
4. Chi tiết từng nhóm: [1](#nhóm-1-danh-mục-địa-lý) · [2](#nhóm-2-tài-khoản-nhân-sự-các-bên) · [3](#nhóm-3-ngựa) · [4](#nhóm-4-mạng-lưới-và-lịch-chạy) · [5](#nhóm-5-xe) · [6](#nhóm-6-dịch-vụ-và-bảng-giá) · [7](#nhóm-7-quy-định-và-giấy-tờ-yêu-cầu) · [8](#nhóm-8-đơn-hàng) · [9](#nhóm-9-vận-hành) · [10](#nhóm-10-chuỗi-giao-nhận) · [11](#nhóm-11-bảo-hiểm) · [12](#nhóm-12-báo-giá-và-thanh-toán) · [13](#nhóm-13-sự-cố-và-chi-phí) · [14](#nhóm-14-chứng-từ) · [15](#nhóm-15-hỗ-trợ)
5. [Dữ liệu dẫn xuất](#dữ-liệu-dẫn-xuất)
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
| Mã tiền tệ | `char(3)` | ISO 4217, `CHECK (currency_code ~ '^[A-Z]{3}$')` |
| Mã quốc gia | `char(2)` | ISO 3166-1 alpha-2 |
| Thời điểm | `timestamptz` | Lưu UTC |
| Ngày | `date` | |
| Giờ trong ngày | `time` | |
| Giá trị liệt kê (enum) | `varchar(30)` + `CHECK (col IN (...))` | Danh mục admin tự thêm được thì làm bảng riêng |

### Đặt tên

- Bảng và cột dùng `snake_case`, tên bảng số ít (`booking`, không phải `bookings`).
- Mọi định danh (bảng, cột, enum, class, biến, endpoint) dùng **tiếng Anh**. Chữ hiển thị cho người dùng đi qua i18n.
- **N1** Không viết tắt, trừ các từ phổ biến: `id`, `url`, `otp`, `ip`, `km`, `kg`.
- **N2** Số hiệu dùng hậu tố `_number`.
- **N3** Thời điểm dùng hậu tố `_at`, ngày dùng `_date`. Riêng `valid_from`/`valid_to` giữ nguyên.
- **N4** Cột boolean bắt đầu bằng `is_` hoặc `has_`.
- **N5** Khóa ngoại đặt theo mẫu `<vai trò>_<bảng đích>_id`, bỏ vai trò khi chỉ có một FK tới bảng đó. FK trỏ tới cột mã: `<bảng>_code`. Cột người thao tác: `<động từ>_by`.
- **N6** Cột phân loại đặt theo mẫu `<x>_type`. Không dùng `kind`, `type`, `option`, `key`, `value`, `role` đứng một mình.
- **N7** Cột đếm dùng hậu tố `_count`, cột sức chứa dùng hậu tố `_capacity`.
- **N8** Mã tiền tệ là `currency_code`, hoặc có tiền tố và hậu tố `_currency_code`.
- **N9** Tên bảng không trùng class hay annotation của framework.
- **N10** Cột có đơn vị thì ghi đơn vị trong tên.
- Tên ràng buộc: `pk_<bảng>`, `fk_<bảng>_<cột>`, `uk_<bảng>_<cột>`, `ck_<bảng>_<ý nghĩa>`, `ex_<bảng>_<ý nghĩa>` (EXCLUDE), `ix_<bảng>_<cột>`.

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
2. **Không có view chứa logic nghiệp vụ.** Cột dẫn xuất chỉ được lưu khi là snapshot của chứng từ đã chốt hoặc là bộ đếm có khóa bảo vệ, và chỉ do một service ghi. Các giá trị khác tính trong code (mục 5).
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
| 1 | Bỏ `terms_version`. `terms_acceptance` lưu thẳng `terms_type` và `terms_version` | Bản điều khoản chỉ cần mã và số phiên bản. Nội dung lưu thành file theo đường dẫn cố định |
| 2 | Bỏ `insurance_revision`. Thêm `revision_reason`, `revision_requested_by`, `revision_requested_at` vào `horse_insurance`. Thêm trạng thái `REVISION_REQUESTED` | Mỗi lúc chỉ có một yêu cầu sửa đang mở. Lịch sử sửa đổi đã có trong `audit_log` |
| 3 | Gộp `document_link` vào `document`: chủ sở hữu là các cột FK với `CHECK num_nonnulls(...) = 1`, thêm `document_requirement_id` | Mỗi chứng từ thuộc đúng một đối tượng. Bảng nối là thừa |
| 4 | Bỏ `incident_party` | Bên xử lý sự cố là `incident_plan.handler_party_id`, vai trò của bên đó lấy từ `party_role` |
| 5 | Bỏ `booking_review` | Trạng thái duyệt đã nằm ở `document.status`, `horse_insurance.status`, và việc đơn đã có `departure`/`horse_leg` |
| 6 | Bỏ vai trò `PICKUP_CONTACT` khỏi `booking_party` | Người liên hệ tại chỗ đã nằm ở `booking_address.contact_name/contact_phone` |
| 7 | `booking.status` rút từ 27 xuống 12 trạng thái | Chỉ giữ các mốc thương mại. Trạng thái vận hành lấy từ `horse_leg`, `incident` và `handover` |
| 8 | `country_rule.direction` còn INTERPROVINCE, EXPORT, IMPORT. Bỏ REIMPORT | Đơn hồi hương là đơn IMPORT có `original_booking_id` |
| 9 | `country_rule`: `*_lead_workdays` đổi thành `*_lead_days` (ngày lịch). Thêm EXCLUDE chống trùng thời hạn hiệu lực | Không cần bảng ngày lễ |
| 10 | `document_requirement`: bỏ `status` và `per_horse`. Thêm UK `NULLS NOT DISTINCT` | `status` trùng với thời hạn hiệu lực của `country_rule`. `per_horse` suy ra được từ `document_type.owner_type` |
| 11 | Bỏ `handover.signed_document_id` và `cost_item.invoice_document_id` | Trùng với `document.handover_id` và `document.cost_item_id`, và tạo vòng FK |
| 12 | `document_type.owner_type` mở rộng để khớp với từng cột chủ sở hữu trong `document` | Mỗi giá trị `owner_type` ứng với đúng một cột FK |
| 13 | Thêm `DIRECT` vào `vehicle_trip.trip_type`, `horse_leg.leg_type`, `billable_service.layer` và `quotation_line.line_type` | Đơn có hub đón trùng hub giao (LOCAL hoặc liên tỉnh gần) chạy thẳng từ địa chỉ tới địa chỉ, không qua trục chính |
| 14 | `hub_service_area`: thêm `UNIQUE (province_code)` | Mỗi tỉnh thuộc đúng một hub, để tra được hub đón và hub giao |
| 15 | Có **trục nội địa** (ví dụ HCM ⇄ Đà Nẵng) với **hub vệ tinh** | Hub vệ tinh là location loại HUB, có vùng phục vụ nhưng không có xe hay nhân viên trực thuộc. `vehicle.home_hub_location_id` và `staff.home_hub_location_id` vẫn là trụ sở |
| 17 | `route.origin_hub_id` đổi thành `origin_location_id`, cho phép điểm đầu là cửa khẩu (migration `V202611231100`) | Đơn IMPORT đi tuyến định kỳ từ cửa khẩu về hub, đối xứng với chiều xuất |
| 18 | Bỏ `user_role`, thêm `app_user.role_code` và `customer`; `party_address` thành `customer_address`; `party` chỉ chứa đối tác; sửa CHECK của `handover` (migration `V202611231200`) | Mỗi tài khoản một vai trò, hồ sơ khách và nhân viên đối xứng, FK chặn gán sai loại. Lý do ở `NGU_CANH_THIET_KE_LAI.md` mục 4 |
| 16 | Bỏ mọi view. Thêm cột dẫn xuất `quotation.total_amount`, `pass_through_amount`, `deposit_amount` và `departure.booked_stall_count` | Logic nghiệp vụ nằm hoàn toàn trong code. Chỉ lưu cột khi giá trị đã chốt (báo giá) hoặc có khóa bảo vệ (khoang). Đã trả và còn nợ tính trong code vì đổi liên tục |
| 19 | Bỏ `country_rule.quarantine_days`, thay bằng loại giấy `QUARANTINE_CLEARANCE` theo từng con | Công ty không bán chỗ cách ly, chỉ chở ngựa đủ điều kiện. Điều kiện thể hiện bằng giấy |
| 20 | Bỏ `vehicle.registration_expiry_date` | Quản lý xe chỉ gồm thuộc tính và trạng thái, không quản lý giấy tờ xe |
| 21 | Thêm `location.counterpart_country_code`, `booking.pickup_method`, `delivery_method`, `horse_health_log.water_intake`, `feed_intake` (**chờ migration** cho hai cột của bảng `horse_health_log`) | Cửa khẩu phải khớp nước đối tác; khách chọn công ty chở hoặc tự chở tới hub; theo dõi ăn uống từng con |
| 22 | Khoang rộng chiếm **2 chỗ** khi tính sức chứa, không đổi cột | Xe dùng vách ngăn di động. Trước đó mỗi ngựa tính 1 chỗ nên bán quá được khoang rộng |
| 23 | Ngừng dùng các giá trị `QUARANTINE`, `QUARANTINE_FARM` và cột `quarantine_result`; chưa xóa khỏi DB | Xóa khi luồng B đồng ý |
| 24 | Thêm `SHIFT` vào `handover.handover_type` (**chờ migration**, luồng B làm) | Đổi người phụ trách ngựa khi ngựa không đổi chỗ: đổi ca ở hub, thay hộ tống giữa đường, đổi xe do sự cố |

Các dòng ghi **chờ migration** là lược đồ đích đã chốt ngày 23/11/2026. Migration và entity làm ở task A6b (`KE_HOACH_TRIEN_KHAI.md`), trước đó DB vẫn theo bản cũ.

---

## Danh sách 59 bảng

| Nhóm | Bảng | Số bảng |
|---|---|---|
| 1. Địa lý | `country`, `province`, `location`, `hub_service_area` | 4 |
| 2. Tài khoản, các bên | `app_user`, `role`, `customer`, `customer_address`, `staff`, `party`, `party_role`, `otp` | 8 |
| 3. Ngựa | `horse_type`, `horse` | 2 |
| 4. Mạng lưới, lịch chạy | `route`, `route_stop`, `departure_schedule`, `departure_schedule_day`, `departure` | 5 |
| 5. Xe | `vehicle_class`, `stall_type`, `vehicle` | 3 |
| 6. Dịch vụ, bảng giá | `billable_service`, `price_list`, `price_item` | 3 |
| 7. Quy định, giấy tờ yêu cầu | `country_rule`, `document_type`, `document_requirement` | 3 |
| 8. Đơn hàng | `booking`, `booking_party`, `booking_address`, `booking_horse`, `booking_horse_service`, `booking_status_history`, `terms_acceptance`, `booking_rating` | 8 |
| 9. Vận hành | `vehicle_trip`, `trip_stop`, `crew_assignment`, `horse_leg`, `horse_stay`, `horse_health_log` | 6 |
| 10. Giao nhận | `handover`, `handover_horse`, `handover_document` | 3 |
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
| 1 | `country`, `role`, `horse_type`, `vehicle_class`, `stall_type`, `billable_service`, `price_list`, `document_type`, `route_cache` |
| 2 | `app_user`, `province`, `country_rule` |
| 3 | `customer`, `otp`, `location`, `party` |
| 4 | `hub_service_area`, `staff`, `party_role`, `customer_address`, `horse`, `route`, `vehicle`, `document_requirement` |
| 5 | `route_stop`, `departure_schedule`, `price_item`, `system_config` |
| 6 | `departure_schedule_day`, `departure` |
| 7 | `booking` |
| 8 | `booking_party`, `booking_address`, `booking_horse`, `booking_status_history`, `terms_acceptance`, `booking_rating`, `vehicle_trip`, `quotation`, `notification`, `audit_log` |
| 9 | `booking_horse_service`, `trip_stop`, `crew_assignment`, `horse_stay`, `horse_insurance`, `payment`, `notification_recipient` |
| 10 | `horse_leg`, `handover`, `payment_transaction` |
| 11 | `horse_health_log`, `handover_horse`, `quotation_line`, `incident` |
| 12 | `incident_plan`, `cost_item` |
| 13 | `document` |
| 14 | `handover_document` (cần `document`) |

---

## Nhóm 1. Danh mục địa lý

### 1.1 `country`: quốc gia

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `char(2)` | | PK | Mã ISO: VN, KH, TH |
| `name` | `varchar(200)` | | | Tên quốc gia |
| `default_currency_code` | `char(3)` | | CK định dạng ISO | Tiền tệ mặc định |

### 1.2 `province`: tỉnh/thành

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `varchar(10)` | | PK | Mã tỉnh, ví dụ `VN-SG`, `KH-12` |
| `country_code` | `char(2)` | | FK → country | |
| `name` | `varchar(200)` | | | |
| `latitude` | `numeric(9,6)` | | CHECK `-90..90` | Trung tâm tỉnh: mở bản đồ và kiểm vị trí ghim |
| `longitude` | `numeric(9,6)` | | CHECK `-180..180` | |

Index: `ix_province_country_code`.

### 1.3 `location`: điểm thuộc mạng lưới của công ty

Gồm hub, trạm nghỉ, cửa khẩu, chuồng và trường đua cố định (`QUARANTINE_FARM` ngừng dùng). **Không chứa** địa chỉ đón hoặc giao của khách: các địa chỉ đó nằm ở `booking_address`.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `HUB-HCM`, `GATE-MOCBAI` |
| `name` | `varchar(200)` | | | |
| `location_type` | `varchar(30)` | | CK `HUB, QUARANTINE_FARM, REST_STOP, BORDER_GATE, STABLE, RACETRACK` | Loại điểm |
| `country_code` | `char(2)` | | FK → country | |
| `province_code` | `varchar(10)` | có | FK → province | |
| `address` | `varchar(500)` | | | |
| `latitude` | `numeric(9,6)` | có | CK −90..90 | |
| `longitude` | `numeric(9,6)` | có | CK −180..180 | |
| `is_headquarters` | `boolean` | | mặc định `false` | Đánh dấu hub trụ sở quản lý xe |
| `opening_time` | `time` | có | | Giờ mở cửa |
| `closing_time` | `time` | có | | Giờ đóng cửa |
| `contact_phone` | `varchar(20)` | có | | |
| `counterpart_country_code` | `char(2)` | có | FK → country | Nước ở phía bên kia cửa khẩu, ví dụ `GATE-MOCBAI` là `KH`, `GATE-LAOBAO` là `LA` |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Ràng buộc bảng:
- `CHECK ((latitude IS NULL) = (longitude IS NULL))`
- `CHECK (counterpart_country_code IS NULL OR location_type = 'BORDER_GATE')`. Khi tạo hoặc cập nhật cửa khẩu, service yêu cầu mã nước đối diện khác `country_code`.

Index: `country_code`, `province_code`, `location_type`, `counterpart_country_code`.

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
| `is_email_verified` | `boolean` | | default `false` | |
| `role_code` | `varchar(30)` | | FK → role | Mỗi tài khoản đúng một vai trò |
| `preferred_language` | `varchar(5)` | | default `'vi'`, CK `vi, en` | |
| `status` | `varchar(30)` | | CK `ACTIVE, LOCKED, DISABLED` | |
| `created_at` | `timestamptz` | | default `now()` | |

Index: `role_code`.

### 2.2 `role`: vai trò hệ thống

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `varchar(30)` | | PK | `CUSTOMER, ADMIN, MANAGER, COORDINATOR, SPECIALIST, DRIVER, ESCORT` |
| `name` | `varchar(200)` | | | |

### 2.3 `customer`: hồ sơ khách (1–1 với app_user)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `user_id` | `bigint` | | PK, FK → app_user | Kế thừa khóa của tài khoản |
| `customer_type` | `varchar(30)` | | CK `PERSON, ORGANIZATION` | Cá nhân hoặc tổ chức |
| `full_name` | `varchar(200)` | | | Tên người hoặc tổ chức |
| `phone` | `varchar(20)` | có | | |
| `address` | `varchar(500)` | có | | Địa chỉ liên lạc hoặc xuất hóa đơn |
| `country_code` | `char(2)` | | FK → country | |
| `identity_document_type` | `varchar(30)` | có | CK `CCCD, PASSPORT, BUSINESS_REG` | |
| `identity_document_number` | `varchar(50)` | có | | |
| `identity_document_country_code` | `char(2)` | có | FK → country | Nước cấp giấy tờ |
| `tax_code` | `varchar(30)` | có | | Mã số thuế, với tổ chức |

Ràng buộc bảng:
- `CHECK (num_nonnulls(identity_document_type, identity_document_number, identity_document_country_code) IN (0, 3))`: giấy tờ hoặc khai đủ cả 3 cột, hoặc bỏ trống cả 3.
- `UNIQUE (identity_document_type, identity_document_number, identity_document_country_code)`.

Index: `country_code`, `identity_document_country_code`.
Không có `status` và `email`: lấy từ `app_user`.

### 2.4 `staff`: hồ sơ nhân viên (1–1 với app_user)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `user_id` | `bigint` | | PK, FK → app_user | Kế thừa khóa của tài khoản |
| `staff_code` | `varchar(30)` | | UK | |
| `full_name` | `varchar(200)` | | | |
| `phone` | `varchar(20)` | | | |
| `hire_date` | `date` | | | |
| `employment_status` | `varchar(30)` | | CK `ACTIVE, ON_LEAVE, TERMINATED` | |
| `home_hub_location_id` | `bigint` | | FK → location | Hub trực thuộc. Đợt này mọi nhân viên thuộc trụ sở. Hub vệ tinh không có nhân viên trực thuộc |
| `driver_license_class` | `varchar(10)` | có | | Chỉ có với tài xế |
| `driver_license_expiry_date` | `date` | có | | |

Ràng buộc bảng: `CHECK ((driver_license_class IS NULL) = (driver_license_expiry_date IS NULL))`.
Index: `home_hub_location_id`.

### 2.5 `party`: đối tác (cá nhân hoặc tổ chức)

Đại lý nước ngoài, trại cách ly, chuồng gửi, bác sĩ thú y, đội cứu hộ, công ty bảo hiểm. Đối tác không có tài khoản. Khách nằm ở `customer`; người gửi và người nhận của đơn nằm ở `booking_party`.

Bốn bảng trả lời bốn câu hỏi khác nhau:

| Bảng | Câu hỏi |
|---|---|
| `customer` | Khách **là ai** |
| `party` | Đối tác **là ai** |
| `party_role` | Đối tác **là gì với công ty** (vai trò lâu dài) |
| `booking_party` | Họ **làm gì trong một đơn** (bản chụp) |

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `party_type` | `varchar(30)` | | CK `PERSON, ORGANIZATION` | |
| `display_name` | `varchar(200)` | | | |
| `phone` | `varchar(20)` | có | | |
| `email` | `varchar(254)` | có | | |
| `address` | `varchar(500)` | có | | Địa chỉ liên lạc hoặc xuất hóa đơn |
| `country_code` | `char(2)` | | FK → country | |
| `tax_code` | `varchar(30)` | có | | Mã số thuế, với tổ chức |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Index: `country_code`.

### 2.6 `party_role`: vai trò của một đối tác

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `party_id` | `bigint` | | PK, FK → party, CASCADE | |
| `role_type` | `varchar(30)` | | PK, CK `DEST_AGENT, QUARANTINE_FARM, STABLE, VET, RESCUE, INSURER` | `QUARANTINE_FARM` ngừng dùng |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `valid_to >= valid_from` | |

Index: `role_type`, dùng để tra danh sách bác sĩ thú y, đội cứu hộ…

### 2.7 `customer_address`: sổ địa chỉ của khách

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `customer_id` | `bigint` | | FK → customer(user_id), CASCADE | |
| `label` | `varchar(100)` | | | Ví dụ "Trang trại Bình Dương" |
| `address_line` | `varchar(500)` | | | |
| `ward` | `varchar(100)` | có | | |
| `province_code` | `varchar(10)` | | FK → province | |
| `country_code` | `char(2)` | | FK → country | |
| `latitude` | `numeric(9,6)` | có | | |
| `longitude` | `numeric(9,6)` | có | | |
| `is_default` | `boolean` | | default `false` | |

Ràng buộc bảng: mỗi khách chỉ có một địa chỉ mặc định, `CREATE UNIQUE INDEX uk_customer_address_default ON customer_address(customer_id) WHERE is_default`.
Index: `customer_id`, `province_code`, `country_code`.

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
| `owner_customer_id` | `bigint` | | FK → customer(user_id) | Chủ ngựa |
| `name` | `varchar(100)` | | | |
| `microchip` | `varchar(30)` | có | UK | Dùng để quét chip khi giao nhận |
| `passport_number` | `varchar(50)` | có | UK | |
| `sex` | `varchar(30)` | | CK `STALLION, MARE, GELDING` | |
| `birth_year` | `smallint` | có | CK `1980..2100` | |
| `color` | `varchar(50)` | có | | |
| `breed` | `varchar(100)` | có | | |
| `sire` | `varchar(100)` | có | | Tên ngựa bố |
| `dam` | `varchar(100)` | có | | Tên ngựa mẹ |
| `horse_type_id` | `bigint` | | FK → horse_type | |
| `identification_marks` | `text` | có | | Đặc điểm nhận dạng |
| `weight_kg` | `numeric(6,1)` | có | CK `> 0` | |
| `declared_value` | `numeric(15,2)` | có | CK `>= 0` | Giá trị khai báo, dùng cho bảo hiểm |
| `declared_currency_code` | `char(3)` | có | | |
| `status` | `varchar(30)` | | CK `ACTIVE, RETIRED, DECEASED` | Trạng thái của hồ sơ. Ngựa đang ở đâu tính từ `handover` mới nhất (mục 5) |
| `created_at` | `timestamptz` | | default `now()` | |
| `updated_at` | `timestamptz` | | | |

Ràng buộc bảng: `CHECK ((declared_value IS NULL) = (declared_currency_code IS NULL))`.
Index: `owner_customer_id`, `horse_type_id`.

---

## Nhóm 4. Mạng lưới và lịch chạy

### 4.1 `route`: tuyến trục chính cố định

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `HCM-PNH` |
| `name` | `varchar(200)` | | | |
| `origin_location_id` | `bigint` | | FK → location | Điểm đầu: HUB, hoặc BORDER_GATE với tuyến chiều nhập |
| `destination_location_id` | `bigint` | | FK → location | Điểm cuối: hub hoặc cửa khẩu |
| `distance_km` | `numeric(8,2)` | | CK `> 0` | |
| `estimated_duration_minutes` | `int` | | CK `> 0` | |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Ràng buộc bảng: `CHECK (origin_location_id <> destination_location_id)`.
Index: `origin_location_id`, `destination_location_id`.

Loại điểm đầu, điểm cuối và điểm dừng được kiểm ở `RouteStopsPolicy` (cần so với bảng `location`).

### 4.2 `route_stop`: các điểm dừng trên tuyến

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `route_id` | `bigint` | | PK, FK → route, CASCADE | |
| `sequence_number` | `int` | | PK, CK `>= 1` | Thứ tự dừng |
| `location_id` | `bigint` | | FK → location | |
| `stop_type` | `varchar(30)` | | CK `HUB, REST, VITAL_CHECK, BORDER, QUARANTINE` | `QUARANTINE` ngừng dùng. Nghỉ đêm là `REST` có `dwell_minutes` đủ dài (quy tắc #31) |
| `arrival_offset_minutes` | `int` | | CK `>= 0` | Số phút tính từ lúc xuất phát |
| `dwell_minutes` | `int` | | CK `>= 0` | Số phút dừng tại điểm |

PK là `(route_id, sequence_number)` nên một tuyến có thể ghé cùng một điểm hai lần.
Index: `location_id`.

### 4.3 `departure_schedule`: lịch chạy định kỳ

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `route_id` | `bigint` | | FK → route | |
| `departure_time` | `time` | | | Giờ xuất phát |
| `booking_cutoff_hours` | `int` | | CK `> 0` | Chốt nhận đơn trước giờ xuất phát bao lâu |
| `stall_capacity` | `int` | | CK `> 0` | Số chỗ tối đa mỗi chuyến (khoang thường 1 chỗ, khoang rộng 2 chỗ) |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |
| `status` | `varchar(30)` | | CK `ACTIVE, PAUSED, ENDED` | |

Index: `route_id`.

### 4.4 `departure_schedule_day`: lịch chạy vào thứ mấy

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `departure_schedule_id` | `bigint` | | PK, FK → departure_schedule, CASCADE | |
| `day_of_week` | `smallint` | | PK, CK `1..7` | Theo ISO: 1 = thứ Hai, 7 = Chủ nhật |

### 4.5 `departure`: chuyến cụ thể theo ngày (khách đặt chỗ vào đây)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `departure_schedule_id` | `bigint` | có | FK → departure_schedule | NULL = chuyến đột xuất |
| `route_id` | `bigint` | | FK → route | Bắt buộc để chuyến đột xuất cũng biết tuyến |
| `departure_date` | `date` | | | |
| `planned_departure_at` | `timestamptz` | | | |
| `booking_cutoff_at` | `timestamptz` | | | |
| `stall_capacity` | `int` | | CK `> 0` | Snapshot từ lịch, sửa được cho từng chuyến |
| `booked_stall_count` | `int` | | default `0` | Dẫn xuất: số **chỗ** đã đặt (khoang thường 1, khoang rộng 2). Chỉ `DepartureService` ghi, khi đang giữ `FOR UPDATE` |
| `status` | `varchar(30)` | | CK `OPEN, CLOSED, IN_PROGRESS, COMPLETED, CANCELLED` | |
| `run_confirmed_at` | `timestamptz` | có | | Lúc quản lý xác nhận chạy. NULL = chưa xác nhận, điều phối chưa được lập lượt trục |
| `run_confirmed_by` | `bigint` | có | FK → app_user | Quản lý xác nhận |

Ràng buộc bảng:
- `CHECK ((run_confirmed_at IS NULL) = (run_confirmed_by IS NULL))`.
- `UNIQUE (departure_schedule_id, departure_date)`: mỗi lịch sinh tối đa một chuyến mỗi ngày. Chuyến đột xuất có `departure_schedule_id` NULL nên không bị giới hạn.
- `CHECK (booking_cutoff_at < planned_departure_at)`.
- `CHECK (booked_stall_count BETWEEN 0 AND stall_capacity)`: DB chặn bán quá chỗ.

Index: `route_id`, `(status, planned_departure_at)`, `run_confirmed_by`.

---

## Nhóm 5. Xe

### 5.1 `vehicle_class`: hạng xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | `LIGHT, MEDIUM, HEAVY` |
| `name` | `varchar(200)` | | | |
| `max_stall_count` | `int` | | CK `> 0` | |

### 5.2 `stall_type`: loại khoang

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `code` | `varchar(30)` | | PK | `STANDARD, WIDE` |
| `name` | `varchar(200)` | | | |

Khi tính sức chứa, `STANDARD` chiếm 1 chỗ, `WIDE` chiếm 2 chỗ.

### 5.3 `vehicle`: xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `license_plate` | `varchar(20)` | | UK | |
| `vehicle_class_id` | `bigint` | | FK → vehicle_class | |
| `standard_stall_count` | `int` | | CK `>= 0` | Số khoang thường ở bố trí mặc định. Chỉ dùng để tính tổng chỗ |
| `wide_stall_count` | `int` | | CK `>= 0` | Số khoang rộng ở bố trí mặc định. Chỉ dùng để tính tổng chỗ, không giới hạn số con khoang rộng và không cố định vị trí |
| `home_hub_location_id` | `bigint` | | FK → location | Hub gốc. Đợt này mọi xe thuộc trụ sở |
| `status` | `varchar(30)` | | CK `ACTIVE, MAINTENANCE, RETIRED` | Do quản trị viên đổi, ghi `audit_log` (quy tắc #36). Xe đang chạy hay rảnh suy ra từ `vehicle_trip` |

Vách ngăn **di động**: hai vị trí liền nhau bất kỳ ghép thành một khoang rộng. Cả xe là một dãy liền, đánh số từ 1 tới tổng chỗ.

Ràng buộc bảng: `CHECK (standard_stall_count + wide_stall_count > 0)`. Service kiểm tổng chỗ `standard_stall_count + 2 × wide_stall_count` ≤ `vehicle_class.max_stall_count` và yêu cầu hub gốc của xe là địa điểm được đánh dấu `is_headquarters`. Cờ này chỉ áp dụng cho `location_type = 'HUB'` và unique index cho phép tối đa một trụ sở. Hub gốc xác định đơn vị quản lý. Vị trí xe và điều kiện nhận lượt tiếp theo thuộc luồng vận hành.
Index: `vehicle_class_id`, `home_hub_location_id`.

---

## Nhóm 6. Dịch vụ và bảng giá

### 6.1 `billable_service`: danh mục dịch vụ tính tiền

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `LINEHAUL_STALL`, `PICKUP_KM`, `VET_CHECK` |
| `name` | `varchar(200)` | | | |
| `layer` | `varchar(30)` | | CK `LINEHAUL, PICKUP, DELIVERY, DIRECT, FUEL, STALL, QUARANTINE, ADD_ON, DOCS, SURCHARGE` | Nhóm cước. `DIRECT` = cước chạy thẳng theo km × hạng xe |
| `billing_unit` | `varchar(30)` | | CK `PER_STALL, PER_KM, PER_HORSE, PER_HORSE_DAY, PER_BOOKING` | Đơn vị tính |
| `is_pass_through` | `boolean` | | default `false` | Thu hộ, không tính biên lợi nhuận |
| `is_auto_applied` | `boolean` | | default `false` | Tự thêm vào báo giá, khách không phải chọn |
| `status` | `varchar(30)` | | CK `ACTIVE, INACTIVE` | |

Ý nghĩa của các dịch vụ đã chốt ngày 23/11/2026 (tên hiển thị đổi bằng migration, mã giữ nguyên):

| Mã | Tên | Cách áp dụng |
|---|---|---|
| `VET_CHECK` | Thú y tại chuồng: khám và lấy mẫu | Tự thêm cho mỗi con khi đơn bắt buộc có `LAB_TEST` do công ty nộp (`submitter_type = COMPANY`). Trường hợp khác khách chọn |
| `QUARANTINE_STAY` | Lưu chuồng theo ngày | Chỉ tính số ngày khách xin đón sớm hơn lịch đón chuẩn. Không phải phí cách ly |
| `FEEDING` | Cho ăn theo chỉ dẫn | Ngừng (`INACTIVE`). Ăn uống nằm trong cước |

### 6.2 `price_list`: bảng giá có hiệu lực

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `name` | `varchar(200)` | | | Ví dụ "Bảng giá 2027" |
| `currency_code` | `char(3)` | | | |
| `margin_rate` | `numeric(5,4)` | | CK `0..1` | Biên lợi nhuận |
| `fuel_buffer_rate` | `numeric(5,4)` | | CK `0..1` | Dự phòng biến động giá nhiên liệu |
| `deposit_rate` | `numeric(5,4)` | | CK `0..1` | Tỷ lệ đặt cọc |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |
| `status` | `varchar(30)` | | CK `DRAFT, ACTIVE, ARCHIVED` | |

Ràng buộc bảng: các bảng giá ACTIVE cùng tiền tệ không được trùng thời hạn hiệu lực.
```sql
EXCLUDE USING gist (currency_code WITH =, daterange(valid_from, valid_to, '[]') WITH &&)
  WHERE (status = 'ACTIVE')
```

### 6.3 `price_item`: đơn giá trong bảng giá

Mỗi dòng là một đơn giá của một dịch vụ, áp theo các điều kiện tùy chọn: tuyến, hạng xe, loại khoang, khoảng km.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `price_list_id` | `bigint` | | FK → price_list, CASCADE | |
| `billable_service_id` | `bigint` | | FK → billable_service | |
| `route_id` | `bigint` | có | FK → route | Giá riêng theo tuyến, dùng cho cước trục chính |
| `vehicle_class_id` | `bigint` | có | FK → vehicle_class | Giá theo hạng xe, dùng cho cước đón và giao |
| `stall_type_code` | `varchar(30)` | có | FK → stall_type | |
| `km_from` | `numeric(8,2)` | có | | Bậc km: từ |
| `km_to` | `numeric(8,2)` | có | | Bậc km: đến, NULL nếu không giới hạn trên |
| `unit_price` | `numeric(15,2)` | | CK `>= 0` | |

Ràng buộc bảng:
- `CHECK (km_to IS NULL OR km_from IS NOT NULL)`
- `CHECK (km_from IS NULL OR km_from < km_to)`
- `UNIQUE NULLS NOT DISTINCT (price_list_id, billable_service_id, route_id, vehicle_class_id, stall_type_code, km_from)`

Index: `billable_service_id`, `route_id`, `vehicle_class_id`.

Cách tra giá: chọn `price_list` ACTIVE có thời hạn hiệu lực chứa ngày báo giá, rồi lọc `price_item` theo `billable_service_id`. Điều kiện nào có giá trị thì phải khớp, điều kiện NULL nghĩa là áp cho mọi giá trị. Riêng `km_from`/`km_to` thì chọn bậc chứa số km theo cận dưới bao gồm và cận trên không bao gồm nếu có.

---

## Nhóm 7. Quy định và giấy tờ yêu cầu

### 7.1 `country_rule`: quy định theo nước và hướng đi

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `country_code` | `char(2)` | | FK → country | **Nước đối tác ngoài VN.** Với INTERPROVINCE dùng `VN` |
| `direction` | `varchar(30)` | | CK `INTERPROVINCE, EXPORT, IMPORT` | Cùng bộ giá trị với `booking.booking_type`, trừ LOCAL |
| `permit_lead_days` | `int` | | CK `>= 0` | Số ngày lịch cần để xin giấy phép |
| `certificate_lead_days` | `int` | | CK `>= 0` | Số ngày lịch cần để làm giấy chứng nhận |
| `valid_from` | `date` | | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |

Ràng buộc bảng: cùng nước và cùng hướng thì không được trùng thời hạn hiệu lực.
```sql
EXCLUDE USING gist (country_code WITH =, direction WITH =,
                    daterange(valid_from, valid_to, '[]') WITH &&)
```

Đơn LOCAL không cần tra quy định. `certificate_lead_days` gồm cả thời gian lấy mẫu, xét nghiệm và chuẩn bị giấy. Không có số ngày cách ly: ngựa phải đủ điều kiện trước khi đón, thể hiện bằng giấy.

### 7.2 `document_type`: loại giấy tờ

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `code` | `varchar(30)` | | UK | Ví dụ `HEALTH_CERT`, `EXPORT_PERMIT`, `HANDOVER_RECORD`, `TOLL_RECEIPT`, `REFUND_RECEIPT` (chủ sở hữu `BOOKING`) |
| `name` | `varchar(200)` | | | |
| `owner_type` | `varchar(30)` | | CK `HORSE, BOOKING, BOOKING_HORSE, HORSE_LEG, VEHICLE_TRIP, INSURANCE, INCIDENT, COST_ITEM, HANDOVER, VEHICLE, STAFF, PARTY, CUSTOMER` | Loại giấy này gắn với đối tượng nào. Mỗi giá trị ứng với đúng một cột chủ sở hữu trong `document` |

Giấy cấp **cho từng con ngựa** có `owner_type = HORSE` hoặc `BOOKING_HORSE`. Giấy cấp **cho cả đơn** có `owner_type = BOOKING`.

Loại giấy thêm ngày 23/11/2026, cùng `owner_type = BOOKING_HORSE`:
- `QUARANTINE_CLEARANCE`: xác nhận ngựa đạt điều kiện cách ly trước khi vận chuyển. Dùng cho EXPORT, `submitter_type = COMPANY` (chuyên viên tải bản do cơ quan thú y hoặc chủ ngựa cung cấp);
- `ORIGIN_QUARANTINE_CERT`: giấy kiểm dịch của nước xuất, dùng cho IMPORT, `submitter_type = AGENT`;
- `IMPORT_APPROVAL`: văn bản đồng ý và hướng dẫn kiểm dịch, dùng cho IMPORT;
- `IMPORT_TRANSPORT_CERT`: giấy vận chuyển động vật nhập khẩu (mẫu 14a, Điều 11 Thông tư 01/2026), cửa khẩu cấp khi ngựa tới, dùng cho IMPORT.

Loại giấy của xe và nhân viên (`VEHICLE_REGISTRATION`, `DRIVER_LICENSE`, `DISINFECTION_RECORD`) còn trong danh mục nhưng không có nghiệp vụ nào dùng.

### 7.3 `document_requirement`: theo quy định thì cần giấy gì

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `country_rule_id` | `bigint` | | FK → country_rule, CASCADE | |
| `document_type_id` | `bigint` | | FK → document_type | |
| `gate_location_id` | `bigint` | có | FK → location | Chỉ áp cho một cửa khẩu. NULL = mọi cửa khẩu |
| `submitter_type` | `varchar(30)` | | CK `CUSTOMER, CONSIGNEE, COMPANY, AGENT` | Ai chịu trách nhiệm nộp |
| `is_mandatory` | `boolean` | | default `true` | |

Ràng buộc bảng: `UNIQUE NULLS NOT DISTINCT (country_rule_id, document_type_id, gate_location_id)`.
Index: `document_type_id`, `gate_location_id`.

---

## Nhóm 8. Đơn hàng

### 8.1 `booking`: phần đầu đơn

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `collection_code` | `varchar(6)` | có | CK 6 chữ số | Mã nhận ngựa, sinh khi chuyển sang `IN_OPERATION`, chỉ trả cho chủ đơn |
| `collection_failed_attempts` | `integer` | | Default 0, CK ≥ 0 | Đếm lần nhập sai dưới khóa dòng `booking`. Giới hạn `COLLECTION_CODE_MAX_ATTEMPTS` |
| `collection_confirmed` | `boolean` | | Default false | Chủ đơn đã xác nhận cho phép giao mà không cần mã |
| `code` | `varchar(30)` | | UK | Mã đơn hiển thị cho khách |
| `customer_id` | `bigint` | | FK → customer(user_id) | Người đặt đơn |
| `booking_type` | `varchar(30)` | | CK `LOCAL, INTERPROVINCE, EXPORT, IMPORT` | |
| `departure_id` | `bigint` | có | FK → departure | Chuyến trục chính. NULL khi chưa gán chuyến, hoặc khi đơn chạy thẳng (hub đón trùng hub giao). Mỗi đơn có tối đa một chặng trục |
| `requested_pickup_date` | `date` | | | Ngày khách muốn đón |
| `pickup_method` | `varchar(30)` | | default `COMPANY`, CK `COMPANY, SELF` | Đầu đón: công ty tới đón, hoặc khách tự chở ngựa tới hub |
| `delivery_method` | `varchar(30)` | | default `COMPANY`, CK `COMPANY, SELF` | Đầu giao: công ty giao tận nơi, hoặc người nhận tới hub. `SELF` chỉ dùng cho đầu trong nước của đơn đi trục (kiểm ở service) |
| `waybill_number` | `varchar(30)` | có | UK | Số vận đơn, cấp sau khi khách đặt cọc |
| `delivery_confirmed_at` | `timestamptz` | có | | Thời điểm khách xác nhận đã nhận ngựa ổn. Hạn khiếu nại tính từ mốc `DELIVERED` trong `booking_status_history`. |
| `emergency_spending_limit` | `numeric(15,2)` | có | CK `>= 0` | Hạn mức chi khẩn cấp không cần hỏi khách |
| `original_booking_id` | `bigint` | có | FK → booking | Đơn này là chuyến hồi hương của đơn nào |
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
| `IN_OPERATION` | Ngựa đã được nhận, khách đã trả đủ và xe đã rời nơi nhận. Đơn đang vận hành. Chi tiết ngựa đang ở đâu xem `horse_leg`, `horse_stay` và `incident` |
| `DELIVERED` | Đã bàn giao mọi ngựa cho người nhận, gồm giao tận nơi hoặc ký trả tại hub, chờ quyết toán |
| `COMPLETED` | Đã quyết toán xong |
| `CANCELLED` / `REJECTED` | Bị hủy / bị từ chối |

Index: `customer_id`, `departure_id`, `original_booking_id`, `(status, created_at)`.

### 8.2 `booking_party`: các bên trong đơn (snapshot)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `booking_id` | `bigint` | | PK, FK → booking, CASCADE | |
| `booking_role` | `varchar(30)` | | PK, CK `PAYER, SHIPPER, CONSIGNEE, EMERGENCY_CONTACT` | |
| `full_name` | `varchar(200)` | | snapshot | |
| `phone` | `varchar(20)` | | snapshot | |
| `identity_document_type` | `varchar(30)` | có | snapshot | |
| `identity_document_number` | `varchar(50)` | có | snapshot | |
| `identity_document_country_code` | `char(2)` | có | snapshot | |
| `address` | `varchar(500)` | | snapshot | Địa chỉ liên lạc hoặc xuất hóa đơn, khác nơi đón ngựa |

### 8.3 `booking_address`: điểm đón và giao của đơn (snapshot)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | Là đích FK của `trip_stop` và `horse_leg` |
| `booking_id` | `bigint` | | FK → booking, CASCADE | |
| `address_type` | `varchar(30)` | | CK `PICKUP, DELIVERY` | |
| `address_line` | `varchar(500)` | | snapshot | |
| `province_code` | `varchar(10)` | | FK → province, snapshot | |
| `country_code` | `char(2)` | | FK → country, snapshot | |
| `latitude` | `numeric(9,6)` | | snapshot | Bắt buộc, để tính km và vẽ lộ trình |
| `longitude` | `numeric(9,6)` | | snapshot | |
| `contact_name` | `varchar(200)` | | snapshot | Người liên hệ tại chỗ |
| `contact_phone` | `varchar(20)` | | snapshot | |
| `access_note` | `text` | có | | Hướng dẫn vào cổng, chỗ đỗ xe |
| `location_id` | `bigint` | có | FK → location | Nếu điểm này trùng một location của mạng lưới, ví dụ trường đua |
| `source_customer_address_id` | `bigint` | có | FK → customer_address, ON DELETE SET NULL | Chép từ sổ địa chỉ nào |

Ràng buộc bảng: `UNIQUE (booking_id, address_type)`.
Index: `province_code`, `location_id`.

### 8.4 `booking_horse`: dòng đơn (mỗi con ngựa một dòng)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking, CASCADE | |
| `horse_id` | `bigint` | | FK → horse | |
| `stall_type_code` | `varchar(30)` | | FK → stall_type | Loại khoang cần đặt |
| `diet_note` | `text` | có | | |
| `feedings_per_day` | `smallint` | có | CK `1..10` | |
| `is_feed_supplied_by_owner` | `boolean` | | default `false` | |
| `status` | `varchar(30)` | | CK `ACTIVE, EXCLUDED, CANCELLED, DELIVERED, RETURNED` | `EXCLUDED` = bị loại vì không đủ sức khỏe khi đón; `RETURNED` = đã trả ngựa xong |
| `added_at` | `timestamptz` | | default `now()` | |
| `pickup_missed_at` | `timestamptz` | có | | Giờ ghi lần đón hụt gần nhất |
| `pickup_choice_due_at` | `timestamptz` | có | Chỉ mục khi chưa chọn | Hạn quyết định cho lần hụt gần nhất |
| `pickup_miss_reason` | `varchar(500)` | có | | Diễn giải lý do tại nơi đón. Mã nguyên nhân và kết quả từng cuộc gọi nằm trong sự kiện `PICKUP_MISSED` của `audit_log` |
| `pickup_miss_liability` | `varchar(20)` | có | CK `CUSTOMER, COMPANY, FORCE_MAJEURE` | Quản lý chốt |
| `pickup_miss_choice` | `varchar(24)` | có | CK `DROP_HORSE, RETRY_PICKUP, SELF_DELIVERY, CHANGE_DEPARTURE, RESCHEDULE_PICKUP, CANCEL_BOOKING` | Quyết định đã ghi dưới khóa đơn |
| `pickup_retry_departure_id` | `bigint` | có | FK → departure | Chuyến đã dùng lượt đón lại |

Ràng buộc bảng: `UNIQUE (booking_id, horse_id)`.
Index: `horse_id`.

Ảnh hiện trường dùng `document` loại `PICKUP_MISS_PHOTO`, chủ sở hữu `BOOKING_HORSE`. Mã nguyên nhân, kết quả từng cuộc gọi và lựa chọn hộ ghi vào `audit_log`. Cấu hình `PICKUP_CHOICE_LONG_HOURS`, `PICKUP_CHOICE_SHORT_HOURS`, `PICKUP_RETRY_PREPARATION_HOURS` và `PICKUP_MISS_WAIT_MINUTES` nằm trong `system_config`.

### 8.5 `booking_horse_service`: dịch vụ thêm cho từng con

`completed_at timestamptz` có thể trống. Quản lý xác nhận dịch vụ thuộc đúng con đang hoạt động, dưới khóa đơn. Thử lại giữ nguyên thời điểm hoàn thành.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `booking_horse_id` | `bigint` | | PK, FK → booking_horse, CASCADE | |
| `billable_service_id` | `bigint` | | PK, FK → billable_service | Chỉ chọn được dịch vụ có `layer = ADD_ON` (kiểm ở service) |
| `quantity` | `int` | | CK `> 0` | |

### 8.6 `booking_status_history`: nhật ký đổi trạng thái đơn

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking, CASCADE | |
| `from_status` | `varchar(30)` | có | | NULL ở dòng đầu tiên |
| `to_status` | `varchar(30)` | | | |
| `changed_by` | `bigint` | có | FK → app_user | NULL = hệ thống tự đổi (scheduler) |
| `changed_at` | `timestamptz` | | default `now()` | |
| `reason` | `text` | có | | Lý do từ chối, yêu cầu bổ sung hồ sơ, đổi chuyến hoặc hủy đơn |
| `cause_type` | `varchar(30)` | có | CK `CUSTOMER, COMPANY, AUTHORITY` | Nguyên nhân đổi chuyến. Để trống khi hệ thống nhả chỗ đơn chưa cọc ở mốc quyết định |

| `previous_departure_id` | `bigint` | có | FK → departure | Chuyến vừa lỡ khi chuyển sang SCHEDULE_CHANGE_REQUIRED. Mốc giữ giá lấy từ ngày chạy của chuyến này |

Index: `(booking_id, changed_at)`, `previous_departure_id`.

### 8.7 `terms_acceptance`: bằng chứng khách đồng ý điều khoản

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `terms_type` | `varchar(30)` | | CK `SERVICE_TERMS, LIABILITY_WAIVER, PRIVACY` | |
| `terms_version` | `varchar(20)` | | | Ví dụ `2027.1`. Nội dung nằm ở file `terms/<type>/<version>.md` |
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
| `trip_type` | `varchar(30)` | | CK `PICKUP, LINEHAUL, DELIVERY, TRANSFER, DIRECT` | `DIRECT` = chạy thẳng từ địa chỉ đón tới địa chỉ giao, không qua hub |
| `departure_id` | `bigint` | có | FK → departure | Bắt buộc khi `LINEHAUL` |
| `vehicle_id` | `bigint` | | FK → vehicle | |
| `start_location_id` | `bigint` | | FK → location | Hub xuất phát. Phải là nơi xe kết thúc lượt chạy trước đó |
| `end_location_id` | `bigint` | | FK → location | Nơi kết thúc lượt chạy |
| `planned_start_at` | `timestamptz` | | | |
| `planned_end_at` | `timestamptz` | | | |
| `actual_start_at` | `timestamptz` | có | | |
| `actual_end_at` | `timestamptz` | có | | |
| `sealed_at` | `timestamptz` | có | | Thời điểm niêm phong xe. Chỉ ghi nhận, đợt này không có quy tắc kiểm tra |
| `disinfected_at` | `timestamptz` | có | | Thời điểm khử trùng xe. Chỉ ghi nhận |
| `status` | `varchar(30)` | | CK `PLANNED, DISPATCHED, IN_PROGRESS, COMPLETED, CANCELLED` | |

Ràng buộc bảng:
- `CHECK (trip_type <> 'LINEHAUL' OR departure_id IS NOT NULL)`
- `CHECK (planned_end_at > planned_start_at)`
- `CHECK (actual_end_at IS NULL OR actual_start_at IS NOT NULL)`

Index: `departure_id`, `vehicle_id`, `(status, planned_start_at)`.

### 9.2 `trip_stop`: các điểm dừng của một lượt chạy

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `vehicle_trip_id` | `bigint` | | FK → vehicle_trip, CASCADE | |
| `sequence_number` | `int` | | CK `>= 1` | |
| `location_id` | `bigint` | có | FK → location | Điểm thuộc mạng lưới |
| `booking_address_id` | `bigint` | có | FK → booking_address | Điểm đón hoặc giao của khách |
| `stop_type` | `varchar(30)` | | CK `PICKUP, DELIVERY, HUB, REST, VITAL_CHECK, BORDER, QUARANTINE` | |
| `route_id` | `bigint` | có | FK cặp `(route_id, route_stop_sequence_number)` → route_stop | Điểm này chép từ điểm nào của tuyến |
| `route_stop_sequence_number` | `int` | có | | |
| `planned_arrival_at` | `timestamptz` | | snapshot | Chép từ `route_stop.arrival_offset_minutes` |
| `planned_departure_at` | `timestamptz` | | snapshot | |
| `actual_arrival_at` | `timestamptz` | có | | |
| `actual_departure_at` | `timestamptz` | có | | |

Ràng buộc bảng:
- `UNIQUE (vehicle_trip_id, sequence_number)`
- `CHECK (num_nonnulls(location_id, booking_address_id) = 1)`: điểm dừng là location của mạng lưới hoặc địa chỉ của khách, chỉ một trong hai.
- `CHECK ((route_id IS NULL) = (route_stop_sequence_number IS NULL))`
- `FOREIGN KEY (route_id, route_stop_sequence_number) REFERENCES route_stop(route_id, sequence_number)`
- `CHECK (planned_departure_at >= planned_arrival_at)`

Index: `location_id`, `booking_address_id`, `(route_id, route_stop_sequence_number)`.

### 9.3 `crew_assignment`: phân công tổ lái cho lượt chạy

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `vehicle_trip_id` | `bigint` | | FK → vehicle_trip, CASCADE | |
| `staff_id` | `bigint` | | FK → staff(user_id) | |
| `crew_role` | `varchar(30)` | | CK `DRIVER, ESCORT, VET` | |
| `status` | `varchar(30)` | | CK `ASSIGNED, ACKNOWLEDGED, RELEASED` | `ACKNOWLEDGED` = nhân viên đã xác nhận nhận việc |
| `assigned_at` | `timestamptz` | | default `now()` | |
| `note` | `text` | có | | |

Ràng buộc bảng: `UNIQUE (vehicle_trip_id, staff_id)`.
Index: `staff_id`.

### 9.4 `horse_leg`: một chặng di chuyển của một con ngựa

Mỗi con ngựa trong đơn có một chuỗi chặng: đón → hub → trục chính → giao. Đơn chạy thẳng chỉ có một chặng `DIRECT` từ địa chỉ đón tới địa chỉ giao. Một chặng do xe công ty chở (`vehicle_trip_id`) hoặc do đại lý đảm nhận (`agent_party_id`).

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | FK → booking_horse | |
| `sequence_number` | `int` | | CK `>= 1` | Thứ tự chặng của con ngựa |
| `leg_type` | `varchar(30)` | | CK `PICKUP, LINEHAUL, DELIVERY, TRANSFER, RETURN, DIRECT` | |
| `from_location_id` | `bigint` | có | FK → location | |
| `from_booking_address_id` | `bigint` | có | FK → booking_address | |
| `to_location_id` | `bigint` | có | FK → location | |
| `to_booking_address_id` | `bigint` | có | FK → booking_address | |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | Xe công ty chở |
| `agent_party_id` | `bigint` | có | FK → party | Đại lý chở |
| `board_trip_stop_id` | `bigint` | có | FK → trip_stop | Lên xe ở điểm dừng nào |
| `alight_trip_stop_id` | `bigint` | có | FK → trip_stop | Xuống xe ở điểm dừng nào |
| `stall_position` | `smallint` | có | CK `>= 1` | Vị trí trên xe, đánh số từ đầu xe ra cửa sau. Khoang rộng chiếm vị trí này và vị trí kế sau. NULL = chưa xếp, hoặc đại lý chở. Chỉ dịch vụ xếp khoang của luồng B ghi |
| `replaced_horse_leg_id` | `bigint` | có | FK → horse_leg | Chặng thay thế cho chặng thất bại, ví dụ giao lại lần hai |
| `planned_start_at` | `timestamptz` | | | |
| `planned_end_at` | `timestamptz` | | | |
| `actual_start_at` | `timestamptz` | có | | |
| `actual_end_at` | `timestamptz` | có | | |
| `status` | `varchar(30)` | | CK `PLANNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED` | |

Ràng buộc bảng:
- `UNIQUE (booking_horse_id, sequence_number)`
- `CHECK (num_nonnulls(from_location_id, from_booking_address_id) = 1)`
- `CHECK (num_nonnulls(to_location_id, to_booking_address_id) = 1)`
- `CHECK (num_nonnulls(vehicle_trip_id, agent_party_id) = 1)`
- `CHECK (agent_party_id IS NULL OR (board_trip_stop_id IS NULL AND alight_trip_stop_id IS NULL))`: chặng do đại lý chở thì không có điểm lên xuống trên xe công ty.
- `CHECK (agent_party_id IS NULL OR stall_position IS NULL)`
- `CHECK (planned_end_at > planned_start_at)`

Index: `vehicle_trip_id`, `agent_party_id`, `board_trip_stop_id`, `alight_trip_stop_id`, `replaced_horse_leg_id`, `status`.

### 9.5 `horse_stay`: thời gian ngựa ở một nơi, không trên xe

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | FK → booking_horse | |
| `stay_type` | `varchar(30)` | | CK `ASSEMBLY, QUARANTINE, OVERNIGHT, STABLING` | Tập kết ở hub / (ngừng dùng) / nghỉ qua đêm / gửi chuồng |
| `location_id` | `bigint` | | FK → location | |
| `operator_party_id` | `bigint` | có | FK → party | Đơn vị vận hành nơi đó, ví dụ chuồng gửi đối tác |
| `planned_start_at` | `timestamptz` | | | |
| `planned_end_at` | `timestamptz` | | | |
| `actual_start_at` | `timestamptz` | có | | |
| `actual_end_at` | `timestamptz` | có | | |
| `balance_hold_at` | `timestamptz` | có | Chỉ trên `STABLING` đã bắt đầu | Mốc T bắt đầu tính phí do giữ sau khi chưa trả số dư. Có thể muộn hơn lúc công ty nhận ngựa |
| `quarantine_result` | `varchar(30)` | có | CK `PASSED, FAILED` | Ngừng dùng cùng `QUARANTINE` |

Ràng buộc bảng:
- `CHECK (planned_end_at > planned_start_at)`
- `CHECK (quarantine_result IS NULL OR stay_type = 'QUARANTINE')`
- `CHECK (actual_end_at IS NULL OR actual_start_at IS NOT NULL)`
- `CHECK (balance_hold_at IS NULL OR (stay_type = 'STABLING' AND actual_start_at IS NOT NULL))`

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
| `horse_stay_id` | `bigint` | có | FK → horse_stay | Đo trong thời gian lưu trú |
| `health_status` | `varchar(30)` | | CK `NORMAL, WATCH, SICK, CRITICAL` | |
| `temperature_celsius` | `numeric(4,1)` | có | CK `30..45` | |
| `heart_rate_bpm` | `smallint` | có | CK `10..200` | |
| `respiration_rate_per_minute` | `smallint` | có | CK `4..100` | |
| `weight_kg` | `numeric(6,1)` | có | CK `> 0` | |
| `water_intake` | `varchar(30)` | có | CK `NORMAL, REDUCED, REFUSED` | Uống nước trong lần dừng hoặc lưu trú (`V202611241000`) |
| `feed_intake` | `varchar(30)` | có | CK `NORMAL, REDUCED, REFUSED` | Ăn trong lần dừng hoặc lưu trú (`V202611241000`) |
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
| `handover_type` | `varchar(30)` | | CK `PICKUP, HUB_IN, HUB_OUT, STAY_IN, STAY_OUT, AGENT, DELIVERY, RETURN, SHIFT` | `SHIFT` chờ migration |
| `trip_stop_id` | `bigint` | có | FK → trip_stop | Giao nhận tại điểm dừng của xe |
| `horse_stay_id` | `bigint` | có | FK → horse_stay | Giao nhận khi nhập hoặc xuất trại, chuồng |
| `from_party_id` | `bigint` | có | FK → party | Đối tác giao. NULL = công ty hoặc bên trong đơn |
| `to_party_id` | `bigint` | có | FK → party | Đối tác nhận. NULL = công ty hoặc bên trong đơn |
| `handled_by` | `bigint` | | FK → staff(user_id) | Nhân viên thực hiện |
| `wait_started_at` | `timestamptz` | có | | Bắt đầu chờ người nhận |
| `handed_at` | `timestamptz` | có | | |
| `status` | `varchar(30)` | | CK `PENDING, DONE, TIMEOUT, FAILED` | |
| `note` | `text` | có | | |

Biên bản ký là một `document` có `handover_id` trỏ về dòng này.

Nhận tại điểm đón, nhận từ đại lý, nhận hoặc giao cho khách tại hub và giao ở điểm giao yêu cầu tình trạng và ảnh của từng con quét đúng chip được giao nhận. Biên bản bắt buộc ở các lần này và khi bàn giao đại lý. Điều phối ghi đại lý đã giao bằng tệp loại `AGENT_DELIVERY_PROOF` có chủ sở hữu `HORSE_LEG`.

Hành trình của chủ đơn chỉ trả biên bản và ảnh gắn với lần giao nhận DONE có ngựa của đơn đã khớp chip. Tệp gắn chung cho nhiều đơn cần được phân quyền theo từng đơn trước khi hiện cho khách.

Ràng buộc bảng:
- `CHECK (num_nonnulls(trip_stop_id, horse_stay_id) = 1)`
- `CHECK (from_party_id IS NULL OR to_party_id IS NULL OR from_party_id <> to_party_id)`
- `CHECK (status <> 'DONE' OR handed_at IS NOT NULL)`

Index: `trip_stop_id`, `horse_stay_id`, `from_party_id`, `to_party_id`, `handled_by`.

Bên giao và bên nhận suy ra từ `handover_type`:

| `handover_type` | Bên giao | Bên nhận |
|---|---|---|
| `PICKUP` | `booking_party` SHIPPER | Công ty |
| `HUB_IN`, `HUB_OUT`, `SHIFT` | Công ty | Công ty |
| `STAY_IN`, `STAY_OUT` | Công ty hoặc đối tác (`from_party_id`) | Công ty hoặc đối tác (`to_party_id`) |
| `AGENT` | Công ty hoặc đối tác (`from_party_id`) | Công ty hoặc đối tác (`to_party_id`); ít nhất một bên là đối tác (kiểm ở service) |
| `DELIVERY` | Công ty, hoặc đại lý (`from_party_id`) | `booking_party` CONSIGNEE |
| `RETURN` | Công ty, hoặc đối tác (`from_party_id`) | `booking_party` SHIPPER |

### 10.2 `handover_horse`: ngựa nào được giao trong lần giao nhận

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `handover_id` | `bigint` | | PK, FK → handover, CASCADE | |
| `horse_leg_id` | `bigint` | | PK, FK → horse_leg | |
| `scanned_microchip` | `varchar(30)` | có | | Mã chip đọc được |
| `is_microchip_matched` | `boolean` | | | So với `horse.microchip` |
| `condition_note` | `text` | có | | Tình trạng ngựa lúc giao nhận |
| `recipient_name` | `varchar(120)` | có | | Họ tên người nhận do nhân viên ghi khi giao |

Index: `horse_leg_id`.

### 10.3 `handover_document`: giấy tờ gốc nhận hoặc trả trong lần giao nhận

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `handover_id` | `bigint` | | PK, FK → handover, CASCADE | |
| `document_id` | `bigint` | | PK, FK → document | Ví dụ hộ chiếu ngựa bản gốc |
| `direction` | `varchar(30)` | | CK `RECEIVED, NOT_RECEIVED, RETURNED` | |

Index: `document_id`.

---

## Nhóm 11. Bảo hiểm

### 11.1 `horse_insurance`: bảo hiểm cho từng con trong đơn (1–1 với booking_horse)

`refunded_premium_amount numeric(15,2)` ghi tổng phí hãng đã hoàn theo chứng từ chuyên viên xác nhận khi đón hụt do khách. Giá trị NULL nếu chưa ghi nhận, nếu có thì dương và không vượt phí bảo hiểm xác nhận. Ghi nhận lại cùng tổng không lập thêm khoản hoàn.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_horse_id` | `bigint` | | UK, FK → booking_horse, CASCADE | |
| `coverage_option` | `varchar(30)` | | CK `PERSONAL, ARRANGED, NONE` | Khách tự mua / công ty mua giúp / không mua |
| `insurer_party_id` | `bigint` | có | FK → party | Công ty bảo hiểm |
| `coverage_amount` | `numeric(15,2)` | có | CK `> 0` | |
| `premium_amount` | `numeric(15,2)` | có | CK `>= 0` | Phí bảo hiểm |
| `currency_code` | `char(3)` | có | | |
| `quote_expires_at` | `timestamptz` | có | | Hạn của báo giá bảo hiểm |
| `policy_number` | `varchar(50)` | có | | Số hợp đồng |
| `valid_from` | `date` | có | | |
| `valid_to` | `date` | có | CK `>= valid_from` | |
| `waiver_terms_acceptance_id` | `bigint` | có | FK → terms_acceptance | Bản cam kết miễn trách khi không mua bảo hiểm |
| `status` | `varchar(30)` | | CK `DRAFT, REVISION_REQUESTED, CONFIRMED` | |
| `revision_reason` | `text` | có | | Lý do yêu cầu khách bổ sung |
| `revision_requested_by` | `bigint` | có | FK → staff(user_id) | |
| `revision_requested_at` | `timestamptz` | có | | |
| `confirmed_by` | `bigint` | có | FK → staff(user_id) | |
| `confirmed_at` | `timestamptz` | có | | |

Ràng buộc bảng:
- `CHECK (status <> 'CONFIRMED' OR coverage_option <> 'ARRANGED' OR insurer_party_id IS NOT NULL)`
- `CHECK (coverage_option <> 'NONE' OR waiver_terms_acceptance_id IS NOT NULL)`
- `CHECK (status <> 'CONFIRMED' OR coverage_option = 'NONE' OR (coverage_amount IS NOT NULL AND currency_code IS NOT NULL))`
- `CHECK (status <> 'REVISION_REQUESTED' OR (revision_reason IS NOT NULL AND revision_requested_at IS NOT NULL))`. Chỉ được yêu cầu bổ sung khi `coverage_option = PERSONAL` (kiểm ở service).
- `CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))`

Index: `insurer_party_id`, `waiver_terms_acceptance_id`.

---

## Nhóm 12. Báo giá và thanh toán

### 12.1 `quotation`: phiên bản báo giá

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking | |
| `version` | `int` | | CK `>= 1` | |
| `price_list_id` | `bigint` | | FK → price_list | Báo giá này tính theo bảng giá nào |
| `currency_code` | `char(3)` | | | |
| `deposit_rate` | `numeric(5,4)` | | CK `0..1`, snapshot | Chép từ `price_list` |
| `expires_at` | `timestamptz` | | | |
| `confirmed_pickup_date` | `date` | | | |
| `confirmed_departure_date` | `date` | | | |
| `estimated_delivery_date` | `date` | | CK `>= confirmed_departure_date` | |
| `total_amount` | `numeric(15,2)` | | CK `>= 0` | Dẫn xuất: Σ dòng báo giá. Bất biến sau khi SENT |
| `pass_through_amount` | `numeric(15,2)` | | CK `0..total_amount` | Dẫn xuất: phần thu hộ |
| `deposit_amount` | `numeric(15,2)` | | CK `pass_through_amount..total_amount` | Dẫn xuất: cọc, gồm toàn bộ phần thu hộ (`DepositPolicy`) |
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

`pickup_deposit_floor boolean NOT NULL DEFAULT false` đánh dấu dòng điều chỉnh dương theo sàn cọc đã trả. Dòng này phải là `ADJUSTMENT` và không phải khoản thu hộ. Khi loại thêm ngựa, báo giá thay thế bỏ dòng sàn cũ và tính lại. Dòng cước đón lại và nhiên liệu gắn `horse_leg_id` để xác định lượt xe đã tính phí.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `quotation_id` | `bigint` | | FK → quotation, CASCADE | |
| `line_number` | `int` | | CK `>= 1` | |
| `line_type` | `varchar(30)` | | CK `LINEHAUL, PICKUP, DELIVERY, DIRECT, FUEL, STALL, QUARANTINE, SERVICE, DOCS, INSURANCE, ADJUSTMENT` | |
| `billable_service_id` | `bigint` | có | FK → billable_service | |
| `price_item_id` | `bigint` | có | FK → price_item | Đơn giá lấy từ dòng giá nào. NULL với dòng điều chỉnh tay và dòng phí bảo hiểm |
| `booking_horse_id` | `bigint` | có | FK → booking_horse | Dòng này tính cho con ngựa nào |
| `horse_leg_id` | `bigint` | có | FK → horse_leg | Dòng này tính cho chặng nào |
| `description` | `varchar(500)` | | | |
| `quantity` | `numeric(10,2)` | | CK `> 0` | |
| `unit` | `varchar(30)` | | | |
| `unit_price` | `numeric(15,2)` | | snapshot | |
| `amount` | `numeric(15,2)` | | `GENERATED ALWAYS AS (round(quantity * unit_price, 2)) STORED` | |
| `is_pass_through` | `boolean` | | snapshot | |
| `refund_payment_id` | `bigint` | có | FK → payment | Khoản hoàn đầu tiên được lập cho dòng này. Từ 24/11/2026 không hoàn riêng từng dòng nên không còn nơi ghi; giữ cột cho dữ liệu cũ |

Ràng buộc bảng:
- `UNIQUE (quotation_id, line_number)`
- `CHECK (unit_price >= 0 OR line_type = 'ADJUSTMENT')`: chỉ dòng điều chỉnh được có đơn giá âm (giảm giá).
- `CHECK (line_type = 'ADJUSTMENT' OR billable_service_id IS NOT NULL)`

Index: `billable_service_id`, `price_item_id`, `booking_horse_id`, `horse_leg_id`, `refund_payment_id`. Sau SENT, các cột giá giữ nguyên.

Tổng tiền lưu ở `quotation.total_amount`, do `QuotationService` tính từ các dòng (mục 5).

### 12.3 `payment`: khoản phải thu hoặc phải hoàn

Một đơn có thể có nhiều khoản `BALANCE` đã trả. Service giữ khóa đơn và chỉ lập thêm khoản chờ khi vẫn còn thiếu so với báo giá đã chấp nhận. Hạn trả tính từ lần giao nhận kích hoạt khoản đó.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | | FK → booking | |
| `quotation_id` | `bigint` | có | FK → quotation | Cọc và số dư gắn với báo giá đã chấp nhận |
| `payment_type` | `varchar(30)` | | CK `DEPOSIT, BALANCE, EXTRA_CHARGE, REFUND` | |
| `amount` | `numeric(15,2)` | | CK `> 0` | Luôn dương. Chiều tiền xác định bằng `payment_type` |
| `currency_code` | `char(3)` | | | |
| `payment_method` | `varchar(30)` | | CK `VNPAY, BANK_TRANSFER, CASH` | |
| `status` | `varchar(30)` | | CK `PENDING, PAID, FAILED, CANCELLED` | |
| `due_at` | `timestamptz` | có | | |
| `paid_at` | `timestamptz` | có | | |
| `refunded_payment_id` | `bigint` | có | FK → payment | |

Ràng buộc bảng:
- `CHECK ((payment_type = 'REFUND') = (refunded_payment_id IS NOT NULL))`
- `CHECK (payment_type NOT IN ('DEPOSIT','BALANCE') OR quotation_id IS NOT NULL)`
- `CHECK (status <> 'PAID' OR paid_at IS NOT NULL)`

Index: `booking_id`, `quotation_id`, `refunded_payment_id`.

### 12.4 `payment_transaction`: giao dịch với cổng thanh toán

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `payment_id` | `bigint` | | FK → payment | |
| `gateway_code` | `varchar(30)` | | | Ví dụ `VNPAY` |
| `gateway_transaction_number` | `varchar(100)` | | | Mã giao dịch phía cổng |
| `bank_code` | `varchar(30)` | có | | |
| `response_code` | `varchar(10)` | | | |
| `amount` | `numeric(15,2)` | | CK `> 0` | Số tiền cổng báo về, dùng để đối chiếu |
| `status` | `varchar(30)` | | CK `PENDING, SUCCESS, FAILED` | |
| `gateway_paid_at` | `timestamptz` | có | | |
| `received_at` | `timestamptz` | | default `now()` | |
| `raw_payload` | `jsonb` | | | Phản hồi thô của cổng. Ngoại lệ được phép dùng JSON |

Ràng buộc bảng: `UNIQUE (gateway_code, gateway_transaction_number)`. Cổng gửi lại callback nhiều lần cũng không tạo bản ghi trùng.
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
| `incident_type` | `varchar(30)` | | CK `HEALTH, VEHICLE_BREAKDOWN, ACCIDENT, DELAY, DOCUMENT, HANDOVER_TIMEOUT, OTHER, COMPLAINT` | |
| `cause_type` | `varchar(30)` | chỉ với `COMPLAINT` | CK `COMPANY_FAULT, HORSE_CONDITION, CUSTOMER_DOCS, AGENT_FAULT, FORCE_MAJEURE, RECIPIENT_ABSENT` | Nguyên nhân do tổ lái đề xuất; trống với `COMPLAINT` |
| `confirmed_cause_type` | `varchar(30)` | có | Cùng tập giá trị với `cause_type` | Nguyên nhân quản lý chốt khi duyệt phương án |
| `cause_confirmed_by` | `bigint` | có | FK → staff.user_id | Quản lý chốt, để trống với sự cố hệ thống |
| `cause_confirmed_at` | `timestamptz` | có | Cùng có với `confirmed_cause_type` | Giờ chốt |
| `title` | `varchar(200)` | | | |
| `description` | `text` | | | |
| `latitude` | `numeric(9,6)` | có | | |
| `longitude` | `numeric(9,6)` | có | | |
| `reported_by` | `bigint` | chỉ với `COMPLAINT` | FK → staff(user_id) | Có thể trống khi khách tự gửi khiếu nại |
| `reported_at` | `timestamptz` | | default `now()` | |
| `status` | `varchar(30)` | | CK `REPORTED, PLAN_PENDING, PLAN_ACTIVE, RESOLVED, CLOSED` | |
| `fit_confirmed_by` | `bigint` | có | FK → staff(user_id) | Người xác nhận ngựa đủ sức khỏe đi tiếp |
| `fit_confirmed_at` | `timestamptz` | có | | |
| `resolved_at` | `timestamptz` | có | | |
| `booking_horse_id` | `bigint` | có | FK → booking_horse | Ngựa được chọn trong khiếu nại. Trống khi khiếu nại cả đơn. |
| `complaint_reply` | `text` | có | | Nội dung quản lý trả lời khiếu nại |
| `complaint_decision` | `varchar(30)` | có | CK `ACCEPTED, REJECTED` | Kết luận khiếu nại |
| `complaint_decision_reason` | `text` | có | | Lý do kết luận |
| `complaint_decided_by` | `bigint` | có | FK → staff(user_id) | Quản lý kết luận khiếu nại |

Ràng buộc bảng:
- `CHECK (num_nonnulls(booking_id, vehicle_trip_id, horse_leg_id) >= 1)`
- `CHECK (incident_type <> 'COMPLAINT' OR booking_id IS NOT NULL)`
- `CHECK (incident_type = 'COMPLAINT' OR (reported_by IS NOT NULL AND cause_type IS NOT NULL))`
- Các trường riêng của khiếu nại chỉ dùng cho `COMPLAINT`. Khi có kết luận phải có lý do, người kết luận, thời điểm xử lý và trạng thái `CLOSED`.
- Mỗi đơn có tối đa một `COMPLAINT` theo unique index có điều kiện. Service kiểm tra ngựa được chọn thuộc đơn, ảnh `COMPLAINT_PHOTO` và quyền khách hoặc quản lý trong cùng luồng ghi khiếu nại.
- `CHECK ((fit_confirmed_by IS NULL) = (fit_confirmed_at IS NULL))`

Index: `booking_id`, `vehicle_trip_id`, `horse_leg_id`, `status`.

### 13.2 `incident_plan`: phương án xử lý sự cố (có phiên bản)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `incident_id` | `bigint` | | FK → incident, CASCADE | |
| `version` | `int` | | CK `>= 1` | |
| `resolution_type` | `varchar(30)` | | CK `CONTINUE, VET_TREATMENT, STABLE_NEARBY, TRANSFER_VEHICLE, RETURN_TO_HUB, CANCEL` | |
| `instructions` | `text` | | | Chỉ đạo cụ thể |
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
| `category` | `varchar(30)` | | CK `TOLL, FUEL, STABLING, VET, RESCUE, REPAIR, WAITING, OTHER` | WAITING chỉ giữ cho dữ liệu cũ |
| `stabling_days` | `bigint` | có | CK dương, chỉ với `STABLING` gắn `horse_stay` | Snapshot số ngày tính trên dòng phí, dùng để tính chênh khi ngựa rời hub |
| `stabling_deposit_credit` | `numeric(15,2)` | | Mặc định 0, từ 0 tới `amount`, chỉ khác 0 cho `STABLING` gắn `horse_stay` | Phần phí giữ được cấn cọc trên đơn đã hủy |
| `description` | `varchar(500)` | | | |
| `amount` | `numeric(15,2)` | | CK `> 0` | |
| `currency_code` | `char(3)` | | | |
| `payer_type` | `varchar(30)` | | CK `COMPANY, CUSTOMER, CONSIGNEE, AGENT` | Ai chịu khoản này |
| `booking_id` | `bigint` | có | FK → booking | |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | |
| `incident_id` | `bigint` | có | FK → incident | |
| `handover_id` | `bigint` | có | FK → handover | Biên bản liên quan. Không thu phí chờ |
| `horse_stay_id` | `bigint` | có | FK → horse_stay | Ví dụ phí gửi chuồng |
| `is_customer_consulted` | `boolean` | | default `false` | Hệ thống đặt khi có phản hồi đồng ý từ khách hoặc qua điện thoại |
| `customer_agreed` | `boolean` | có | Đồng thời có người và giờ phản hồi | Đồng ý hoặc từ chối |
| `customer_response_by` | `bigint` | có | FK → app_user.id | Chủ đơn hoặc điều phối ghi cuộc gọi |
| `customer_response_at` | `timestamptz` | có | | Giờ phản hồi |
| `customer_response_due_at` | `timestamptz` | có | | Hạn phản hồi, lấy từ CUSTOMER_COST_RESPONSE_MINUTES |
| `phone_consent_note` | `varchar(500)` | có | | Nội dung khách đồng ý qua điện thoại |
| `emergency_override_reason` | `varchar(500)` | có | | Lý do quản lý duyệt VET khi khách từ chối |
| `agent_recovery_required` | `boolean` | | default false | Khoản công ty chịu do đại lý có lỗi |
| `agent_recovered_at` | `timestamptz` | có | Phải có cờ thu hồi | Giờ quản lý đánh dấu đã đòi |
| `incurred_at` | `timestamptz` | | | |
| `recorded_by` | `bigint` | | FK → staff(user_id) | |
| `approved_by` | `bigint` | có | FK → staff(user_id) | |
| `approved_at` | `timestamptz` | có | | |
| `extra_charge_payment_id` | `bigint` | có | FK → payment | Khoản thu thêm từ khách cho chi phí này |

Hóa đơn hoặc biên lai là một `document` có `cost_item_id` trỏ về dòng này.

Ràng buộc bảng:
- `CHECK (payer_type = 'COMPANY' OR booking_id IS NOT NULL)`
- `CHECK (num_nonnulls(booking_id, vehicle_trip_id, incident_id) >= 1)`
- `CHECK ((approved_by IS NULL) = (approved_at IS NULL))`
- `CHECK (extra_charge_payment_id IS NULL OR payer_type <> 'COMPANY')`

Index: `booking_id`, `vehicle_trip_id`, `incident_id`, `handover_id`, `horse_stay_id`, `extra_charge_payment_id`.

---

## Nhóm 14. Chứng từ

Phí biểu phí gắn lưu trú `STABLING` thuộc `CONSIGNEE` và khách thanh toán. Dòng tự duyệt ghi nhân viên thao tác theo ràng buộc hiện có. Số ngày đã thu nằm trên từng dòng phí, không lưu tổng trên `horse_stay`. Khoản thu thêm có `due_at` và quá hạn được tính khi đọc.

`document_type` có mã `VET_REPORT` với chủ thể `INCIDENT`. Điều phối dùng mã này để xác nhận đủ sức thay tổ lái.

### 14.1 `document`: tệp chứng từ

Mỗi chứng từ thuộc **đúng một** đối tượng, được chỉ bằng một trong các cột `*_id` ở phần dưới của bảng. Cột nào được điền phải khớp với `document_type.owner_type`.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `document_type_id` | `bigint` | | FK → document_type | |
| `document_requirement_id` | `bigint` | có | FK → document_requirement | Chứng từ này đáp ứng yêu cầu giấy tờ nào |
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
| `horse_id` | `bigint` | có | FK → horse | owner_type `HORSE` |
| `booking_id` | `bigint` | có | FK → booking | owner_type `BOOKING` |
| `booking_horse_id` | `bigint` | có | FK → booking_horse | owner_type `BOOKING_HORSE` |
| `horse_leg_id` | `bigint` | có | FK → horse_leg | owner_type `HORSE_LEG` |
| `vehicle_trip_id` | `bigint` | có | FK → vehicle_trip | owner_type `VEHICLE_TRIP` |
| `horse_insurance_id` | `bigint` | có | FK → horse_insurance | owner_type `INSURANCE` |
| `incident_id` | `bigint` | có | FK → incident | owner_type `INCIDENT` |
| `cost_item_id` | `bigint` | có | FK → cost_item | owner_type `COST_ITEM` |
| `handover_id` | `bigint` | có | FK → handover | owner_type `HANDOVER` |
| `evidence_horse_leg_id` | `bigint` | có | FK → horse_leg và FK ghép (`handover_id`, `evidence_horse_leg_id`) → handover_horse | Chỉ định con trong ảnh giao nhận. Cột này không phải chủ sở hữu tệp. |
| `vehicle_id` | `bigint` | có | FK → vehicle | owner_type `VEHICLE` |
| `staff_id` | `bigint` | có | FK → staff(user_id) | owner_type `STAFF` |
| `party_id` | `bigint` | có | FK → party | owner_type `PARTY` (đối tác) |
| `customer_id` | `bigint` | có | FK → customer(user_id) | owner_type `CUSTOMER` |

Ràng buộc bảng:
```sql
CHECK (num_nonnulls(horse_id, booking_id, booking_horse_id, horse_leg_id, vehicle_trip_id,
                    horse_insurance_id, incident_id, cost_item_id, handover_id,
                    vehicle_id, staff_id, party_id, customer_id) = 1)
CHECK (status NOT IN ('APPROVED','REJECTED') OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL))
CHECK (status <> 'REJECTED' OR review_note IS NOT NULL)
```

Index: mỗi cột chủ sở hữu một partial index, ví dụ `CREATE INDEX ix_document_booking ON document(booking_id) WHERE booking_id IS NOT NULL`. Thêm index cho `document_type_id` và `document_requirement_id`.

---

## Nhóm 15. Hỗ trợ

### 15.1 `notification`: nội dung thông báo

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `id` | `bigint` | | PK, identity | |
| `booking_id` | `bigint` | có | FK → booking | |
| `title` | `varchar(200)` | | | |
| `message` | `text` | | | |
| `link_url` | `varchar(500)` | có | | Đường dẫn trong ứng dụng |
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
| `config_key` | `varchar(100)` | | PK | Ví dụ `HANDOVER_WAIT_MINUTES` |
| `audit_target_id` | `bigint` | | identity, UK | Định danh ổn định để ghi `audit_log.target_id` khi cập nhật tham số |
| `config_value` | `varchar(500)` | | | |
| `min_value` | `int` | có | CHECK cùng có giá trị hoặc cùng NULL với `max_value` | Cận dưới, tính cả giá trị cận |
| `max_value` | `int` | có | CHECK `min_value <= max_value` khi có giới hạn | Cận trên, tính cả giá trị cận |
| `unit` | `varchar(30)` | có | | `MINUTE`, `HOUR`, `DAY`, `COUNT`, `TEXT` (giá trị chữ, chỉ sửa qua API dạng chữ) |
| `config_group` | `varchar(50)` | | | |
| `description` | `text` | có | | |
| `updated_by` | `bigint` | có | FK → staff(user_id) | |
| `updated_at` | `timestamptz` | | default `now()` | |

`HOTLINE` và `OFFICE_ADDRESS` (nhóm `CONTACT`, đơn vị `TEXT`) hiện cho khách qua `GET /public/contact`; `HOTLINE` phải là số điện thoại.

Backend kiểm tra giới hạn khi cập nhật giá trị. Nếu hai cận đều NULL, quy tắc cập nhật số nguyên dương cho đơn vị được hỗ trợ vẫn áp dụng.

| Tham số | Nhỏ nhất | Lớn nhất |
|---|---:|---:|
| `DEPARTURE_GENERATION_WEEKS` | 1 | 52 |
| `OTP_EXPIRY_MINUTES` | 1 | 30 |
| `OTP_MAX_ATTEMPTS` | 1 | 10 |
| `QUOTATION_VALID_HOURS` | 1 | 720 |
| `DEPARTURE_DECISION_HOURS` | 1 | 168 |
| `DEPARTURE_MIN_FILL_PERCENT` | 1 | 100 |
| `BALANCE_WAIT_MINUTES` | 5 | 240 |
| `ADDRESS_MAX_KM_FROM_PROVINCE` | 50 | 500 |
| `PICKUP_WINDOW_START_HOUR`, `DELIVERY_WINDOW_START_HOUR` | 0 | 23 |
| `PICKUP_WINDOW_END_HOUR`, `DELIVERY_WINDOW_END_HOUR` | 1 | 24 |
| `STOP_BASE_MINUTES` | 5 | 120 |
| `STOP_MINUTES_PER_HORSE` | 1 | 60 |
| `SCHEDULE_CHANGE_NOTICE_MINUTES` | 5 | 240 |

Bỏ `BALANCE_DUE_DAYS_BEFORE_PICKUP`: số dư không còn trả trước ngày đón.

`ADDRESS_MAX_KM_FROM_PROVINCE` (mặc định 200, nhóm `ADDRESS`): khi lưu sổ địa chỉ, vị trí ghim cách trung tâm tỉnh đã chọn tối đa bao nhiêu km.

Nhóm `SCHEDULE_WINDOW` (24/11/2026): khung giờ đón, giao 6–18, dừng 20 phút cộng 10 phút mỗi con, giờ dời quá 30 phút thì báo khách. `TripSchedulePolicy` tính giờ lượt xe từ các tham số này và đường thật (Valhalla).

Giá trị khởi đầu của các tham số chốt ngày 23/11/2026, thêm bằng migration ở A6b:

| Tham số | Giá trị | Ý nghĩa |
|---|---:|---|
| `DEPARTURE_DECISION_HOURS` | 48 | Mốc quyết định chạy, tính trước giờ xuất phát |
| `DEPARTURE_MIN_FILL_PERCENT` | 30 | Ngưỡng cảnh báo số chỗ đã cọc trên `stall_capacity` |
| `HUB_CHECKIN_HOURS` | 3 | Ngựa phải tới hub trước giờ chuyến trục xuất phát |
| `BALANCE_WAIT_MINUTES` | 30 | Thời gian chờ khách trả số dư (tổng trừ số đã trả) sau khi nhận ngựa |
| `BALANCE_PICKUP_REMINDER_MID_MINUTES`, `BALANCE_PICKUP_REMINDER_LAST_MINUTES` | 15, 25 | Nhắc sau khi nhận tại nhà |
| `BALANCE_HUB_FINAL_REMINDER_HOURS`, `BALANCE_HUB_LOADING_HOURS` | 3, 1 | Mốc nhắc cuối và mốc nhả chỗ, tính trước giờ chạy |
| `BALANCE_HUB_GATEWAY_GRACE_MINUTES` | 5 | Giới hạn chờ xác nhận phiên thanh toán đã mở ở mốc xếp ngựa |
| `BALANCE_IMPORT_HUB_HOURS` | 24 | Hạn giữ miễn phí sau khi ngựa nhập về hub |
| `STABLING_MAX_DAYS`, `STABLING_WRITTEN_NOTICE_DAYS`, `STABLING_MANAGER_NOTICE_DAYS` | 3, 7, 45 | Hạn phản hồi, mốc gửi văn bản và số ngày sau văn bản để báo quản lý |
| `STABLING_REMINDER_HOUR`, `BALANCE_HOLD_MIN_CONTACTS` | 8, 3 | Giờ nhắc hằng ngày và số lần ghi đã liên hệ trước khi tự hủy |
| `MAX_CONTINUOUS_HOURS` | 4 | Thời gian chạy liên tục tối đa |
| `MIN_REST_MINUTES` | 30 | Thời gian nghỉ tối thiểu ở trạm |
| `MAX_DRIVE_HOURS_PER_DAY` | 10 | Giờ lái tối đa mỗi ngày |
| `OVERNIGHT_REST_HOURS` | 10 | Thời gian nghỉ đêm |
| `DOCUMENT_REMINDER_DAYS` | 5 | Nhắc thiếu giấy trước chuyến |
| `AUTHORITY_DELAY_REFUND_MISSES` | 2 | Số lần lỡ chuyến do cơ quan cấp giấy để hoàn cọc vận chuyển khi hủy. Đơn vị `COUNT`, giới hạn 1–10 |
| `PRICE_HOLD_DAYS` | 30 | Giữ giá khi đổi chuyến, tính từ ngày chạy của chuyến vừa lỡ |
| `SETTLEMENT_AUTO_CLOSE_DAYS` | 7 | Hạn khiếu nại trên ứng dụng kể từ lúc đơn sang `DELIVERED`; sau hạn mới xét tự đóng đơn không phát sinh |
| `COMPLAINT_MANAGER_DEADLINE_DAYS` | 14 | Hạn quản lý nhập khiếu nại thay khách kể từ lúc đơn sang `DELIVERED` |
| `SCHEDULE_CHOICE_REMINDER_1..4_DAYS` | 3, 7, 14, 25 | Nhắc chọn lại lịch sau đón hụt |
| `EXTRA_CHARGE_DUE_DAYS` | 7 | Hạn khoản thu thêm từ lúc lập, 1–30 ngày |
| `STABLING_DAILY_FEE` | 1.000.000 đ | Phí giữ tại hub mỗi con mỗi ngày |
| `HANDOVER_FREE_WAIT_MINUTES` | 1 | 1440 |

### 15.5 `route_cache`: bộ đệm kết quả tính lộ trình

Không phải dữ liệu nghiệp vụ. Có thể xóa toàn bộ bất kỳ lúc nào.

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `cache_key` | `varchar(200)` | | PK | Hash của điểm đi, điểm đến và tham số |
| `payload` | `text` | | | Kết quả trả về từ dịch vụ bản đồ |
| `created_at` | `timestamptz` | | default `now()` | Dọn bản ghi cũ theo cột này |

Index: `created_at`.

---

## Dữ liệu dẫn xuất

DB **không có view chứa logic nghiệp vụ**. Mọi phép tính nằm trong code (`policy/` và service).

Có hai cách xử lý giá trị dẫn xuất:
- **Lưu cột** khi giá trị là snapshot của chứng từ đã chốt, hoặc là bộ đếm có khóa bảo vệ. Mỗi cột chỉ do **một service** ghi, trong cùng transaction với thao tác gốc.
- **Tính khi đọc** bằng truy vấn tổng hợp ở repository, rồi đưa qua policy. Áp dụng cho giá trị thay đổi liên tục.

### Cột dẫn xuất được lưu

| Cột | Ý nghĩa | Ai ghi, khi nào | Ràng buộc ở DB |
|---|---|---|---|
| `quotation.total_amount` | Tổng các dòng báo giá | `QuotationService`, lúc tạo hoặc sửa bản DRAFT. Bất biến sau khi SENT | `CHECK (total_amount >= 0)` |
| `quotation.pass_through_amount` | Phần thu hộ (bảo hiểm, lệ phí nhà nước) | Như trên | `CHECK (pass_through_amount BETWEEN 0 AND total_amount)` |
| `quotation.deposit_amount` | Tiền cọc, gồm **toàn bộ** phần thu hộ | Như trên, tính bằng `DepositPolicy` | `CHECK (deposit_amount BETWEEN pass_through_amount AND total_amount)` |
| `cost_item.stabling_days` | Snapshot số ngày của từng dòng phí giữ đã tính | `StablingChargeService`, khi tạo dòng phí theo biểu | Dương, chỉ áp dụng với khoản STABLING có `horse_stay_id` |
| `cost_item.stabling_deposit_credit` | Snapshot phần phí đã cấn vào tiền cọc | `StablingChargeService`, khi chốt phí trên đơn đã hủy dưới khóa đơn | Tổng phần đã cấn của đơn không vượt tiền cọc thực nhận |
| `departure.booked_stall_count` | Số chỗ đã được đặt (khoang rộng 2 chỗ) | `DepartureService`, cộng khi đặt chỗ, trừ khi nhả chỗ (hủy, từ chối, báo giá hết hạn, loại ngựa). Luôn giữ `SELECT … FOR UPDATE` trên dòng `departure` | `CHECK (booked_stall_count BETWEEN 0 AND stall_capacity)`: DB chặn bán quá chỗ |

Công thức:

```
total_amount        = Σ quotation_line.amount
pass_through_amount = Σ quotation_line.amount với is_pass_through = true
deposit_amount      = min(làm tròn lên nghìn đồng của (deposit_rate × (total_amount − pass_through_amount) + pass_through_amount), total_amount)   -- tổng không đổi
```

Phí bảo hiểm và lệ phí thu hộ được **thu đủ ngay khi đặt cọc**. Tỷ lệ cọc chỉ áp lên cước dịch vụ của công ty. Khoản `payment` DEPOSIT lấy `amount = quotation.deposit_amount`.

### Giá trị tính khi đọc

| Giá trị | Nguồn | Nơi tính |
|---|---|---|
| Đã trả, đã hoàn của đơn | `SUM(payment.amount)` với `status = PAID`, tách theo `payment_type` | Repository tổng hợp |
| Chi phí phát sinh khách phải trả | `SUM(cost_item.amount)` với `payer_type IN ('CUSTOMER','CONSIGNEE')` và đã duyệt | Repository tổng hợp |
| Số còn nợ | `total_amount` của báo giá ACCEPTED + chi phí phát sinh khách phải trả − đã trả + đã hoàn | `SettlementPolicy` |
| Ngựa đang trong tay ai | `handover` DONE mới nhất có `handover_horse` thuộc chặng của con ngựa đó. Xét cả `handover_type` (bảng ở mục 10.1): `to_party_id` NULL có thể là công ty, người nhận (DELIVERY) hoặc người gửi (RETURN) | Repository, `HandoverService` |
| Đơn đã đủ giấy bắt buộc chưa | Xem cách tính bên dưới | `DocumentCompletenessPolicy` |

Mọi số tiền cộng với nhau phải cùng `currency_code` với báo giá (quy tắc #22).

### Cách xác định đơn đã đủ giấy

1. Xác định nước đối tác của đơn. Với EXPORT là `country_code` của `booking_address` DELIVERY, với IMPORT là của PICKUP, với INTERPROVINCE là `VN`.
2. Tìm `country_rule` cùng nước, có `direction = booking_type` và có thời hạn hiệu lực chứa `requested_pickup_date`.
3. Lấy các `document_requirement` có `is_mandatory` của quy định đó.
4. Lọc theo mốc kiểm:
   - sang báo giá: chỉ giấy có `submitter_type = CUSTOMER`;
   - xe xuất phát đón (LOCAL không có, INTERPROVINCE, EXPORT): mọi giấy bắt buộc;
   - nhận ngựa ở cửa khẩu (IMPORT): mọi giấy bắt buộc, vì `IMPORT_TRANSPORT_CERT` chỉ có khi ngựa tới nơi.
5. Với mỗi yêu cầu:
   - nếu `document_type.owner_type` là `HORSE` hoặc `BOOKING_HORSE` thì **mỗi** `booking_horse` ACTIVE phải có một `document` APPROVED, còn hạn, có `document_requirement_id` này;
   - nếu là `BOOKING` thì chỉ cần một `document` cho cả đơn.
6. Kết quả gồm danh sách yêu cầu, con ngựa (nếu có) và trạng thái đạt hay chưa. Repository nạp dữ liệu, còn `DocumentCompletenessPolicy` quyết định đạt hay không. Policy này cần unit test với nhiều trường hợp.

---

## Ràng buộc phải kiểm ở service

Các quy tắc dưới đây cần so dữ liệu ở nhiều bảng nên CHECK không làm được. Đặt chúng trong lớp policy hoặc service, theo `AGENTS.md`.

| # | Quy tắc | Bảng liên quan |
|---|---|---|
| 1 | `location` được tham chiếu phải đúng `location_type`: `hub_service_area`, `staff.home_hub_location_id`, `vehicle.home_hub_location_id` là HUB. Hub gốc của xe phải có `is_headquarters = true`. `route.origin_location_id` là HUB hoặc BORDER_GATE. `document_requirement.gate_location_id` là BORDER_GATE | location |
| 2 | `departure.route_id` phải bằng `departure_schedule.route_id` khi có `departure_schedule_id` | departure |
| 3 | Chỉ nhận đặt chỗ khi `departure.status = OPEN`, `now() < booking_cutoff_at` và còn đủ chỗ (`booked_stall_count + số chỗ của đơn ≤ stall_capacity`; khoang thường 1 chỗ, khoang rộng 2 chỗ) | booking, departure |
| 4 | `booking_cutoff_at` phải đủ xa: lấy `permit_lead_days` và `certificate_lead_days` từ `country_rule` | departure, country_rule |
| 5 | Tổng chỗ của xe (`standard_stall_count + 2 × wide_stall_count`) ≤ `vehicle_class.max_stall_count` | vehicle |
| 6 | Các `trip_stop` của một lượt LINEHAUL phải chép từ `route_stop` của đúng tuyến của `departure` | trip_stop |
| 7 | Chuỗi `horse_leg` của một con ngựa phải liền mạch: `to_*` của chặng n bằng `from_*` của chặng n+1 | horse_leg |
| 8 | `board_trip_stop_id`/`alight_trip_stop_id` phải thuộc đúng `vehicle_trip_id` của chặng, và `board.sequence_number < alight.sequence_number` | horse_leg, trip_stop |
| 9 | Tại một thời điểm, số chỗ ngựa trên xe chiếm ≤ tổng chỗ của xe (khoang rộng 2 chỗ). Vách ngăn di động nên không cố định số khoang từng loại | horse_leg, vehicle |
| 10 | Một nhân viên hoặc một xe không được nằm trong hai lượt chạy trùng thời gian. Lượt mới của nhân viên bắt đầu ở nơi lượt trước kết thúc, giống quy tắc #20 của xe | crew_assignment, vehicle_trip |
| 11 | Cột chủ sở hữu được điền trong `document` phải khớp `document_type.owner_type` | document |
| 12 | `document.document_requirement_id` phải cùng `document_type_id` với yêu cầu | document |
| 13 | `incident_plan.handler_party_id` phải có `party_role` phù hợp với `resolution_type`, ví dụ VET_TREATMENT thì bên xử lý có vai trò VET | incident_plan |
| 14 | `horse_insurance` chỉ chuyển sang REVISION_REQUESTED khi `coverage_option = PERSONAL` | horse_insurance |
| 15 | `booking_horse_service.billable_service_id` phải là dịch vụ có `layer = ADD_ON` | booking_horse_service |
| 16 | `booking` chỉ chuyển sang DELIVERED khi mọi `booking_horse` ACTIVE đã có `horse_leg` leg_type DELIVERY hoặc DIRECT ở trạng thái COMPLETED | booking, horse_leg |
| 17 | Đơn giá trong `quotation_line` phải lấy từ `price_item` thuộc đúng `quotation.price_list_id`. Ngoại lệ: dòng ADJUSTMENT nhập tay và dòng INSURANCE lấy `horse_insurance.premium_amount` hãng báo, cả hai có `price_item_id` NULL | quotation_line |
| 18 | Mỗi lần đổi `booking.status` phải ghi một dòng `booking_status_history`, trong cùng transaction. Khi chuyển sang SCHEDULE_CHANGE_REQUIRED, lưu chuyến vừa lỡ và nguyên nhân (COMPANY khi hủy chuyến, NULL khi nhả đơn chưa cọc tại mốc quyết định) | booking, booking_status_history |
| 19 | Chọn cách chạy theo hub, không theo `booking_type`: hub đón trùng hub giao thì chạy thẳng (`DIRECT`, `departure_id` NULL); khác hub thì đi trục (bắt buộc có `departure_id`, `route` nối hai hub hoặc nối hub với cửa khẩu) | booking, hub_service_area, route |
| 20 | `vehicle_trip.start_location_id` phải bằng `end_location_id` của lượt chạy liền trước của cùng xe. Xe tới hub vệ tinh thì phải có lượt chạy về trụ sở, ví dụ chuyến trục chiều ngược | vehicle_trip |
| 21 | Đợt này ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến. Vì vậy `departure.booked_stall_count` đếm theo cả chuyến là đúng | horse_leg, trip_stop, departure |
| 22 | Mọi khoản `payment` và `cost_item` của một đơn dùng cùng `currency_code` với báo giá. Chi phí phát sinh ở nước ngoài quy đổi lúc ghi; hóa đơn gốc lưu ở `document` | payment, cost_item, quotation |
| 23 | `quotation.total_amount`, `pass_through_amount`, `deposit_amount` chỉ được ghi khi báo giá còn DRAFT, và phải khớp tổng các dòng. Tổng dòng ADJUSTMENT không được làm tổng báo giá âm. Báo giá có tổng hoặc tiền cọc bằng 0 thì không gửi được. Báo giá thay thế khi loại ngựa chỉ tính khoản giảm tới mức tiền còn lại ngoài thu hộ | quotation, quotation_line |
| 24 | Thay báo giá đã ACCEPTED (ví dụ khi loại ngựa): chuyển bản cũ sang SUPERSEDED và bản mới sang ACCEPTED trong cùng một transaction | quotation |
| 25 | Mọi thao tác làm đổi số chỗ đã đặt (đặt chỗ, đổi chuyến, hủy, từ chối, báo giá hết hạn, loại ngựa) phải cập nhật `departure.booked_stall_count` trong cùng transaction, khi đang giữ `FOR UPDATE` | departure, booking, booking_horse |
| 26 | Bên được tham chiếu phải có `party_role` còn hiệu lực đúng loại: `horse_leg.agent_party_id` là `DEST_AGENT`; `horse_stay.operator_party_id` là `QUARANTINE_FARM`, `STABLE` hoặc `DEST_AGENT`; `horse_insurance.insurer_party_id` là `INSURER`. `incident_plan.handler_party_id` theo quy tắc #13 | party_role, horse_leg, horse_stay, horse_insurance |
| 27 | Vai trò khớp hồ sơ: tài khoản `CUSTOMER` có đúng một dòng `customer` và không có `staff`; vai trò khác có đúng một dòng `staff` và không có `customer`. Ghi trong cùng transaction | app_user, customer, staff |
| 28 | `handover` loại `AGENT` phải có ít nhất một bên là đối tác | handover |
| 29 | Đơn EXPORT, IMPORT chỉ đi tuyến có cửa khẩu mà `counterpart_country_code` bằng nước đối tác của đơn | booking, route, location |
| 30 | Mọi ngựa ACTIVE của một đơn đi cùng một lượt LINEHAUL | horse_leg, vehicle_trip |
| 31 | Trên tuyến, thời gian chạy giữa hai điểm dừng liên tiếp ≤ `MAX_CONTINUOUS_HOURS`; điểm `REST`, `VITAL_CHECK` dừng ≥ `MIN_REST_MINUTES`; chạy đủ `MAX_DRIVE_HOURS_PER_DAY` thì phải có điểm nghỉ ≥ `OVERNIGHT_REST_HOURS`. Một chiều chặng đón và giao do công ty chở ≤ `MAX_DRIVE_HOURS_PER_DAY / 2`. Nếu thời gian lái vượt `MAX_CONTINUOUS_HOURS` thì bố trí điểm nghỉ | route_stop, trip_stop |
| 32 | Tại hub, kiểm đã trả đủ trước khi hoàn tất HUB_OUT. Tại cửa khẩu, xe trục được chở ngựa đã nhận về hub khi đơn IMPORT chưa trả đủ. Đơn chỉ sang IN_OPERATION khi đã trả đủ. Quy trình quá hạn ở nhà khách và giữ ngựa tại hub theo phương án mục 9. | trip_stop, handover, payment |
| 33 | `quotation.expires_at` là mốc sớm nhất trong 24 giờ sau khi gửi, hết ngày đón và hết ngày hiệu lực báo giá bảo hiểm (nếu có). Mốc `planned_departure_at − DEPARTURE_DECISION_HOURS` chỉ nhả chỗ đơn chưa gửi báo giá. Đơn AWAITING_DEPOSIT giữ chỗ đến khi báo giá hết hạn | quotation, departure |
| 34 | Xe chỉ rời trạm nghỉ khi mọi ngựa trên xe có `horse_health_log` ở điểm đó (gồm `water_intake`, `feed_intake`) và đã nghỉ đủ. Rời sớm phải xác nhận kèm lý do | trip_stop, horse_health_log |
| 35 | Đơn IMPORT: hoàn tất `handover` AGENT ở cửa khẩu chỉ khi đủ giấy bắt buộc, gồm `IMPORT_TRANSPORT_CERT` | handover, document |
| 36 | Xe không chuyển sang MAINTENANCE, RETIRED khi đang chạy hoặc còn lượt chưa hoàn thành. RETIRED không quay lại. Không gán xe khác ACTIVE vào lượt mới | vehicle, vehicle_trip |
| 37 | Người phụ trách một con ngựa là `handled_by` của `handover` DONE mới nhất mà công ty là bên nhận. Không sửa tay. `crew_assignment` chỉ là kế hoạch | handover, crew_assignment |
| 38 | Hộ tống không kết thúc ca hoặc lượt xe khi còn phụ trách ngựa chưa bàn giao. Người ký nhận phải đang trong ca, có mặt ở nơi đó, và đã xem phiếu tóm tắt của từng con | handover, vehicle_trip |
| 39 | Trên cùng một đoạn đường của lượt xe, hai con không trùng `stall_position`. Khoang rộng chiếm hai vị trí liền nhau **bất kỳ** (vách di động). Vị trí cuối không vượt số chỗ của xe (quy tắc #9) | horse_leg, vehicle, trip_stop |
| 40 | Sơ đồ chốt **trước khi lượt xe bắt đầu**: mọi con của lượt xe phải có `stall_position` thì mới cho bắt đầu. Lượt xe đã bắt đầu thì khóa cả sơ đồ, kể cả các con đón ở điểm dừng sau. Lưu sơ đồ phải khóa `vehicle_trip` và lưu cả xe trong một giao dịch | horse_leg, vehicle_trip |
| 41 | Đại lý của chặng nước ngoài phải là đối tác ở đúng nước của địa chỉ đầu đó (`party.country_code` = `booking_address.country_code`). Đơn nhập đã giữ chỗ chuyến trục thì đại lý giao ngựa ở đúng cửa khẩu chuyến đó xuất phát | horse_leg, party, booking_address, route |

### Bổ sung đợt 2

Các phương án đã duyệt trước đợt 2 được chuyển tiếp bằng nguyên nhân đã dùng và giờ duyệt. Khoản khách chịu chưa duyệt được mở cửa sổ phản hồi theo cấu hình. Cờ xác nhận cũ của nhân viên không mang sang chi phí chưa duyệt.

- `incident_plan.customer_agreed_by` (FK tới `app_user.id`) và `customer_agreed_at` (`timestamptz`) cùng có khi chủ đơn đồng ý với phiên bản phương án.
- `CUSTOMER_COST_RESPONSE_MINUTES` mặc định 15 phút, cấu hình từ 5 đến 1440.
- `booking_status_history.previous_departure_id` lưu chuyến đã nhả chỗ khi báo giá hết hạn, để giữ lại chỗ khi mở lại.
