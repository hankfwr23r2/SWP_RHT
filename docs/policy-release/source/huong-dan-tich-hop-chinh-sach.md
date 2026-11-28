# Bàn giao chính sách cho đội phát triển

**Tài liệu nội bộ · RHT · Phiên bản 1.0 · 17/11/2026**

## 1. Đầu vào và phạm vi bàn giao

Đầu vào nghiệp vụ chính là `docs/BAO_CAO_CHINH_SACH_CHO_BA_2026-11-17.md`, đối chiếu mã nguồn commit `bab27cb5`. Bộ nội dung mới thay bản thảo QA 0.1; kết quả QA nằm riêng trong `qa/QA_RECONFIRM.md`. Không đưa mô tả test, lỗi, giả lập hay quyết định nội bộ vào văn bản khách đọc.

Bàn giao ba PDF khách hàng theo đúng khung ba tab hiện có và một PDF hướng dẫn nội bộ. Từ 18/11/2026 ba PDF khách hàng đã được đưa vào `frontend/public/policies` và `POLICY_DOCS` đã trỏ sang `.pdf` (PR #318); các tệp SVG mẫu đã gỡ.

## 2. Ánh xạ ba tài liệu vào khung hiện có

| Khóa POLICY_DOCS | Tệp đưa vào frontend/public/policies | Đường dẫn khách mở |
|---|---|---|
| transport | dieu-khoan-van-chuyen.pdf | /terms?doc=transport |
| liability | dieu-khoan-trach-nhiem-han-che.pdf | /terms?doc=liability |
| incident_cost | chinh-sach-chi-phi-su-co.pdf | /terms?doc=incident_cost |

Đã thực hiện 18/11/2026: ba tệp tương ứng từ thư mục `pdf/` của gói bàn giao được chép vào public, và phần `file` của ba mục trong `frontend/src/shared/config/business-rules.ts` được đổi từ `.svg` sang `.pdf`; giữ nguyên khóa và tên ba tab. `PolicyPage.tsx` đã nhận biết đuôi PDF và hiển thị iframe, có liên kết mở tab mới. Không cần dựng trang hoặc hệ thống tài liệu mới cho đợt thay nội dung.

Không sao chép `huong-dan-tich-hop-chinh-sach.pdf`, tài liệu QA, thư mục source hoặc ZIP nội bộ vào thư mục public. PDF hướng dẫn không phải điều khoản cho khách chấp thuận.

## 3. Quy tắc nội dung đã quyết định cho bản 1.0

| Nội dung | Quy tắc dùng trong bản khách đọc |
|---|---|
| Cọc và số dư | 30% theo báo giá; số dư 70% sau khi nhận đủ ngựa/giấy tờ/biên bản, trước khởi hành; tham số và hạn cụ thể theo báo giá đã xác nhận |
| Báo giá | Thông thường 24 giờ, có thể ngắn hơn; khoảng chờ callback không gia hạn quyền trả tiền |
| Rút/hủy trước cọc | Miễn phí; trước báo giá dùng rút yêu cầu, sau báo giá dùng từ chối |
| Khách hủy sau cọc | Mất phần cọc vận chuyển; phần bảo hiểm thu hộ tách riêng |
| Sau khi nhận ngựa/trả đủ | Không tự hủy online; nhà xe tiếp nhận yêu cầu để xử lý phù hợp |
| Công ty hủy do lỗi công ty | Sau cọc, trước rời điểm đón: hoàn 100% số đã thu, gồm bảo hiểm; chuyển khoản bên ngoài hệ thống |
| Không trả 70% | Không khởi hành; quản lý xử lý và nêu nguyên nhân; không công bố đồng hồ tự hủy hoặc hạn cứng chưa có |
| Thu hộ bảo hiểm khi khách hủy | Hoàn phần thu chưa chuyển cộng phần đối tác hoàn, giới hạn trong số thu của khách; không hứa hoàn toàn bộ mọi trường hợp |
| Sự cố/chờ bàn giao | Công ty chịu chi phí; chờ hai giờ ở điểm giao không phải hạn trả số dư |
| Ngoài quy tắc hiện có | Hủy do khách sau trả đủ, dừng giữa hành trình, bất khả kháng: tiếp nhận và xác định riêng; không tự đặt tỷ lệ hoàn |
| Thời hạn hoàn, bồi thường | Không thêm SLA 5 ngày hoặc mức trần chưa được duyệt; bản ghi REFUND không đồng nghĩa khách đã nhận tiền |

Bản 1.0 không hứa quyền đổi lịch thay thế tự động khi công ty hủy; không tự thêm VAT 10%; không ủy quyền an tử/bán ngựa hoặc miễn trách nhiệm tuyệt đối khi khách không mua bảo hiểm.

## 4. Điểm nối tới luồng khách hàng

Giữ liên kết `transport` tại bước khách chấp thuận báo giá/đặt cọc; `liability` tại lựa chọn không mua bảo hiểm; `incident_cost` tại thông tin sự cố và các vị trí đang sử dụng. Kiểm tra các `PolicyLink` cùng trỏ về đúng phiên bản PDF, không còn mở SVG mẫu.

Thông tin bảo hiểm hiển thị bên trên tài liệu tại trang điều khoản phải khớp ba lựa chọn theo từng ngựa. Không dùng nội dung dịch cũ nói khách trả quyết toán sự cố. Khách không phải trả thêm để đóng đơn hoặc đánh giá.

Tệp này chỉ cung cấp bản tiếng Việt. Nếu đổi ngôn ngữ giao diện, giữ nhãn rõ tài liệu tiếng Việt; không coi văn bản PDF đã tự được dịch theo giao diện tiếng Anh.

## 5. Bằng chứng chấp thuận: phần việc riêng với thay PDF

Thay PDF chỉ giải quyết nội dung được đọc. Việc lưu chấp thuận (L8) đã có từ PR #316: bảng `terms_acceptance` ghi mã văn bản, phiên bản lấy từ `TermsDocuments`, thời điểm máy chủ, người dùng, đơn và ngựa liên quan. Mỗi lần đổi nội dung điều khoản đã công bố phải đổi phiên bản ở `TermsDocuments`, để bản ghi cũ vẫn trỏ đúng văn bản khách đã đồng ý.

Đợt triển khai lưu chấp thuận cần bao gồm: tài khoản thực hiện; mã/phiên bản và bản nội dung văn bản; thời điểm server; hành động chấp thuận; mã đơn, ngựa hoặc snapshot báo giá liên quan. Liên kết bản đã nhận phải đọc lại được và không bị ghi đè khi ban hành phiên bản mới.

Hiện hệ thống lưu chấp thuận ở hai điểm: đăng ký tài khoản (Điều khoản vận chuyển) và đặt đơn có ngựa không mua bảo hiểm (Điều khoản Trách nhiệm Hạn chế, ghi kèm đơn và ngựa). Backend bắt buộc cả hai, kể cả khi gọi API trực tiếp. **Chưa lưu:** chấp thuận báo giá khi đặt cọc; ô tick trên giao diện đặt cọc không được gửi lên và backend không đòi. Không tick sẵn, không gộp tiếp thị vào điều kiện đặt chuyến.

## 6. Những thông tin phải hoàn thiện trước công bố vận hành thật

Tên pháp nhân, địa chỉ và kênh tiếp nhận hỗ trợ/khiếu nại phải được xác nhận. Không lấy hotline `1900 6868`, địa chỉ trong cấu hình hoặc `billing@racehorsetransport.vn` làm thông tin thật chỉ vì chúng đang tồn tại trong code.

Các dòng giá do trình duyệt tự tính từ tuyến và dịch vụ (quản lý chỉ thêm khoản điều chỉnh) và báo giá chưa có dòng VAT riêng. Bộ PDF không tự kết luận giá đã gồm VAT hoặc mức thuế; cần chủ sản phẩm chốt cách thể hiện thuế/hóa đơn và đồng bộ báo giá trước khi nhận thanh toán thật.

Mục dữ liệu trong Điều khoản vận chuyển chỉ mô tả thông tin phục vụ chuyến; không thay thế một thông báo quyền riêng tư đầy đủ. Cần chốt đơn vị kiểm soát dữ liệu, bên nhận/nơi xử lý, thời hạn lưu, kênh thực hiện quyền và thông báo cụ thể tại chỗ thu thập. Không biến checkbox vận chuyển thành sự cho phép mọi mục đích xử lý dữ liệu.

Chưa có hạn trả 70%, hạn hoàn tiền hay hạn khiếu nại được duyệt. Không thêm các con số vào UI/PDF mà chưa cập nhật quy trình vận hành. Không tự suy ra quyền của khách từ thiếu chức năng hoặc trạng thái `COMPLETED` của khoản hoàn.

Các đầu mục pháp lý cần được rà soát theo phạm vi vận hành thật; bộ bàn giao này là nội dung nghiệp vụ cho đồ án, không chứng nhận tuân thủ. Nguồn chính thức để rà soát gồm Luật Bảo vệ quyền lợi người tiêu dùng và Luật Bảo vệ dữ liệu cá nhân, cùng quy định chuyên ngành/tuyến cụ thể khi triển khai.

## 7. Nghiệm thu tích hợp

1. Ba URL `/terms?doc=...` mở đúng tài liệu, HTTP 200, nội dung PDF đúng tên và bản 1.0; liên kết mở tab mới hoạt động.
2. Mở cả ba PDF trên desktop và điện thoại; tiếng Việt đủ dấu, phóng to/đọc/tải được, không cắt chữ. Nếu trình duyệt không xem iframe được, liên kết mở trực tiếp vẫn sử dụng được.
3. Checkbox/liên kết tại bước bảo hiểm và đặt cọc trỏ đúng tài liệu. Không coi thử nghiệm liên kết là thử nghiệm lưu consent.
4. Rà soát các màn thanh toán, hủy chuyến, thông báo hoàn tiền và sự cố: cọc vận chuyển/bảo hiểm thu hộ tách rõ; hoàn ngoài hệ thống không bị hiển thị như tiền đã ghi có tự động.
5. Nếu chỉ thay ba tài liệu và mapping, chạy lint, TypeScript, unit test, build và kiểm tra ba trang. Nếu sửa nghiệp vụ/API, chạy đủ quality gate của repository và test nhánh thay đổi; giữ quy tắc xe và kíp xe về hub gốc mới được nhận chuyến mới (`AGENTS.md` mục 2).
6. Trước công bố phiên bản mới, lưu nguyên PDF/bản nội dung cũ gắn với đơn đã chấp thuận. Không ghi đè lịch sử bằng cách thay tệp cùng URL mà không lưu phiên bản tương ứng.

## 8. Tái tạo và quản lý bản phát hành

Các tệp Markdown trong `source/` là bản biên tập. Chạy `python3 build_pdfs.py` tại thư mục bàn giao, với Python có `weasyprint` và `markdown`, để tái tạo PDF. Font DejaVu Sans được nhúng nhằm giữ dấu tiếng Việt.

`manifest.json` ghi tên, mã tài liệu, phiên bản, ngày biên soạn, hash SHA-256 và số trang. Kiểm tra hash khi bàn giao. ZIP chỉ là gói giao dev, không phải một tệp tài liệu công khai.

Khi thay nội dung, cập nhật phiên bản, ngày, PDF, manifest và kiểm tra trình bày. Ngày biên soạn 17/11/2026 không tự là ngày hiệu lực pháp lý hoặc bằng chứng khách đã chấp thuận.
