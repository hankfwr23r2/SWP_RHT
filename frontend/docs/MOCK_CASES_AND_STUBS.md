# BÁO CÁO ĐỒNG BỘ: KẾT NỐI API THỰC TẾ & DANH MỤC CÁC TÍNH NĂNG CHỜ BACKEND

> **Tài liệu theo dõi tiến độ chuẩn hóa Backend – Frontend cho hệ thống RaceHorse Transport (SWP_RHT).**  
> Cập nhật mới nhất sau khi hoàn thành kết nối các API có sẵn và dọn dẹp các tính năng bị khuyết API từ Backend.

---

## 1. Các API Thực tế Đã Được Kết Nối Thành Công (Đã Hoàn Tất 100%) ✅

| Chức năng | API Backend được kết nối | File Frontend tích hợp | Trạng thái kỹ thuật |
| :--- | :--- | :--- | :--- |
| **Upload ảnh lên Cloudinary thật** | `POST /api/v1/files/upload`<br>`FileUploadController.java` | [CaptureField.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/ui/CaptureField.tsx)<br>[ImageThumb.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/ui/ImageThumb.tsx) | **Hoàn tất 100%**. Khi chụp ảnh hoặc chọn file ảnh, client gọi trực tiếp Cloudinary upload, nhận URL `https://res.cloudinary.com/...` và lưu vào DB. Ảnh xem trước không còn bị mất khi F5 tải lại trang. |
| **Xử lý Ngoại lệ Quá hạn bàn giao 2h (Delivery Timeout)** | `POST /api/v1/delivery/{id}/timeout`<br>`DeliveryController.java` | [DriverJob.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/backoffice/features/driver/DriverJob.tsx) | **Hoàn tất 100%**. Tại bước bàn giao, bổ sung nút và modal cho Tài xế kích hoạt báo sự cố quá 2h (`UNREACHABLE`, `DESTINATION_UNREADY`, `CONSIGNEE_REJECTED`), lựa chọn gửi trạm nghỉ (`STABLE_NEARBY`) hoặc quay về trụ sở (`RETURN_TO_HQ`). |
| **Nhật ký an sinh cá thể ngựa (Health Logs)** | `POST /api/v1/horse-health-logs`<br>`HorseHealthLogController.java` | [EscortJob.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/backoffice/features/escort/EscortJob.tsx) | **Hoàn tất 100%**. Khi Hộ tống ghi nhận thể trạng ngựa tại trạm nghỉ/điểm giao, hệ thống tự động ghi nhận vào bảng `horse_health_logs` cho từng cá thể ngựa trên xe. |
| **Tra cứu đơn công khai không đăng nhập** | `GET /api/v1/bookings/public-track`<br>`BookingController.java` | [HomePage.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/customer/features/home/HomePage.tsx)<br>[bookings.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/services/bookings.ts)<br>[SecurityConfig.java](file:///d:/SWP_BE/RaceHorse_Transport/backend/src/main/java/com/example/racehorse_transport/config/SecurityConfig.java) | **Hoàn tất 100%**. Tra cứu công khai bằng mã đơn (ID / Vận đơn) và 4 số cuối SĐT. Xác thực thông tin qua SĐT người gửi/nhận/khách hàng. Trả về tiến trình 5 bước gọn, trạm hiện tại và ETA mà không lộ thông tin tài chính nhạy cảm. |
| **Khách tự ý hủy đơn sau cọc (Mất cọc 100%)** | `POST /api/v1/payments/cancel?bookingId={id}`<br>`PaymentController.java`<br>`PaymentServiceImpl.java` | [OrderDetailPage.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/customer/features/orders/OrderDetailPage.tsx)<br>[bookings.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/services/bookings.ts) | **Hoàn tất 100%**. Áp dụng chính sách khách tự ý hủy đơn sẽ mất 100% tiền cọc (ghi nhận giao dịch `PENALTY` vào bảng `PAYMENT`, không cần tạo thêm entity `Refund`). Nút "Hủy đơn này" và modal xác nhận hoàn tất. |
| **Cập nhật tài khoản người dùng / nhân viên (Admin)** | `PUT /api/v1/admin/accounts/{userId}`<br>`AdminAccountController.java`<br>`AdminAccountServiceImpl.java` | [accounts.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/services/accounts.ts)<br>[adminAccounts.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/api/adminAccounts.ts) | **Hoàn tất 100%**. Cho phép Quản trị viên cập nhật họ tên, email, số điện thoại của nhân viên và khách hàng với đầy đủ kiểm tra trùng lặp email/SĐT và ghi log kiểm toán `SystemLog`. |
| **Báo cáo chuyến đi & Hiệu suất vận hành (Manager)** | `GET /api/v1/trip-reports`<br>`ReportController.java` | [reports.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/services/reports.ts)<br>[staffMenus.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/backoffice/layouts/staffMenus.ts) | **Hoàn tất 100%**. API trích xuất danh sách chuyến đi đã hoàn thành từ bảng `booking`, tổng hợp chi tiết từng chặng (`booking_leg`), nhật ký thể trạng ngựa (`horse_health_logs`), đánh giá sao và phản hồi của khách hàng. Đã mở lại 2 menu `/manager/reports` và `/manager/trip-reports` trên thanh điều hướng Quản lý. |
| **Hệ thống Thông báo & Chuông thông báo (`NoticeBell`)** | `GET /api/v1/notifications`<br>`PATCH /api/v1/notifications/{id}/read`<br>`PATCH /api/v1/notifications/read-all`<br>`NotificationController.java` | [notices.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/services/notices.ts)<br>[CustomerLayout.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/customer/layouts/CustomerLayout.tsx)<br>[StaffTopbar.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/backoffice/layouts/StaffTopbar.tsx) | **Hoàn tất 100%**. Tạo entity `Notification`, cung cấp API phân trang theo người dùng đang đăng nhập, đánh dấu đã đọc một thông báo hoặc tất cả thông báo, kèm theo bộ thông báo khởi tạo (seed) theo vai trò. Icon chuông đã được khôi phục trên cả Header Khách hàng và Topbar Backoffice. |

---

## 2. Các Tính Năng Đang Tạm Ẩn Chờ Bổ Sung Module Backend 🛡️

*Hiện tại không còn tính năng nào bị ẩn. Tất cả các tính năng từng bị ẩn hoặc dùng stub đã được bổ sung Controller/Service/Repository trên Backend và tích hợp hoàn chỉnh vào giao diện.*

---

## 3. Danh mục Cấu hình & Biểu phí Mẫu Giữ Làm Tầng Dự Phòng (Data Layer Fallback)

Các phần sau được giữ nguyên làm giá trị mặc định (Fallback) để duy trì hoạt động bình thường của toàn bộ hệ thống đặt chuyến và không phá vỡ 134 Unit Tests:
1. **Mạng lưới địa lý ([network.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/config/network.ts))**: Danh mục 16 tỉnh/thành phố, 5 cửa khẩu quốc tế, 9 kho bãi, 27 trạm nghỉ và 12 trạm cứu hộ (dùng để gợi ý lộ trình và bản đồ cứu hộ).
2. **Biểu phí mẫu ([booking-rules.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/config/booking-rules.ts))**: Mức bảo hiểm giống ngựa, gói cỏ/nước, khoang đơn, đơn giá km, kíp xe, margin 5%.
3. **Showcase trên trang chủ ([HomePage.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/customer/features/home/HomePage.tsx))**: Hoạt ảnh SVG minh họa 3 tuyến VN, LA, KH cho khách tham khảo.
