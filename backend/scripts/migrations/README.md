# Gộp OTP và audit admin

Hai script dành cho schema PostgreSQL hiện có trước refactor. DB mới hoàn toàn được Hibernate tạo từ entity mới; không chạy các script nâng cấp yêu cầu bảng cũ trên DB trống.

## Thứ tự

1. Dừng mọi backend đang kết nối DB cần nâng cấp (kể cả instance cũ ở máy khác). Kiểm tra đúng DB đích.
2. Sao lưu bằng `pg_dump -Fc` và thử `pg_restore` sang DB cô lập. Backup không đưa vào Git.
3. Chạy `001_unify_otp_and_audit.sql` bằng `psql -X -v ON_ERROR_STOP=1 -f ...`.
4. Đối soát kết quả. Script 001 có thể chạy lại trước cutover, không nhập trùng theo khóa nguồn; không chạy lại khi backend mới đã nhận OTP mới.
5. Khi backend vẫn dừng, chạy `002_remove_legacy_tables.sql`. Script kiểm tra lại nội dung OTP/lịch sử trước khi bỏ bốn bảng cũ; không dùng CASCADE.
6. Khởi động backend phiên bản mới và kiểm tra đăng nhập/xác thực email/reset password, CRUD danh mục và trang lịch sử admin. Khởi động lại để kiểm tra seeder không tạo dữ liệu trùng.

Không khởi động code cũ sau bước 5: Hibernate `ddl-auto=update` có thể tạo lại bảng cũ rỗng và code cũ sẽ ghi sai nơi. Các script không được đặt vào seeder hoặc chạy lại mỗi lần ứng dụng khởi động.

## Dữ liệu được bảo toàn

- OTP: chuẩn hóa email và phân biệt VERIFY_EMAIL/RESET_PASSWORD, giữ hash, hạn dùng, thời điểm gửi và attempts. Dữ liệu trùng email sau chuẩn hóa làm script dừng, không tự chọn dòng để bỏ.
- Audit riêng: giữ nguyên từng thay đổi theo trường, actor văn bản, timestamp, giá trị cũ/mới; có source_table/source_id chống nhập lại. Do lịch sử trước đây ghi kép, bản nhập có thể trùng nghĩa với audit thao tác đã có; description đánh dấu nguồn nhập. Không xóa lịch sử chỉ vì timestamp gần nhau.
- Audit vận hành: chuyển target BOOKING/INCIDENT sang log, giữ toàn bộ bản ghi gốc trong metadata_json và ID nguồn trong source_system_log_id. Các lời gọi incident cũ dùng booking ID; VEHICLE_RETURNED_HQ dùng vehicle ID nên không gắn FK booking cho sự kiện đó. Target ngoài danh sách đã khảo sát làm script dừng để phân loại thủ công.
- Log hiện có không đổi ID; incident.logid vẫn hợp lệ. Identity mới cấp ID lớn hơn max hiện có.
- route_cache giữ nguyên.

`DatabaseConsolidationMigrationTest` kiểm tra dữ liệu mẫu trên PostgreSQL Testcontainers thật, bao gồm replay, bảo toàn hash/attempts, FK, identity và rollback khi có xung đột.

## Seeder

Seeder tiếp tục ghi trực tiếp danh mục bằng repository, không gọi AdminAuditWriter cần SecurityContext. Không có seed OTP hay hai bảng lịch sử đã bỏ. ReferenceDataSeeder vẫn có các bước migrate metadata/seedNetworkLocations nằm ngoài cờ enabled như trước refactor; không dùng cờ seed=false để giả định DB không bị ghi khi startup. DocumentRequirementSeeder tạo phần thiếu, FixedRouteSeeder giữ tuyến/location liên quan, DataInitializer chỉ bootstrap admin theo cấu hình.

## Rollback

Trước khi phiên bản mới nhận request: dừng backend và phục hồi backup đã kiểm chứng, sau đó mới khởi động binary cũ. Sau khi có dữ liệu mới: cần kế hoạch reverse migration/replay dữ liệu phát sinh, không phục hồi backup mù vì sẽ mất các thay đổi sau backup. Không xóa volume Docker để rollback.

## Kiểm thử

Chạy Quality Gate backend với Java 21 trên DB kiểm thử riêng; một số test tự tạo PostgreSQL qua Testcontainers. Không trỏ `mvnw verify` vào DB có dữ liệu cần giữ vì test và seeder có thể ghi dữ liệu.

Frontend giữ nguyên contract auth và system-logs; không cần truyền purpose từ client. Nhãn/action audit nhập lịch sử dùng action hiện có nên trang system logs tiếp tục đọc oldData/newData theo JSON.
