# Kết quả kiểm thử luồng local — BK-004

Ngày kiểm tra: 12/11/2026, múi giờ Asia/Saigon.

Ứng dụng: http://localhost:5173, API: http://localhost:8080/api/v1.
Backend và database thật trên Docker local, không mock API nghiệp vụ.
Turnstile dùng chế độ test đã cấu hình. Không sửa mã nguồn trong lượt kiểm thử này.

## Dữ liệu test

- Đơn: BK-004, nội địa, 1 ngựa QA FLOW 20261112 (H-10).
- Tuyến: Kho Đồng Nai → CLB Cưỡi ngựa Sài Gòn, 31 km đường xe tải.
- Khởi hành: 20/12/2026, 05:00 giờ Việt Nam.
- Xe: 65C-101.22, chuyến TRP-004-1; driver02 và escort02.
- Hồ sơ ngựa và đơn ban đầu tạo qua API thật; các bước tiếp nhận, duyệt,
  chốt xe, phát hành báo giá và xử lý giấy tờ được thao tác bằng Playwright trên giao diện.
- Giấy tờ là tệp ảnh mẫu đánh dấu QA, không phải chứng từ vận chuyển thực.
- Theo xác nhận của người dùng, đã đặt lại mật khẩu driver02 và escort02 trên database local.
- Các bước vật lý được mô phỏng trên đơn test; camera dùng thiết bị giả lập Chromium.
  Upload ảnh, kiểm tra chứng từ, API, database và callback VNPay vẫn chạy thật.

## Kết quả

| Bước | Kết quả |
|---|---|
| Tạo ngựa, tải hộ chiếu, tiêm chủng, xét nghiệm | Thành công |
| Khách tạo đơn | pending_intake |
| Quản lý tiếp nhận, giao chuyên viên/điều phối | under_review |
| Chuyên viên đối chiếu, duyệt | API thành công; danh sách vẫn hiển thị Cần duyệt khi quay lại không refresh |
| Điều phối tính tuyến Valhalla, chốt xe | pending_commercial; Cần xác nhận giảm ngay về 0, Đã chốt tăng lên 4 |
| Quản lý phân công kíp và phát hành báo giá | awaiting_payment |
| Khách xem báo giá | Tổng 9.301.000đ; cọc 2.790.000đ; còn lại 6.511.000đ |
| Phí nhân sự | Không có dòng thu riêng trong dữ liệu báo giá |
| Thanh toán cọc bằng thẻ test VNPay sandbox | Thành công, waybill_issued |
| Cọc lần hai | Bị chặn HTTP 409 |
| Thanh toán 70% trước khi nhận ngựa | Bị chặn HTTP 409 |
| Tải giấy kiểm dịch, sửa ghi chú | Tái hiện lỗi: photos từ 1 → 0, done → todo |
| Khôi phục ảnh mẫu, tải giấy ủy quyền, hoàn tất giấy tờ | Thành công, clearance_done |
| Bản đồ khách trên desktop 1440px và mobile 390px | Không tràn ngang trong trường hợp này |
| Tài xế nhận lệnh | clearance_done; chờ hộ tống |
| Hộ tống nhận lệnh | ready_for_pickup |
| Tài xế xuất phát, chụp ảnh tại điểm đón | en_route_to_pickup |
| Hộ tống quét chip, tài xế thu bản gốc và tải biên bản | Thành công |
| Bắt đầu hành trình trước khi trả 70% | Nút bị khóa đúng |
| Thanh toán 70% bằng VNPay sandbox | Thành công, 6.511.000đ; tổng đã trả 9.301.000đ |
| Khởi hành vận chuyển, check-in điểm giao | in_transit |
| Hộ tống ghi an sinh, tài xế trả bản gốc và tải biên bản giao | Thành công |
| Hoàn tất giao ngựa | delivered_pending_settlement |
| Gửi bảng kê không có phát sinh | Tự phát hành quyết toán 0đ, settlement_issued |
| Khách đánh giá và xác nhận quyết toán 0đ | completed, 5 sao |
| Trước khi xác nhận về trụ sở | API khả dụng vẫn khóa driver02/escort02; nhưng báo xe VH-006 rảnh sớm |
| Tài xế chụp ảnh và xác nhận về trụ sở | completed, có homeReturn.backAt; cả kíp đã được nhả |

