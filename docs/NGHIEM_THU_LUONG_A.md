# Nghiệm thu đồng bộ tên và quản lý xe luồng A

Ngày 23/11/2026. Phần đồng bộ tên đã được đẩy lên `origin/redesign/system-v2` tại commit `e095a35`. Phần A2.1 quản lý xe cùng hồ sơ này đang ở working tree của nhánh `feat/a2-vehicles`, tách từ commit đó.

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

## Kết quả kiểm tra

| Kiểm tra | Đồng bộ tên | A2.1 |
|---|---|---|
| `mvnw.cmd verify` | 201 đạt, 1 bỏ qua, 0 lỗi | 217 đạt, 1 bỏ qua, 0 lỗi |
| Test migration PostgreSQL 16 | 1 đạt trên dữ liệu có sẵn | 2 đạt, gồm suy ra trụ sở hợp lệ và từ chối dữ liệu có nhiều hub gốc |
| `npm run lint` | Đạt, có cảnh báo | Đạt, có cảnh báo |
| `npx tsc -b` | Đạt | Đạt |
| `npm test` | 350 đạt | 350 đạt |
| `npm run build` | Đạt | Đạt |

`RoutingLiveTest` được bỏ qua theo cấu hình hiện có vì lượt chạy không bật `ROUTING_LIVE_TEST=1`. Backend khởi động với `ddl-auto=validate`. A2.1 có 16 test mới bao phủ policy, DTO, service, API và migration. Kiểm tra trên trình duyệt chưa được thực hiện.

## Phần tiếp theo của luồng A

Phần backend danh mục, tuyến, lịch và chuyến của A1 đã merge qua PR #348 và #349. Form quản trị tuyến và địa điểm cũ còn thiếu đầu vào bắt buộc như mã tuyến và loại điểm dừng. Frontend A1 theo kế hoạch đi sau P0-FE và P0.3-B. Các phần còn lại của A2 gồm quản trị tài khoản và nhân sự, hồ sơ và sổ địa chỉ khách, đối tác và tham số kỹ thuật. Phần ghi tham số cần `AuditService`. Backend A3 và A4 có thể triển khai trên nền hiện tại. [Kế hoạch luồng A](KE_HOACH_TRIEN_KHAI.md#6-luồng-a-thương-mại-và-danh-mục)
