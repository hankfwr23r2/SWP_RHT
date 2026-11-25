# Danh sách nghiệm thu (cập nhật 25/11/2026)

Nghiệm thu trên nhánh `redesign/system-v2`, máy cá nhân, dữ liệu mẫu. Thao tác và điều kiện chặn chi tiết của từng màn: `docs/MAN_HINH_VA_TAC_DONG.md`. Sai khác với file đó là lỗi.

## Cách chạy

1. `docker compose up -d postgres` (database mới hoặc đã `docker compose down -v`, vì lược đồ khác bản cũ).
2. Backend, trong `backend/`: đặt `FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/seed-dev`, rồi `.\mvnw.cmd spring-boot:run`. Cần `TURNSTILE_SECRET_KEY`; dùng khóa thử công khai của Cloudflare thì thêm `SPRING_PROFILES_ACTIVE=local,local-turnstile-test`.
3. Frontend, trong `frontend/`: đặt `VITE_TURNSTILE_SITE_KEY`, `npm install`, `npm run dev`. Khách ở `/`, nhân viên ở `/backoffice`.
4. Tài khoản mẫu và mật khẩu thử: đầu file `backend/src/main/resources/db/seed-dev/R__dev_seed_base.sql`. Đăng nhập bằng email `<tên>@racehorse.test`, ví dụ `manager.dev@racehorse.test`.


Mục tài khoản mẫu và `APP_SEED_REFERENCE_DATA` trong `README.md` là của bản cũ, không dùng.

Muốn chạy hết một đơn trong một buổi (tua giờ, thanh toán giả lập, không cần khóa): `docs/CHAY_THU.md`.

## Nghiệm thu được ngay

Đã mở bằng backend thật ở bề rộng 1280 và 360: mọi màn của nhân viên trong bảng. Cột "Cần thử" là việc của người nghiệm thu.

| Vai trò | Màn | Cần thử |
|---|---|---|
| Khách | Đăng ký, đăng nhập, quên mật khẩu | Mã OTP (không cấu hình mail thì mã in ở log backend) |
| Khách | Tổng quan, Hồ sơ, Địa chỉ, Ngựa | Thêm, sửa; chip sai hoặc trùng phải báo ngay dưới ô |
| Khách | Đặt đơn 4 bước | Nội địa, xuất, nhập; hết chỗ; ngựa đang bận; giữ bản nháp khi quay lại |
| Khách | Đơn của tôi, chi tiết, lịch sử | Nộp giấy, chấp nhận hoặc từ chối báo giá, đổi chuyến, hủy đơn (xem số tiền trước khi hủy), hành trình từng con ngựa, đánh giá |
| Chuyên viên | Thẩm định đơn, Đơn thiếu giấy | Duyệt và từ chối giấy, xác nhận hoặc mở lại bảo hiểm |
| Quản lý | Đơn hàng, Chuyến cần quyết định, Hoàn tiền | Báo giá, gửi, chuyến đột xuất, dồn, hủy chuyến, quyết toán, hủy đơn |
| Quản lý | Sự cố, Chuyến đi, Bảng giá | Duyệt phương án, báo cáo vận hành, tạo và kích hoạt bảng giá |
| Quản trị viên | Toàn bộ menu | Khóa và mở tài khoản, hotline, xe, đối tác, quy định, tuyến, lịch, bảng giá, nhật ký |
| Điều phối | Kế hoạch đơn, Theo dõi, Hub | Đổi chuyến trước khi cọc; ngựa nào ai giữ, trễ khám; lịch hẹn và giao nhận ở hub |
| Điều phối | Điều độ, Sự cố | Gom tự động, lập lượt xe với giờ tự tính, gán tổ xe, sơ đồ khoang; chốt sơ đồ trước khi xuất phát |
| Tài xế, hộ tống | Lượt xe của tôi | Nhận việc, xuất phát, điểm dừng, giao nhận, giấy gốc, báo sự cố (thử trên điện thoại) |

## Luồng nên đi trọn

1. Nội địa qua hub: đặt đơn → thẩm định → báo giá → cọc → điều độ → đón → trả số dư → trục → giao → đóng đơn → đánh giá. Phần vận hành dùng được ba đơn mẫu đã xác nhận, mã bắt đầu bằng `DEV-`.
2. Hủy đơn: trước cọc (không mất tiền), sau cọc (mất cọc, gồm phần thu hộ), công ty hủy (hoàn 100%).
3. Thiếu giấy quá mốc 48 giờ: quản lý chọn nguyên nhân, khách đổi chuyến.
4. Xuất và nhập qua cửa khẩu, có đại lý.
5. Không gặp người nhận; sự cố trên đường.

## Chưa kiểm chứng

- Thanh toán VNPAY từ đầu tới cuối (cần khóa sandbox của nhóm).
- Tải ảnh, giấy lên Cloudinary bằng khóa thật.
- Chỉ đường thật từ máy chủ Valhalla công cộng, gửi email thật.
- Một đơn đi trọn từ luồng A sang luồng B trên giao diện. Mỗi luồng mới được thử riêng.
- Chữ trong ba chính sách ở `docs/chinh-sach/` là bản nháp, chưa duyệt và chưa xuất PDF. Trang chính sách khách đang thấy vẫn là bản cũ.

## Chưa làm, đừng báo lỗi

- Cước giao lại, đồng hồ phí giữ dừng khi khách sẵn sàng, kết quả cuối của ngựa không người nhận, giờ giao tính lại sau mỗi điểm: đang làm.
- Chia nợ nhiều kỳ, đổi nơi giao sau khi cọc.
- Các gói G2, G3, G5 và điểm của bạn B chưa ghi Đã sửa trong `NGHIEP_VU_6_LUONG.md`.

## Báo lỗi

Ghi đủ: vai trò, màn, các bước, kết quả mong đợi (dẫn mục trong `MAN_HINH_VA_TAC_DONG.md`), ảnh chụp, và bề rộng máy nếu là lỗi hiển thị.
