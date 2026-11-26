# Kế hoạch làm mô hình bến xe

Người dùng duyệt ngày 26/11/2026. Nguồn: quyết định ở [NGU_CANH_THIET_KE_LAI.md](NGU_CANH_THIET_KE_LAI.md) mục 4b, nghiệp vụ ở [NGHIEP_VU_6_LUONG.md](NGHIEP_VU_6_LUONG.md), quy chế ở [QUY_CHE_DOI_XE.md](QUY_CHE_DOI_XE.md), task M1–M10 ở [KE_HOACH_TRIEN_KHAI.md](KE_HOACH_TRIEN_KHAI.md) mục 11.

## Cách làm

- Tối đa 2 phiên Codex cùng lúc, mỗi phiên một worktree và một nhánh riêng.
- Mỗi việc một PR vào `redesign/system-v2`. Luồng A kiểm code, chạy test liên quan ở máy, CI xanh thì gộp. Bộ đầy đủ và e2e chạy trên CI.
- Gộp sang `main` chỉ khi người dùng đồng ý.

## Phân công

| Mã | Việc | Ai | Phụ thuộc |
|---|---|---|---|
| M1 | Mạng lưới bến xe, dữ liệu mẫu theo quy chế | A | Không |
| M2 | Sức chứa theo xe, tự gắn xe để sẵn, nhãn Còn chỗ / Sắp đầy / Hết chỗ cho khách | A | M1 |
| M3 | Xe riêng | A | M2 |
| M4 | Chia ngựa vào nhiều xe trục theo cụm điểm giao | **A (nhận từ B)** | M2, M3 |
| M7 | Gỡ chặng và bàn giao đại lý | **A (nhận từ B)** | M1 |
| M10 | Bắt buộc trạm nghỉ trên chặng đón, giao dài; chặng tới 8 giờ | **A (nhận từ B)** | M1 |
| M5 | Khám bắt buộc ở hai đầu hub | B | Không |
| M6 | Xe về hub gốc (ảnh, tình trạng xe) | B | Không |
| M8 | Màn Các hub cho quản lý, số cần theo quy chế | B | Không |
| M9 | Ghép lại, test cả luồng, sơ đồ bảng, chạy thử trên giao diện | A, B rà | M1–M10 |

Nếu tới lúc A xong M4 mà B chưa bắt đầu M5, M6, M8 thì A báo người dùng để quyết có làm thay không.

## Thứ tự

| Đợt | Việc | Ghi chú |
|---|---|---|
| 1 | M1 | Đang làm |
| 2 | M2 và M7 + M10, hai phiên song song | Giới hạn chặng 8 giờ chỉ bật khi M10 gộp |
| 3 | M3 | |
| 4 | M4 | |
| 5 | M9 | Ca liên tỉnh, xuất Phnom Penh, nhập từ Viêng Chăn, xe riêng; ảnh hoặc video chạy thử |

## Giá xe riêng (M3, đã duyệt)

Không thêm công thức mới, dùng bảng giá đang có:
- **Cước trục** tính **đủ sức chứa của xe dành riêng × đơn giá khoang tiêu chuẩn**, dù chở ít ngựa hơn. Khoang rộng không có đơn giá riêng cho xe riêng; nếu khoang rộng khiến phải chọn hạng xe lớn hơn, tổng cước đổi theo sức chứa xe đó.
- **Cước đón, giao** tính theo km với hạng xe của xe riêng, như hiện nay.
- Dịch vụ, bảo hiểm, thủ tục giữ nguyên.

## Lưu ý trang đặt đơn của khách (M3, M2)

Có **hai loại dịch vụ**, phải tách rõ trên trang đặt đơn để khách không chọn nhầm và giá tính đúng đơn vị:

| Loại | Gồm | Chọn ở đâu |
|---|---|---|
| **Dịch vụ cho cả đơn (cả chuyến)** | Xe ghép hay **xe riêng**; công ty đón hay tự đưa tới hub; công ty giao hay tự tới hub nhận; dịch vụ thêm tính theo đơn (`billing_unit = PER_BOOKING`) | **Màn 1 (tuyến và chuyến)**: vì xe riêng ảnh hưởng chuyến nào còn chọn được. Dịch vụ thêm tính theo đơn ở một khối riêng "Dịch vụ cho cả đơn" của màn 3 |
| **Dịch vụ cho từng con** | Loại khoang, chế độ ăn, dịch vụ thêm tính theo con, theo ngày, theo chỗ (`PER_HORSE`, `PER_HORSE_DAY`, `PER_STALL`), bảo hiểm | Màn 2 và khối "Dịch vụ cho từng con" của màn 3, như hiện nay |

Hiện trạng cần sửa:
- Màn 3 đang cho chọn **mọi** dịch vụ thêm theo từng con, kể cả dịch vụ tính theo đơn. Phải tách theo `billing_unit`.
- Nút "Áp cho mọi con" chỉ chép dịch vụ từng con.
- Giá tạm tính và màn xem lại (màn 4) phải ghi rõ hai nhóm.
- Khi chọn xe riêng, danh sách chuyến hiện nhãn theo việc **còn xe trống để chạy riêng** (M2, M3).
- Khi đổi giữa xe ghép và xe riêng thì giá tạm tính đổi ngay. Bản nháp giữ lựa chọn khi quay lại màn trước.
- Màn xem lại và chi tiết đơn ghi rõ "Xe riêng" để khách và nhân viên đều thấy.

## Theo dõi

| Mã | Tình trạng | PR |
|---|---|---|
| M1 | Xong | #521 |
| M2 | Xong | #525 |
| M3 | Xong | #527 |
| M4 | Xong | #528 |
| M7 | Xong | #526 |
| M10 | Xong | #526 |
| M5 | Xong (bạn B) | #520 |
| M6 | Xong (bạn B) | #522 |
| M8 | Xong (bạn B) | #523 |
| M9 | Chưa làm | |
