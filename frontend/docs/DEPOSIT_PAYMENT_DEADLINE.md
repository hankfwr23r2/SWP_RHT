# Hạn thanh toán cọc và số dư

## Cọc và khoảng chờ xác nhận

Hạn báo giá cũng là hạn cọc. Khách chấp nhận báo giá trước khi tạo phiên VNPAY. Link cọc hết hạn ở mốc sớm hơn giữa giờ tạo link cộng 15 phút mặc định và hạn báo giá. Nếu link hết hạn trước báo giá, khách có thể tạo link khác khi báo giá còn hiệu lực. Từ đúng giây hết hạn báo giá, backend chặn tạo phiên cọc mới.

Nếu khoản cọc của báo giá hiện tại có phiên VNPAY `PENDING` mở trước hạn, đơn giữ trạng thái `AWAITING_DEPOSIT` và giữ chỗ thêm tối đa 5 phút tính từ hạn báo giá. Phiên của báo giá cũ không giữ chỗ cho báo giá mới. Khoảng chờ dành cho kết quả trả tiền đúng hạn. Khi thiếu phiên đang chờ, tác vụ có thể nhả chỗ ngay ở lượt quét sau hạn.

Backend kiểm chữ ký, mã đơn vị, mã phiên và số tiền. Kết quả thành công có `vnp_ResponseCode` và `vnp_TransactionStatus` cùng bằng `00`. Để ghi nhận cọc, đơn phải còn `AWAITING_DEPOSIT`, báo giá `ACCEPTED` và khoản cọc `PENDING`. Giờ `vnp_PayDate` phải hợp lệ, từ giây tạo phiên trở đi, trước hạn báo giá và không sau thời điểm backend nhận kết quả. Backend phải nhận kết quả trước mốc hết khoảng chờ. Giờ VNPAY dùng GMT+7 và định dạng `yyyyMMddHHmmss`. [Tài liệu VNPAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html)

| Ví dụ với hạn cọc 10:00:00 | Xử lý |
|---|---|
| Trả lúc 09:59:59, kết quả đến lúc 10:02, đơn còn giữ chỗ | Cọc `PAID`, cấp số vận đơn và chuyển đơn sang `CONFIRMED` |
| Trả từ 10:00:00 hoặc kết quả thành công đến từ 10:05:00 | Ghi nhận giao dịch và báo quản lý đối soát, kể cả khi tác vụ hết hạn chưa quét |
| Kết quả thành công thiếu hoặc sai `vnp_PayDate`, thuộc báo giá cũ hoặc đến sau khi nhả chỗ | Đối soát. Giữ nguyên trạng thái đơn, không khôi phục chỗ đã nhả |
| VNPAY xác nhận thất bại | Giao dịch `FAILED`. Khoản cọc còn `PENDING` có thể thanh toán lại khi báo giá còn hạn |
| Hết khoảng chờ và chưa có kết quả | Khoản cọc `CANCELLED`, giao dịch chưa rõ kết quả vẫn `PENDING`. Nhả chỗ trên chuyến, báo giá `EXPIRED` và đơn `QUOTE_EXPIRED` |

`payment` lưu khoản thu. `payment_transaction` lưu phiên và kết quả VNPAY. Nếu đã nhận tiền nhưng chưa thể áp dụng vào khoản thu, kết quả Return URL có `needsReview = true`. Quản lý đối soát và xử lý hoàn tiền. Khách đã trả tiền chờ kết quả đối soát.

## Số dư tại nơi nhận

Khi nhận ngựa của đơn `CONFIRMED` đã trả cọc, backend lập khoản `BALANCE` nếu còn phải thu. Hạn `due_at` tính từ giờ nhận ngựa cộng `BALANCE_WAIT_MINUTES` (mặc định 30 phút). Xe chỉ rời nơi nhận khi các đơn có ngựa lên xe đã trả đủ.

Khi đến hạn mà khoản số dư còn `PENDING`, tổ xe trả ngựa tại chỗ và xử lý hủy đơn. Nếu có phiên VNPAY mở trước hạn và còn chờ kết quả, backend hoãn hủy đến tối đa 5 phút sau `due_at`. `BookingCancellationServiceImpl` trả 409 khi `PaymentCheckoutService.hasPendingBalanceConfirmation(bookingId)` còn đúng. Nếu phiên đã có kết quả thất bại và không còn phiên phù hợp khác, có thể hủy ngay khi quá hạn.

Trong code hiện tại, mốc 30 phút và khoảng chờ 5 phút điều khiển việc hủy do quá hạn. `PaymentPolicy` vẫn cho tạo link số dư khi đơn còn `CONFIRMED`, báo giá `ACCEPTED` và khoản thu còn `PENDING`. Việc áp dụng kết quả số dư cũng xét trạng thái khoản thu và đơn. Khoản tiền về sau khi đơn đã hủy cần đối soát.

