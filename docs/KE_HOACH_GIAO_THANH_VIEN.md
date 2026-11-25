# Kế hoạch giao việc cho thành viên

Ngày 25/11/2026. Các gói dưới đây lấy từ [PHUONG_AN_6_LUONG.md](PHUONG_AN_6_LUONG.md), **nghiệp vụ đã được người dùng duyệt**. 30 điểm chưa có phương án nằm ở [GIAO_VIEC_BAN_B.md](GIAO_VIEC_BAN_B.md), không làm ở đây.

## 1. Ai làm gì

| Gói | Nội dung | Bắt đầu | Cỡ | Người làm |
|---|---|---|---|---|
| **G1** | Khiếu nại và nghiệm thu của khách | Ngay | Lớn | HoangPhuc-IT |
| **G2** | Thông báo còn thiếu | Ngay | Vừa | HoangPhuc-IT |
| **G3** | Xử lý chuyến trễ | Ngay | Lớn | HoangPhuc-IT |
| **G4** | Nghiệm thu bằng tay phần đã gộp (không viết code) | Ngay | Vừa | HoangPhuc-IT |
| **G5** | Phần sửa dở: chỉ số sức khỏe lúc nhận, tệp giao nhận nhiều đơn | Sau khi 1b gộp | Nhỏ | HoangPhuc-IT |
| *Giữ lại* | 1b đón hụt và chưa trả số dư; đợt 2 tiền và trách nhiệm sự cố; nhiều nơi giao cùng tuyến | Đang làm | | Luồng A |

Mỗi gói một người. G1, G2, G3 làm song song được.

## 2. Quy định chung

1. Đọc [AGENTS.md](../AGENTS.md), rồi mục được dẫn ở gói của mình. Nghiệp vụ đã duyệt, nhưng **cách làm và danh sách file** vẫn trình trưởng nhóm trước khi sửa (mục 1.1).
2. Nhánh từ `redesign/system-v2` mới nhất: `feat/g1-complaint`, `feat/g2-notice`, `feat/g3-delay`, `feat/g5-handover-rest`. Không làm trên `main`. Commit theo Conventional Commits.
3. **Migration:** G1 dùng `V202611270900` đến `0959`, G3 dùng `V202611271000` đến `1059`, G5 dùng `V202611271100` đến `1159`. Không sửa migration cũ. G2 không cần migration.
4. Đừng sửa file ở cột "Đừng đụng" của gói. Cần sửa thì hỏi luồng A.
5. Mở PR vào `redesign/system-v2`, CI xanh cả ba. **Không tự gộp.** Luồng A đọc PR và file nghiệm thu rồi gộp.
6. Mỗi gói nộp **một file nghiệm thu** theo [mẫu](nghiem-thu/MAU_NGHIEM_THU.md), nằm trong chính PR đó. Thiếu file thì chưa coi là xong.
7. Gặp chỗ phương án không nói: ghi vào phần "Câu hỏi" của file nghiệm thu và hỏi trưởng nhóm. Không tự chọn quy tắc tiền.
8. Dữ liệu thử là giả. Không đưa mật khẩu, khóa, thông tin thật vào file hay ảnh chụp.

## 3. Các gói

### G1. Khiếu nại và nghiệm thu của khách

- **Điểm:** 6.G18, 6.G24. **Phương án:** mục 4, dòng "Chưa có khiếu nại" và "Không có bước nghiệm thu"; mục 7 dòng 4.

**Quy tắc đã duyệt**

1. Đơn Đã giao: khách bấm **Xác nhận đã nhận ngựa ổn** hoặc **Khiếu nại**. Đã xác nhận thì không rút lại, không khiếu nại trên app nữa; khách vẫn gọi công ty được, quản lý nhập thay trong cả 14 ngày.
2. Khiếu nại trên app trong **7 ngày** (cấu hình) kể từ lúc **đơn sang Đã giao** (con cuối đã giao, không đếm từng con), có nội dung và **ít nhất một ảnh**, chọn con ngựa hoặc cả đơn. Khách gửi trên app một lần; mỗi đơn chỉ một khiếu nại đang mở.
3. Ngày 8 đến 14: khách gọi công ty, **quản lý nhập thay**, cũng phải có ảnh. Sau ngày 14 không nhận trên hệ thống.
4. Đơn có khiếu nại **chưa giải quyết** thì quản lý và hệ thống đều **không đóng được**.
5. Trong 7 ngày đầu, đơn chỉ đóng khi khách đã xác nhận ổn và do **quản lý** đóng; hệ thống không tự đóng sớm. Hết 7 ngày không ai khiếu nại thì đóng như cũ. G1 **giữ nguyên** điều kiện tiền, chi phí, hoàn tiền; đóng đơn khi còn nợ (mục 13.3) do luồng A làm sau.
6. Quản lý xử lý: trả lời, kết luận (chấp nhận hoặc không, lý do bắt buộc). Kết luận xong chuyển thẳng `CLOSED`, không dùng `RESOLVED`. Khách thấy kết luận. **Không làm tiền**: hệ thống chưa có hoàn tiền sau khi giao, không ghi hay hứa số tiền.
7. Đơn đã Hoàn thành mà có khiếu nại ngày 8 đến 14: đơn giữ Hoàn thành, khiếu nại vẫn theo dõi riêng. Không thêm trạng thái đơn, không mở lại đơn.
8. "Khiếu nại khoản thu" ở mục 13.5 là việc khác, **không thuộc G1**.

