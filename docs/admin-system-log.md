# Nhật ký hệ thống dành cho admin

## Phạm vi

Trang `/backoffice/admin/system-logs` và API `/api/v1/admin/system-logs` chỉ dành cho `ADMIN`, chỉ đọc.
Nguồn dữ liệu là bảng `SYSTEM_LOG` hiện có. Các nhóm được phép tra cứu:

| TargetTable | Thao tác ghi nhận |
|---|---|
| USER | Tạo/sửa tài khoản, khóa/mở khóa, admin đặt lại mật khẩu, người dùng đã xác thực tự đổi mật khẩu |
| SYSTEM_CONFIG | Thay đổi tham số nghiệp vụ, biểu giá và công thức |
| SERVICE | Tạo/sửa dịch vụ, đổi trạng thái |
| ROUTE | Tạo/sửa/ngừng tuyến, gồm các điểm dừng theo thứ tự |
| LOCATION | Tạo/sửa địa điểm, đổi trạng thái |
| DOCUMENT_REQUIREMENT | Tạo/sửa quy định giấy tờ, đổi trạng thái |
| HORSE_TYPE | Tạo/sửa/xóa giống ngựa, thay đổi giá trị bảo hiểm |

Log đơn vận chuyển và các bảng lịch sử chuyên biệt được giữ nguyên. API mới giới hạn nhóm dữ liệu ngay trong truy vấn database và kiểm tra lại nhóm khi đọc chi tiết, nên không thể mở log đơn qua ID.
Các bảng lịch sử địa điểm và giấy tờ tiếp tục ghi trong cùng transaction với audit tập trung.
Không ghi nhật ký từ thao tác GET hoặc mô phỏng giá. Không thêm API tạo/sửa/xóa nhật ký.

## Ghi dữ liệu

`AdminAuditWriter` dùng transaction nghiệp vụ hiện tại (`MANDATORY`). Nếu ghi audit thất bại, thay đổi quản trị phải rollback. Không bắt rồi bỏ qua lỗi ghi audit.
Danh tính được lấy từ `SecurityContext` và database, không lấy từ tên người thực hiện do frontend gửi.
Snapshot tên, email, vai trò được lưu tại thời điểm thao tác. Trường hợp sửa chính tài khoản đang đăng nhập phải chụp danh tính trước khi sửa username/email.

Mỗi thao tác lưu một sự kiện, với mô tả tách khỏi dữ liệu trước/sau. Dữ liệu mới có `DataVersion = 1` và JSON chỉ gồm các trường được cho phép; không serialize entity người dùng hoặc request chứa mật khẩu. Không ghi mật khẩu, hash, token, OTP.
Lưu không thay đổi dữ liệu sẽ không tạo audit mới. Sự kiện đặt lại/đổi mật khẩu chỉ ghi thông tin sự kiện, không có snapshot bí mật.

## Nâng cấp bảng đang có

Không xóa, đổi tên hoặc sửa các cột cũ. Bổ sung bảy cột nullable:

| Cột vật lý PostgreSQL | Kiểu |
|---|---|
| description | text |
| actor_name | varchar(255) |
| actor_email | varchar(255) |
| actor_role | varchar(50) |
| target_key | varchar(255) |
| target_label | varchar(255) |
| data_version | integer |

Repo hiện dùng Hibernate `ddl-auto=update`, tự bổ sung cột và index khi khởi động. Nếu môi trường triển khai dùng `validate` hoặc quản lý schema thủ công, áp dụng SQL tương đương trước khi chạy phiên bản mới:

```sql
BEGIN;
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS description text;
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS actor_name varchar(255);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS actor_email varchar(255);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS actor_role varchar(50);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS target_key varchar(255);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS target_label varchar(255);
ALTER TABLE system_log ADD COLUMN IF NOT EXISTS data_version integer;
CREATE INDEX IF NOT EXISTS idx_system_log_target_time
  ON system_log (target_table, created_at, system_logid);
CREATE INDEX IF NOT EXISTS idx_system_log_actor_time
  ON system_log (action_by_userid, created_at);
COMMIT;
```

Log có `DataVersion` null được đánh dấu dữ liệu lịch sử. Nội dung trước/sau được hiển thị nguyên văn, không suy đoán cấu trúc JSON hoặc khôi phục thông tin đã mất. Nếu thiếu người thực hiện, hiển thị “Không xác định”. Nếu có liên kết tài khoản, thông tin hiển thị là tài khoản hiện tại, không phải snapshot lịch sử.
Log cũ không có vai trò sẽ không khớp bộ lọc vai trò. Không backfill người thực hiện hoặc loại đối tượng bằng suy đoán.

## API và giao diện

- `GET /admin/system-logs`: bộ lọc `target`, `action`, `actor` (tên/email), `role`, `key` (ID/tên/khóa cấu hình), `from`, `to`, `page`, `size`.
- `GET /admin/system-logs/options`: danh mục nhóm/hành động từ cùng định nghĩa mà writer kiểm tra.
- `GET /admin/system-logs/{id}`: chi tiết nhật ký quản trị, trả 404 nếu ID thuộc nhóm khác hoặc không tồn tại.

Kết quả nằm trong `ApiResponse.data`: `content`, `totalElements`, `totalPages`, `number`, `size`. Trang bắt đầu từ 0; kích thước tối đa 100. Sắp xếp `createdAt DESC, id DESC` để ổn định khi nhiều sự kiện cùng thời gian.
ID số được so khớp chính xác, nên ID 7 không trả nhầm ID 17. Tên/khóa chuỗi tìm theo phần nội dung; ký tự `%` và `_` được escape, không trở thành wildcard do người dùng nhập.

Database lưu thời gian UTC (`Instant`). Giao diện hiển thị Việt Nam UTC+7 với giây; khoảng lọc mặc định bảy ngày lịch. API dùng `from <= createdAt < to`, giao diện chuyển ngày kết thúc thành đầu ngày tiếp theo để không bỏ sót bản ghi cuối ngày.
URL lưu bộ lọc, phân trang và ID chi tiết. Thay bộ lọc đưa về trang đầu. Request cũ bị hủy khi chuyển bộ lọc/trang. Lỗi tải, danh sách trống và trang vượt phạm vi được hiển thị riêng.

## Kiểm tra

- Unit test writer: danh tính xác thực, snapshot, trường bí mật, không thay đổi, lỗi ghi audit.
- Unit test reader: phạm vi quản trị, dữ liệu cũ, snapshot danh tính, phân trang.
- Method security: sáu vai trò còn lại không đọc được danh sách, chi tiết, danh mục bộ lọc.
- Integration test PostgreSQL: truy vấn thật, escape, ID chính xác, biên thời gian, rollback cấu hình, admin sửa chính tài khoản.
- Frontend: biên ngày UTC+7, dữ liệu trước/sau và ánh xạ lịch sử tài khoản.
- Playwright `e2e/system-logs.e2e.ts`: tra cứu, phân trang, chi tiết, Escape, retry và chặn vai trò. Test giao diện dùng API giả lập; kiểm tra database thực hiện ở integration test backend.

Chạy các Quality Gate theo `frontend/docs/AGENT_GUIDE.md`. Một số test backend hiện có dùng Testcontainers và yêu cầu Docker, ngoài PostgreSQL dùng cho `contextLoads` và integration test audit.
