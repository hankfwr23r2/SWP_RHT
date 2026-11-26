# Chạy thử một đơn trong một buổi (25/11/2026)

Dùng để thử hoặc demo trên máy cá nhân. Không cần khóa VNPAY, Cloudinary, mail hay Turnstile. **Không dùng cho bản deploy**: backend tự từ chối khởi động nếu cơ sở dữ liệu không nằm trên máy hoặc có profile `prod`/`production`.

## Cần vì sao

Một đơn thật kéo dài nhiều ngày: nhận đặt đóng trước giờ chạy 72 giờ, quản lý chỉ quyết định chạy trong 48 giờ cuối, đơn liên tỉnh cần 5 ngày làm giấy, đơn xuất 21 ngày. Chế độ này cho **tua giờ** nên chạy hết một đơn trong 20 phút, quy tắc không đổi.

## Bật

Cách 1, cả hệ thống bằng Docker:

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

Cách 2, chạy tay:

```powershell
docker compose up -d postgres
```

Trong `backend/`:

```powershell
$env:SPRING_PROFILES_ACTIVE="local-demo"; $env:FLYWAY_LOCATIONS="classpath:db/migration,classpath:db/seed-dev"; .\mvnw.cmd spring-boot:run
```

Trong `frontend/`:

```powershell
$env:VITE_LOCAL_DEMO="1"; $env:VITE_TURNSTILE_SITE_KEY="1x00000000000000000000AA"; npm run dev
```

Khách ở `http://localhost:5173`, nhân viên ở `http://localhost:5173/backoffice`, trang tua giờ ở `http://localhost:5173/api/v1/public/demo`. Tài khoản: `docs/NGHIEM_THU.md` mục "Cách chạy".

Mở nhiều vai cùng lúc thì dùng nhiều cửa sổ ẩn danh. Tua giờ xong phải **tải lại trang** đang mở.

## Chế độ này thay những gì

| Phần | Bình thường | Chạy thử |
|---|---|---|
| Giờ hệ thống | Giờ thật | Giờ thật cộng khoảng đã tua, giữ qua lần khởi động lại. "Về giờ thật" để bỏ |
| Việc tự động | Theo lịch | Chạy ngay sau mỗi lần tua: sinh chuyến, đóng nhận đặt, hết hạn báo giá, nhả chỗ, tự đóng đơn |
| Tệp tải lên | Cloudinary | Thư mục `local-demo/files` |
| VNPAY | Cổng thử của VNPAY | Trang "Thanh toán giả lập", ký và báo kết quả như cổng thật |
| Turnstile | Khóa thật | Khóa thử công khai của Cloudflare (vẫn cần mạng) |

Phiên đăng nhập làm mới bằng cookie 15 ngày theo giờ thật, không bị ảnh hưởng.

## Kịch bản: đơn liên tỉnh TP.HCM → Đà Nẵng (đã chạy trọn ngày 25/11/2026)

| # | Vai | Màn | Làm gì |
|---|---|---|---|
| 1 | Khách | Sổ địa chỉ, Hồ sơ ngựa | Thêm nơi đón ở TP.HCM, nơi giao ở Đà Nẵng, một con ngựa mới (4 con mẫu đang nằm trong đơn mẫu) |
| 2 | Khách | Đặt chuyến mới | Chọn chuyến không bị ghi "Không kịp làm giấy", gửi đơn |
| 3 | Khách | Chi tiết đơn | Nộp hộ chiếu ngựa, sổ tiêm phòng (tệp PDF hoặc ảnh bất kỳ) |
| 4 | Chuyên viên | Thẩm định đơn | Tiếp nhận, duyệt giấy, xác nhận bảo hiểm, bắt đầu báo giá |
| 5 | Quản lý | Đơn hàng | Lập bản nháp, gửi khách |
| 6 | Khách | Chi tiết đơn | Chấp nhận, trả cọc |
| 7 | Chuyên viên | Đơn thiếu giấy | Nộp thay và duyệt giấy kiểm dịch |
| 8 | Trang tua giờ | | Tua tới trong 48 giờ trước giờ chạy |
| 9 | Quản lý | Chuyến cần quyết định | **Chạy** |
| 10 | Điều phối | Điều độ | Gom tự động lượt **Đón**; **Lập lượt xe** loại **Trục** rồi xếp ngựa; Gom tự động lượt **Giao** ở hub Đà Nẵng với chính xe trục. Mỗi lượt phân công tài xế và hộ tống. Xếp đúng thứ tự đón, trục, giao |
| 11 | Tài xế, hộ tống | Lượt xe của tôi | Nhận việc, xuất phát, tới, nhận ngựa (quét chip, nhận giấy gốc, tải biên bản), hoàn tất |
| 12 | Khách | Chi tiết đơn | Trả số dư. Chưa trả thì xe không rời được |
| 13 | Tài xế | Lượt xe của tôi | Rời, tới hub, giao vào hub, kết thúc lượt |
| 14 | Tài xế, hộ tống | Lượt trục | Nhận từ hub, mỗi trạm ghi ăn uống và nghỉ 30 phút (tua 1 giờ), gửi chuồng qua đêm, nhận lại, giao vào hub |
| 15 | Tài xế, hộ tống | Lượt giao | Nhận từ hub, giao ngựa, về hub, kết thúc |
| 16 | Khách, quản lý | Chi tiết đơn | Đánh giá; đóng đơn |
| 17 | Quản lý, điều phối | Chuyến cần quyết định, Điều độ | Đưa xe về: quản lý xác nhận chạy chuyến Đà Nẵng – TP.HCM, điều phối bấm **Lập lượt về** ở hàng của xe |

