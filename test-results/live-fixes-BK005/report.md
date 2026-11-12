# Kiểm chứng sửa 5 lỗi — BK-005

Ngày: 12/11/2026, Asia/Saigon. Bản Docker local đã build lại từ source đã sửa.

## Kết quả

| Lỗi | Kiểm chứng sau sửa |
|---|---|
| Duyệt xong vẫn ở Cần duyệt | Chuyên viên mở danh sách, duyệt BK-005 rồi quay lại bằng điều hướng nội bộ: Cần duyệt giảm về 0 ngay, không refresh trang. |
| Sửa ghi chú mất ảnh | Tải hai ảnh mẫu, lưu, sửa ghi chú nhiều lần và lưu lại: mỗi hạng mục vẫn có 1 ảnh, trạng thái done. Thử cả API POST cũ chỉ gửi note: ảnh giữ nguyên. |
| Thanh tiến độ sai bước | awaiting_payment hiển thị bước 3/6 Báo giá & cọc; settlement_issued hiển thị bước 6/6; chỉ completed mới hiển thị Đơn đã hoàn tất. |
| Tự lưu và nhật ký quá nhiều | Upload, sửa ghi chú và blur không gửi cập nhật nghiệp vụ; nút Lưu cập nhật gửi 1 PUT cho nhiều hạng mục và backend thêm đúng 1 dòng lịch sử. |
| Xe rảnh trước khi về trụ sở | Sau completed nhưng chưa có homeReturn.backAt: available=false, cả kíp vẫn bận. Thử phân công VH-006 cho BK-006 bị HTTP 409 vì chưa về trụ sở. Sau CONFIRM_HOME, xe và kíp có available=true. |

BK-006 là dữ liệu phụ kiểm tra chặn phân công trùng; đã chuyển rejected với lý do QA để không giữ tài nguyên.

## Luồng thực tế đã chạy lại

Tạo ngựa H-11 và 3 giấy tờ QA → tạo BK-005 → quản lý tiếp nhận → chuyên viên duyệt
→ điều phối chốt xe, tuyến Valhalla 31km → quản lý phân công kíp, phát hành báo giá
→ khách cọc 2.790.000đ bằng VNPay sandbox → cập nhật giấy tờ theo đợt
→ tài xế/hộ tống nhận lệnh → đón ngựa, quét chip, thu bản gốc, chụp biên bản
→ thanh toán 70% còn lại 6.511.000đ trên sandbox → vận chuyển → ghi an sinh
→ trả giấy tờ gốc, ký giao nhận → giao ngựa → kê khai không phát sinh
→ quyết toán tự động 0đ → khách đánh giá → completed → chặn xe đến khi CONFIRM_HOME.

Tổng đã thanh toán sandbox: 9.301.000đ. Không có phí nhân sự riêng.
Hồ sơ, booking và callback thanh toán dùng backend/database thật. Những bước vật lý được
mô phỏng trên dữ liệu QA, ảnh dùng camera giả lập Chromium. Không dùng tiền hoặc thẻ thật.
Email local được tạm ngắt trong lúc phát hành/chốt tài chính, sau đó khôi phục cấu hình ban đầu.

## Kiểm thử tự động

- `mvnw verify`: 482 test, 0 lỗi/thất bại, 1 skipped.
- Frontend: 315 test đạt; lint, `tsc -b`, production build đạt.
- Playwright: 36 đạt, 3 skipped do yêu cầu biến môi trường/tài khoản/mã đơn riêng.
- Ba E2E mới: lưu nhiều thay đổi một lần, giữ bản nháp khi lưu lỗi, chặn rời trang chưa lưu.
- Test backend: note-only giữ ảnh, xóa ảnh chủ động, batch ghi 1 lịch sử, no-op không ghi lặp,
  chặn sửa ở trạng thái khóa; giữ riêng từng xe chưa về HQ kể cả đơn completed và lịch mới rất xa.

## Bằng chứng

- [Danh sách chuyên viên sau duyệt](specialist-queue.png)
- [Điều phối đã chốt](coordinator-confirmed.png)
- [Đúng bước chờ cọc](progress-awaiting-deposit.png)
- [Bản nháp giấy tờ](documents-draft.png)
- [Ảnh còn nguyên sau lưu](documents-saved.png)
- [Thanh toán cọc sandbox](deposit-paid.png)
- [Thanh toán 70% sandbox](balance-paid.png)
- [Đơn completed](completed.png)
- [Xe và kíp trước khi xác nhận về](resources-before-home.json)
- [Từ chối phân công xe chưa về](assignment-guard.json)
- [Xe và kíp sau khi xác nhận về](resources-after-home.json)
- [Tài xế xác nhận về trụ sở](returned-home.png)

Phạm vi kiểm chứng thực tế: luồng nội địa bình thường, một xe, không có chi phí phát sinh.
Chưa chạy lại trực tiếp luồng quốc tế, SOS hoặc quyết toán thu thêm tiền.
