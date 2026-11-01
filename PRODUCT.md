# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Sản phẩm là đồ án môn học SWP; khách hàng và nhà xe là giả định, không có người dùng thật. Các vai trò trong hệ thống:

- **Khách hàng (chủ ngựa, trại ngựa)**: đặt chuyến vận chuyển ngựa đua, quản lý hồ sơ ngựa và giấy tờ, xem báo giá, thanh toán cọc 30% và 70% còn lại, theo dõi đơn, thanh toán quyết toán và chấm điểm chuyến. Dùng **máy tính** là chính; đặt chuyến có cân nhắc, đọc kỹ báo giá và điều khoản.
- **Quản lý (Manager)**: tiếp nhận đơn, duyệt và phát hành báo giá, duyệt phương án và chi phí sự cố, xem báo cáo doanh thu, chuyến đi, đơn hủy.
- **Chuyên viên (Specialist)**: thẩm định hồ sơ ngựa và giấy tờ chuyến đi trong từng đơn.
- **Điều phối viên (Coordinator)**: chốt xe, lộ trình, phân kíp xe; giám sát chuyến; lập phương án xử lý sự cố trên bản đồ; quản lý đội xe.
- **Tài xế (Driver)** và **Hộ tống (Escort)**: chạy chuyến, check-in từng mốc, quét chip, bàn giao ngựa, báo SOS, ghi nhật ký sức khỏe, nộp bảng kê chi phí. Dùng **điện thoại** ngoài hiện trường là chính.
- **Quản trị viên (Admin)**: chỉ quản lý hệ thống (biểu giá, tuyến cố định, dịch vụ, quy định, giấy tờ bắt buộc, tài khoản); không tham gia nghiệp vụ đơn.

## Product Purpose

Hệ thống Vận chuyển Ngựa đua (RaceHorse Transport): nền tảng đặt và vận hành chuyến chở ngựa đua bằng đường bộ, nội địa Việt Nam và xuyên biên giới Việt Nam – Lào, Việt Nam – Campuchia qua cửa khẩu đường bộ quốc tế.

Thành công là buổi demo trước giảng viên/hội đồng chạy trôi chảy hết các luồng nghiệp vụ, nghiệp vụ đúng, giao diện trông chuyên nghiệp như một sản phẩm thật.

## Positioning

Mỗi đơn có người thật chịu trách nhiệm từng khâu: chuyên viên xác minh hồ sơ thú y, điều phối viên chọn xe, tài xế, hộ tống và lập lộ trình riêng, quản lý duyệt báo giá trước khi khách đặt cọc, và duyệt phương án khi có sự cố. Mọi xe và kíp xe xuất phát từ một trụ sở duy nhất ở Việt Nam và quay về đó sau chuyến.

## Operating Context

Sáu luồng nghiệp vụ chính:

1. Khách đặt đơn (4 bước: chuyến đi, chọn ngựa, dịch vụ và bảo hiểm, xác nhận), chọn hoặc tạo hồ sơ ngựa, đính kèm giấy tờ.
2. Thẩm định song song: Chuyên viên duyệt hồ sơ, Điều phối viên chốt xe và lộ trình cùng lúc; Quản lý chỉ duyệt giá khi cả hai xong.
3. Báo giá hiệu lực 24 giờ; khách cọc 30% thì có số vận đơn điện tử; hủy ở bước này bị khấu trừ cọc.
4. Vận hành chuyến: phải trả đủ 70% trước giờ xuất phát, nếu không tài xế không bắt đầu được; check-in mốc, bàn giao ngựa; người nhận quá 2 giờ không đến thì kích hoạt quy trình lưu chuồng, chi phí tính vào quyết toán; xe và kíp phải xác nhận về trụ sở mới sẵn sàng nhận chuyến mới.
5. Sự cố (SOS): tài xế/hộ tống báo, điều phối viên lập phương án trên bản đồ, quản lý duyệt phương án và chi phí.
6. Quyết toán: tài xế nộp bảng kê kèm hóa đơn, quản lý đối soát và phát hành quyết toán (hạn 24 giờ), khách thanh toán và chấm điểm để đóng đơn.

Thanh toán qua VNPay; bản đồ dùng Vietmap/MapLibre/Leaflet; ảnh và giấy tờ lưu trên Cloudinary.

## Capabilities and Constraints

- Hai ứng dụng riêng trong `frontend/`: cổng khách hàng (`/`, gồm trang chủ công khai) và backoffice nội bộ (`/backoffice`) có menu theo vai trò. React 19, TypeScript, Vite; backend Spring Boot.
- Toàn bộ giao diện bằng tiếng Việt (`lang="vi"`).
- Backend quyết định trạng thái, tiền, hạn và quyền; frontend chỉ hiển thị những gì backend trả về.
- Giấy tờ ngựa không có bước "chờ duyệt": khách tải lên là đặt đơn được; Chuyên viên thẩm định trong từng đơn.
- Họ tên khi đăng ký là tên người, chỉ gồm chữ cái (không có chữ số); frontend và backend dùng cùng quy tắc.
- Màn của Tài xế và Hộ tống phải dùng tốt trên điện thoại; màn khách hàng và các vai trò văn phòng tối ưu cho máy tính.

## Brand Commitments

- Tên: "Vận chuyển Ngựa đua" (RaceHorse Transport). Logo: `frontend/public/logo.png`.
- Chữ trên giao diện phải là chữ của một trang web bình thường, ví dụ "Thông tin liên hệ", "Điểm đón và điểm giao", "Lịch trình". Không đưa khung phân tích nội bộ (5W, "Ai", "Vận chuyển gì") hay nhãn giải thích cách hệ thống phân loại lên UI.

## Evidence on Hand

- Ảnh: `frontend/public/images/` (checkup, race, stable, trailers, transport).
- Chính sách dạng ảnh: `frontend/public/policies/` (điều khoản vận chuyển, trách nhiệm hạn chế, chi phí sự cố).
- Tài liệu báo giá, hóa đơn và bảo hiểm ngựa: `docs/BAO_GIA_HOA_DON_VA_BAO_HIEM_NGUA.docx`.
- Chưa có số hotline hay email hỗ trợ thật: không thêm dòng liên hệ cho tới khi có.
- Không có khách hàng thật, đánh giá, số liệu vận hành hay đối tác thật. Không bịa testimonial, logo đối tác hay số liệu thành tích.

## Product Principles

1. Nghiệp vụ đúng trước, đẹp sau: giao diện phản ánh đúng trạng thái backend trả về, không tự suy luận quy tắc.
2. Mỗi vai trò thấy đúng việc của mình và bước tiếp theo cần làm.
3. Tiền và hạn phải rõ: mốc 30%/70%, hạn báo giá 24 giờ, hạn quyết toán, khoản khấu trừ luôn hiển thị minh bạch.
4. Trông như sản phẩm thật: chữ tự nhiên, không lộ dấu vết demo hay khung nội bộ.
5. Người ngoài hiện trường dùng điện thoại: thao tác check-in, SOS, bàn giao phải nhanh và chắc chắn.
