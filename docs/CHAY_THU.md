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

## Kịch bản: đơn liên tỉnh TP.HCM → Huế (mô hình bến xe)

| # | Vai | Màn | Làm gì |
|---|---|---|---|
| 1 | Khách | Sổ địa chỉ, Hồ sơ ngựa | Thêm nơi đón ở TP.HCM, nơi giao ở Huế, một con ngựa mới (4 con mẫu đang nằm trong đơn mẫu) |
| 2 | Khách | Đặt chuyến mới | Chọn chuyến tuyến TP.HCM – Đà Nẵng có nhãn Còn chỗ và không bị ghi "Không kịp làm giấy"; chọn dịch vụ cho cả đơn và cho từng con, gửi đơn |
| 3 | Khách | Chi tiết đơn | Nộp hộ chiếu ngựa, sổ tiêm phòng (tệp PDF hoặc ảnh bất kỳ) |
| 4 | Chuyên viên | Thẩm định đơn | Tiếp nhận, duyệt giấy, xác nhận bảo hiểm, bắt đầu báo giá |
| 5 | Quản lý | Đơn hàng | Lập bản nháp, gửi khách |
| 6 | Khách | Chi tiết đơn | Chấp nhận, trả cọc |
| 7 | Trang tua giờ | | Tua tới trong 48 giờ trước giờ chạy |
| 8 | Quản lý | Chuyến cần quyết định | Xem các xe của chuyến, bấm **Chạy** |
| 9 | Điều phối | Điều độ | Gom tự động lượt **Đón** ở hub TP.HCM; **Gợi ý chia xe** rồi lập các lượt **Trục**; gom tự động lượt **Giao** ở hub Đà Nẵng. Mỗi lượt phân công tài xế và hộ tống của hub đó |
| 10 | Tài xế, hộ tống | Lượt xe của tôi | Nhận việc, xuất phát, tới, nhận ngựa (quét chip, nhận giấy gốc, tải biên bản), hoàn tất |
| 11 | Khách | Chi tiết đơn | Trả số dư. Chưa trả thì xe không rời được |
| 12 | Tài xế | Lượt xe của tôi | Rời, tới hub, giao vào hub, kết thúc lượt |
| 13 | Hộ tống | Lượt trục | Ghi khám **trước khi chạy** rồi nhận từ hub; mỗi trạm nghỉ ghi ăn, uống, sức khỏe và nghỉ 30 phút (tua 1 giờ); tới hub Đà Nẵng giao vào hub |
| 14 | Hộ tống, tài xế | Lượt giao | Ghi khám **trước khi giao**, nhận từ hub, giao người nhận với mã nhận ngựa 6 số |
| 15 | Khách, quản lý | Chi tiết đơn | Đánh giá; đóng đơn |
| 16 | Tài xế lượt trục | Lượt xe của tôi | Xe về hub gốc: tới giờ thì bấm **Đã về**, kèm ảnh và tình trạng xe |

Các loại khác:
- **Chạy thẳng** (cùng hub, ví dụ Củ Chi → Biên Hòa): bỏ bước 7, 8 và chỉ có một lượt.
- **Đơn xuất** (TP.HCM → Phnom Penh qua Mộc Bài) và **đơn nhập** (Viêng Chăn → Vinh qua Cầu Treo): tuyến trục đi thẳng tới hub nước ngoài của công ty. Ở điểm dừng cửa khẩu, xe chỉ rời được khi mọi đơn trên xe đủ giấy của cửa khẩu đó. Đón và giao ở nước ngoài do xe và người của hub nước ngoài làm, như hub trong nước.
- **Xe riêng**: khách chọn ở màn 1; báo giá tính đủ số chỗ của xe; gợi ý chia xe đặt đơn vào đúng xe giữ riêng.

Kịch bản đầy đủ cho team test: tài liệu "Bộ test case các luồng chính" (5 kịch bản và bảng mốc giờ).

## Vùng phục vụ của dữ liệu mẫu

