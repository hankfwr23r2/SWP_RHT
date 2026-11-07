# Thanh toán cọc sát hạn báo giá

Hạn báo giá cũng là hạn thanh toán cọc. Link VNPAY hết hạn ở mốc sớm hơn giữa
thời gian sống của link (mặc định 15 phút) và hạn báo giá. Từ đúng giây hết hạn,
backend không tạo phiên cọc mới.

## Giữ chỗ chờ kết quả

Nếu có giao dịch cọc VNPAY `PENDING` được tạo trong thời gian hiệu lực của báo giá
hiện tại, backend giữ nguyên phân công xe, driver và escort thêm tối đa 5 phút.
Thời gian này tính từ hạn báo giá, không tính lại theo callback hoặc lần tải trang.
Manager không được đổi kíp xe ở bước chờ cọc; lịch đang giữ vẫn được tính khi kiểm tra
xung đột phân công cho đơn khác.

Nếu không có giao dịch đang chờ, hoặc VNPAY đã xác nhận thất bại, đơn được nhả chỗ
ở lượt quét hết hạn tiếp theo. Chu kỳ quét mặc định là 30 giây. Sau khoảng chờ,
việc nhả chỗ có thể trễ thêm một chu kỳ quét khi hệ thống hoạt động bình thường.

Ví dụ hạn báo giá 10:00:00, kết thúc khoảng chờ là 10:05:00:

| Kết quả | Xử lý |
|---|---|
| Trả lúc 09:59:59, callback đến trước 10:05:00, đơn vẫn giữ chỗ | Cọc `COMPLETED`, báo giá `ACCEPTED`, đơn `waybill_issued`; giữ phân công |
| VNPAY báo thất bại | Payment `FAILED`; nếu đã hết hạn, nhả chỗ ở lượt quét tiếp theo |
| Trả từ 10:00:00 trở đi | Payment `NEEDS_REVIEW`, không cấp vận đơn |
| Callback đến từ 10:05:00 trở đi, kể cả tác vụ chưa kịp quét | Payment `NEEDS_REVIEW`, không cấp vận đơn |
| Hết khoảng chờ mà chưa có kết quả | Đơn `quote_expired`, xóa liên kết xe/kíp xe; payment vẫn `PENDING` và có ghi chú chờ đối soát |
| Tiền về sau khi đã nhả chỗ hoặc từ phiên bị thay thế | Payment `NEEDS_REVIEW`, không tự khôi phục phân công |

`PENDING` có nghĩa là chưa biết kết quả, không phải xác nhận thanh toán thất bại.
`NEEDS_REVIEW` có nghĩa đã có thông báo tiền về nhưng chưa áp dụng được vào đơn.
Quản lý cần kiểm tra và xử lý hoàn tiền; hệ thống chưa tự động gọi API hoàn tiền.

## Thời điểm và chống xử lý đồng thời

Backend xác thực chữ ký và số tiền trước khi dùng `vnp_PayDate`. Thời điểm này được
đọc theo GMT+7, định dạng `yyyyMMddHHmmss`, theo
[tài liệu thanh toán VNPAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
Thông báo thành công của tiền cọc mà thiếu/sai thời điểm được đưa vào đối soát,
không suy đoán thời điểm khách trả bằng thời gian backend nhận callback.

Hạn được so sánh ở độ chính xác giây giống `vnp_ExpireDate`; giây hết hạn không còn
được thanh toán. `PaymentDate` của phiên chờ là giờ tạo phiên, còn khi nhận tiền
thành công được cập nhật theo giờ VNPAY ghi nhận thanh toán.

Tạo phiên, xác nhận callback, từ chối/hủy đơn, phân công kíp xe và tác vụ hết hạn
đều khóa booking trước khi thay đổi dữ liệu liên quan. Callback tiếp tục khóa payment
sau booking để IPN và Return URL không ghi nhận cùng giao dịch hai lần.
Tác vụ hết hạn chỉ lấy danh sách ID rồi mới khóa và đọc lại từng booking, tránh
dùng trạng thái cũ đã nạp trước khi chờ khóa.

## Cấu hình backend

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `VNPAY_EXPIRE_MINUTES` | `15` | Thời gian sống tối đa của link |
| `VNPAY_DEPOSIT_CONFIRMATION_GRACE_MINUTES` | `5` | Số phút chờ kết quả sau hạn báo giá |
| `VNPAY_QUOTATION_EXPIRY_SCAN_MS` | `30000` | Chu kỳ quét hết hạn, tính bằng mili giây |

Các biến được Spring Boot đọc từ môi trường chạy backend. Nếu chạy Docker Compose
và muốn thay mặc định, cần truyền biến vào service backend trong Compose.

API booking có trường tùy chọn `depositConfirmationUntil` (timestamp mili giây),
đồng bộ với TypeScript. Frontend thông báo đang xác nhận, chặn tạo phiên sau hạn
và cập nhật định kỳ. Trang kết quả phân biệt đã nhận tiền cần đối soát với thất bại.
Khoảng chờ không tự đóng cửa sổ VNPAY đang mở.

## Kiểm thử

Unit test dùng đồng hồ cố định để kiểm tra mốc hết hạn và mốc hết 5 phút, callback
chậm, thất bại, thiếu/sai thời điểm và phiên cũ. `DepositPaymentConcurrencyTest`
chạy PostgreSQL trong Testcontainers, kiểm tra cả hai thứ tự nhận cọc/nhả chỗ bằng
hai transaction thật và xác nhận luồng còn lại phải chờ khóa database.
