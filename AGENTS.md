# AGENT RULES & BEHAVIORAL CONSTRAINTS

## 1. Nguyên Tắc Bắt Buộc: Xác Nhận Trước Khi Sửa Code (Confirmation Before Edit)

- **TUYỆT ĐỐI KHÔNG TỰ Ý CHỈNH SỬA**: Agent **KHÔNG ĐƯỢC** tự ý gọi các công cụ chỉnh sửa hoặc ghi file (`replace_file_content`, `multi_replace_file_content`, `write_to_file`) trên mã nguồn của dự án khi **CHƯA ĐƯỢC NGƯỜI DÙNG XÁC NHẬN ĐỒNG Ý**.
- **QUY TRÌNH 2 BƯỚC BẮT BUỘC**:
  1. **Bước 1 - Trình bày phương án**:
     - Phân tích rõ nguyên nhân của vấn đề hoặc yêu cầu.
     - Đưa ra đề xuất giải pháp kỹ thuật cụ thể (danh sách file liên quan, cách tiếp cận, ảnh hưởng).
     - Hỏi ý kiến người dùng và dừng lại để chờ phản hồi.
  2. **Bước 2 - Thực thi sau khi có lệnh**:
     - Chỉ khi người dùng phản hồi đồng ý (ví dụ: "đồng ý", "tiến hành đi", "làm đi", "ok", "có"...), Agent mới được phép chỉnh sửa mã nguồn.

---

## 2. Quy Trình Đồng Bộ & Kiểm Thử
- Khi được phép sửa code, phải tuân thủ tài liệu hướng dẫn tại `frontend/docs/AGENT_GUIDE.md`:
  - Kiểm tra tính tương thích giữa Frontend và Backend.
  - Chạy đầy đủ Quality Gate (`mvnw verify`, `npm run lint`, `npx tsc -b`, `npm test`, `npm run build`).
  - Không phá vỡ unit test hiện có và kiến trúc Single HQ.

## 3. Quy ước package và tên file Java

- Các file Java đặt trực tiếp trong `service/` phải có hậu tố `Service.java` và định nghĩa hợp đồng nghiệp vụ.
- Các file Java đặt trực tiếp trong `service/impl/` phải có hậu tố `Impl.java` và triển khai hợp đồng tương ứng.
- Mỗi package chuyên biệt chỉ chứa file đúng trách nhiệm và quy ước đặt tên của package đó. Ví dụ, controller dùng `*Controller.java`, repository dùng `*Repository.java` và mapper dùng `*Mapper.java`.
- Khi thêm hoặc di chuyển lớp, xác định trách nhiệm và kiểm tra service hiện có để tái sử dụng trước khi chọn package và tên file.