Thanh toán dùng [thẻ test chính thức của VNPay](https://sandbox.vnpayment.vn/apis/vnpay-demo/),
trên sandbox.vnpayment.vn; không dùng tiền hay thẻ thật.
Kênh gửi email local tạm ngắt trong lúc phát hành báo giá và thanh toán để tránh gửi ra ngoài.
Đã khôi phục cấu hình email ban đầu và giữ Turnstile test sau khi kiểm tra.

## Lỗi cần sửa

**Cập nhật:** cả 5 mục dưới đây đã được sửa và kiểm chứng lại trên BK-005.
Xem [báo cáo sau sửa](../live-fixes-BK005/report.md). Các quan sát dưới đây được giữ làm bằng chứng trước sửa.

1. Duyệt hồ sơ ngựa xong, quay lại danh sách vẫn thấy đơn trong Cần duyệt: cache chưa bị vô hiệu hóa.
2. Sửa ghi chú giấy tờ làm mất liên kết ảnh đã tải và đổi trạng thái về Chưa nộp.
3. Thanh tiến độ khách dùng chỉ số của 8 bước nghiệp vụ cho giao diện 6 bước: đang Chờ đặt cọc nhưng hiển thị Chuẩn bị chuyến.
4. Nhật ký giấy tờ thêm một dòng mỗi lần cập nhật, gồm cả sửa ghi chú; chưa gộp theo phiên chỉnh sửa.
5. API xe khả dụng báo VH-006 AVAILABLE trước khi xác nhận về trụ sở, trong khi
   homeReturn chưa có backAt. API nhân sự theo cùng ngày vẫn khóa đúng kíp xe.
   Đã xác nhận trạng thái API; chưa thử đặt đơn thứ hai trùng xe để xác minh khả năng phân công thực tế.

Ảnh mẫu đã được khôi phục sau khi tái hiện lỗi. Trạng thái cuối: completed,
đã thanh toán đủ 9.301.000đ trên sandbox, đã ghi nhận về trụ sở và giải phóng kíp xe.
Các phiên tạo thanh toán cọc thử trước đó đã CANCELLED; chỉ có hai giao dịch COMPLETED:
cọc 2.790.000đ và số dư 6.511.000đ. Không sửa mã nguồn để xử lý các lỗi đã tìm thấy.

Phạm vi: luồng nội địa bình thường, không có sự cố/phát sinh. Chưa kiểm tra luồng
quốc tế, SOS, quyết toán có thu thêm tiền hoặc vận hành đồng thời nhiều xe.

## Ảnh kiểm chứng

- [Danh sách chuyên viên còn đơn đã duyệt](specialist-queue.png)
- [Báo giá trước khi phát hành](quote-before-send.png)
- [Báo giá phía khách](customer-quote.png)
- [Kết quả thanh toán cọc](deposit-result.png)
- [Sửa ghi chú làm mất ảnh](note-removes-document.png)
- [Giấy tờ được khôi phục](documents-restored.png)
- [Bản đồ desktop](customer-map-1440.png)
- [Bản đồ mobile](customer-map-390.png)
- [Tài xế nhận lệnh](driver-acknowledged.png)
- [Hộ tống nhận lệnh](escort-acknowledged.png)
- [Đón ngựa, chờ số dư](pickup-before-balance.png)
- [Thanh toán 70%](balance-paid.png)
- [Giao ngựa](delivered.png)
- [Đơn hoàn tất](completed.png)
- [Xác nhận về trụ sở](returned-home.png)
- [Trạng thái tài nguyên trước khi về trụ sở](resources-before-home.json)
