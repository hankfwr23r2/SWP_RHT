# Bộ chính sách RHT — bàn giao BA cho dev

Phiên bản nội dung 1.0, biên soạn 17/11/2026 theo `docs/BAO_CAO_CHINH_SACH_CHO_BA_2026-11-17.md` và code `bab27cb5`.

Sửa 18/11/2026 để khớp code `a089ec0e` (lấy code làm chuẩn, chưa công bố nên giữ nhãn phiên bản 1.0): hủy do khách sau khi trả 70% mất cả cọc và 70% (không gồm bảo hiểm thu hộ); thống nhất tên “phí phạt giữ chỗ và chuẩn bị phương tiện”; đơn quốc tế khách chịu phí giấy phép nhập khẩu và kiểm dịch theo dòng “Thủ tục kiểm dịch và hải quan” trong báo giá; trả ngựa khi hủy có biên bản và đơn hiển thị “Đã hủy, chờ trả ngựa” rồi mới thành “Đã hủy” sau khi trả ngựa xong; báo giá là phiếu báo giá, không phải hóa đơn; câu hứa liên hệ khách ghi rõ qua thông tin liên hệ trong đơn. Nếu bản này đã công bố trước khi sửa thì phải đổi phiên bản ở `TermsDocuments`.

## Tệp bàn giao

- [Điều khoản vận chuyển](pdf/dieu-khoan-van-chuyen.pdf).
- [Điều khoản Trách nhiệm Hạn chế](pdf/dieu-khoan-trach-nhiem-han-che.pdf).
- [Chính sách chi phí khi có sự cố](pdf/chinh-sach-chi-phi-su-co.pdf).
- [Hướng dẫn tích hợp dành riêng cho dev](pdf/huong-dan-tich-hop-chinh-sach.pdf).
- [Manifest phiên bản và hash](manifest.json).

Ba PDF đầu tương ứng ba tab `transport`, `liability`, `incident_cost` hiện có. Hướng dẫn dev nêu tệp cần chép, mapping `.pdf`, vị trí liên kết, kiểm thử và phần lưu chấp thuận chưa có. Chưa thay tệp public, chưa sửa mã nguồn hoặc deploy.

Nội dung khách đọc không chứa phân tích QA, chữ placeholder hoặc khẳng định chức năng chưa có. Chưa tự thêm thời hạn trả 70%, SLA hoàn tiền 5 ngày, mức trần bồi thường, VAT hay quyền đổi lịch tự động khi công ty hủy.

Bộ này phục vụ đồ án/demo. Thông tin pháp nhân/liên hệ thật, chính sách dữ liệu chi tiết và cơ chế lưu chấp thuận phải được hoàn thiện trước công bố vận hành thật; xem hướng dẫn dev. Ngày biên soạn không tự là ngày hiệu lực.

## Nguồn và tái tạo

Bản Markdown biên tập trong `source/`; chạy `python3 build_pdfs.py` với `weasyprint` và `markdown`. Font DejaVu Sans được nhúng, PDF có số trang và phiên bản. Manifest ghi hash từng tệp.

Các nguồn chính thức để rà soát pháp lý trước vận hành thật: [Luật Bảo vệ quyền lợi người tiêu dùng](https://vanban.chinhphu.vn/?classid=1&docid=208363&pageid=27160&typegroupid=3), [Luật Bảo vệ dữ liệu cá nhân](https://vanban.chinhphu.vn/?classid=1&docid=214590&pageid=27160&typegroupid=). Việc viện dẫn nguồn không thay thế rà soát pháp lý toàn bộ hợp đồng/tuyến vận chuyển.