| Hub | Tỉnh |
|---|---|
| Trụ sở TP.HCM | TP.HCM, Đồng Nai, Tây Ninh, Đồng Tháp, Vĩnh Long, An Giang, Cần Thơ, Cà Mau |
| Hub Nha Trang | Khánh Hòa, Lâm Đồng, Đắk Lắk, Gia Lai |
| Hub Đà Nẵng | Đà Nẵng, Huế, Quảng Ngãi, Quảng Trị |
| Hub Vinh | Nghệ An, Hà Tĩnh, Thanh Hóa |
| Hub Hà Nội | Hà Nội, Hải Phòng, Bắc Ninh, Hưng Yên, Ninh Bình, Phú Thọ, Quảng Ninh, Thái Nguyên, Tuyên Quang, Lạng Sơn, Cao Bằng, Lào Cai, Lai Châu, Điện Biên, Sơn La |
| Hub Phnom Penh | Các tỉnh Campuchia, trừ Banteay Meanchey và Ratanakiri |
| Hub Viêng Chăn | Vientiane Capital, Vientiane, Bolikhamsai, Xaisomboun, Xiangkhouang, Luang Prabang, Houaphan, Oudomxay, Xaignabouli |
| Hub Savannakhet | Savannakhet, Khammouane, Salavan, Champasak, Xekong, Attapeu |

Tỉnh không có trong bảng (ví dụ Banteay Meanchey, Ratanakiri, Bokeo, Luang Namtha, Phongsaly) chưa được phục vụ: trang đặt đơn nói rõ và liệt kê các tỉnh đang phục vụ. Mỗi đơn có tối đa một chặng trục, nối hub đón với hub giao.

## Máy cũ không khởi động được backend

Bản trước 25/11 có lỗi: máy đã từng lập lượt trục thì hễ dữ liệu mẫu đổi là backend dừng ở `fk_trip_stop_route_stop`. Đã sửa, chỉ cần lấy code mới, **không** phải xóa dữ liệu.

## Demo trên giờ thật (dịch vụ thật, bỏ kiểm tra mốc thời gian)

Dùng khi cần trình diễn với VNPAY sandbox, Cloudinary, mail và Turnstile thật. Không tua giờ; thay vào đó các mốc thời gian chặn thao tác được bỏ qua để đi hết một đơn trong buổi demo. Bản thường, test và CI vẫn giữ đủ kiểm tra. Chỉ khởi động được với cơ sở dữ liệu trên máy.

Bật bằng Docker (key lấy từ `.env`: `VNPAY_HASH_SECRET`, `CLOUDINARY_*`, `MAIL_*`, `TURNSTILE_SECRET_KEY`, `VITE_TURNSTILE_SITE_KEY`):

```powershell
docker compose -f docker-compose.yml -f docker-compose.demo-live.yml up -d --build
```

Chạy tay: trong `backend/` đặt `$env:SPRING_PROFILES_ACTIVE="local,demo-live"` (profile `local` nạp key trong `application-local.properties`) rồi `.\mvnw.cmd spring-boot:run`.

| Bỏ qua | Bình thường |
|---|---|
| Giờ chốt nhận đơn (chuyến nhận đơn tới giờ xuất phát; chuyến đã đóng được mở lại sau 1 phút) | 72 giờ trước giờ chạy |
| Cửa sổ quyết định chạy chuyến | 48 giờ cuối |
| Thời gian làm giấy theo quy định nước | 5 / 14+7 / 30+7 ngày |
| Đón trước ngày chạy | Ít nhất 1 ngày |
| Hạn ngựa có mặt ở hub | 3 giờ trước giờ chạy |
| Khung giờ đón, giao | 06:00–18:00 |
| Xuất phát sớm phải ghi lý do | Sớm hơn 30 phút |
| Nghỉ ở trạm, chờ người nhận, chờ ở điểm đón | 30 phút, 120 phút, 30 phút |

Giữ nguyên: sức chứa, giấy tờ phải được duyệt và còn hạn, tiền, hạn báo giá 24 giờ, hạn trả số dư, phân quyền. Trang quy định cho khách vẫn hiện số gốc.

Lưu ý: tài khoản mẫu `@racehorse.test` không nhận mail thật; đăng ký một khách bằng email thật. Thanh toán bằng thẻ test của VNPAY sandbox.