**Gợi ý kỹ thuật**

- Không thêm bảng: dùng `incident` gắn đơn, thêm loại `COMPLAINT`. Lưu ý `incident.reported_by` hiện bắt buộc là nhân viên và nguyên nhân bắt buộc có, nên cần migration nới ràng buộc **chỉ cho loại này** và thêm cột tài khoản người gửi.
- Khiếu nại không đi qua phương án sự cố và không cần xác nhận ngựa đủ sức.
- Quyết toán đã chặn đóng khi còn sự cố mở (`SettlementServiceImpl`), tận dụng. Điều kiện xác nhận ổn hoặc đủ 7 ngày viết thành **hàm riêng trong policy** để luồng A nối vào mục 13.3.
- **Lọc khiếu nại khỏi danh sách sự cố của điều phối** ở truy vấn. Chỗ chặn lập phương án, xác nhận đủ sức, ghi chi phí cho khiếu nại: G1 **liệt kê** trong file nghiệm thu, luồng A thêm sau đợt 2.
- Migration gộp **sau** 1b, đợt 2 và nhiều nơi giao; cứ làm và mở PR.

**File chính:** `SettlementServiceImpl`, service khiếu nại mới, `incident` (entity, migration), màn chi tiết đơn của khách, màn đơn của quản lý.
**Đừng đụng:** `IncidentServiceImpl`, `IncidentActionServiceImpl`, `CostItemServiceImpl` (luồng A làm đợt 2); `Payment*`.

**Đạt khi**

| # | Kiểm | Kết quả mong đợi |
|---|---|---|
| G1-1 | Khách xác nhận ổn ở ngày 1, quản lý đóng đơn | Đóng được |
| G1-2 | Chưa ai xác nhận, quản lý đóng ở ngày 3 | Bị chặn, nói lý do |
| G1-3 | Khiếu nại không có ảnh | Bị chặn |
| G1-4 | Khiếu nại ở ngày 7 đúng giờ cuối, rồi quá một phút | Nhận, rồi từ chối |
| G1-5 | Có khiếu nại mở, tới lượt tự đóng 00:30 | Không đóng |
| G1-6 | Quản lý kết luận, khách xem | Thấy kết luận, đơn đóng được |
| G1-7 | Khách khác mở khiếu nại của đơn không phải của mình | Không thấy |
| G1-8 | Quản lý nhập thay ngày 10; ngày 15 | Nhận; từ chối |
| G1-9 | Bấm gửi hai lần liền | Chỉ một khiếu nại |
| G1-10 | Đã xác nhận ổn, khách gửi khiếu nại trên app; quản lý nhập thay ngày 5 khi đơn chưa đóng | Từ chối; nhận và chặn đóng |
| G1-11 | Đơn Hoàn thành, quản lý nhập ngày 10 | Nhận, đơn vẫn Hoàn thành |
| G1-12 | Điều phối mở danh sách sự cố | Không thấy khiếu nại |

### G2. Thông báo còn thiếu

- **Điểm:** 3.G5, 4.F4, 4.F9, 5.G7, 5.G8, 5.G19, 6.G16 (phần hoàn tiền), 6.G17. **Phương án:** mục 5.

| Khi | Báo ai |
|---|---|
| Tổ lái báo sự cố | Mọi điều phối viên; khách, nếu là con của khách |
| Lập phương án | Quản lý |
| Duyệt, từ chối phương án | Điều phối và tổ lái của lượt; khách, khi được duyệt |
| Ghi sức khỏe **Ốm** hoặc **Nguy cấp** | Điều phối ngay. Màn gợi ý mở sự cố cho con đó, **không tự mở** |
| Hủy lượt, gỡ người khỏi lượt | Người trong tổ lái bị ảnh hưởng |
| Xe trục xuất phát; ngựa tới hub, tới hub đích | Khách có ngựa trên xe, mỗi đơn **một** thông báo |
| Đơn sang Đã giao | Quản lý |
| Quản lý ghi đã hoàn tiền | Khách |