## API và khóa booking

- `GET /api/v1/bookings/{bookingId}/payments` trả danh sách khoản thu có `dueAt` và `confirmationUntil`. Backend tính `confirmationUntil` từ các phiên đang chờ phù hợp.
- `POST /api/v1/payments/{id}/checkout` tạo link theo ID khoản thu.
- `GET /api/v1/payments/vnpay/ipn` nhận kết quả trực tiếp từ VNPAY. Khi trình duyệt quay về, trang kết quả gửi tham số qua `GET /api/v1/payments/vnpay/return` để backend xác thực và xử lý cùng logic. IPN có thể ghi nhận kết quả dù khách mất kết nối. Khi chưa nhận kết quả, phiên có thể vẫn `PENDING`.
- Tạo phiên, xử lý kết quả và hết hạn báo giá dùng chung khóa dòng booking. Backend ghi giờ nhận kết quả trước khi chờ khóa và kiểm lại trạng thái sau khi lấy khóa. Ràng buộc duy nhất của `payment_transaction` chống ghi trùng kết quả IPN và Return URL.
- Nếu nhận cọc hợp lệ trước, tác vụ hết hạn thấy đơn đã `CONFIRMED` và bỏ qua. Nếu nhả chỗ trước, kết quả thành công đến sau được đưa vào đối soát.

## Hiển thị ở FE luồng A

Còn hạn thì khách có thể tạo link cọc. Khi đã hết hạn và có `confirmationUntil` còn hiệu lực, hiện “Đang xác nhận thanh toán” cùng mốc chờ và cập nhật dữ liệu định kỳ. Từ hạn cọc, chặn tạo link cọc mới. Kết quả `needsReview = true` cần hiện “Đã nhận tiền, cần đối soát” và hướng khách về chi tiết đơn để chờ xử lý.

## Code và cấu hình

| Thành phần | Vai trò |
|---|---|
| [PaymentPolicy](../../backend/src/main/java/com/example/racehorse_transport/policy/PaymentPolicy.java), [DepositPaymentWindowPolicy](../../backend/src/main/java/com/example/racehorse_transport/policy/DepositPaymentWindowPolicy.java) | Kiểm tra điều kiện thanh toán và mốc nhận kết quả |
| [PaymentCheckoutServiceImpl](../../backend/src/main/java/com/example/racehorse_transport/service/impl/PaymentCheckoutServiceImpl.java), [PaymentResultServiceImpl](../../backend/src/main/java/com/example/racehorse_transport/service/impl/PaymentResultServiceImpl.java) | Tạo phiên, xác nhận kết quả và báo đối soát |
| [PaymentServiceImpl](../../backend/src/main/java/com/example/racehorse_transport/service/impl/PaymentServiceImpl.java), [QuotationServiceImpl](../../backend/src/main/java/com/example/racehorse_transport/service/impl/QuotationServiceImpl.java), [QuotationExpiryScheduler](../../backend/src/main/java/com/example/racehorse_transport/scheduler/QuotationExpiryScheduler.java) | Lập khoản số dư, xử lý và quét báo giá hết hạn |
| [PaymentResponseDto](../../backend/src/main/java/com/example/racehorse_transport/dto/payment/PaymentResponseDto.java), [PaymentResultResponseDto](../../backend/src/main/java/com/example/racehorse_transport/dto/payment/PaymentResultResponseDto.java) | Hợp đồng API cho hạn thanh toán và `needsReview` |
| [VnpayReturnPage.tsx](../src/apps/customer/features/payment/VnpayReturnPage.tsx), [payments.ts](../src/shared/api/payments.ts) | FE cũ vẫn dùng `paymentStatus === 'NEEDS_REVIEW'` và API tạo link cũ. Cần chuyển sang hợp đồng hiện tại khi làm FE luồng A |
| [DepositPaymentWindowPolicyTest](../../backend/src/test/java/com/example/racehorse_transport/policy/DepositPaymentWindowPolicyTest.java), [PaymentPolicyTest](../../backend/src/test/java/com/example/racehorse_transport/policy/PaymentPolicyTest.java), [QuotationPaymentPostgresTest](../../backend/src/test/java/com/example/racehorse_transport/service/QuotationPaymentPostgresTest.java) | Kiểm tra chính sách thanh toán và luồng báo giá, cọc trên PostgreSQL |

Cấu hình mặc định trong [application.properties](../../backend/src/main/resources/application.properties) gồm `VNPAY_EXPIRE_MINUTES=15`, `VNPAY_DEPOSIT_CONFIRMATION_GRACE_MINUTES=5` và `VNPAY_QUOTATION_EXPIRY_SCAN_MS=30000`. Tác vụ chạy lần đầu sau 30 giây và nghỉ 30 giây sau mỗi lượt. Chỗ được nhả ở lượt quét đủ điều kiện sau khi lấy khóa booking.
