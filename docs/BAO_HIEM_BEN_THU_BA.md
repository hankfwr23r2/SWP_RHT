# Bảo hiểm bên thứ ba theo đơn

## Menu Chuyên viên

Ba mục ngang cấp: Duyệt hồ sơ ngựa, Giấy tờ chuyến đi, Bảo hiểm. Mục Bảo hiểm có danh sách riêng (API GET /specialist/bookings/insurance), chỉ trả đơn được phân công; quản lý/admin được phép đọc theo quyền hiện có. Các trang duyệt hồ sơ và làm giấy tờ chỉ còn tiến độ và liên kết sang bảo hiểm.

Danh sách phân loại cần báo giá/kiểm tra, chờ duyệt/đặt cọc, cần hợp đồng và đã xử lý/đóng. Chọn đơn rồi chọn từng ngựa. Tiến độ y tế không phụ thuộc tiến độ bảo hiểm; quản lý kiểm tra cả hai trước khi phát hành báo giá chung.

## Trước báo giá

Khách chọn bảo hiểm cá nhân, hỗ trợ hoặc không bảo hiểm cho từng ngựa. Đơn cũ chưa khai báo phải xác nhận rõ với khách.

- Hỗ trợ: nhập nhà cung cấp, phí thu hộ, hạn báo giá bên thứ ba nếu có, ghi chú và chứng từ báo giá. Không nhập số hợp đồng hoặc ngày hiệu lực hợp đồng ở giai đoạn này.
- Cá nhân: kiểm tra nhà cung cấp, số hợp đồng/giấy chứng nhận, thời hạn bao phủ ngày khởi hành và chứng từ.
- Không bảo hiểm: lưu lựa chọn, không yêu cầu hợp đồng.

Lưu nháp chưa xác nhận hoàn tất; có thể lưu hợp đồng cá nhân chưa đủ thông tin trong giai đoạn thẩm định. Xác nhận báo giá/kiểm tra yêu cầu dữ liệu hợp lệ. Khi chỉnh sửa, yêu cầu xác nhận lại.

Phí nhập có dấu phân cách hàng nghìn, gửi API dưới dạng số nguyên VNĐ. Chứng từ JPG/PNG/PDF tối đa 5 MB mỗi tệp và 10 tệp mỗi nhóm; hỗ trợ xem ảnh/PDF, tải xuống, gỡ và thử lại khi tải lỗi. Tên tệp lưu cùng URL để hiển thị lại sau reload. Gỡ khỏi form có hiệu lực với hồ sơ khi lưu, không xóa vật lý tài liệu lịch sử khỏi kho tệp.

## Báo giá và thanh toán

Backend chặn phát hành nếu bảo hiểm chưa đủ, chưa xác nhận hoặc báo giá bên thứ ba đã hết hạn. Phí được cộng đúng một lần như khoản thu hộ, không tính lại theo giống ngựa hay danh mục bảo hiểm cũ. Hạn đặt cọc là thời điểm sớm hơn giữa hạn báo giá chung theo cấu hình và cuối ngày hết hạn của báo giá bảo hiểm (múi giờ Việt Nam).

Phát hành lưu insuranceSnapshot gồm thông tin và chứng từ báo giá. Khi khách đang cân nhắc báo giá, chuyên viên chỉ được xem. Cọc/số dư được tính từ tổng báo giá đã chốt; báo cáo và đối soát tiếp tục tách phần thu hộ.

## Sau đặt cọc

Phí, đơn vị, hạn, ghi chú và documents của báo giá được giữ nguyên ở backend. Chuyên viên bổ sung policyNumber, validFrom, validUntil và policyDocuments; xác nhận thu xếp sau khi đủ hợp đồng và thời hạn bao phủ ngày khởi hành. Lưu nháp hợp đồng không có nghĩa bảo hiểm đã có hiệu lực. Điều kiện khởi hành vẫn kiểm tra hợp đồng và thời hạn.

Khách có tab Bảo hiểm trong đơn. Báo giá đã phát hành lấy từ bản chụp; hợp đồng hỗ trợ đang lưu nháp không hiển thị với khách cho đến khi chuyên viên xác nhận. Phần Thanh toán hiển thị bản chụp báo giá, không hiển thị hợp đồng bổ sung như tài liệu thuộc báo giá gốc.

## Dữ liệu cũ

Thông tin bảo hiểm đang xử lý lưu tại `booking_horse_insurance`, gắn duy nhất với cặp đơn/ngựa trong `booking_horse`. Khách sở hữu hồ sơ được xác định qua đơn và tài khoản khách hàng, tránh lưu lặp thông tin chủ sở hữu. Các mốc ngày dùng kiểu `DATE`, phí dùng kiểu số thập phân chính xác; các trường xác nhận và thông tin chưa hoàn tất được phép để trống khi lưu nháp.

Chứng từ lưu tại `insurance_document`: nhóm `DECLARATION` giữ nguyên các URL trong `documents` (hồ sơ cá nhân hoặc báo giá), nhóm `POLICY` giữ các URL hợp đồng bổ sung. Tên tệp đi cùng từng URL; các tên cũ chưa còn trong danh sách URL vẫn được giữ bằng dòng `NAME_ONLY`. Các cờ hiện diện giữ khác biệt giữa danh sách/map chưa khai báo và danh sách/map rỗng. Cập nhật giữ ID của chứng từ còn được sử dụng; gỡ một dòng không xóa vật lý tệp trên kho lưu trữ.

API tiếp tục trả `horses[].insurance` theo DTO hiện có. `insuranceSnapshot` của báo giá đã phát hành là lịch sử bất biến, được giữ riêng với bảo hiểm đang xử lý; thay đổi hoặc bổ sung hợp đồng không làm đổi bản chụp báo giá. Các trường người duyệt và thời điểm duyệt không được suy diễn từ cờ xác nhận của dữ liệu cũ.

Migration phải bảo toàn khai báo và chứng từ cũ, đối chiếu ngựa với quan hệ trong đơn trước khi chuyển dữ liệu. Đơn chưa có khai báo bảo hiểm rõ ràng vẫn cần xác nhận với khách, không tự chuyển thành "không bảo hiểm". `documents` cũ tiếp tục hiển thị để đọc lịch sử, không tự phân loại lại thành hợp đồng. Danh mục và tỷ lệ bảo hiểm cũ không dùng tính báo giá mới.

## Kiểm tra

Vitest kiểm tra nhập tiền và phân loại tiến độ. Playwright kiểm tra form báo giá/hợp đồng, upload thử lại, xem PDF, dữ liệu chưa lưu và bố cục điện thoại với API giả lập. Backend kiểm tra giới hạn phân công, draft/confirm, khóa báo giá, hạn báo giá, snapshot và thu tiền. Kiểm tra tích hợp đầy đủ cần Docker/PostgreSQL; thao tác upload thực cần cấu hình Cloudinary.
