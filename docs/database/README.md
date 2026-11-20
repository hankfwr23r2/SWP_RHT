# Sơ đồ database SWP RHT

Mở [SWP_RHT_Database.drawio](SWP_RHT_Database.drawio) bằng draw.io. File gồm hai tab có thể chỉnh sửa: **ERD** chỉ hiện tên thực thể và **Logical Diagram** hiện đầy đủ kiểu dữ liệu, thuộc tính, PK/FK/UQ.

## Bản dùng trong tài liệu

| Sơ đồ | PNG | SVG phóng to không vỡ chữ |
| --- | --- | --- |
| ERD | [3464 × 1644](SWP_RHT_Database_ERD.png) | [ERD.svg](SWP_RHT_Database_ERD.svg) |
| Logical | [4505 × 2580](SWP_RHT_Database_Logical.png) | [Logical.svg](SWP_RHT_Database_Logical.svg) |

Bố cục gom các bảng thành cụm: User và Staff phía trên; Booking ở trung tâm nhóm vận chuyển; hồ sơ ngựa và dịch vụ bên trái; tuyến đường và xe bên phải; thanh toán phía dưới. Các bảng độc lập được xếp vào khoảng trống phía trên. Toàn bộ dây và chữ dùng màu đen.

Toàn bộ **34 entity trong ERD cùng kích thước 240 × 120 px**, cùng kiểu viền, bo góc và cỡ chữ. Tab Logical thống nhất chiều rộng bảng 360 px, tiêu đề 30 px và dòng thuộc tính 20 px; chiều cao bảng theo số thuộc tính để giữ đủ dữ liệu. Các điểm nối được chia đều trên mỗi cạnh, cách góc ít nhất 24 px; mỗi dây có đoạn thẳng ít nhất 32 px ở hai đầu trước khi rẽ.

Giữ đủ **34 bảng, 317 thuộc tính và 43 quan hệ**. Cả hai tab có **0 giao cắt dây, 0 đoạn dây chồng nhau và 0 dây xuyên bảng**; không dùng cầu vòng để che giao cắt. Khoảng cách nhỏ nhất giữa hai dây khác nhau là 24 px, bao gồm các góc rẽ và đầu nối. Logical giữ dòng thuộc tính 20 px, giữ nguyên tên cột, kiểu dữ liệu, NULL và ký hiệu khóa. Nên dùng SVG khi chèn vào tài liệu; bản Logical đầy đủ cần phóng to để đọc chi tiết.

## Quan hệ đề xuất

Đây là bản thiết kế trên diagram; code và database chưa thay đổi. Đường đen liền biểu diễn 38 FK gốc. Đường đen nét đứt biểu diễn 5 FK đề xuất:

| Bảng cha | Bảng con / cột FK | Quan hệ |
| --- | --- | --- |
| `booking` | `capture_asset.booking_id` | Một đơn có 0..N ảnh; mỗi ảnh thuộc một đơn. |
| `location` | `location_change_log.locationid` | Một địa điểm có 0..N dòng lịch sử sửa. |
| `users` | `notification.recipient_userid` | Một tài khoản có 0..N thông báo; thông báo có thể không có người nhận cụ thể. |
| `booking` | `notification.bookingid` | Một đơn có 0..N thông báo; thông báo có thể không liên quan đơn. |
| `users` | `password_reset_otps.userid` | Một tài khoản có 0..N OTP; mỗi OTP thuộc một tài khoản. |

Logical có hai cột đề xuất `notification.recipient_userid` (integer, nullable) và `password_reset_otps.userid` (integer, NOT NULL). Kiểu thiết kế của `notification.bookingid` đổi từ varchar(50) sang integer. Các khóa đề xuất hiển thị `FK*` màu đen và có tooltip giải thích.

Thông báo theo vai trò vẫn có thể để trống `recipient_userid`; diagram chưa thêm bảng người nhận để quản lý trạng thái đã đọc riêng cho từng người. `document_requirement`, `email_verification_otps` và `system_config` giữ độc lập. Hai bảng `document_requirement_change_log` và `route_cache` đã bỏ khỏi cả hai tab, nhưng vẫn tồn tại trong ứng dụng và database.

## Nguồn và kiểm tra

Nguồn ban đầu: `backend/src/main/resources/db/migration/V1__application_schema.sql` và tập bảng JPA. Đường dẫn/SHA-256 trong [metadata](SWP_RHT_Database.schema.json) mô tả baseline gốc; các mục `status: proposed` là thiết kế riêng của diagram.

[validation.json](validation.json) ghi kết quả đối chiếu bảng, thuộc tính, FK, cardinality và hình học. Cả hai tab được mở và xuất ảnh bằng draw.io. Thay đổi chỉ nằm trong `docs/database/`; không chạy Quality Gate ứng dụng cho thay đổi tài liệu này.
