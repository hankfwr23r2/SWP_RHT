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

---

## 2. Các Tính Năng Đang Tạm Ẩn Chờ Bổ Sung Module Backend 🛡️

Nhằm đảm bảo trải nghiệm người dùng không gặp lỗi ngoại lệ hoặc giao diện rỗng, các phần sau tạm thời ẩn:

| Tính năng | Vị trí giao diện | Lý do ẩn | Hướng xử lý hiện tại | Yêu cầu Backend bổ sung sau |
| :--- | :--- | :--- | :--- | :--- |
| **Chuông thông báo (`NoticeBell`)** | Header Khách hàng & Topbar Backoffice | Backend chưa có module Notifications theo thời gian thực (SSE/WebSocket), chuông luôn hiển thị 0 tin. | Đã ẩn icon chuông khỏi [AppHeader.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/ui/AppHeader.tsx) và [StaffTopbar.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/backoffice/layouts/StaffTopbar.tsx). | Cần xây dựng module Notifications (gửi thông báo khi có đơn mới, duyệt giá, báo sự cố,...) qua SSE hoặc WebSocket. |
| **Menu Báo cáo doanh thu & Chuyến đi** | Navigation bar Quản lý [staffMenus.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/backoffice/layouts/staffMenus.ts) | Backend chưa có `GET /api/v1/trip-reports` và API tổng hợp tài chính. | Đã tạm ẩn 2 mục menu này khỏi thanh điều hướng của Quản lý. | Cần bổ sung Controller báo cáo `ReportController` tính toán doanh thu/chi phí thực tế từ DB thay vì FE tính toán offline. |

---

## 3. Danh mục Cấu hình & Biểu phí Mẫu Giữ Làm Tầng Dự Phòng (Data Layer Fallback)

Các phần sau được giữ nguyên làm giá trị mặc định (Fallback) để duy trì hoạt động bình thường của toàn bộ hệ thống đặt chuyến và không phá vỡ 134 Unit Tests:
1. **Mạng lưới địa lý ([network.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/config/network.ts))**: Danh mục 16 tỉnh/thành phố, 5 cửa khẩu quốc tế, 9 kho bãi, 27 trạm nghỉ và 12 trạm cứu hộ (dùng để gợi ý lộ trình và bản đồ cứu hộ).
2. **Biểu phí mẫu ([booking-rules.ts](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/shared/config/booking-rules.ts))**: Mức bảo hiểm giống ngựa, gói cỏ/nước, khoang đơn, đơn giá km, kíp xe, margin 5%.
3. **Showcase trên trang chủ ([HomePage.tsx](file:///d:/SWP_BE/RaceHorse_Transport/frontend/src/apps/customer/features/home/HomePage.tsx))**: Hoạt ảnh SVG minh họa 3 tuyến VN, LA, KH cho khách tham khảo.
