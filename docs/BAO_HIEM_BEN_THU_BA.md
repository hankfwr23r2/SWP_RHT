# Bảo hiểm bên thứ ba và thu hộ

Bảo hiểm là thông tin theo từng ngựa trong đơn vận chuyển. Khách chọn bảo hiểm cá nhân, yêu cầu hỗ trợ hoặc không bảo hiểm. Công ty liên hệ bên thứ ba và thu hộ phí được báo; khoản này được gửi cùng báo giá vận chuyển.

- Bước đặt chuyến lấy khoang và thức ăn từ danh mục dịch vụ; lựa chọn bảo hiểm nằm trong phần riêng.
- Cấu hình tỷ lệ bảo hiểm theo giống được ngừng sử dụng và ẩn khỏi cấu hình đang áp dụng; dữ liệu cũ không bị xóa.
- Danh mục bảo hiểm cũ được giữ để đọc lịch sử, không xuất hiện trong API danh mục công khai và không dùng tính phí đơn mới.
- Chuyên viên nhập đơn vị cung cấp, phí thu hộ và có thể đính kèm chứng từ. Xác nhận kiểm tra báo giá là thao tác thủ công của chuyên viên; hệ thống không tự xác minh với nhà bảo hiểm.
- Bảo hiểm cá nhân cần đơn vị, số hợp đồng, thời hạn bao phủ ngày khởi hành. Không bảo hiểm không cần xác nhận kiểm tra hợp đồng/báo giá.
- Với đơn cũ chưa có lựa chọn, chuyên viên phải xác nhận lại với khách rồi chọn rõ; giao diện không mặc định chọn không bảo hiểm khi lưu.
- Khi sửa thông tin trước phát hành báo giá, giao diện yêu cầu xác nhận kiểm tra lại. Backend vẫn kiểm tra thông tin bắt buộc và quyền người được phân công.
- Báo giá mới chỉ cộng phí bên thứ ba do chuyên viên nhập; không tính theo giống ngựa hoặc giá danh mục cũ. Mức phí được chụp lại khi phát hành báo giá.
- Phí thu hộ nằm trong tổng tiền đơn, cùng các mốc cọc/số dư hiện có. Báo cáo và đối soát xử lý riêng phần thu hộ.
- Sau đặt cọc, chuyên viên bổ sung hợp đồng và xác nhận thu xếp. Phí, đơn vị cung cấp và kết quả kiểm tra đã chốt được backend giữ theo báo giá.
- Báo giá lịch sử đã phát hành giữ nguyên số tiền đã lưu. Màn hình không tự suy ra phí bảo hiểm lịch sử từ giống ngựa hoặc đơn giá danh mục.
