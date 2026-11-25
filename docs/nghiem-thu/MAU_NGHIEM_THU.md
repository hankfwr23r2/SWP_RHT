# Nghiệm thu gói G…: tên gói

Chép file này thành `docs/nghiem-thu/G<số>.md`. Điền mọi mục; không có thì ghi "Không có", không xóa mục. **Chưa chạy thì ghi "Chưa chạy", không ghi Đạt.**

## 1. Thông tin

| | |
|---|---|
| Người làm | |
| Nhánh, PR | |
| Commit được nghiệm thu | |
| Ngày nghiệm thu | |
| Bản nền `redesign/system-v2` | |

## 2. Đã làm gì

| Điểm hở | Trước | Sau |
|---|---|---|
| | | |

## 3. Kết quả kiểm

Lấy đủ mã kiểm từ [KE_HOACH_GIAO_THANH_VIEN.md](../KE_HOACH_GIAO_THANH_VIEN.md). Kết quả: **Đạt**, **Không đạt**, **Chưa chạy**.

| Mã | Các bước đã bấm, vai, dữ liệu | Kết quả thấy được | Kết quả | Ảnh hoặc test |
|---|---|---|---|---|
| | | | | |

## 4. Quality Gate

| Lệnh | Kết quả thật (số ca, số lỗi) |
|---|---|
| `.\mvnw.cmd verify` | |
| `npm run lint` | |
| `npx tsc -b` | |
| `npm test` | |
| `npm run build` | |
| e2e (`npx playwright test`) | |
| CI của PR (link) | |

## 5. Quy tắc bắt buộc

- [ ] Công thức và quy tắc nằm ở `policy/`, không ở màn hình
- [ ] Thao tác tiền, trạng thái: gọi lặp và chạy song song không sai
- [ ] Phân quyền ở máy chủ, thử cả vai không được phép
- [ ] Có đủ tiếng Việt và tiếng Anh
- [ ] Màn có đủ đang tải, trống, lỗi, có dữ liệu; dùng được ở 360px
- [ ] Migration chạy được từ cơ sở dữ liệu trống, đúng dải số
- [ ] `openapi.json` và `shared/api/generated` đã sinh lại nếu API đổi
- [ ] Đặc tả và tài liệu màn hình đã cập nhật
- [ ] Không nới, không xóa test cũ
- [ ] Không có mật khẩu, khóa, thông tin thật trong code, tài liệu, ảnh

## 6. Danh sách file đã sửa

## 7. Test cũ phải đổi và lý do

| Test | Đổi gì | Vì sao đúng luật mới |
|---|---|---|
| | | |

## 8. Tự quyết và câu hỏi

| Việc | Cách chọn | Nguồn hoặc lý do |
|---|---|---|
| | | |

## 9. Còn thiếu, chưa kiểm chứng

## 10. Kết luận của người duyệt

| | |
|---|---|
| Người duyệt | |
| Ngày | |
| Mã kiểm đã thử lại | |
| Kết luận | Đạt / Cần sửa |
| Cần sửa gì | |