**Quy tắc:** mỗi đơn một thông báo cho mỗi mốc, gọi lặp không báo hai lần; sự cố cả lượt chỉ báo khách có ngựa **đang trên xe**; không lộ tên khách hay ngựa của đơn khác; nội dung không chứa chi phí chưa duyệt; bấm thông báo mở đúng trang. Báo lỗi không làm hỏng thao tác chính.

**File chính:** `IncidentServiceImpl` (chỉ thêm lời gọi báo), `HealthLogServiceImpl`, `DispatchServiceImpl`, `CrewAssignmentServiceImpl`, `BookingStatusServiceImpl`, `RefundServiceImpl`, `CustomerNotice*`.
**Đừng đụng:** logic tiền và nguyên nhân sự cố; `HandoverOutcomeServiceImpl`, `HandoverServiceImpl`, `HubHandoverServiceImpl`, `TripRunPage`, `HubPage` (1b). Mốc ngựa tới hub cần móc vào `HandoverOutcomeServiceImpl`: **làm sau khi 1b gộp**. 6.G19, 6.G20 do luồng A làm cùng 1b.

**Đạt khi**

| # | Kiểm | Kết quả mong đợi |
|---|---|---|
| G2-1 | Báo sự cố một con | Điều phối và đúng khách của con đó nhận |
| G2-2 | Báo sự cố cả lượt, lượt có ba đơn, một đơn đã xuống xe | Hai khách nhận, khách đã xuống không nhận |
| G2-3 | Lập, từ chối, duyệt phương án | Đúng người ở từng bước |
| G2-4 | Ghi Ốm; ghi Cần theo dõi | Báo điều phối; không báo |
| G2-5 | Hủy lượt có hai người | Cả hai nhận |
| G2-6 | Gọi lặp cùng mốc | Không thêm thông báo |
| G2-7 | Bấm từng loại thông báo | Mở đúng trang, đúng vai trò |
| G2-8 | Xe trục xuất phát, đơn hai con | Khách nhận một thông báo |

### G3. Xử lý chuyến trễ

- **Điểm:** 1.F6, 3.G2, 4.F7, 5.G23. **Phương án:** bảng ở mục 3.F của [NGHIEP_VU_6_LUONG.md](NGHIEP_VU_6_LUONG.md).

**Quy tắc đã duyệt** (T là giờ chạy gốc của chuyến)

| Mốc | Hệ thống | Khách |
|---|---|---|
| T − 24 giờ chưa quyết | Nhắc quản lý, điều phối; trang quản lý tô đỏ | |
| Tới T chưa quyết, hoặc quá T + 30 phút chưa chạy | Ghi **Chậm**, nguyên nhân công ty | Báo ngay |
| Quản lý nhập giờ mới và nguyên nhân | Giữ giờ gốc, lưu giờ mới, dời giờ kế hoạch phía sau | Thấy giờ cũ, giờ mới, lý do |
| Trễ từ 2 giờ | | Đổi chuyến miễn phí, giữ giá |
| Trễ từ 5 giờ | | Hoàn 100% hoặc chờ tiếp |
| T + 5 giờ vẫn không có giờ mới | Tự hủy chuyến, nguyên nhân công ty | Đổi hoặc hoàn 100% |

**Thêm**

- Chuyến **đã xuất phát** thì không vào quy trình này; đi trễ dọc đường chỉ báo điều phối và khách (4.F7).
- Sự cố loại Chậm trễ hiện liên kết tới chuyến của lượt xe (5.G23).
- Mọi số giờ là **cấu hình**. Trễ tính từ **giờ gốc**, dời nhiều lần vẫn tính từ giờ gốc.
- Đổi chuyến do công ty **không** tính lượt đổi miễn phí của khách. Hoàn tiền theo luồng công ty hủy.
- Ngựa công ty **đã nhận**: không hoàn ngay trên app; hiện cho quản lý liên hệ khách và xử lý. Chưa nhận thì khách tự bấm.
- Không thêm trạng thái vào đơn; không đổi 48 giờ và 72 giờ hiện có.

**File chính:** `DepartureServiceImpl`, `DepartureDecisionServiceImpl`, policy mới cho chuyến trễ, `DepartureScheduler`, `departure` (entity, migration), màn chuyến của quản lý, chi tiết đơn của khách.
**Đừng đụng:** `BookingRescheduleServiceImpl`, `ScheduleChangeServiceImpl`, `Payment*`, `HorseStay*`, `StopReadiness*` (1b). Cần đổi chuyến, hoàn tiền thì **gọi hàm có sẵn**; thiếu hàm thì hỏi luồng A.

**Đạt khi**

