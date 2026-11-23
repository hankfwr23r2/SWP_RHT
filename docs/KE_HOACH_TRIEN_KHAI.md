# Kế hoạch triển khai hệ thống mới (nhánh `redesign/system-v2`)

> Mục đích: chuyển toàn bộ project sang mô hình 59 bảng một cách **chặt chẽ, đúng kỹ thuật, chạy song song được**.
> Đọc kèm: `AGENTS.md` (quy tắc, **bắt buộc**), `docs/DB_SCHEMA_59_BANG.md` (bảng), `docs/NGHIEP_VU_THEO_59_BANG.md` (nghiệp vụ), `docs/NGU_CANH_THIET_KE_LAI.md` (lý do).
> Khi kế hoạch này mâu thuẫn với các file trên, các file trên đúng. Agent phải dừng lại và hỏi.

## Mục lục

1. [Tổng quan phạm vi](#1-tổng-quan-phạm-vi)
2. [Chiến lược kỹ thuật chung](#2-chiến-lược-kỹ-thuật-chung)
3. [Chia luồng và chủ sở hữu](#3-chia-luồng-và-chủ-sở-hữu)
4. [Lịch các đợt và điểm đồng bộ](#4-lịch-các-đợt-và-điểm-đồng-bộ)
5. [Đợt 0: nền móng](#5-đợt-0-nền-móng)
6. [Luồng A: thương mại và danh mục](#6-luồng-a-thương-mại-và-danh-mục)
7. [Luồng B: vận hành](#7-luồng-b-vận-hành)
8. [Đợt cuối: tích hợp và dọn dẹp](#8-đợt-cuối-tích-hợp-và-dọn-dẹp)
9. [Quy trình cho agent](#9-quy-trình-cho-agent)
10. [Bảng tiến độ](#10-bảng-tiến-độ)

---

## 1. Tổng quan phạm vi

| Phần | Thay đổi | Mức |
|---|---|---|
| **DB** | 59 bảng mới, tạo bằng Flyway. Bỏ `ddl-auto=update`, bỏ mọi class Java sửa lược đồ hoặc seed | Làm mới hoàn toàn. **Không chuyển dữ liệu cũ** |
| **BE: hạ tầng** | JWT, refresh cookie, Turnstile, mail, Cloudinary, VNPAY (giao tiếp cổng), Valhalla, bộ đệm lộ trình, xử lý lỗi, Clock, OpenAPI | **Giữ**, chỉ di chuyển vào đúng package (`client/`, `policy/`) |
| **BE: domain** | 37 entity, 33 repository, 41 service impl, 37 controller, 118 DTO | **Viết lại** theo bảng mới. Tận dụng logic theo bảng ở từng task |
| **FE: hạ tầng** | Hai ứng dụng, router dạng bảng có loader, `apiClient`, auth, i18n, bản đồ | **Giữ**, thêm design token, `shared/ui`, client sinh từ OpenAPI, lint chiều phụ thuộc |
| **FE: màn hình** | Khoảng 60 trang | **Viết lại** theo luồng mới, tận dụng phần bản đồ, chụp ảnh, form |
| **FE: logic nghiệp vụ** | Trong `shared/lib`: `booking`, `pricing`, `insuranceMoney`, `incidentCosts`, `deadlines`, `horseLocation`, `trip`. Trong `shared/config`: các file `*rules*`, `public-pricing`, danh mục địa lý | **Bỏ**. Backend tính, frontend hiển thị |

**Nguyên tắc tận dụng:** code cũ chỉ còn trên `main`. Agent đọc bằng `git show origin/main:<đường dẫn>`, **chép có chọn lọc rồi sửa cho đúng quy tắc**, không chép nguyên file.

---

## 2. Chiến lược kỹ thuật chung

### 2.1 Nhánh

```
main  (bản cũ, vẫn deploy được)
 └─ redesign/system-v2          nhánh tích hợp. Chỉ nhận PR, không commit trực tiếp
     ├─ rd/p0-db                 nhánh task: rd/<mã task>-<mô tả ngắn>
     ├─ rd/a3-pricing
     └─ rd/b2-handover
```

- Mỗi task một nhánh `rd/<mã>-<mô-tả>`, tạo từ `redesign/system-v2` mới nhất, PR ngược về `redesign/system-v2`.
- PR nhỏ: một task có thể chia nhiều PR (BE trước, FE sau). Mỗi PR phải tự xanh CI.
- Rebase lên `redesign/system-v2` **mỗi ngày** và trước khi mở PR.
- Merge vào `main` chỉ một lần ở đợt cuối, khi đạt mốc M4.

### 2.2 Migration Flyway

- Đợt 0 tạo `V1`…`V14` theo 14 bậc trong "Thứ tự tạo bảng" của `DB_SCHEMA_59_BANG.md`.
- Sau đợt 0, mọi migration đặt tên theo thời điểm: `V<yyyyMMddHHmm>__<mo_ta>.sql`, ví dụ `V202612031015__add_vehicle_note.sql`.
- Trước khi merge, nếu `redesign/system-v2` đã có migration với số lớn hơn của mình, **đổi số của mình thành thời điểm hiện tại**.
- Hai thư mục:
  - `db/migration`: lược đồ và dữ liệu nền bắt buộc (vai trò, loại khoang, hạng xe, dịch vụ…), chạy ở mọi môi trường;
  - `db/seed-dev`: dữ liệu mẫu (tài khoản thử, tuyến, chuyến, đơn ở các trạng thái), chỉ chạy ở profile `local` và `test` qua `spring.flyway.locations`. Dùng **repeatable migration** `R__dev_seed_<nhóm>.sql`: Flyway chạy file `R__` sau mọi migration có số, và chạy lại khi nội dung đổi. Câu lệnh phải idempotent (`INSERT … ON CONFLICT DO NOTHING`). Không dùng `V<số>` cho seed dev, vì seed sẽ chạy trước các migration thêm cột về sau.
- Đổi lược đồ thì sửa `DB_SCHEMA_59_BANG.md` **trong cùng PR**.

### 2.3 Hợp đồng API (OpenAPI → TypeScript)

1. Backend có test `OpenApiExportTest` ghi `frontend/openapi/openapi.json` từ springdoc (đã có `springdoc-openapi`, `OpenApiConfig`).
2. Frontend dùng **openapi-typescript** sinh **kiểu dữ liệu** vào `src/shared/api/generated/schema.ts` từ file đó. Hàm gọi API viết tay, mỏng, trong `shared/api/<tài-nguyên>.ts`, dùng kiểu trong `generated/` và gọi qua `apiClient` hiện có, nên giữ nguyên cơ chế token và refresh (AGENTS.md mục 8.1).
   - Công cụ là devDependency khóa phiên bản trong `package-lock.json`. Nó khai báo peer `typescript@^5`, dự án dùng TypeScript 6 nên `package.json` có `overrides` để công cụ dùng TypeScript của dự án (kết quả đã so: giống hệt khi chạy với TypeScript 5). Có bản hỗ trợ TypeScript 6 thì bỏ `overrides`.
   - Không chọn orval: orval sinh cả hàm gọi API, trùng vai trò với `shared/api/<tài-nguyên>.ts` mà AGENTS.md mục 8.1 quy định.
3. Lệnh: `npm run api:gen`. CI chạy lại lệnh và `git diff --exit-code`, lệch là đỏ.
4. **Không bao giờ sửa tay hay gộp xung đột bằng tay** `openapi.json` và `generated/`: khi xung đột, chạy lại export và `api:gen`.

### 2.4 Chủ sở hữu dữ liệu

- **Ghi:** mỗi bảng có **một service chủ sở hữu** (mục 3.2). Service khác muốn ghi phải gọi service chủ sở hữu.
- **Đọc:** service nào cũng được đọc bảng của luồng khác qua repository.
- Các điểm gọi chéo giữa hai luồng là **interface trong `service/`**, được tạo ở đợt 0 (mục 5, P0.3). Luồng chưa có implementation thì luồng kia test bằng mock.

### 2.5 File dễ xung đột

| File | Quy tắc |
|---|---|
| `AGENTS.md`, `docs/*.md` | Chỉ sửa trong PR riêng có tiền tố `docs:`, cả hai thành viên duyệt |
| `frontend/openapi/openapi.json`, `shared/api/generated/` | Sinh lại, không gộp tay (mục 2.3) |
| `apps/*/routes.tsx`, `layouts/staffMenus.ts` | Chỉ thêm dòng. Xung đột thì giữ cả hai |
| i18n | Tách theo namespace: `shared/i18n/locales/vi/<namespace>.ts` và `locales/en/<namespace>.ts`. Mỗi tính năng sở hữu namespace của mình |
| `SecurityConfig` | Ưu tiên `@PreAuthorize` trên controller để hạn chế sửa file này |
| `shared/ui/*`, `tokens.css` | Chủ sở hữu là thành viên B. Thành viên A cần thêm thì mở PR nhỏ riêng |
| Test fixture dùng chung (`TestData`) | Chỉ thêm method, không sửa method đã có |

---

## 3. Chia luồng và chủ sở hữu

### 3.1 Hai luồng

| | **Luồng A: Thương mại và danh mục** | **Luồng B: Vận hành** |
|---|---|---|
| Phạm vi nghiệp vụ | Dữ liệu nền, đặt đơn, thẩm định, bảo hiểm, giấy tờ, báo giá, thanh toán, quyết toán | Điều độ xe, chặng, điểm dừng, giao nhận, lưu trú, sức khỏe, sự cố, chi phí, theo dõi, báo cáo |
| Giai đoạn nghiệp vụ | GĐ1–GĐ5 (phần thương mại), GĐ9–GĐ10 | GĐ5 (phần điều độ), GĐ6–GĐ8, nhánh phụ 5.1, 5.3 |
| Backend | `LocationService`, `RouteService`, `DepartureService`, `VehicleService`, `CustomerService`, `PartyService`, `PriceListService`, `PricingService`, `RegulationService`, `BookingService`, `BookingStatusService`, `InsuranceService`, `DocumentReviewService`, `QuotationService`, `PaymentService`, `SettlementService`, `RatingService` | `DispatchService`, `HorseLegService`, `TripStopService`, `HandoverService`, `HorseStayService`, `HealthLogService`, `IncidentService`, `CostItemService`, `TrackingService`, `ReportService` |
| Frontend | `apps/customer` (trừ khối theo dõi), `backoffice/features/admin`, `manager` (tiếp nhận, báo giá, quyết toán), `specialist` | `backoffice/features/coordinator`, `driver`, `escort`, `manager` (sự cố, duyệt chi phí, báo cáo), `shared/components/HorseJourney` |
| Đợt 0 | Backend nền: DB, entity nhóm 1–8, auth | Backend chéo (entity nhóm 9–15, document, notification, audit, ArchUnit, OpenAPI) và toàn bộ frontend nền |

Có **3 thành viên** thì tách luồng A thành A-BE và A-FE (khách hàng), giữ nguyên ranh giới task.

### 3.2 Bảng → service chủ sở hữu

| Nhóm | Bảng | Service chủ sở hữu | Luồng / task |
|---|---|---|---|
| 1 | `country`, `province`, `location`, `hub_service_area` | `LocationService` | A1 |
| 2 | `app_user`, `role`, `otp` | `AuthService`, `AccountService` | P0 (auth), A2 (quản trị tài khoản) |
| 2 | `staff` | `AccountService` | A2 |
| 2 | `customer`, `customer_address` | `CustomerService` | P0 (tạo khi đăng ký), A2 |
| 2 | `party`, `party_role` | `PartyService` | A2 |
| 3 | `horse_type`, `horse` | `HorseService` | A5 |
| 4 | `route`, `route_stop`, `departure_schedule`, `departure_schedule_day` | `RouteService` | A1 |
| 4 | `departure` (kể cả `booked_stall_count`) | `DepartureService` | A1 |
| 5 | `vehicle_class`, `stall_type`, `vehicle` | `VehicleService` | A2 |
| 6 | `billable_service`, `price_list`, `price_item` | `PriceListService` | A3 |
| 7 | `country_rule`, `document_type`, `document_requirement` | `RegulationService` | A4 |
| 8 | `booking`, `booking_party`, `booking_address`, `booking_horse`, `booking_horse_service`, `terms_acceptance` | `BookingService` | A5 |
| 8 | `booking.status`, `booking_status_history` | `BookingStatusService` | P0 (interface), A5 |
| 8 | `booking_rating` | `RatingService` | A8 |
| 9 | `vehicle_trip`, `trip_stop` (kế hoạch), `crew_assignment`, `horse_leg` | `DispatchService`, `HorseLegService` | B1 |
| 9 | `trip_stop` (giờ thực tế) | `TripStopService` | B2 |
| 9 | `horse_stay`, `horse_health_log` | `HorseStayService`, `HealthLogService` | B3 |
| 10 | `handover`, `handover_horse`, `handover_document` | `HandoverService` | B2 |
| 11 | `horse_insurance` | `InsuranceService` | A5 (bản nháp), A6 |
| 12 | `quotation`, `quotation_line` | `QuotationService` | A7 |
| 12 | `payment`, `payment_transaction` | `PaymentService` | A7 |
| 13 | `incident`, `incident_plan` | `IncidentService` | B4 |
| 13 | `cost_item` | `CostItemService` | B4 |
| 14 | `document` | `DocumentService` (tải lên, gắn chủ sở hữu), `DocumentReviewService` (duyệt) | P0, A6 |
| 15 | `notification`, `notification_recipient` | `NotificationService` | P0 |
| 15 | `audit_log` | `AuditService` | P0 |
| 15 | `system_config` | `SystemConfigService` | A2 |
| 15 | `route_cache` | `RouteCacheService` | P0 (giữ code cũ) |

### 3.3 Hợp đồng gọi chéo (tạo interface ở P0.3)

| Interface (trong `service/`) | Ai cài đặt | Ai gọi | Dùng khi |
|---|---|---|---|
| `BookingStatusService.transition(bookingId, toStatus, reason, actor)` | A | A, B | B chuyển đơn sang `IN_OPERATION` (khách đã trả đủ và xe rời nơi nhận ngựa) và `DELIVERED` (chặng giao cuối cùng xong, hoặc ký trả ngựa tại hub ở đầu SELF) |
| `BookingHorseService.exclude(bookingHorseId, reason, actor)` | A | B | Ngựa không đạt khi nhận (GĐ6), kể cả tại hub. A nhả chỗ và tạo báo giá mới |
| `DepartureService.reserve/release(departureId, stalls)` | A | A | Mọi thay đổi `booked_stall_count`. `stalls` là số **chỗ** (khoang rộng 2 chỗ) |
| `DocumentService.attach(file, documentTypeCode, ownerSubject, ownerId, uploadedByUserId)` | P0 | A, B | Ảnh hiện trường, biên bản giao nhận, biên lai, giấy tờ |
| `PaymentService.createExtraCharge(bookingId, costItemIds, actorUserId)` | A | A (quyết toán) | Lập khoản thu thêm |
| `CostItemService.linkExtraPayment(costItemIds, paymentId)` | B | A | Gắn khoản thu thêm vào chi phí |
| `NotificationService.notify(...)`, `AuditService.record(...)` | P0 | A, B | Mọi giai đoạn |
| `DocumentCompletenessService.isComplete(bookingId)` | A | B | Khi xe xuất phát đón (GĐ5), khi nhận tại hub nếu SELF, hoặc khi nhận tại cửa khẩu với IMPORT |
| `PaymentReadinessService.isPaidInFull(bookingId)` | A | B | Khi xe **rời** nơi nhận ngựa (GĐ6) |
| `PaymentService.requestBalance(bookingId, receivedAt)` (chưa có, A7) | A | B | Lập khoản số dư khi nhận ngựa xong |
| `BookingCancellationService.cancelUnpaidAtPickup(bookingId, actorUserId)` (chưa có, A8) | A | B | Trả ngựa tại chỗ khi quá thời gian chờ |

---

## 4. Lịch các đợt và điểm đồng bộ

```
Đợt 0   P0.1 (A) ─► P0.2 (A ∥ B) ─► P0.3 (A ∥ B)        song song: P0-FE (B)     ──► M0
Đợt 1   A1 Mạng lưới, lịch  +  A2 Xe, nhân sự, đối tác  ∥  B1 Điều độ                ──► M1
Đợt 2   A3 Bảng giá  +  A4 Quy định, giấy tờ             ∥  B2 Điểm dừng, giao nhận
Đợt 3   A5 Đặt đơn                                        ∥  B3 Lưu trú, sức khỏe       ──► M2
Đợt 4   A6 Thẩm định, bảo hiểm  +  A7 Báo giá, thanh toán ∥  B4 Sự cố, chi phí  +  B5 Giao thất bại
Đợt 5   A8 Quyết toán, đóng đơn                           ∥  B6 Theo dõi, báo cáo, thông báo ──► M3
Đợt 6   Tích hợp end-to-end, dọn code cũ, ERD                                          ──► M4
```

Luồng B không phải chờ luồng A: đợt 0 đã có đủ entity, và `db/seed-dev` có sẵn tuyến, chuyến, xe, đơn ở trạng thái `CONFIRMED` để điều độ.

| Mốc | Điều kiện đạt (kiểm bằng test hoặc demo) |
|---|---|
| **M0** | App khởi động trên DB mới với `ddl-auto=validate`. Đăng nhập được bằng tài khoản seed. ArchUnit và lint xanh. `npm run api:gen` chạy được. Có bộ `shared/ui` và layout mới |
| **M1** | Admin dựng được tuyến, lịch, xe. Scheduler sinh `departure`. Điều phối viên tạo lượt xe và gán ngựa cho đơn seed |
| **M2** | Khách đặt đơn chọn chuyến, có xem trước giá. Tài xế đón ngựa, quét chip, giao vào hub. Đơn chuyển `IN_OPERATION` |
| **M3** | Chạy trọn hai ví dụ trong `NGHIEP_VU_THEO_59_BANG.md` mục 6 (bản 23/11/2026): Phnom Penh (quốc tế) và Đồng Nai → Huế (trục nội địa), kèm một ca chạy thẳng, một ca sự cố và một ca giao thất bại |
| **M4** | E2E mới xanh. Đã xóa hết code cũ. ERD sinh từ DB. Merge vào `main` |

**Đồng bộ:** cuối mỗi đợt, hai thành viên cùng rebase, chạy Quality Gate đầy đủ trên `redesign/system-v2`, demo mốc, cập nhật bảng tiến độ (mục 10).

---

## 5. Đợt 0: nền móng

Mục tiêu: dựng sẵn **mọi thứ cả hai luồng cùng cần**, để từ đợt 1 hai người không phải chờ nhau.

### P0.1 Dọn code cũ và tạo DB (A, làm trước, chặn các việc khác)

**Xóa** (vẫn đọc được trên `main`):
- Backend:
  - toàn bộ `entity/`, `repository/`;
  - controller, service, impl và DTO domain;
  - các class migration và seeder: `BookingCodeMigration`, `*StaffCodeMigration`, `InternalExpenseReviewMigration`, `HorseMicrochipUniqueIndex`, `DataInitializer`, `DocumentRequirementSeeder`, `FixedRouteSeeder`, `ReferenceDataSeeder`;
  - `ModelMapperConfig` và dependency `modelmapper`;
  - `util/BookingDetailsJson`, `BookingHistory`, `QuoteTotals`, `QuoteLinePolicy`, `BookingStatusLabels`, `BookingTripRules`, `CustomerBookingRules`, `ClearanceDocumentPolicy`, `DocumentRequirementPolicy`, `IncidentCostPolicy`;
  - test của các phần trên.
- Việc xóa này là phần **đã được duyệt** của kế hoạch, không vi phạm quy tắc "không xóa test" trong `AGENTS.md` mục 10.1.

**Giữ và di chuyển:**

| Code cũ | Chỗ mới | Sửa gì |
|---|---|---|
| `security/*` | Giữ | `JwtUtils` lấy vai trò từ `app_user.role_code` |
| `config/SecurityConfig`, `CorsConfig`, `CacheConfig`, `JacksonConfig`, `ClockConfiguration`, `CloudinaryConfig`, `VnpayProperties`, `OpenApiConfig` | Giữ | Bỏ các đường dẫn API cũ khỏi `SecurityConfig` |
| `RoadRoutingServiceImpl` (gọi Valhalla) | `client/ValhallaClient` + `RoadRoutingService` | Phần gọi HTTP chuyển sang client, service chỉ điều phối |
| `RouteCacheServiceImpl`, `TerritoryLookupServiceImpl` | Giữ | Map sang bảng `route_cache` mới |
| `CloudinaryStorageServiceImpl` | `client/CloudinaryClient` | |
| `MailDeliveryServiceImpl` (SMTP, Brevo) | `client/MailClient` + `MailService` | |
| `VnpayServiceImpl`, `util/VnpayUtil` | `client/VnpayClient` | Phần nghiệp vụ thanh toán làm lại ở A7 |
| `util/Polyline`, `RouteGeometry`, `RestPlaceNamer`, `ContactPatterns`, `BookingCodes` | Giữ trong `util/` | Không mang nghiệp vụ |
| `util/JourneyPlanner` | `policy/JourneyPlanPolicy` | Hàm thuần nhận tham số, không đọc giờ hệ thống |
| `util/MicrochipPolicy`, `DepositPaymentWindow`, `PaymentAmountLimits`, `HorseInsurancePolicy`, `BookingSchedulePolicy`, `TermsDocuments` | `policy/` | Giữ test cũ đi kèm. `HorseInsurancePolicy` sửa lại ở A6 |
| `exception/*`, `dto/common/*`, `HealthCheckController` | Giữ | |

**Tạo DB:**
- Thêm `spring-boot-starter-flyway` và `flyway-database-postgresql` (Spring Boot 4 quản lý phiên bản). Đặt `spring.jpa.hibernate.ddl-auto=validate`.
- Viết `V1__tier1.sql` … `V14__tier14.sql` đúng **từng cột, CHECK, UK, FK, index, EXCLUDE** trong `DB_SCHEMA_59_BANG.md`, kể cả `btree_gist`, `booked_stall_count`, các cột tiền của `quotation`.
- `V15__reference_data.sql`: vai trò, `stall_type`, `vehicle_class`, `billable_service`, `document_type`, 3 quốc gia, tỉnh.
- `db/seed-dev/R__dev_seed_base.sql`: tài khoản mỗi vai trò, `HUB-HCM`, `HUB-DN`, trại cách ly, cửa khẩu, tuyến HCM-PNH / HCM-DN / DN-HCM, lịch, xe, bảng giá mẫu.
- Test `MigrationTest` (Testcontainers PostgreSQL 16): migrate từ đầu, kiểm vài CHECK quan trọng bằng câu INSERT sai (ví dụ `booked_stall_count > stall_capacity` phải lỗi).
- Annotation `@PostgresIntegrationTest` (SpringBootTest + `TestcontainersConfiguration` có `@ServiceConnection` + profile `test` nạp `db/seed-dev`). **Mọi test cần DB dùng annotation này**, không tự dựng container.

**Xong khi:** `mvnw verify` xanh với app chỉ còn hạ tầng, `MigrationTest` xanh.

**Ghi chú sau khi làm P0.1** (khác với dự kiến ban đầu):
- `DepositPaymentWindow`, `HorseInsurancePolicy`, `BookingSchedulePolicy` phụ thuộc entity và DTO cũ nên đã xóa. Làm lại từ `main` ở A7, A6, A1.
- `RoutePreviewServiceImpl` phụ thuộc domain cũ nên đã xóa. A1 làm lại.
- Bảng `route_cache` (nhóm 15) đã có entity `RouteCacheEntry` và repository ở P0.1, vì hạ tầng định tuyến được giữ cần nó. P0.2-B bỏ qua bảng này.
- `JwtAuthenticationFilter` tạm bỏ bước từ chối token của tài khoản bị khóa. **P0.3-A phải làm lại** trên bảng `app_user`.
- Dữ liệu đơn mẫu (trạng thái `CONFIRMED`, để luồng B điều độ) chuyển sang P0.3-A.
- `docker-compose.yml` dùng volume mới `postgres_data_v2`: Flyway không chạy trên DB cũ đã có bảng. Chạy ngoài Docker thì tạo DB mới và trỏ tới trong `application-local.properties`, đặt `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed-dev` để có dữ liệu mẫu.

### P0.2 Entity và repository (A ∥ B, sau P0.1)

- **A:** nhóm 1–8. **B:** nhóm 9–15.
- Việc làm thuần cơ học: mỗi bảng một entity, enum đặt ở `entity/enums/`, quan hệ dạng `@ManyToOne(fetch = LAZY)`, kiểu đúng quy ước (`Long`, `Instant`, `BigDecimal`). Cột dẫn xuất ghi comment `// derived: <service>`, snapshot ghi `// snapshot: <lý do>`.
- Mỗi entity có một `*Repository`, chưa có query nghiệp vụ.
- Test: app khởi động với `validate` (sai một cột là đỏ).

### P0.3 Phần dùng chung của backend (A ∥ B)

**A:**
- **Auth** trên bảng mới: đăng ký (tạo `app_user` vai trò CUSTOMER + `customer`), đăng nhập, refresh, OTP (một bảng `otp` có `purpose`), quên mật khẩu. Tận dụng `AuthServiceImpl`, `EmailVerificationServiceImpl`, `PasswordResetServiceImpl`, `ExpiredOtpCleanup`, test `UnifiedOtpIntegrationTest`.
- Các interface gọi chéo ở mục 3.3, chưa cần cài đặt.
- `TestData` fixture dùng chung: tạo nhanh người dùng, đơn ở từng trạng thái, chuyến.

**B:**
- `DocumentService` (tải lên Cloudinary, ghi `document`, kiểm cột chủ sở hữu theo `document_type.owner_type`), dùng lại `FileUploadController`, `shared/lib/documentUpload`.
- `NotificationService` (`notification` + `notification_recipient`), `AuditService` (`audit_log`).
- **ArchUnit** (`archunit-junit5`): đủ các luật trong `AGENTS.md` mục 10.3.
- `OpenApiExportTest` ghi `frontend/openapi/openapi.json`.

### P0-FE Nền frontend (B, chạy song song với P0.1–P0.3)

| Việc | Chi tiết | Tận dụng |
|---|---|---|
| Design token | Mở rộng `tokens.css` theo `AGENTS.md` mục 8.5. Bỏ `manager-theme.css` (`.mgr`) | Bảng màu hiện có, tương phản đã đạt 4.5:1 |
| `shared/ui` | `Button`, `Input`, `Textarea`, `Select`, `Field` (nhãn + lỗi), `Modal`, `ConfirmDialog`, `Table` (tự chuyển thành thẻ trên điện thoại), `Badge`, `StatusBadge`, `Card`, `Tabs`, `Stepper`, `Skeleton`, `EmptyState`, `ErrorState`, `Toast`, `PageHeader` | `Modal.tsx`, `Select.tsx`, `toast.tsx`, `FormSelect`, `FileField`, các class `.btn`/`.card` trong `base.css` |
| `shared/hooks` | Chuyển `useLoad`, `useAutoRefresh`, `useNow`, `usePagination`, `useRoadRoute` | Giữ nguyên logic |
| Layout | `StaffLayout`, `StaffSidebar`, `StaffTopbar`, layout khách dựng lại bằng `shared/ui` | Cấu trúc layout hiện có |
| Kiểu API sinh tự động | Cài `openapi-typescript` (mục 2.3), lệnh `api:gen`, kiểm `git diff` của `openapi.json` và `generated/` trong CI | `shared/api/client.ts` giữ nguyên |
| Alias | Thêm `@customer/*`, `@backoffice/*` vào `vite.config.ts` và `tsconfig.app.json`, đúng `AGENTS.md` mục 8.3 | `@shared` đã có |
| Lint | oxlint `no-restricted-imports` theo `AGENTS.md` mục 8.3. Script `scripts/check-style.mjs` chặn mã màu và `style={{` trong file **mới** (file cũ nằm trong danh sách ngoại lệ, danh sách này chỉ được rút ngắn) | |
| i18n | Tách `vi.ts`/`en.ts` (khoảng 1700 dòng mỗi file) theo namespace | Nội dung chuỗi hiện có |
| Test component | Thêm `@testing-library/react`, `jsdom`, cho vitest chạy cả `*.test.tsx`. Mỗi component `shared/ui` có test | |
| Trang mẫu | Một trang "Thư viện giao diện" trong backoffice (chỉ ADMIN) hiển thị mọi component và 4 trạng thái | |

**Ghi chú sau khi làm P0.2-A:**
- Bảng `billable_service` (migration hiện là `service`) có entity `BillableService`, bảng `booking_horse_service` có entity `BookingHorseAddOn`: tránh trùng annotation `@Service` của Spring và interface `BookingHorseService`.
- Khóa ghép dùng `@EmbeddedId` + `@MapsId`; `staff` và `booking_rating` dùng `@MapsId` trên `@OneToOne`. Khi lưu entity có khóa ghép, đặt đủ giá trị khóa trước khi gọi `save`.
- Chỉ có quan hệ `@ManyToOne`/`@OneToOne` ở phía giữ FK, chưa có collection. Task nào cần `@OneToMany` thì thêm trong task đó.
- Cột có mặc định `now()` dùng `@Generated` + `insertable = false`: DB đặt giờ. Cột `char(n)` dùng `@JdbcTypeCode(SqlTypes.CHAR)`, cột `smallint` dùng `Short`.
- Profile `test` tắt lazy initialization để `contextLoads` bắt lỗi của mọi bean và truy vấn repository.

**Ghi chú sau khi làm P0.3-A:**
- Đăng ký tách ra `CustomerRegistrationService` để `AuthServiceImpl` không vượt 10 dependency (`AGENTS.md` mục 7.1). Dọn OTP hết hạn nằm ở `EmailVerificationService`.
- Vai trò lấy từ `app_user.role_code`, mỗi tài khoản một vai trò. Token có claim `role` và `roles` (danh sách một phần tử, giữ cho giao diện). Đường dẫn `/auth/**` giữ như cũ.
- `JwtAuthenticationFilter` từ chối token khi `app_user.status` khác ACTIVE hoặc nhân viên đã TERMINATED.
- Thời hạn OTP và số lần nhập sai đọc từ `system_config` qua `SystemConfigService` (entity `SystemConfig` tạo ở P0.3-A, P0.2-B bỏ qua bảng này). Task A2 thêm phần quản trị tham số.
- Không ghi mã OTP ra log. Máy chưa cấu hình mail thì không gửi được mã; tài khoản trong seed dev đều đã xác thực email.
- 10 interface gọi chéo ở mục 3.3 đã có trong `service/`, mỗi file ghi rõ luồng cài đặt và luồng gọi.
- `TestData` (`support/TestData`, được `@PostgresIntegrationTest` nạp sẵn): tạo nhanh tài khoản, khách, nhân viên, đơn, chuyến đột xuất. Chỉ thêm method mới, không sửa method cũ.
- `db/seed-dev/R__dev_seed_bookings.sql`: chuyến cho 8 tuần tới và 3 đơn CONFIRMED của `customer1.dev` (`DEV-DIRECT-001` chạy thẳng, `DEV-DOMESTIC-001` trục HCM-DN, `DEV-EXPORT-001` trục HCM-PNH), có ngựa, bảo hiểm đã xác nhận, báo giá ACCEPTED và đã trả đủ. Chuyến do seed sinh trùng lịch, nên test tạo chuyến riêng thì dùng chuyến đột xuất (`TestData.departure`).

**Mốc M0** khi P0.1–P0.3 và P0-FE đã merge.

---

## 6. Luồng A: thương mại và danh mục

Mỗi task ghi: **BE** (backend), **FE** (frontend), **Tận dụng** (code cũ, đọc bằng `git show origin/main:<path>`), **Xong khi**.

### A1. Mạng lưới, tuyến, lịch chạy, chuyến (đợt 1)

> **Ghi chú khi làm A1:** làm backend trước (2 PR: danh mục, chuyến). Frontend A1 làm sau khi P0-FE và P0.3-B merge.
> Tuyến được xuất phát từ cửa khẩu (`route.origin_location_id`), có tuyến chiều nhập `MOCBAI-HCM` trong seed dev.
> `BookingStatusService` (và `BookingStatusPolicy`) cài đặt sớm ở PR2 của A1 vì hủy chuyến cần đổi trạng thái đơn.
> Gợi ý chuyến tách thành `DepartureSuggestionService` để `DepartureServiceImpl` không vượt 10 dependency. Loại đơn được kiểm khớp với nơi đón, nơi giao (`HubResolutionPolicy.requireConsistentBookingType`). Cửa khẩu được xếp theo khoảng cách chim bay tới điểm ở nước ngoài, không lọc theo ngưỡng.
> Sinh chuyến dùng `INSERT ... ON CONFLICT DO NOTHING` nên chạy lại hoặc nhiều máy chủ cùng chạy không tạo trùng. `DepartureScheduler`: sinh lúc 00:15 giờ Việt Nam, đóng chuyến qua giờ chốt mỗi 15 phút (cấu hình được bằng `app.departure.*`).

- **BE:**
  - `LocationService` (location, `hub_service_area`, kiểm `UNIQUE (province_code)`), `RouteService` (route, route_stop, `departure_schedule`, `_day`);
  - `DepartureService`: sinh chuyến từ lịch (scheduler hằng ngày, idempotent nhờ `UNIQUE (departure_schedule_id, departure_date)`), đóng chuyến khi tới giờ chốt, tạo chuyến đột xuất, hủy chuyến (đơn → `SCHEDULE_CHANGE_REQUIRED` qua `BookingStatusService`), `reserve/release` có `FOR UPDATE`;
  - `HubResolutionPolicy`: tra hub đón/giao và quyết định chạy thẳng hay đi trục (quy tắc #19);
  - endpoint gợi ý chuyến phù hợp cho khách.
- **FE:** admin quản lý điểm mạng lưới, vùng phục vụ, tuyến (bản đồ, điểm dừng, `arrival_offset_minutes`/`dwell_minutes`), lịch chạy, danh sách chuyến.
- **Tận dụng:**
  - `LocationServiceImpl`, `RouteServiceImpl`, `RoutePreviewServiceImpl`, `CoordinatorRouteController`;
  - `JourneyPlanPolicy` (P0.1) để tính `arrival_offset_minutes` khi dựng tuyến;
  - FE `admin/routes/AdminRoutesPage.tsx` (1017 dòng): **tách** phần bản đồ thành `shared/components/RouteEditorMap`, phần form viết lại bằng `shared/ui`;
  - `shared/lib/fixedRouteMatch.ts` (+ test): **chuyển xuống backend** thành `DepartureMatchPolicy` (hành lang ≤ 80 km, đi vòng ≤ 1,5 lần);
  - `BookingScheduleScheduler` → `DepartureScheduler`.
- **Xong khi:**
  - test policy: chọn hub, khớp hành lang;
  - test scheduler chạy hai lần không sinh trùng;
  - test hai đơn đặt cùng khoang cuối thì chỉ một đơn thành công;
  - các màn hình admin có đủ 4 trạng thái.

### A2. Xe, nhân sự, đối tác, tham số (đợt 1)

- **BE:**
  - `VehicleService` (kiểm tổng chỗ ≤ `vehicle_class.max_stall_count`, hub gốc là trụ sở). Đổi trạng thái xe và bỏ hạn đăng ký: A6b;
  - `AccountService` (tài khoản và `staff`);
  - `CustomerService` (hồ sơ khách cá nhân, tổ chức và sổ địa chỉ);
  - `PartyService` (đối tác và `party_role`);
  - `SystemConfigService` (chỉ tham số kỹ thuật).
- **FE:** admin quản lý xe, tài khoản nhân viên, đối tác (đại lý, thú y, chuồng gửi, bảo hiểm), tham số. Xe không có giấy tờ.
- **Tận dụng:** `AdminAccountServiceImpl`, `SystemConfigServiceImpl` (bỏ các khóa giá), FE `admin/accounts`, `admin/horse-types`. Bỏ cách đọc sức chứa từ chuỗi `vehicle_type`.
- **Xong khi:** đủ test CRUD và quy tắc. Đổi tham số thì ghi `audit_log`.

### A3. Bảng giá và tính giá (đợt 2)

- **BE:**
  - `PriceListService`: CRUD; chỉ kích hoạt được khi không trùng hiệu lực (có sẵn EXCLUDE ở DB);
  - `PricingPolicy` (hàm thuần): đầu vào là cách chạy, km các chặng, tuyến, hạng xe, loại khoang, số ngày đón sớm, dịch vụ thêm; đầu ra là các dòng giá kèm `price_item_id`. Áp dụng các dòng `DIRECT`, `PICKUP`, `DELIVERY`, `LINEHAUL`, `FUEL`, `QUARANTINE`, `DOCS`;
  - `PricingService`: nạp `price_list` đang hiệu lực, gọi Valhalla lấy km, gọi policy;
  - endpoint `POST /pricing/preview`.
- **FE:** admin quản lý bảng giá (bậc km, giá theo tuyến, hạng xe). Khối xem trước giá trong luồng đặt đơn.
- **Tận dụng:**
  - **công thức** trong FE `shared/lib/pricing.ts`, `booking.ts` (`quoteLines`) và **test** `pricing.test.ts`, `booking.test.ts`: chuyển thành test Java cho `PricingPolicy` (giữ ca kiểm, đổi nguồn tham số);
  - FE `admin/pricing/AdminPricingPage.tsx`: giữ bố cục, đổi dữ liệu sang `price_list`/`price_item`;
  - `RoadRoutingService`.
- **Xong khi:** policy có test cho từng loại dòng và từng bậc km. Frontend không còn công thức giá nào. Xem trước giá khớp với báo giá thật.

### A4. Quy định và yêu cầu giấy tờ (đợt 2)

- **BE:** `RegulationService` (`country_rule` có EXCLUDE chống trùng hiệu lực, `document_type`, `document_requirement`). `RegulationPolicy`: tìm quy định theo nước đối tác, chiều đi và ngày; tính thời gian cần trước ngày đón (`permit_lead_days`, `certificate_lead_days`; `quarantine_days` bỏ ở A6b).
- **FE:** admin quản lý quy định và giấy tờ yêu cầu. Danh sách giấy cần nộp hiển thị cho khách.
- **Tận dụng:** dữ liệu trong `DocumentRequirementSeeder` → đưa vào seed SQL. FE `admin/rules`, `admin/documents`.
- **Xong khi:** test chọn đúng quy định theo ngày hiệu lực và chiều đi, kể cả INTERPROVINCE cho VN.

### A5. Đặt đơn (đợt 3)

- **BE:**
  - `HorseService` (hồ sơ ngựa, microchip ISO);
  - `BookingService.submit`: một transaction ghi `booking`, `booking_party` ×4, `booking_address` ×2 (snapshot, có tọa độ), `booking_horse`, `booking_horse_service`, `horse_insurance` DRAFT, `terms_acceptance`, `reserve` khoang, rồi `BookingStatusService` → `PENDING_INTAKE`;
  - `BookingStatusPolicy`: bảng chuyển trạng thái hợp lệ của 12 trạng thái;
  - `BookingHorseService.exclude`.
- **FE:** wizard đặt đơn 4 bước bằng `Stepper`:
  1. chọn loại đơn, điểm đón và giao, **chọn chuyến** (gợi ý từ A1);
  2. chọn ngựa, loại khoang, chế độ ăn;
  3. dịch vụ thêm và bảo hiểm (khai báo, cam kết miễn trách nếu không mua);
  4. các bên trong đơn, điều khoản, xem trước giá, gửi.
  Có lưu bản nháp. Thêm trang hồ sơ ngựa và sổ địa chỉ.
- **Tận dụng:**
  - BE: `HorseServiceImpl`, `MicrochipPolicy`, `ContactPatterns`, `BookingCodes`;
  - FE: `AddressPicker`, `DepartureDatePicker`, `booking/draft.ts` (+ test), `routeValidation.ts`, `horses/HorseFormModal.tsx`, `contactValidation`, `microchip`;
  - viết lại các trang `Step1…Step4` (bỏ chọn ngày tự do, bỏ tính giá).
- **Xong khi:**
  - test submit đủ bảng trong một transaction và rollback khi hết khoang;
  - test bảng chuyển trạng thái;
  - E2E đặt đơn với API giả lập.

### A6. Thẩm định, giấy tờ, bảo hiểm (đợt 4)

- **BE:**
  - `DocumentReviewService`: chuyên viên tải giấy của COMPANY, CONSIGNEE, AGENT và duyệt hoặc từ chối giấy ở UNDER_REVIEW, NEEDS_RESUBMISSION, QUOTING, AWAITING_DEPOSIT, CONFIRMED. Giấy do chuyên viên tải vẫn chờ duyệt. Từ chối phải có lý do. Ở CONFIRMED, khách được nộp lại giấy CUSTOMER bắt buộc nếu thiếu bản còn hiệu lực;
  - `DocumentCompletenessService.isCompleteForQuoting` kiểm giấy bắt buộc của CUSTOMER. `isComplete` kiểm tất cả giấy bắt buộc khi xe xuất phát đón. Hai mốc dùng chung policy và đều chờ xác định yêu cầu phụ thuộc cửa khẩu. Checklist trả bên nộp và cờ cho hai mốc;
  - `InsuranceService`: PERSONAL / ARRANGED / NONE, `REVISION_REQUESTED` chỉ với PERSONAL, xác nhận. Khi bắt đầu báo giá, kiểm lại bảo hiểm của từng ngựa ACTIVE theo ngày đón, ngày xuất phát hiện tại và hạn báo giá ARRANGED. Chuyên viên có thể mở lại CONFIRMED về DRAFT khi đơn UNDER_REVIEW hoặc NEEDS_RESUBMISSION, có lý do và audit;
  - trả đơn về khách (`NEEDS_RESUBMISSION`), từ chối đơn.
- **FE:** chuyên viên duyệt hồ sơ, giấy tờ, bảo hiểm. Quản lý tiếp nhận đơn. Khách bổ sung hồ sơ.
- **Tận dụng:**
  - `SpecialistServiceImpl`, `HorseDocumentServiceImpl`;
  - `HorseInsurancePolicy` (giữ quy tắc chỉ yêu cầu bổ sung với PERSONAL, đổi nguồn từ JSON sang cột);
  - toàn bộ luồng nhánh `feat/insurance-resubmission`;
  - FE `specialist/*`, `manager/intake`, `InsuranceFields`, `InsuranceDocuments`, `orders/InsuranceRevision.tsx` (+ test).
- **Xong khi:** test completeness với nhiều trường hợp (giấy theo con, giấy theo đơn, hết hạn, sai loại). Đủ điều kiện mới cho sang `QUOTING`.
- **API:** nhân viên đọc `GET /bookings/review-queue`, `GET /bookings/{id}/review-detail` và `GET /bookings/{id}/documents/checklist`. Khách xem yêu cầu và kết quả của đơn mình qua `GET /bookings/{id}/documents/my-checklist`.
- Thao tác đơn dùng `POST /bookings/{id}/intake`, `return-for-resubmission`, `resubmit`, `reject`, `change-departure`, `start-quoting`. Tải giấy qua `POST /bookings/{id}/documents/upload` (multipart) cho khách hoặc `POST /bookings/{id}/documents/upload-for-partner` (multipart) cho chuyên viên, duyệt qua `POST /bookings/{id}/documents/{documentId}/approve` hoặc `reject`.
- Thao tác bảo hiểm dùng `POST /bookings/{id}/insurances/{insuranceId}/record-evidence`, `confirm`, `reopen`, `request-revision`, `revise`. Chuyên viên nhập phí và số tiền bảo hiểm theo chứng từ. Khoản VND dùng số nguyên đồng. Khách sửa thông tin hợp đồng PERSONAL sau yêu cầu bổ sung, giữ lý do gần nhất trên bảo hiểm và nhật ký yêu cầu trong `audit_log`.

### A6b. Đồng bộ quyết định ngày 23/11/2026 (đợt 4, làm trước A7)

Đưa các quyết định đã ghi trong `DB_SCHEMA_59_BANG.md`, `NGHIEP_VU_THEO_59_BANG.md`, `MAN_HINH_VA_TAC_DONG.md` vào code A1–A6. Danh sách file phải được duyệt riêng trước khi sửa.

| Phần | Việc |
|---|---|
| Migration | Bỏ `country_rule.quarantine_days`, `vehicle.registration_expiry_date`; thêm `location.counterpart_country_code`, `booking.pickup_method`, `booking.delivery_method`, `horse_health_log.water_intake`, `feed_intake`; loại giấy và tham số mới; đổi tên `VET_CHECK`, `QUARANTINE_STAY`. Entity của B (`horse_health_log`) cần B đồng ý |
| Seed dev | 6 tuyến mỗi tuần một chuyến, cửa khẩu Lao Bảo, đại lý Lào, điểm nghỉ đạt quy tắc #31, xe và tổ xe đủ chạy, quy định và giấy mới, bỏ trại cách ly |
| A1 | Chỗ theo loại khoang, cửa khẩu khớp nước đối tác, quy tắc #31 khi lưu tuyến, mốc 48 giờ, màn hình chuyến cần quyết định, chuyến đột xuất |
| A2 | Đổi trạng thái xe, quy tắc chỗ, bỏ hạn đăng ký, tham số mới |
| A3 | Bỏ tính tiền cách ly, lưu chuồng theo ngày đón sớm, tự thêm `VET_CHECK` |
| A4 | Bỏ ngày cách ly khỏi thời gian cần trước ngày đón |
| A5 | Giữ chỗ theo loại khoang, chọn cách đưa và nhận ngựa |
| A6 | `LAB_TEST` do công ty nộp, `QUARANTINE_CLEARANCE`, mốc kiểm của đơn nhập, danh sách đơn thiếu giấy chuyển A8 |

**Xong khi:** 9 ngựa khoang rộng không đặt được trên chuyến 9 chỗ; đơn Lào không chọn được Mộc Bài; tuyến có đoạn quá 4 giờ bị từ chối; xe bảo dưỡng không gán được (B kiểm); không còn `quarantine_days` trong code.

### A7. Báo giá, đặt cọc, thanh toán (đợt 4)

- **BE:**
  - `QuotationService`: tạo phiên bản từ `PricingService`, dòng ADJUSTMENT, ghi 3 cột tiền qua `DepositPolicy` khi còn DRAFT, gửi, khách chấp nhận hoặc từ chối, SUPERSEDED + ACCEPTED trong một transaction;
  - `PaymentService`: DEPOSIT, BALANCE, REFUND;
  - `VnpayClient`: callback và IPN ghi `payment_transaction`, chống trùng bằng UK;
  - cấp vận đơn;
  - scheduler: hết hạn báo giá (nhả chỗ). Hạn báo giá theo quy tắc #33. Số dư lập khi nhận ngựa (`requestBalance`), hạn 30 phút;
  - `PaymentReadinessService`.
- **FE:** quản lý lập, điều chỉnh, gửi báo giá. Khách xem báo giá, chấp nhận, thanh toán VNPAY, trang kết quả trả về.
- **Tận dụng:**
  - `VnpayServiceImpl`, `VnpayUtil`, `DepositPaymentWindow`, `PaymentAmountLimits`, `PaymentServiceImpl` (khóa đặt cọc, chờ xác nhận 5 phút), `QuotationExpiryScheduler`;
  - test `DepositPaymentConcurrencyTest`, `VnpayServiceImplTest`, `VnpayUtilTest`;
  - FE `shared/ui/QuoteSheet.tsx` → `shared/components/QuoteSheet` (chỉ hiển thị), `manager/approvals`, `payment/VnpayReturnPage`, `frontend/docs/DEPOSIT_PAYMENT_DEADLINE.md`.
- **Xong khi:**
  - test `DepositPolicy` (cọc gồm đủ phần thu hộ);
  - test báo giá không sửa được sau khi SENT;
  - test callback gửi hai lần chỉ ghi một lần;
  - test hết hạn thì nhả khoang.

### A8. Quyết toán, hủy đơn, đánh giá (đợt 5)

- **BE:**
  - `SettlementPolicy` (số còn nợ, theo `DB_SCHEMA_59_BANG.md` mục 5);
  - `SettlementService`: lấy chi phí đã duyệt mà khách hoặc người nhận chịu, gọi `createExtraCharge` và `CostItemService.linkExtraPayment`, đóng đơn khi nợ bằng 0;
  - hủy đơn theo `NGHIEP_VU_THEO_59_BANG.md` mục 5.4, gồm hủy do không trả số dư tại nơi nhận; tự đóng đơn không phát sinh sau 7 ngày;
  - danh sách đơn thiếu giấy và đổi chuyến của đơn đã cọc (màn hình 3.5b), xử lý theo nguyên nhân ở GĐ5;
  - `RatingService`.
- **FE:** quản lý quyết toán. Khách xem khoản thu thêm, thanh toán, đánh giá. Hóa đơn gửi qua mail.
- **Tận dụng:** `SettlementServiceImpl`, `SettlementExpiryScheduler`, `BillMailServiceImpl`, `CustomerRatingController`, FE `orders/TripFeedback.tsx`, `manager/cancelled-orders`.
- **Xong khi:** test số còn nợ trong các ca có hoàn tiền, có thu thêm, có khoản do người nhận chịu. Đóng đơn đúng điều kiện.

---

## 7. Luồng B: vận hành

### B1. Điều độ: lượt xe, điểm dừng, tổ lái, chặng (đợt 1)

- **BE:**
  - `DispatchService`: tạo `vehicle_trip` theo loại PICKUP, LINEHAUL, DELIVERY, TRANSFER, DIRECT;
  - sinh `trip_stop`: với LINEHAUL thì chép từ `route_stop` (giờ = `planned_departure_at + arrival_offset_minutes`), với lượt đón và giao thì từ `booking_address`, thứ tự dừng do `JourneyPlanPolicy` tính;
  - `crew_assignment`, nhân viên xác nhận nhận việc;
  - `HorseLegService`: gán ngựa vào lượt xe (`board_trip_stop_id`, `alight_trip_stop_id`), kiểm chuỗi chặng liền mạch.
  - `DispatchPolicy` (hàm thuần), gồm các quy tắc #8, #9, #10, #20, #21:
    - không trùng giờ xe hoặc nhân viên;
    - lượt mới bắt đầu ở nơi lượt trước kết thúc;
    - số chỗ ngựa chiếm ≤ tổng chỗ của xe tại mọi thời điểm (khoang rộng 2 chỗ);
    - ngựa cùng đơn đi cùng lượt LINEHAUL (#30); chỉ gán xe ACTIVE (#36); nhân viên bắt đầu ở nơi lượt trước kết thúc (#10);
    - lên xuống trục ở đầu và cuối tuyến.
- **FE:** bảng điều độ cho điều phối viên, theo ngày và theo chuyến: danh sách ngựa chờ, kéo ngựa vào lượt xe, bản đồ lộ trình, phân công tổ lái.
- **Tận dụng:**
  - `FleetServiceImpl` (logic kiểm trùng lịch) → `DispatchPolicy`;
  - test `FleetServiceImplTest`, `MultiVehicleTripFlowTest` (giữ ý tưởng ca kiểm);
  - FE `coordinator/fleet-plan/FleetPlanPage.tsx` (1303 dòng): **tách** `RouteMapPicker`, `InlineRouteMap` thành `shared/components`, phần còn lại viết mới;
  - `coordinator/fleet/*`, `shared/lib/waypoints.ts`, `routePreview.ts`, `useRoadRoute`.
- **Xong khi:** mỗi quy tắc trong `DispatchPolicy` có test vi phạm. Điều độ được đơn seed trong cả 3 ca: chạy thẳng, trục nội địa, quốc tế.

### B2. Thực hiện tại điểm dừng và chuỗi giao nhận (đợt 2)

- **BE:**
  - `TripStopService`: xe xuất phát (kiểm `DocumentCompletenessService`, bảo hiểm CONFIRMED, tài xế đã xác nhận); rời nơi nhận ngựa thì kiểm `PaymentReadinessService` (#32); rời trạm nghỉ thì kiểm #34;
  - `HandoverService`: PICKUP, HUB_IN/OUT, STAY_IN/OUT, AGENT, DELIVERY, RETURN; quét chip (`MicrochipPolicy`); `handover_document` nhận và trả giấy gốc; biên bản qua `DocumentService`;
  - đầu SELF nhận hoặc trả ngựa tại hub bằng `handover` PICKUP hoặc DELIVERY gắn `horse_stay`. Kiểm giấy khi nhận tại hub và giữ hai địa chỉ của đơn cho giấy tờ;
  - cập nhật trạng thái `horse_leg`. Nhận ngựa xong thì gọi `requestBalance`; xe rời nơi nhận thì gọi `BookingStatusService` → `IN_OPERATION`. Đơn IMPORT nhận ngựa ở cửa khẩu (#35).
  - **Endpoint theo tài nguyên**, ví dụ:
    - `POST /vehicle-trips/{id}/start`;
    - `POST /trip-stops/{id}/arrive`, `POST /trip-stops/{id}/depart`;
    - `POST /handovers`, `POST /handovers/{id}/horses`, `POST /handovers/{id}/complete`.
- **FE:** ứng dụng tài xế và hộ tống, ưu tiên điện thoại: danh sách việc theo lượt xe, màn hình theo **từng điểm dừng**, quét chip, chụp ảnh, ký biên bản, nhận và trả giấy gốc.
- **Tận dụng:**
  - `TripProgressServiceImpl` (switch khoảng 21 action): **chỉ lấy quy tắc** ở từng nhánh, tách thành các service trên;
  - `DeliveryServiceImpl`, `CaptureServiceImpl`;
  - test `PickupChipCheckTest`, `DepartToPickupTest`, `CrewAcknowledgementTest`;
  - FE `driver/DriverJob.tsx`, `escort/EscortJob.tsx` (viết lại theo điểm dừng), `CameraCapture`, `CaptureField`, `shared/lib/capture.ts`, `captureScope.ts`, e2e `return-horses.e2e.ts`, `camera.e2e.ts`.
- **Xong khi:**
  - test không rời nơi nhận được khi chưa trả đủ tiền, quá giờ thì trả ngựa và hủy đơn;
  - test chip sai thì không nhận;
  - test giao nhận làm đúng chuyển trạng thái chặng và trạng thái đơn;
  - không còn endpoint kiểu `action`.

### B3. Lưu trú, cho ăn, sức khỏe (đợt 3)

- **BE:**
  - `HorseStayService`: ASSEMBLY, OVERNIGHT, STABLING. Không làm cách ly (`QUARANTINE` ngừng dùng);
  - `HealthLogService`: đo và ghi ăn uống (`water_intake`, `feed_intake`) tại điểm dừng, trong lúc lưu trú, hoặc trên đường. Bỏ uống 2 trạm liền thì WATCH và báo điều phối.
- **FE:** hộ tống ghi sức khỏe hằng ngày. Điều phối viên quản lý lưu trú tại hub và chuồng gửi.
- **Tận dụng:** `HorseHealthLogServiceImpl`, FE `escort/*`, `healthLogs` API cũ (đổi dữ liệu).
- **Xong khi:** test ghi ăn uống ở trạm, nghỉ đêm, hub; test chặn rời trạm khi thiếu. Chuỗi chặng của con ngựa bị loại được hủy đúng.

### B4. Sự cố, phương án, chi phí (đợt 4)

- **BE:**
  - `IncidentService`: báo sự cố gắn `vehicle_trip` hoặc `horse_leg`; `incident_plan` có phiên bản, đề xuất, duyệt hoặc từ chối; xác nhận ngựa đủ sức đi tiếp; đóng sự cố;
  - `CostItemService`: ghi chi phí, `CostPayerPolicy` (bên chịu theo `cause_type`; `FORCE_MAJEURE` do công ty), duyệt, kiểm hạn mức chi khẩn cấp, `linkExtraPayment`;
  - phương án `TRANSFER_VEHICLE` và `RETURN_TO_HUB` gọi `DispatchService` và `HorseLegService`.
- **FE:** tài xế báo SOS. Điều phối viên lập phương án trên bản đồ. Quản lý duyệt phương án và chi phí. Tổ xe ghi chi phí kèm biên lai.
- **Tận dụng:**
  - `IncidentServiceImpl` (tách 32 cột thành 3 bảng);
  - test `CompanyIncidentCostFlowTest`, `TollReceiptTest` (sửa theo nguyên tắc chia chi phí mới);
  - FE `coordinator/incidents/IncidentPlanner.tsx`, `backoffice/shared/IncidentMap.tsx`, `IncidentPlanView.tsx`, `shared/lib/incidentRoute.ts`, `manager/incidents`;
  - **bỏ** `incidentCosts.ts`.
- **Xong khi:** test `CostPayerPolicy` đủ 6 nguyên nhân. Có hạn mức khẩn cấp. Phương án bị từ chối thì sinh phiên bản 2.

### B5. Giao thất bại và giao lại (đợt 4)

- **BE:**
  - scheduler kiểm hết thời gian chờ (`HANDOVER_FREE_WAIT_MINUTES`): handover `TIMEOUT` → incident `HANDOVER_TIMEOUT`;
  - phương án gửi chuồng hoặc về hub giao, gồm chặng RETURN/TRANSFER, `horse_stay` STABLING, chặng DELIVERY mới có `replaced_horse_leg_id`;
  - chi phí do CONSIGNEE chịu;
  - chặng giao cuối xong thì gọi `BookingStatusService` → `DELIVERED` (quy tắc #16).
- **FE:** tài xế bấm bắt đầu chờ và xem đồng hồ đếm giờ. Điều phối viên chọn phương án.
- **Tận dụng:** test `DeliveryTimeoutTest`, logic hết giờ chờ trong `DeliveryServiceImpl`, FE `backoffice/shared/HomeReturnPanel.tsx`.
- **Xong khi:** test trọn ca timeout → gửi chuồng → giao lại thành công → đơn `DELIVERED`.

### B6. Theo dõi, thông báo, báo cáo (đợt 5)

- **BE:**
  - `TrackingService`: hành trình từng con ngựa (chặng, lưu trú, giao nhận) và ngựa đang trong tay ai;
  - tra cứu công khai có `PublicTrackingGuard`;
  - `ReportService` (báo cáo chuyến từ `trip_stop`, `horse_health_log`, `incident`, `cost_item`, `booking_rating`);
  - thông báo theo sự kiện.
- **FE:**
  - `shared/components/HorseJourney`: timeline từng con ngựa, cả hai ứng dụng dùng; luồng A gắn vào trang chi tiết đơn của khách;
  - chuông thông báo;
  - dashboard và báo cáo của quản lý;
  - nhật ký hệ thống.
- **Tận dụng:**
  - `TripReportServiceImpl`, `NotificationServiceImpl`, `StaffNotificationServiceImpl`, `AdminSystemLogServiceImpl`;
  - FE `TripTimeline`, `TripTrackMap`, `NoticeBell`, `manager/dashboard`, `manager/reports`, `manager/trip-reports`, `admin/system-logs`;
  - **bỏ** `shared/lib/horseLocation.ts`, `trip.ts`, `shared/services/tracking.ts`.
- **Xong khi:** timeline hiển thị đúng cả 3 ví dụ ở mốc M3.

---

## 8. Đợt cuối: tích hợp và dọn dẹp

| Việc | Ai |
|---|---|
| Test tích hợp backend chạy trọn 3 ca của mốc M3 (`FullFlowIntegrationTest`), dùng `TestData` | A viết ca thương mại, B viết ca vận hành, cùng một file test |
| Viết lại E2E Playwright theo luồng mới. Xóa E2E cũ không còn ý nghĩa | A: đặt đơn, thanh toán. B: điều độ, tài xế, theo dõi |
| Xóa FE cũ: `shared/services`, `shared/types`, các file nghiệp vụ trong `shared/lib`/`shared/config`, trang cũ còn sót. Danh sách ngoại lệ của `check-style.mjs` phải rỗng | B |
| Sinh ERD từ DB (SchemaSpy hoặc DBeaver), đặt ở `docs/database/` | A |
| Rà `AGENTS.md`, `DB_SCHEMA_59_BANG.md`, `NGHIEP_VU_THEO_59_BANG.md` khớp với code. Đóng câu hỏi mở trong `NGU_CANH_THIET_KE_LAI.md` | Cả hai |
| Cập nhật CI và deploy (biến môi trường Flyway, profile), merge `redesign/system-v2` vào `main` | A, khi người dùng đồng ý |

---

## 9. Quy trình cho agent

### 9.1 Trước khi làm

1. Đọc `AGENTS.md` (toàn bộ), task trong file này, và các mục tài liệu mà task nhắc tới.
2. `git fetch` rồi tạo nhánh: `git switch -c rd/<mã>-<mô-tả> origin/redesign/system-v2`.
3. Đọc code cũ cần tận dụng bằng `git show origin/main:<path>`. Không khôi phục nguyên file.
4. **Trình bày phương án** theo `AGENTS.md` mục 1.1, rồi chờ thành viên đồng ý. Phương án gồm:
   - danh sách file tạo, sửa, xóa;
   - migration (nếu có);
   - endpoint;
   - test sẽ viết.

### 9.2 Thứ tự làm một task

```
1. Migration (chỉ khi task cần đổi lược đồ) + sửa DB_SCHEMA_59_BANG.md
2. Policy (hàm thuần) + unit test
3. Service + test (luồng chính và luồng vi phạm)
4. DTO (from / toEntity / applyTo) + test
5. Controller (@PreAuthorize, @Valid, ApiResponseDto, endpoint theo tài nguyên)
6. Chạy OpenApiExportTest → npm run api:gen
7. FE: hook / loader → trang → component, dùng shared/ui và token, đủ 4 trạng thái, i18n vi + en
8. FE test (logic và component) + E2E nếu task có màn hình chính
9. Quality Gate đầy đủ (AGENTS.md mục 10.2)
10. Tick checklist AGENTS.md mục 12, mở PR vào redesign/system-v2
```

### 9.3 Mẫu câu giao việc cho agent

```
Thực hiện task <mã> trong docs/KE_HOACH_TRIEN_KHAI.md trên nhánh rd/<mã>-<mô-tả>
tạo từ origin/redesign/system-v2. Tuân thủ AGENTS.md.
Trước khi sửa: trình bày phương án (file, migration, endpoint, test) và chờ tôi đồng ý.
Phạm vi lần này: <toàn bộ task | chỉ phần BE | chỉ phần FE>.
Không sửa file thuộc luồng còn lại. Cần ghi bảng của luồng khác thì gọi interface ở mục 3.3.
Xong thì chạy Quality Gate và báo kết quả thật.
```

### 9.4 Mẫu mô tả PR

```
## Task
<mã> <tên task>. Luồng A/B.

## Thay đổi
- DB: <migration hoặc "không">
- BE: <service, endpoint>
- FE: <trang, component>

## Tận dụng từ main
- <path cũ> → <path mới>: <đã sửa gì>

## Kiểm tra
- [ ] Quality Gate: mvnw verify / lint / tsc / test / build: <kết quả>
- [ ] Checklist AGENTS.md mục 12
- [ ] Không sửa file của luồng khác (nếu có, ghi lý do và người duyệt)
```

### 9.5 Khi gặp vướng mắc

| Tình huống | Làm gì |
|---|---|
| Tài liệu mâu thuẫn hoặc thiếu | Dừng lại và hỏi. Sửa tài liệu bằng PR `docs:` |
| Cần ghi vào bảng của luồng khác | Dùng interface ở mục 3.3. Chưa có method phù hợp thì đề xuất thêm method và báo thành viên kia |
| Cần sửa `shared/ui` hoặc token | Mở PR nhỏ riêng, thành viên B duyệt |
| Migration bị trùng số khi rebase | Đổi số của mình thành thời điểm hiện tại (mục 2.2) |
| Xung đột `openapi.json` hoặc `generated/` | Chạy lại export và `api:gen`, không sửa tay |
| ArchUnit hay lint báo vi phạm | Sửa code cho đúng. Không thêm ngoại lệ khi chưa được đồng ý |

---

## 10. Bảng tiến độ

> Mỗi người **chỉ sửa dòng của mình**, trong PR của task đó.

| Mã | Task | Luồng | Đợt | Trạng thái | PR |
|---|---|---|---|---|---|
| P0.1 | Dọn code cũ, Flyway, 59 bảng | A | 0 | Xong | #344 |
| P0.2-A | Entity, repository nhóm 1–8 | A | 0 | Chờ review (nhánh `rd/p0-2a-entities`) | |
| P0.2-B | Entity, repository nhóm 9–15 | B | 0 | Xong | #351 |
| P0.3-A | Auth, interface gọi chéo, `TestData` | A | 0 | Chờ review (nhánh `rd/p0-3a-auth`) | |
| P0.3-B | Document, Notification, Audit, ArchUnit, OpenAPI | B | 0 | Xong | #353 |
| P0-FE | Token, `shared/ui`, layout, openapi-typescript, lint, i18n | B | 0 | Xong (8 PR) | #355, #358, #367, #368, #369, #372, #373, #375 |
| A1 | Mạng lưới, tuyến, lịch, chuyến | A | 1 | Backend chờ review: PR1 danh mục (`rd/a1-network`), PR2 chuyến (`rd/a1-departures`). FE làm sau P0-FE | |
| A2 | Xe, nhân sự, đối tác, tham số | A | 1 | Chưa làm | |
| B1 | Điều độ | B | 1 | Xong (4 PR) | #377, #378, #379, #380 |
| A3 | Bảng giá, tính giá | A | 2 | Chưa làm | |
| A4 | Quy định, giấy tờ yêu cầu | A | 2 | Chưa làm | |
| B2 | Điểm dừng, giao nhận | B | 2 | Đang làm: PR1 backend (#381), PR2 màn tổ xe (#382) xong; còn giao nhận AGENT ở cửa khẩu, biên bản và giấy gốc; chờ A7 (thanh toán: trả 70%, rời nơi nhận), A (loại ngựa) cho Không nhận, A8 cho trả ngựa tại chỗ | #381, #382 |
| A5 | Đặt đơn | A | 3 | Chưa làm | |
| B3 | Lưu trú, sức khỏe | B | 3 | Xong phần chính (2 PR). Còn: nghỉ đêm OVERNIGHT, bàn giao ca SHIFT; hủy chặng của con bị loại chờ luồng A | #383, #384 |
| A6 | Thẩm định, bảo hiểm | A | 4 | Backend xong, chưa commit (`feat/a6-review`) | |
| A6b | Đồng bộ quyết định 23/11/2026 | A | 4 | Tài liệu xong, code chờ duyệt danh sách file | |
| A7 | Báo giá, thanh toán | A | 4 | Chưa làm | |
| B4 | Sự cố, chi phí | B | 4 | Xong phần chính (2 PR). Còn: phương án đổi xe, về hub tự lập lượt xe; biên lai, ảnh hiện trường | #385, #386 |
| B5 | Giao thất bại, giao lại | B | 4 | Xong phần chính: chờ người nhận, quá giờ thì giao thất bại, về hub, giao lại. Còn: gửi chuồng | #387 |
| A8 | Quyết toán, hủy, đánh giá | A | 5 | Chưa làm | |
| B6 | Theo dõi, thông báo, báo cáo | B | 5 | Xong phần chính (6 PR): chuông thông báo, hành trình ngựa và tra cứu công khai, báo cáo vận hành, nhật ký hệ thống. Còn: báo khách khi nhận, giao ngựa (cần tách `HandoverServiceImpl`), bảng điều khiển `/manager` | #388, #389, #390, #391, #392 |
| E | Tích hợp, dọn dẹp, ERD, merge | A + B | 6 | Chưa làm | |
