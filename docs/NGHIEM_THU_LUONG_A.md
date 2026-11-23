# Nghiệm thu đồng bộ tên và quản trị A2

Ngày 23/11/2026. Phần đồng bộ tên và A2.1 quản lý xe đã được đẩy lên `origin/redesign/system-v2` lần lượt tại `e095a35` và `94f4c8e`. A2.2 đến A2.5 đang ở working tree của nhánh `feat/a2-parties` trên nền `c4375e2`, sau khi tích hợp P0.2-B.

## Đồng bộ tên lược đồ và API

- [Migration đồng bộ tên](../backend/src/main/resources/db/migration/V202611231300__sync_names.sql) đổi `service` thành `billable_service`, `handover_item` thành `handover_document`, và đổi 109 cột theo [bảng đổi tên](BANG_DOI_TEN.md). Migration đổi tên constraint, index và sequence mang tên cũ. Các migration đã merge giữ nguyên.
- Hai cột giờ mở và đóng cửa chuyển từ số phút sang PostgreSQL `time`. Dữ liệu `NULL` được giữ, hai ràng buộc số phút cũ được bỏ. Java dùng `LocalTime`.
- Entity, truy vấn, DTO, service, seed dev và test đã được cập nhật. Đã sinh kiểu TypeScript từ OpenAPI, cập nhật lớp gọi API tuyến và địa điểm, đánh dấu các mục đã đồng bộ và cập nhật [bàn giao tài khoản và hồ sơ](BAN_GIAO_TAI_KHOAN_VA_HO_SO.md) để dẫn tới commit `698fd29`. Luồng tài khoản và hồ sơ trong commit đó đã hoàn tất trước phiên này.

| API | Trường JSON đã đổi |
|---|---|
| `/countries` | `defaultCurrency` → `defaultCurrencyCode` |
| `/locations` | `type` → `locationType`, `openingMinutes` → `openingTime`, `closingMinutes` → `closingTime`. Hai trường giờ dùng chuỗi `HH:mm:ss`. Bộ lọc query `type` giữ tên hiện tại. |
| `/routes` | `estDurationMinutes` → `estimatedDurationMinutes`. Trong `stops[]`, `seq` → `sequenceNumber`, `offsetMinutes` → `arrivalOffsetMinutes`. |
| `/departure-schedules` | `capacityStalls` → `stallCapacity`, `weekdays` → `daysOfWeek`. |
| `/departures` và kết quả `/departures/suggestions` | `capacityStalls` → `stallCapacity`. Response chuyến còn đổi `scheduleId` → `departureScheduleId`, `plannedDepartAt` → `plannedDepartureAt`, `bookedStalls` → `bookedStallCount`. |

[SyncNamesMigrationTest](../backend/src/test/java/com/example/racehorse_transport/SyncNamesMigrationTest.java) nâng cấp DB từ phiên bản tài khoản và hồ sơ trên PostgreSQL 16. Test kiểm giờ `00:00:00`, `23:59:00`, `NULL`, dữ liệu còn đọc được, khóa ngoại, index và sequence tiếp tục cấp ID.

## A2.1 quản lý xe

[Migration trụ sở](../backend/src/main/resources/db/migration/V202611231400__mark_headquarters.sql) thêm `location.is_headquarters`. DB giới hạn cờ này cho địa điểm loại `HUB` và cho tối đa một trụ sở. Khi dữ liệu xe và nhân viên có đúng một hub gốc, migration dùng hub đó. Nếu dữ liệu có nhiều hub gốc, migration dừng để xác nhận và hiệu chỉnh dữ liệu trước khi chạy lại. DB mới chưa có hub gốc cần thiết lập trụ sở khi chuẩn bị dữ liệu triển khai. Seed dev đánh dấu `HUB-HCM` nếu chưa có trụ sở.