Các loại khác: **chạy thẳng** (cùng hub, ví dụ Củ Chi → Bến Cát) bỏ bước 8, 9 và chỉ có một lượt; **đơn xuất** thêm **Chặng đại lý** ở Điều độ, bàn giao ở cửa khẩu, rồi điều phối bấm **Đại lý đã giao** ở trang Theo dõi.

## Vùng phục vụ của dữ liệu mẫu

| Hub | Tỉnh |
|---|---|
| Trụ sở TP.HCM (`HUB-HCM`) | TP.HCM, Đồng Nai, Tây Ninh, Vĩnh Long, Đồng Tháp, Khánh Hòa, Lâm Đồng |
| Hub Đà Nẵng (`HUB-DN`) | Đà Nẵng, Huế, Quảng Ngãi, Quảng Trị, Gia Lai |
| Hub Hà Nội (`HUB-HN`) | Hà Nội và 17 tỉnh, thành khác, gồm Điện Biên và Hà Tĩnh |
| Hub Cần Thơ (`HUB-CT`) | Cần Thơ, An Giang, Cà Mau |
| Hub Buôn Ma Thuột (`HUB-BMT`) | Đắk Lắk |

Nếu địa chỉ trong nước ở tỉnh chưa có hub, trang đặt đơn báo đầu nằm ngoài vùng phục vụ và liệt kê các tỉnh đang phục vụ.

## Tuyến trục của dữ liệu mẫu

Mỗi chiều là một tuyến, tổng cộng 22 tuyến. Mỗi tuyến có một chuyến mỗi tuần, 9 chỗ và chốt nhận đặt trước 72 giờ. Lịch có hiệu lực từ 01/11/2026 và dùng giờ Việt Nam. Đơn có tối đa một chặng trục nên các đơn quốc tế dùng tuyến nối trực tiếp hub phục vụ địa chỉ trong nước với cửa khẩu của nước đối tác.

| Hai đầu tuyến | Chiều đi | Chiều về |
|---|---|---|
| TP.HCM ⇄ Mộc Bài (Campuchia) | `HCM-PNH` · Thứ Hai 06:00 | `MOCBAI-HCM` · Thứ Hai 14:00 |
| TP.HCM ⇄ Đà Nẵng | `HCM-DN` · Thứ Ba 05:00 | `DN-HCM` · Chủ nhật 05:00 |
| Đà Nẵng ⇄ Lao Bảo (Lào) | `DN-LAOBAO` · Thứ Sáu 05:00 | `LAOBAO-DN` · Thứ Bảy 07:00 |
| Đà Nẵng ⇄ Hà Nội | `DN-HN` · Thứ Năm 05:00 | `HN-DN` · Thứ Bảy 05:00 |
| TP.HCM ⇄ Hà Nội | `HCM-HN` · Thứ Tư 05:00 | `HN-HCM` · Thứ Hai 05:00 |
| Hà Nội ⇄ Cầu Treo (Lào) | `HN-CAUTREO` · Thứ Bảy 09:00 | `CAUTREO-HN` · Chủ nhật 07:00 |
| TP.HCM ⇄ Cần Thơ | `HCM-CT` · Thứ Ba 07:00 | `CT-HCM` · Thứ Sáu 07:00 |
| Cần Thơ ⇄ Tịnh Biên (Campuchia) | `CT-TINHBIEN` · Thứ Tư 07:00 | `TINHBIEN-CT` · Thứ Năm 08:00 |
| TP.HCM ⇄ Buôn Ma Thuột | `HCM-BMT` · Thứ Ba 05:30 | `BMT-HCM` · Chủ nhật 06:00 |
| Buôn Ma Thuột ⇄ Bờ Y (Lào) | `BMT-BOY` · Thứ Tư 06:00 | `BOY-BMT` · Thứ Năm 07:00 |
| Buôn Ma Thuột ⇄ Lệ Thanh (Campuchia) | `BMT-LETHANH` · Thứ Sáu 07:00 | `LETHANH-BMT` · Thứ Bảy 07:00 |

Trên cơ sở dữ liệu đã nạp seed, Flyway cập nhật lịch và tác vụ sinh chuyến dùng lịch mới ở lượt chạy kế tiếp, mặc định lúc 00:15 giờ Việt Nam.

## Máy cũ không khởi động được backend

Bản trước 25/11 có lỗi: máy đã từng lập lượt trục thì hễ dữ liệu mẫu đổi là backend dừng ở `fk_trip_stop_route_stop`. Đã sửa, chỉ cần lấy code mới, **không** phải xóa dữ liệu.
