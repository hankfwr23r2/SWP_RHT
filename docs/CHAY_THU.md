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
| Trụ sở TP.HCM | TP.HCM, Đồng Nai, Tây Ninh, Vĩnh Long, Cần Thơ, An Giang, Cà Mau, Đồng Tháp, Đắk Lắk, Khánh Hòa, Lâm Đồng |
| Hub Đà Nẵng | Đà Nẵng, Huế, Quảng Ngãi, Quảng Trị, Gia Lai |

Miền Bắc và các tỉnh còn lại chưa có hub: trang đặt đơn nói rõ và liệt kê các tỉnh đang phục vụ. Campuchia chỉ đi và về qua hub TP.HCM (Mộc Bài), Lào chỉ qua hub Đà Nẵng (Lao Bảo); mỗi đơn có tối đa một chặng trục.

## Máy cũ không khởi động được backend

Bản trước 25/11 có lỗi: máy đã từng lập lượt trục thì hễ dữ liệu mẫu đổi là backend dừng ở `fk_trip_stop_route_stop`. Đã sửa, chỉ cần lấy code mới, **không** phải xóa dữ liệu.