`home_hub_location_id` xác định hub quản lý xe. Xe vẫn có thể đi và dừng ở hub khác. Vị trí thực tế và điều kiện nhận lượt mới tiếp tục theo quy tắc điều độ. [Đặc tả dữ liệu](DB_SCHEMA_59_BANG.md#53-vehicle-xe)

| API dưới `/api/v1` | Tiêu chí nghiệm thu |
|---|---|
| `GET /vehicles` | Admin xem danh sách xe theo biển số. |
| `GET /vehicles/{id}` | Admin xem chi tiết xe. Xe không tồn tại trả `404`. |
| `GET /vehicle-classes` | Admin xem danh sách hạng xe và sức chứa tối đa. |
| `POST /vehicles` | Tạo xe, chuẩn hóa biển số và đặt trạng thái `ACTIVE` từ backend. |
| `PUT /vehicles/{id}` | Cập nhật xe hiện có và giữ ID, trạng thái do backend quản lý. |

Backend kiểm từng loại khoang không âm, tổng khoang từ 1 đến giới hạn của hạng xe, và hub gốc là trụ sở. Biển số trùng trả `409`. Sai sức chứa hoặc chọn hub vệ tinh trả `400` với lỗi theo trường. Khách hàng truy cập API quản trị nhận `403`. API địa điểm chặn đổi loại của trụ sở sang loại khác `HUB`. [Controller quản lý xe](../backend/src/main/java/com/example/racehorse_transport/controller/VehicleController.java)

Đã sinh lại [kiểu TypeScript](../frontend/src/shared/api/generated/schema.ts) từ OpenAPI. Màn hình quản trị xe và các thao tác đổi trạng thái hoặc xóa xe nằm ngoài phạm vi A2.1 đã duyệt.

## A2.2 quản lý đối tác

Admin và điều phối viên quản lý hồ sơ đối tác cá nhân hoặc tổ chức. Mỗi cặp đối tác và loại vai trò có một khoảng hiệu lực theo khóa chính của `party_role`. Ngày cuối có thể để trống để biểu thị hiệu lực vô thời hạn. Khi có ngày cuối, khoảng hiệu lực bao gồm cả ngày đầu và ngày cuối. Tính năng dùng lược đồ hiện tại và đã sinh lại [kiểu TypeScript](../frontend/src/shared/api/generated/schema.ts) từ OpenAPI.

| API dưới `/api/v1` | Tiêu chí nghiệm thu |
|---|---|
| `GET /parties` và `GET /parties/{id}` | Trả hồ sơ và các vai trò đã gán. Đối tác không tồn tại trả `404`. |
| `POST /parties` | Tạo đối tác với trạng thái `ACTIVE` do backend đặt. Quốc gia không tồn tại trả `400`. |
| `PUT /parties/{id}` | Cập nhật hồ sơ và giữ ID, trạng thái và các vai trò hiện có. |
| `POST /parties/{id}/roles` | Thêm vai trò, trả `409` khi vai trò đã tồn tại. |
| `PUT /parties/{id}/roles/{roleType}` | Cập nhật khoảng hiệu lực. Vai trò không tồn tại trả `404`. |

Cả hai thao tác ghi vai trò trả `400` nếu ngày cuối sớm hơn ngày đầu. Backend khóa hồ sơ khi ghi vai trò để tuần tự hóa các cập nhật đồng thời của cùng một đối tác. Khách hàng truy cập API quản trị nhận `403`. [Controller quản lý đối tác](../backend/src/main/java/com/example/racehorse_transport/controller/PartyController.java)

## A2.3 tài khoản và nhân sự

Admin xem danh sách và chi tiết tại `GET /api/v1/staff-accounts` và `GET /api/v1/staff-accounts/{id}`. `POST /api/v1/staff-accounts` tạo `app_user` và `staff` trong một transaction. Backend mã hóa mật khẩu bằng BCrypt, yêu cầu vai trò nội bộ và hub quản lý là trụ sở. Chỉ tài xế được khai bằng lái và phải khai đủ hạng bằng, ngày hết hạn. Trùng tên đăng nhập, email hoặc mã nhân viên trả `409`.

`PUT /api/v1/staff-accounts/{id}` sửa hồ sơ nhân viên. Vai trò, thông tin đăng nhập và trạng thái được trả về để admin tra cứu. Quy trình đổi quyền, trạng thái hoặc mật khẩu nằm ngoài phạm vi cập nhật hồ sơ đã duyệt. [AccountController](../backend/src/main/java/com/example/racehorse_transport/controller/AccountController.java)

## A2.4 hồ sơ khách và sổ địa chỉ

Khách xem và cập nhật hồ sơ của mình tại `GET/PUT /api/v1/customers/me`. Bộ giấy tờ gồm loại, số và quốc gia cấp. Khách có thể khai đủ ba trường hoặc để trống cả ba. Khóa duy nhất của DB chặn đăng ký trùng bộ giấy tờ.

| API dưới `/api/v1/customers/me` | Tiêu chí nghiệm thu |
|---|---|
| `GET /addresses` | Trả địa chỉ của khách đang đăng nhập. |
| `POST /addresses` | Thêm địa chỉ. Tỉnh phải thuộc đúng quốc gia và tọa độ phải đủ cặp hoặc bỏ trống cả hai. |
| `PUT /addresses/{id}` | Cập nhật địa chỉ của khách. Gửi `defaultAddress: true` để chọn địa chỉ mặc định, `false` để bỏ chọn, bỏ trường để giữ lựa chọn hiện tại. |
| `DELETE /addresses/{id}` | Xóa địa chỉ của khách. Địa chỉ thuộc tài khoản khác trả `404`. |

Backend lấy tài khoản từ JWT và khóa hồ sơ khi ghi địa chỉ. Khi chọn địa chỉ mặc định mới, địa chỉ cũ được bỏ chọn trong cùng transaction. [CustomerController](../backend/src/main/java/com/example/racehorse_transport/controller/CustomerController.java)

## A2.5 tham số kỹ thuật và audit

Admin xem danh sách và chi tiết tại `GET /api/v1/system-configs` và `GET /api/v1/system-configs/{key}`. `PUT /api/v1/system-configs/{key}` cập nhật giá trị của một tham số hiện có. API nhận số nguyên dương cho các đơn vị `MINUTE`, `HOUR`, `DAY`, `COUNT`. Response và bản chụp audit biểu diễn giá trị dưới dạng chuỗi theo lược đồ. Backend lấy người sửa từ tài khoản xác thực và thời gian từ `Clock`.

[Migration tham chiếu audit](../backend/src/main/resources/db/migration/V202611231500__system_config_audit_target.sql) cấp `audit_target_id` kiểu `bigint` duy nhất cho tham số hiện có và tham số thêm sau này. Ứng dụng vẫn tra cứu bằng `config_key`. Khi giá trị thực sự thay đổi, `AuditService` ghi một dòng `UPDATE_SYSTEM_CONFIG` với người sửa, ID đích và bản chụp trước và sau trong cùng transaction. Gửi lại giá trị hiện tại trả bản ghi sẵn có. [SystemConfigController](../backend/src/main/java/com/example/racehorse_transport/controller/SystemConfigController.java)

### Giới hạn tham số và định dạng mã nhân viên

API tham số trả thêm `minValue` và `maxValue` từ DB. Giá trị ngoài khoảng trả `400` kèm hai cận. Migration dừng và báo khóa tham số nếu dữ liệu hiện có sai định dạng số nguyên hoặc vượt giới hạn. [Giới hạn theo tham số](DB_SCHEMA_59_BANG.md#154-system_config-tham-số-kỹ-thuật)

Khi tạo hoặc cập nhật tài khoản nhân viên, mã nhân viên chỉ gồm chữ ASCII, chữ số, dấu chấm, gạch dưới và gạch ngang. Khoảng trắng ở hai đầu được bỏ khi lưu.

## Kết quả kiểm tra

| Kiểm tra | Đồng bộ tên | A2.1 | A2.2 | A2.3 và A2.4 | A2.5 trên nền P0.2-B |
|---|---|---|---|---|---|
| `mvnw.cmd verify` | 201 đạt, 1 bỏ qua, 0 lỗi | 217 đạt, 1 bỏ qua, 0 lỗi | `clean verify` đạt, 229 đạt, 1 bỏ qua, 0 lỗi | 245 đạt, 1 bỏ qua, 0 lỗi | `clean verify` đạt, 258 đạt, 1 bỏ qua, 0 lỗi |
| Test migration PostgreSQL 16 | 1 đạt trên dữ liệu có sẵn | 2 đạt, gồm suy ra trụ sở hợp lệ và từ chối dữ liệu có nhiều hub gốc | Bộ test migration hiện có đạt | Bộ test migration hiện có đạt | 1 test mới đạt trên DB có sẵn tham số, các test migration cũ đều đạt |
| `npm run lint` | Đạt, có cảnh báo | Đạt, có cảnh báo | Đạt, có cảnh báo | Đạt, có cảnh báo | Đạt, có cảnh báo |
| `npx tsc -b` | Đạt | Đạt | Đạt | Đạt | Đạt |
| `npm test` | 350 đạt | 350 đạt | Đạt | 350 đạt | 350 đạt |
| `npm run build` | Đạt | Đạt | Đạt | Đạt | Đạt |

`RoutingLiveTest` được bỏ qua theo cấu hình hiện có vì lượt chạy không bật `ROUTING_LIVE_TEST=1`. Backend khởi động với `ddl-auto=validate`. A2.1 có 16 test mới bao phủ policy, DTO, service, API và migration. Kiểm tra trên trình duyệt chưa được thực hiện.

A2.2 có 12 test mới cho policy, DTO, service và API. Lượt `verify` đầu gặp `NoClassDefFoundError: com/example/racehorse_transport/exception/PartyRuleException$DuplicateRole` khi tiến trình sinh OpenAPI biên dịch vào cùng thư mục build trong lúc test đang chạy. Lượt `clean verify` riêng đã qua toàn bộ bộ kiểm tra.

A2.3 có 7 test mới và A2.4 có 9 test mới. Lượt chạy đầu ghi lỗi `AccountFlowTest.adminCreatesDriverAndTheDriverCanSignIn: Status expected:<200> but was:<503>` với phản hồi `CAPTCHA verification is not configured.`. Test đã dùng lại fixture Turnstile của repo và lượt `verify` tiếp theo qua toàn bộ bộ kiểm tra. Hai phần dùng cùng lược đồ và được nghiệm thu trên một bản build. [Kiểu OpenAPI đã sinh](../frontend/src/shared/api/generated/schema.ts)

Sau khi kéo `c4375e2`, bản tích hợp A2.2–A2.4 với P0.2-B qua `mvnw verify` với 248 test đạt, 1 test Valhalla live bỏ qua và 0 lỗi. Ba test ánh xạ mới của luồng B đều đạt. Frontend qua lint, TypeScript, 350 test và build. Hai test Chromium trong `e2e/route-picker.e2e.ts` của PR #352 đều đạt.

A2.5 có 10 test mới. Test xác nhận dữ liệu đang có được cấp ID audit, tham số mới nhận ID khác và cập nhật giá trị giữ ID cũ. Test service kiểm tra giá trị, người sửa, timestamp và ảnh chụp được ghi đúng, cả tham số lẫn nhật ký cùng rollback. Test API kiểm tra phân quyền và từ chối giá trị thập phân. [Kiểu OpenAPI của A2](../frontend/src/shared/api/generated/schema.ts)

## Phần tiếp theo của luồng A

Phần backend danh mục, tuyến, lịch và chuyến của A1 đã merge qua PR #348 và #349. Form quản trị tuyến và địa điểm cũ còn thiếu đầu vào bắt buộc như mã tuyến và loại điểm dừng. Frontend A1 theo kế hoạch đi sau P0-FE và P0.3-B. Màn hình quản trị A2 cần nền P0-FE. Phần AuditService đã duyệt cùng A2.5 triển khai hợp đồng ghi audit, còn các dịch vụ khác của P0.3-B thuộc phạm vi luồng B. [Kế hoạch luồng A](KE_HOACH_TRIEN_KHAI.md#6-luồng-a-thương-mại-và-danh-mục)