| # | Kiểm | Kết quả mong đợi |
|---|---|---|
| G3-1 | T − 24 giờ chưa quyết | Nhắc một lần, trang tô đỏ |
| G3-2 | T + 30 phút chưa chạy | Ghi Chậm, khách được báo một lần |
| G3-3 | Dời 1 giờ; dời 2 giờ | Không mở đổi chuyến; mở |
| G3-4 | Dời 3 giờ rồi dời thêm 2 giờ | Tính 5 giờ từ giờ gốc, mở hoàn tiền |
| G3-5 | Khách hoàn 100% | Đúng số đã trả, gồm thu hộ; nhả chỗ, gỡ chặng |
| G3-6 | T + 5 giờ không có giờ mới | Tự hủy một lần dù chạy lặp |
| G3-7 | Chuyến đã xuất phát rồi trễ | Không hủy, không hoàn |
| G3-8 | Đơn có ngựa đã nhận | Không tự hoàn, hiện cho quản lý |
| G3-9 | Hai người cùng dời giờ và tự hủy chạy | Không hỏng dữ liệu |

### G4. Nghiệm thu bằng tay phần đã gộp

Không viết code. Chạy theo [CHAY_THU.md](CHAY_THU.md) trên `redesign/system-v2`, ghi kết quả và ảnh màn hình (dữ liệu giả).

| # | Kịch bản | Gộp ở |
|---|---|---|
| G4-1 | Năm loại đơn chạy trọn: liên tỉnh, trong tỉnh, xuất, nhập, tự chở và tự nhận ở hub | cũ |
| G4-2 | Hủy đơn đã xếp lượt: ngựa rời khỏi lượt, lịch hẹn hub mất, điều phối được báo | 1a |
| G4-3 | Người nhận vắng: giữ ở hub, lập giao lại, chưa trả phí giữ thì không lên xe, trả rồi thì đi | 1a |
| G4-4 | Người nhận tự tới hub khi còn nợ phí giữ: vẫn lấy được ngựa | 1a |
| G4-5 | Khoản thu thêm quá hạn: không đặt đơn mới được; trả rồi đặt được | 1a |
| G4-6 | Sự cố sức khỏe phải chọn con; xác nhận đủ sức sau khi lượt kết thúc; điều phối xác nhận thay bằng kết quả thú y | 1a |
| G4-7 | Chuyến sang Đang chạy, Đã xong; nút Hủy chuyến mất | 1a |
| G4-8 | Nhận và giao thiếu ảnh, tình trạng, biên bản, giấy gốc: bị chặn từng thứ | 3a |
| G4-9 | Mã nhận ngựa đúng; sai quá số lần; khách xác nhận thay mã | 3a |
| G4-10 | Khách xem biên bản, ảnh; khách khác không xem được | 3a |
| G4-11 | Nhắc giấy đúng bên; giấy hết hạn trước ngày chạy; tệp giả đuôi PDF | 4a |
| G4-12 | Mọi màn trên ở bề rộng 360px | |

Lỗi tìm thấy: ghi vào file nghiệm thu, **không tự sửa**. Lỗi chặn đơn hoặc sai tiền thì báo luồng A ngay.

### G5. Phần sửa dở

Làm **sau khi 1b gộp**.

- **4.F5:** lúc nhận ngựa thêm nhiệt độ, nhịp tim, nhịp thở (không bắt buộc), ghi vào nhật ký sức khỏe. Giới hạn như 4.15.
- **6.G13:** tệp của lần giao nhận gồm nhiều đơn: khách chỉ xem tệp **gắn con ngựa của mình**; biên bản chung thì **không** hiện.

| # | Kiểm | Kết quả mong đợi |
|---|---|---|
| G5-1 | Nhập chỉ số ngoài giới hạn | Bị chặn dưới đúng ô |
| G5-2 | Nhận ngựa có chỉ số | Nhật ký có một dòng, đúng người và giờ |
| G5-3 | Lần giao nhận hai đơn, khách A xem | Thấy ảnh con mình, không thấy của khách B và biên bản chung |

## 4. Nộp và duyệt

1. Nhận gói: điền tên ở mục 1, tạo nhánh.
2. Trình cách làm và danh sách file cho trưởng nhóm.
3. Làm xong: chép [mẫu](nghiem-thu/MAU_NGHIEM_THU.md) thành `docs/nghiem-thu/G<số>.md`, điền đủ, đưa vào PR.
4. Luồng A chạy lại Quality Gate, thử ít nhất một phần ba số kiểm, trả lời **Đạt** hoặc **Cần sửa** trong file.
5. Đạt thì luồng A gộp và ghi Đã sửa vào [NGHIEP_VU_6_LUONG.md](NGHIEP_VU_6_LUONG.md).
