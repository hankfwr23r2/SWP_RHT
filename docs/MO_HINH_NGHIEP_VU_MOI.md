# Mô hình nghiệp vụ mới – RaceHorse Transport

Tài liệu tổng hợp mô hình vận hành mới, tham chiếu cách làm thực tế của **IRT (International Racehorse Transport)** và **Peden Bloodstock**, điều chỉnh cho phạm vi **Việt Nam – Lào – Campuchia, đường bộ**. Dùng làm đầu vào để vẽ lại DB trước khi sửa code.

- Trạng thái: **bản phân tích**, chưa triển khai. Code và DB hiện tại chưa thay đổi.
- Phần lớn quy tắc lấy từ điều khoản giao dịch và mẫu phiếu yêu cầu (SLI) của IRT. Peden không công bố điều khoản chi tiết, chỉ dùng cho mô hình tổ chức.
- Các điểm chưa xác minh được tạm **theo logic hiện tại** của hệ thống. Số ngày cách ly là **tham số cấu hình**.
- Lỗi thiết kế DB hiện tại xem tại [DANH_SACH_LOI_THIET_KE.md](DANH_SACH_LOI_THIET_KE.md).

---

## 1. Định vị và phạm vi

| Nội dung | Mô hình mới |
|---|---|
| Vai trò công ty | **Đại lý tổ chức vận chuyển trọn gói**. Công ty là đầu mối duy nhất với khách, được tự chọn phương tiện, tuyến và **thuê lại bên khác** thực hiện một phần dịch vụ (IRT Úc điều 8, IRT Anh điều 3–4, IRT Đức §8) |
| Phạm vi | Việt Nam, Lào, Campuchia. Chỉ đường bộ |
| Vận hành | Công ty tự chạy các đoạn trong Việt Nam. Đoạn ở Lào và Campuchia do **đại lý nước ngoài** chạy. Ngựa được **bàn giao tại cửa khẩu**, nên xe công ty không cần giấy phép liên vận |
| Mạng lưới | Trụ sở và các điểm trung chuyển (hub). Xe kết thúc chuyến ở trụ sở hoặc hub thì sẵn sàng nhận chuyến mới. Có thể giữ quy tắc "chỉ trụ sở" bằng cách chỉ khai báo một hub |
| Rủi ro | Rủi ro với ngựa thuộc **chủ ngựa**. Trách nhiệm của công ty **có trần**. Khách ký xác nhận khi đặt đơn (mẫu SLI của IRT: "All horses are handled at the owner's risk") |

## 2. Các bên

| Bên | Vai trò |
|---|---|
| Khách (người đặt đơn) | Ký phiếu yêu cầu và điều khoản, chịu chi phí cuối cùng |
| Người thanh toán, người gửi, người nhận | Tách riêng như mẫu SLI. Người nhận chịu chi phí phát sinh sau khi tới nơi, nếu không trả thì khách trả thay (IRT Anh điều 9, IRT Úc điều 14) |
| Chuyên viên | Hồ sơ ngựa, giấy tờ kiểm dịch, bảo hiểm |
| Điều phối viên | Kế hoạch đoạn, chuyến, lưu trú, cửa khẩu, xử lý sự cố |
| Quản lý | Duyệt báo giá, phương án sự cố, khoản chi, quyết toán |
| Tài xế, groom | Chạy chuyến, chăm ngựa theo chỉ dẫn, check-in, bàn giao. Vai trò "Hộ tống" đổi thành groom |
| Đối tác | Đại lý Lào/Campuchia, cơ sở cách ly, đơn vị bảo hiểm, thú y, chuồng lưu |
| Cơ quan nhà nước | Cục Chăn nuôi và Thú y (Việt Nam), GDAHP (Campuchia), DLF (Lào), hải quan |

## 3. Loại đơn

| Loại | Đặc điểm |
|---|---|
| Nội địa cùng tỉnh | Một đoạn, không cần kiểm dịch vận chuyển |
| **Nội địa liên tỉnh** | Phải có **giấy chứng nhận kiểm dịch vận chuyển ra khỏi tỉnh**. Xe được niêm phong và tiêu độc khử trùng (TT 01/2026, Điều 5) |
| Quốc tế xuất (Việt Nam → Lào/Campuchia) | Kiểm dịch xuất khẩu, có thể cách ly trước xuất, qua cửa khẩu, bàn giao đại lý, có thể cách ly sau khi đến |
| Quốc tế nhập (Lào/Campuchia → Việt Nam) | Đăng ký với Cục Chăn nuôi và Thú y. **Chủ hàng tự bố trí nơi cách ly, tối đa 45 ngày** (TT 01/2026, Điều 11) |
| Khứ hồi (giai đoạn 2) | Hai đơn liên kết. Chiều về bị coi là **nhập khẩu** (TT 01/2026, Điều 18) |

## 4. Dịch vụ: 3 lớp

Nguyên tắc từ IRT:
- Dịch vụ lõi gồm vận chuyển, lưu chuồng chờ vận chuyển, thông quan và kiểm dịch, groom đi kèm (IRT Úc điều 1).
- Dịch vụ phụ chỉ làm khi đã thỏa thuận bằng văn bản (IRT Đức §4) và lập hóa đơn riêng (§7.7).
- Chi phí phát sinh ngoài dự kiến tính lại theo thực chi (§7.3).

### Lớp A. Loại hình vận chuyển

Khách chọn một loại, loại này quyết định cách tính cước.

| Mã | Tên | Đơn vị tính | Ghi chú |
|---|---|---|---|
| `DEDICATED` | Chuyến riêng | Theo xe theo km, hoặc trọn gói theo tuyến | Thực chất là khách trả thêm để **không bị ghép**. Ghép chuyến là quyền của công ty (IRT Đức §7.4) |
| `SHARED` | Ghép chuyến | Theo khoang (hoặc theo ngựa) theo km | Xem mục 9. Chưa chốt làm ở bản này |

- Cấu hình khoang: `STALL_STANDARD` (khoang tiêu chuẩn), `STALL_WIDE` (khoang rộng một ngựa, cho ngựa giống đực, ngựa mẹ kèm con, ngựa giá trị cao).
- Groom và chăm sóc cơ bản đã nằm trong cước.

### Lớp B. Dịch vụ bổ sung (bản rút gọn)

| Mã | Dịch vụ | Đơn vị | Cách áp dụng |
|---|---|---|---|
| `QUARANTINE_DOCS` | Thủ tục kiểm dịch và thông quan | Theo đơn | **Tự động** cho đơn liên tỉnh và quốc tế. Lệ phí nhà nước là khoản thu hộ |
| `BOARDING` | Lưu chuồng, cách ly | Theo ngựa theo ngày | **Tự động** khi có cách ly bắt buộc. Khách cũng có thể tự đặt |
| `FEEDING` | Cho ăn uống theo chỉ dẫn | Theo ngựa theo ngày | Khách chọn. IRT Anh điều 15: chỉ làm khi khách yêu cầu, chi phí do khách |
| `INSURANCE_ARRANGEMENT` | Thu xếp bảo hiểm | Theo ngựa | Khi khách chọn "hỗ trợ". Phí bảo hiểm là thu hộ, **phí thu xếp** là doanh thu |

Lớp B chỉ có 2 quy tắc:
- Một dịch vụ hoặc **tự động**, hoặc **khách chọn**.
- Chỉ có 3 đơn vị tính: theo đơn, theo ngựa, theo ngựa theo ngày.

Mấy mục sau để giai đoạn 2, khi cần chỉ thêm dòng dữ liệu, không đổi cấu trúc: thú y đi kèm, người đi kèm, mang phụ kiện, huấn luyện trong thời gian cách ly.

### Lớp C. Phụ phí phát sinh

Khách không chọn. Tính theo biểu giá hoặc thực chi.

| Mã | Khoản | Cách tính | Nguồn |
|---|---|---|---|
| `EXTRA_BOARDING` | Lưu chuồng do chậm trễ hoặc chờ thanh toán | Theo ngựa theo ngày | IRT Anh điều 5, IRT Mỹ điều 6 |
| `EXTRA_QUARANTINE` | Cách ly kéo dài hoặc xét nghiệm lại, không do lỗi công ty | Thực chi | IRT Đức §7.3 |
| `EMERGENCY_VET` | Thú y, thuốc, an thần khẩn cấp | Thực chi | IRT Úc 6(iii), IRT Đức §6.4 |
| `CONSIGNEE_WAITING` | Chờ người nhận quá mốc miễn phí | Theo giờ | IRT Anh điều 9 |
| `REROUTE` | Đổi tuyến hoặc phương án vì lợi ích của ngựa | Thực chi | IRT Úc điều 10 |
| `RATE_ADJUSTMENT` | Cước hoặc phí bảo hiểm tăng sau ngày báo giá | Phần chênh | IRT Anh điều 7, IRT Mỹ điều 4 |

### Đối chiếu với danh mục hiện tại

Danh mục cũ: [ReferenceDataSeeder.java](../backend/src/main/java/com/example/racehorse_transport/config/ReferenceDataSeeder.java).

| Hiện tại | Hướng mới |
|---|---|
| Khoang tiêu chuẩn / Khoang đơn mở rộng | Giữ, chuyển thành cấu hình khoang ở lớp A |
| Gói thức ăn cơ bản / nâng cao / thể thao | **Bỏ**. Thay bằng `FEEDING` và chỉ dẫn chế độ ăn theo từng ngựa |
| Cữ nước mỗi 3 giờ / 2 giờ / 1 giờ (đang ngừng dùng) | **Bỏ**. Đưa vào chỉ dẫn chăm sóc |
| Mua bảo hiểm / Từ chối | **Bỏ khỏi danh mục**. Lựa chọn bảo hiểm chuyển sang bảng bảo hiểm theo ngựa. Danh mục chỉ giữ `INSURANCE_ARRANGEMENT` |

## 5. Ngày tháng và lịch

Có ba loại ngày khác nhau. Đơn nội địa thì ba ngày trùng nhau; đơn quốc tế mới tách ra.

| Ngày | Ý nghĩa |
|---|---|
| Ngày đón ngựa | Công ty nhận ngựa tại chuồng khách. Từ lúc này ngựa thuộc trách nhiệm công ty |
| Thời gian cách ly | Ngựa ở trạm chờ xét nghiệm và giấy chứng nhận |
| Ngày xuất phát | Xe rời đi chặng chính. Bằng ngày ra khỏi trạm cách ly |

**Quy tắc:**
- Khách **đề xuất ngày xuất phát**. Mẫu SLI của IRT chỉ có ô "Proposed date of export"; công ty có toàn quyền chọn tuyến và phương tiện (IRT Anh điều 4, IRT Đức §8.2). Ngựa đi đua thì thêm **hạn phải có mặt** (nullable).
- Công ty tính lùi và tính tiến từ ngày xuất phát:
  ```
  ngày đón ngựa     = ngày vào cách ly = ngày xuất phát − số ngày cách ly trước xuất
  ngày giao dự kiến = ngày xuất phát + đường đi + thủ tục cửa khẩu + cách ly sau khi đến + đoạn cuối
  ngày xuất phát sớm nhất = hôm nay + thời gian thẩm định + thời gian làm giấy (ngày làm việc) + số ngày cách ly
  ```
- Thời gian làm giấy, tính bằng **ngày làm việc**:
  - Việt Nam: xem hồ sơ 1 ngày; cấp giấy chứng nhận 1 ngày (nếu không xét nghiệm) hoặc tới 5 ngày (nếu xét nghiệm).
  - Lào: cấp giấy phép nhập 5 ngày.
  - Campuchia: chưa rõ, để tham số.
  - Nhập vào Việt Nam: Cục trả lời trong 4 ngày.
- Lịch được **chốt trong báo giá** gồm ngày đón, ngày xuất phát, ngày giao dự kiến. Đây là cam kết với khách.
- **Mọi mốc tiền, bảo hiểm và trách nhiệm tính theo ngày đón.**

**Khi lịch thay đổi:**

| Tình huống | Ngày nào đổi | Ai chịu |
|---|---|---|
| Khách đề xuất ngày quá sớm | Điều phối viên đề xuất ngày sớm nhất khả thi | Khách chấp nhận hoặc không |
| Không kịp hạn phải có mặt | Trả đơn về khách để đổi khung ngày | |
| Khách đổi lịch sau khi đã cọc | Lập lại báo giá | Phí đổi lịch (tham số) |
| Cách ly kéo dài | Ngày xuất phát lùi, ngày đón giữ nguyên | Khách, nếu không do lỗi công ty |
| Ngựa không đạt cách ly | Ngựa đó bị tách khỏi đơn | Khách chịu chi phí chở về |
| Công ty thiếu xe hoặc lỗi điều phối | Ngày xuất phát lùi | Công ty |

**Ví dụ:** đơn TP.HCM → Phnom Penh, khách đề xuất xuất phát 20/12. Cách ly trước xuất và sau khi đến lấy theo tham số, ví dụ 7 ngày và 5 ngày.

| Ngày | Việc |
|---|---|
| 01/12 | Khách gửi yêu cầu |
| 02–04/12 | Thẩm định, tính được ngày đón 13/12 |
| 05/12 | Báo giá chốt: đón 13/12, xuất phát 20/12, giao dự kiến 25/12 |
| 06/12 | Khách cọc 30% |
| 10/12 | Hạn trả đủ số dư (N ngày trước **ngày đón**) |
| 13/12 | Đón ngựa, vào trạm cách ly. Mốc không hoàn tiền. Bảo hiểm có hiệu lực từ ngày này |
| 20/12 | Ra trạm, xuất phát, qua cửa khẩu Mộc Bài, bàn giao đại lý, vào cách ly sau khi đến |
| 25/12 | Ra khỏi cách ly sau khi đến, đại lý giao ngựa cho người nhận |
| đến 01/01 | Hạn khiếu nại |

## 6. Các giai đoạn của đơn

### GĐ1. Tiếp nhận yêu cầu (theo phiếu SLI)
- Khách khai:
  - ba bên (người thanh toán, người gửi, người nhận) và nơi đón;
  - thông tin ngựa: tên (hoặc tên bố, mẹ), năm sinh, màu lông, giống, giới tính, chip hoặc thẻ tai, cân nặng, dị ứng, bệnh, mang thai (ngày phối gần nhất), giá trị khai báo kèm loại tiền;
  - chỉ dẫn chế độ ăn, phụ kiện đi kèm;
  - ngày xuất phát đề xuất, hạn phải có mặt;
  - loại hình vận chuyển, dịch vụ lớp B;
  - bảo hiểm cho từng ngựa: tự có (ghi đơn vị bảo hiểm), nhờ hỗ trợ, hoặc không bảo hiểm.
- Khách **ký chấp nhận điều khoản**: rủi ro thuộc chủ ngựa, trách nhiệm của công ty có trần.
- Hệ thống cảnh báo theo quy định của nước liên quan: đơn liên tỉnh cần kiểm dịch, nước đến cần giấy phép nhập, khứ hồi phải cách ly khi về.
- → `SUBMITTED`

### GĐ2. Thẩm định song song
- **Chuyên viên:**
  - duyệt hồ sơ ngựa và giấy tờ theo quy định của nước và chiều đi;
  - xác định **bên nào phải nộp giấy gì**. Giấy phép nhập của nước đến thường do người nhận hoặc đại lý xin;
  - kiểm tra bảo hiểm cá nhân, lấy báo giá bảo hiểm cho ngựa chọn "hỗ trợ".
- **Điều phối viên:**
  - lập kế hoạch đoạn (xe công ty hoặc đại lý), điểm bàn giao tại cửa khẩu;
  - lập kế hoạch các lần lưu trú (cách ly trước xuất, cách ly sau khi đến, lưu chuồng);
  - xếp chuyến riêng hoặc ghép, dự kiến xe và groom;
  - tính lịch theo mục 5.
- Thiếu hoặc sai thông tin thì trả đơn về khách → `NEEDS_RESUBMISSION`.
- Quản lý chỉ phát hành báo giá khi cả hai nhánh đã xong.
- → `UNDER_REVIEW`

### GĐ3. Báo giá
- Hệ thống tính từ **bảng giá đang hiệu lực** (mục 13), Quản lý duyệt.
- Báo giá ghi lịch đã chốt, hiệu lực N ngày, kèm điều khoản: cước hoặc phí bảo hiểm tăng thì khách trả phần chênh.
- Phí khám và xét nghiệm cấp giấy chứng nhận sức khỏe không nằm trong báo giá, trừ khi ghi rõ (IRT Mỹ điều 3).
- → `QUOTED`

### GĐ4. Xác nhận và đặt cọc
- Khách chấp nhận và **cọc 30%**. Hệ thống giữ chỗ xe, giữ chỗ với đại lý, cấp vận đơn.
- Quá hạn báo giá mà chưa cọc → `EXPIRED`, nhả chỗ. Quy tắc chờ xác nhận VNPAY giữ như [DEPOSIT_PAYMENT_DEADLINE.md](../frontend/docs/DEPOSIT_PAYMENT_DEADLINE.md).
- → `CONFIRMED`

### GĐ5. Chuẩn bị
- Khách **trả đủ số dư trước ngày đón N ngày**. Chưa trả đủ thì công ty không nhận ngựa (IRT Mỹ điều 5). IRT Đức thu đủ 3 ngày trước khi xuất (§7.5).
- Nộp hồ sơ kiểm dịch: Việt Nam (liên tỉnh hoặc xuất khẩu), giấy phép nhập của nước đến.
- **Chốt bảo hiểm trước khi giao ngựa** (IRT Anh điều 10, IRT Đức §16.1). Hợp đồng phải bao phủ từ ngày đón đến ngày giao.
- Chốt xe, groom, chỗ ở trạm cách ly.
- → `READY`

### GĐ6. Nhận ngựa
- Kiểm tra định danh (chip, thẻ tai), ghi nhận tình trạng ngựa có ảnh.
- **Mốc không hoàn tiền:** phí coi như đã thu đủ khi ngựa được giao cho công ty (IRT Úc điều 12).
- Có cách ly trước xuất thì ngựa vào trạm ngay. Xe về hub, không bị giữ lại.
- → `IN_PROGRESS`

### GĐ7. Hành trình: các đoạn và các lần lưu trú xen kẽ
- **Lưu trú** (mục 7): groom chăm ngựa, ghi nhật ký sức khỏe hằng ngày, kết quả tính theo từng ngựa.
- **Đoạn xe:** check-in theo mốc. Xe liên tỉnh được niêm phong và khử trùng.
- **Cửa khẩu** (mục 8): xác nhận giấy chứng nhận kiểm dịch, thông quan, bàn giao cho đại lý.
- **Sự cố (SOS):**
  - Điều phối viên lập phương án, Quản lý duyệt;
  - mỗi khoản chi ghi **nguyên nhân và bên chịu**;
  - không liên lạc được khách thì công ty tự quyết điều trị, kể cả an thần, trong **hạn mức ủy quyền** (IRT Đức §6.4);
  - công ty báo khách ngay khi có nguy cơ hoặc đã có thiệt hại (IRT Đức §6.5).

### GĐ8. Giao ngựa
- Người nhận ký nhận và ghi nhận tình trạng ngựa. Hư hại nhìn thấy được phải ghi ngay lúc giao (IRT Đức §14.1).
- Chờ quá mốc miễn phí thì tính phí chờ cho người nhận.
- → `DELIVERED`

### GĐ9. Quyết toán và khiếu nại
- Khoản chi lớp C: phần khách chịu thành một khoản thu phát sinh; phần công ty chịu trừ vào lợi nhuận.
- Đối soát với đại lý và tách riêng các khoản thu hộ.
- **Hạn khiếu nại:** 7 ngày kể từ khi giao (IRT Úc điều 18, IRT Đức §14). Bồi thường không vượt trần trách nhiệm.
- → `SETTLED`

### GĐ10. Đóng đơn
- Khách đánh giá → `COMPLETED`

### Nhánh phụ và trạng thái
- `CANCELLED`: khách hủy trước GĐ6, áp dụng biểu phí hủy.
- `EXPIRED`: quá hạn báo giá hoặc quá hạn thanh toán.
- Ngựa không đạt cách ly: tách ngựa đó khỏi đơn, khách chịu chi phí chở về, báo giá điều chỉnh cho các ngựa còn lại.

```
SUBMITTED → UNDER_REVIEW ⇄ NEEDS_RESUBMISSION → QUOTED → CONFIRMED → READY
  → IN_PROGRESS → DELIVERED → SETTLED → COMPLETED
  (EXPIRED / CANCELLED trước IN_PROGRESS)
```

Trạng thái của đoạn, chuyến xe và lưu trú được theo dõi trên bảng riêng, nhờ vậy trạng thái đơn chỉ có khoảng 10 mức:
- **Đoạn:** `PLANNED → CONFIRMED → IN_TRANSIT → ARRIVED` (hoặc `INCIDENT`).
- **Lưu trú:** `SCHEDULED → IN → PASSED / FAILED / EXTENDED`.
- **Chuyến xe:** `PLANNED → DEPARTED → RETURNED_TO_HUB`. Khi về hub, xe và kíp xe chuyển sang `AVAILABLE`.

## 7. Lưu trú và cách ly

Gộp mọi khoảng ngựa đứng yên thành một khái niệm **lưu trú**, tính theo từng ngựa.

| Loại | Ý nghĩa | Do ai thực hiện |
|---|---|---|
| `PEQ` | Cách ly trước xuất | Trạm của công ty (hub có chuồng cách ly) hoặc cơ sở đối tác |
| `PAQ` | Cách ly sau khi đến | Đại lý hoặc cơ sở nhà nước nước đến. Ở Campuchia có thể giữ **ngay tại cửa khẩu** |
| `BOARDING` | Lưu chuồng chờ vận chuyển | Công ty hoặc đối tác |
| `REST_STOP` | Nghỉ dọc đường | Công ty |
| `HOLD` | Bị giữ (ví dụ ở cửa khẩu qua đêm) | Phát sinh |

**Quy tắc:**

| # | Quy tắc |
|---|---|
| Q1 | Số ngày cách ly lấy từ **tham số theo nước và chiều đi**. Bằng 0 thì bỏ bước |
| Q2 | Trong thời gian cách ly: groom chăm theo chỉ dẫn, ghi nhật ký sức khỏe hằng ngày, **không cho người ngoài vào** (an toàn sinh học) |
| Q3 | **Xe không bị giữ** trong thời gian cách ly. Đoạn sau là một chuyến khác |
| Q4 | Kết quả theo từng ngựa: Đạt / Không đạt / Kéo dài |
| Q5 | Số ngày dự kiến tính vào báo giá qua `BOARDING`. Phần kéo dài là phụ phí lớp C, hỏi khách trước nếu liên lạc được |
| Q6 | Cách ly sau khi đến do đại lý báo cáo. Công ty cập nhật cho khách. Đơn vẫn ở `IN_PROGRESS` |
| Q7 | Ngựa không đạt cách ly sau khi đến: cơ quan nước đến quyết định. Phải đưa về Việt Nam thì tạo **đơn chiều về liên kết** |
| Q8 | Bảo hiểm và hạn khiếu nại tính đến ngày người nhận ký nhận, không phải ngày qua biên giới |

## 8. Cửa khẩu và bàn giao đại lý

- Ngựa được bàn giao giữa công ty và đại lý tại cửa khẩu, có biên bản ghi nhận tình trạng.
- Công ty **vẫn chịu trách nhiệm với khách**, vì đại lý là bên được thuê lại. Lỗi do đại lý thì công ty đòi lại đại lý theo hợp đồng riêng.
- Thông quan và kiểm dịch biên giới là **sự kiện tính bằng giờ**, không phải lưu trú. Giữ qua đêm thì thành `HOLD`.
- Kiểm dịch tại cửa khẩu xuất: cán bộ kiểm tra và xác nhận vào giấy chứng nhận (TT 01/2026, Điều 9).
- Nếu sau này xe công ty chạy xuyên biên giới thì cần:
  - giấy phép vận tải quốc tế của doanh nghiệp;
  - giấy phép liên vận cho từng xe. Tuyến Việt Nam – Campuchia: đi nhiều lần, mỗi lần ở lại không quá 30 ngày, hiệu lực 1 năm (TT 39/2015). Tuyến ba nước (CLV): hiệu lực 1 năm (TT 63/2013).

  Đây là giai đoạn 2.

## 9. Ghép chuyến

### Hiện trạng
**Code hiện tại chưa có ghép chuyến.** Mỗi đơn chiếm trọn xe:
- Trang chủ chỉ bán "Xe nguyên chuyến: từ 2 đến 9 ngăn, không ghép ngựa của đơn khác".
- Đơn gắn với xe qua `BOOKING_VEHICLE`. Không có thực thể chuyến xe dùng chung.
- Mỗi đơn giữ xe trong một khoảng thời gian cố định: nội địa từ 1 ngày trước đến 2 ngày sau ngày đi, quốc tế từ 2 ngày trước đến 4 ngày sau. Khoảng giữ của hai đơn chồng nhau thì bị chặn ([FleetServiceImpl.java](../backend/src/main/java/com/example/racehorse_transport/service/impl/FleetServiceImpl.java)).
- Đơn ở `under_review` đã giữ xe.
- **Đã có tuyến cố định** (3 tuyến mẫu: TP.HCM – Đà Nẵng, TP.HCM – Phnom Penh qua Mộc Bài, TP.HCM – Viêng Chăn qua Lao Bảo) và logic khớp đơn vào tuyến (cách tuyến ≤ 80 km, đi vòng ≤ 1,5 lần):
  - [FixedRouteSeeder.java](../backend/src/main/java/com/example/racehorse_transport/config/FixedRouteSeeder.java)
  - [fixedRouteMatch.ts](../frontend/src/shared/lib/fixedRouteMatch.ts)

  Hiện tuyến cố định chỉ dùng để gợi ý lộ trình và tra giá.

### Đề xuất (chưa chốt làm ở bản này)

**Khái niệm:**
- **Chuyến định kỳ:** tuyến cố định cộng ngày khởi hành, sức chứa tính bằng khoang, có hạn chốt nhận đơn.
- Đơn ghép chọn một chuyến định kỳ thay vì tự đề xuất ngày. Điểm đón và giao phải nằm trong hành lang của tuyến.

**Luồng:**
1. Điều phối viên mở chuyến định kỳ: tuyến, ngày, xe dự kiến, số khoang, số khoang tối thiểu, hạn chốt.
2. Khách chọn chuyến và số khoang. Hệ thống kiểm tra còn chỗ, nằm trong hành lang và hạn chốt đủ thời gian làm giấy và cách ly.
3. Thẩm định và báo giá như đơn thường. Cước tính theo khoang theo km trên phần tuyến đơn đi qua.
4. Đến hạn chốt:
   - đủ số khoang tối thiểu thì chuyến được xác nhận chạy;
   - không đủ thì khách chọn: chuyển sang chuyến sau, đổi sang chuyến riêng (trả thêm), hoặc hủy không mất phí.
5. Chuyến chạy, đón và giao theo thứ tự điểm trên tuyến.
6. Xe về hub. Mỗi đơn quyết toán riêng.

**Quy tắc riêng:**

| # | Quy tắc |
|---|---|
| G1 | Mọi ngựa phải đủ giấy kiểm dịch trước khi lên xe. Con nào thiếu thì bị loại, chuyến vẫn chạy |
| G2 | Cách ly có thể làm theo nhóm. Một con dương tính kéo dài thời gian cả nhóm thì tính là kéo dài không do lỗi công ty, chi phí chia theo điều khoản |
| G3 | Chuyến trễ vì một đơn thì đơn đó chịu chi phí phát sinh |
| G4 | Ngựa giống đực, ngựa mẹ kèm con, ngựa giá trị cao có thể bắt buộc khoang rộng hoặc không được ghép |
| G5 | Xe chỉ bị giữ khi chuyến định kỳ đã mở, không giữ theo từng đơn từ lúc `under_review` |

**Nếu để giai đoạn 2:** vẽ `trip` tách khỏi đơn ngay từ bây giờ và chừa `scheduled_departure_id` nullable trên đơn. Như vậy sau này thêm ghép chuyến không phải đổi cấu trúc DB.

## 10. Khứ hồi (giai đoạn 2)

- Làm dưới dạng **hai đơn liên kết** (đơn đi và đơn về), mỗi đơn có báo giá, giấy tờ, kiểm dịch riêng. Mẫu SLI của IRT cũng tách theo chiều.
- Lúc ngựa về Việt Nam bị coi là **nhập khẩu**: đăng ký trước 4 ngày làm việc, chủ hàng tự bố trí nơi cách ly tối đa 45 ngày (TT 01/2026, Điều 11 và 18).
- Khi tạo đơn đi có đánh dấu "có chiều về", hệ thống **cảnh báo ngay** điều kiện nhập lại Việt Nam.
- Công ty có thể cung cấp nơi cách ly cho chiều về qua `BOARDING`.
- DB chỉ cần chừa `return_of_booking_id` nullable trên đơn.

## 11. Tiền và trách nhiệm

| Chủ đề | Quy tắc |
|---|---|
| Báo giá | Hiệu lực N ngày, có điều khoản điều chỉnh giá. Lưu lại toàn bộ dòng báo giá tại thời điểm chốt. IRT Đức: báo giá hết hạn sau 30 ngày (§3.3) |
| Thanh toán | Cọc 30% khi chấp nhận. Số dư trả trước ngày đón N ngày |
| Hoàn tiền | Theo biểu phí hủy nếu hủy trước GĐ6. **Không hoàn** sau khi đã nhận ngựa |
| Khoản thu hộ | Bảo hiểm và lệ phí nhà nước tách khỏi doanh thu trong báo giá và đối soát |
| Chi phí phát sinh | Chia **theo nguyên nhân**: lỗi của công ty thì công ty chịu; do ngựa, khách, người nhận hoặc bất khả kháng thì khách hoặc người nhận chịu. Phải hỏi khách trước khi chi, trừ trường hợp khẩn cấp (IRT Đức §7.3) |
| Sau khi tới nơi | Chi phí trước lúc giao do người nhận trả. Người nhận không trả thì khách trả (IRT Anh điều 9) |
| Khai sai thông tin | Khách chịu mọi chi phí và tiền phạt (IRT Đức §5.2, IRT Úc điều 4) |
| Trần trách nhiệm | Đề xuất: tối đa bằng **tổng phí của đơn** (IRT Mỹ điều 10). Không dùng loại trừ toàn bộ như IRT Úc, vì có thể không hợp lệ theo luật Việt Nam |
| Chờ người nhận | Có số giờ miễn phí (hiện tại: 2 giờ). Quá mốc thì tính phí cho người nhận |
| Tạm dừng dịch vụ | Công ty được tạm dừng dịch vụ đến khi khách thanh toán đủ (IRT Úc điều 12). Không áp dụng quyền giữ và bán ngựa |
| Người đi kèm | Tự chịu rủi ro, cần duyệt (IRT Đức §11). Giai đoạn 2 |

**Ma trận chi phí phát sinh:**

| Tình huống | Bên chịu |
|---|---|
| Hỏng xe, tai nạn do công ty | Công ty. Thiệt hại cho ngựa tính trong giới hạn trần, phần vượt do bảo hiểm |
| Ngựa ốm hoặc bị thương không do lỗi công ty | Khách |
| Chờ người nhận trong số giờ miễn phí | Công ty (đã tính trong giá) |
| Chờ quá mốc, phải lưu chuồng | Người nhận, không trả thì khách |
| Giấy tờ khách cung cấp sai, bị giữ ở cửa khẩu | Khách |
| Thủ tục do công ty hoặc đại lý làm sai | Công ty, sau đó đòi lại đại lý |
| Đóng cửa khẩu, thiên tai, dịch bệnh | Khách (chi phí phát sinh) |

## 12. Bảo hiểm và thức ăn

### Bảo hiểm
- Giữ ba lựa chọn theo từng ngựa: **cá nhân** (khách tự có), **hỗ trợ** (công ty thu xếp qua bên thứ ba), **không bảo hiểm**.
- **Bổ sung:**
  - Công ty chỉ thu xếp khi khách yêu cầu **trước khi giao ngựa** (IRT Anh điều 10, IRT Đức §16.1).
  - Chọn "không bảo hiểm" thì khách **xác nhận đã hiểu công ty chỉ chịu trách nhiệm trong giới hạn của điều khoản**, gắn với phiên bản điều khoản đã ký.
  - Giá trị khai báo dùng để tính thuế, **không có nghĩa** khách đã mua bảo hiểm (IRT Đức §16.1).
  - Nếu bên bảo hiểm từ chối bồi thường, khách làm việc trực tiếp với bên bảo hiểm (IRT Anh điều 10).
- Hợp đồng bảo hiểm phải bao phủ **từ ngày đón đến ngày giao**, gồm cả các lần cách ly.
- Luồng báo giá bảo hiểm, bản chụp báo giá và bổ sung hợp đồng sau khi đặt cọc giữ như [BAO_HIEM_BEN_THU_BA.md](BAO_HIEM_BEN_THU_BA.md).

### Thức ăn
- Bỏ 3 gói thức ăn.
- Mỗi ngựa có **chỉ dẫn chế độ ăn**: khẩu phần, số lần cho ăn, thức ăn cấm hoặc dị ứng, khách có tự gửi thức ăn không.
- Cho ăn uống là dịch vụ `FEEDING`, tính theo ngựa theo ngày.
- Đơn quốc tế cảnh báo hạn chế mang thức ăn qua biên giới theo quy định nước đến.

## 13. Báo giá: các thành phần

```
Tổng báo giá =
  Σ cước các đoạn công ty chạy   (theo km × đơn giá loại xe; riêng = theo xe, ghép = theo khoang)
+ Σ giá đại lý cho đoạn thuê ngoài (giá vốn + tỷ lệ lợi nhuận)
+ Σ dịch vụ lớp B                 (tự động + khách chọn)
+ Σ khoản thu hộ                  (phí bảo hiểm, lệ phí nhà nước)
+ thuế
```

Các thực thể tham gia:
- `price_list` (bảng giá có hiệu lực theo thời gian).
- `price_item` (mục giá: loại phí, đơn vị, đơn giá, điều kiện áp dụng như loại hình, loại xe, tuyến, quốc gia, bậc km; có cờ thu hộ).
- `partner_rate` (giá đại lý).
- `exchange_rate` (nếu đại lý tính bằng ngoại tệ).
- `quotation` và `quotation_line` (lưu lại số lượng, đơn giá, thành tiền và mục giá nguồn tại thời điểm chốt).

## 14. Quy định pháp lý của 3 nước

### Việt Nam: Thông tư 01/2026/TT-BNNMT
Hiệu lực từ 01/01/2026, thay thế Thông tư 25/2016 (Điều 30).

| Trường hợp | Điều | Quy trình và thời hạn |
|---|---|---|
| Chở ra khỏi tỉnh | 5 | Đăng ký với cơ quan thú y tỉnh, xem hồ sơ trong 1 ngày làm việc. Khám lâm sàng, lấy mẫu, niêm phong xe, tiêu độc khử trùng. Giấy chứng nhận cấp trong 5 ngày làm việc, hoặc 1 ngày nếu ngựa từ cơ sở an toàn dịch hoặc đã tiêm phòng còn miễn dịch |
| Xuất khẩu | 8, 9 | Đăng ký với chi cục vùng hoặc cơ quan kiểm dịch cửa khẩu, xem hồ sơ trong 1 ngày. Kiểm tra theo yêu cầu của nước nhập. Giấy chứng nhận cấp trong 5 ngày nếu xét nghiệm, 1 ngày nếu không. **Không quy định số ngày cách ly cố định** |
| Nhập khẩu | 11 | Đăng ký với Cục, Cục trả lời trong 4 ngày làm việc. Khai báo với cửa khẩu trước khi hàng đến. Chủ hàng tự bố trí nơi cách ly, **tối đa 45 ngày** |
| Tạm xuất tái nhập | 18 | Lúc đi làm như xuất khẩu, lúc về làm như nhập khẩu |
| Thi đấu thể thao | 21 | Trong nước theo Điều 5, nhập về thi đấu theo Điều 11, xuất đi sau giải theo Điều 8 |
| Đánh dấu | 25, 27 | Ngựa xuất hoặc nhập khẩu phải bấm thẻ tai màu vàng có mã số. Ngựa nhập đã có đánh dấu thì miễn |
| Bệnh kiểm dịch ở ngựa | Phụ lục III | 13 bệnh, trong đó có dịch tả ngựa châu Phi, thiếu máu truyền nhiễm, tỵ thư, cúm ngựa, viêm não tủy ngựa, lê dạng trùng |

### Campuchia
- Giấy phép nhập khẩu do GDAHP cấp, sau khi MAFF duyệt.
- Giấy tờ: giấy phép nhập, giấy chứng nhận xuất xứ, đăng ký kinh doanh, giấy phép xuất của nước xuất, giấy tờ định danh con vật, giấy chứng nhận vệ sinh, hợp đồng nhập khẩu, hợp đồng kỹ thuật với GDAHP.
- Động vật sống được giữ cách ly tại biên giới. Campuchia **không có trạm kiểm dịch động vật** (theo bài trình bày tại WOAH, khoảng 2023).
- Chưa có quy định riêng cho ngựa.

### Lào
- DLF cấp giấy phép nhập trong 5 ngày làm việc nếu đủ hồ sơ. Chỉ nhập vì mục đích kinh doanh mới cần giấy phép.
- Tại cửa khẩu xuất trình giấy phép nhập và giấy chứng nhận sức khỏe của nước xuất.
- Khi xuất từ Lào: DLF kiểm tra, cách ly, cấp giấy chứng nhận sức khỏe và giấy phép xuất.
- Có Nghị định 644/GoL (25/11/2024), chưa đọc được bản gốc.

### Vận tải qua biên giới

| Tuyến | Văn bản | Nội dung |
|---|---|---|
| Việt Nam – Campuchia | TT 39/2015 (hợp nhất 24/VBHN-BGTVT năm 2022, sửa đổi 47/2019) | Giấy phép liên vận đi nhiều lần, mỗi lần không quá 30 ngày, hiệu lực 1 năm |
| Việt Nam – Lào | TT 88/2014 | Điều 8–16 đã bị bãi bỏ bởi TT 05/2022. Cần tra văn bản đang hiệu lực |
| Ba nước (CLV) | TT 63/2013 | Giấy phép cho xe thương mại hiệu lực 1 năm. Cấp trong 3 ngày làm việc |

## 15. Thực thể để vẽ DB

| Nhóm | Thực thể |
|---|---|
| Danh mục | `country`, `country_rule` (theo chiều: xuất / nhập / tái nhập / liên tỉnh; có cần giấy phép nhập không; số ngày làm giấy; số ngày cách ly tối thiểu và tối đa; nơi cách ly; danh sách bệnh), `document_requirement` (thêm chiều và bên nộp), `location` (HQ, HUB, BORDER_GATE, QUARANTINE, STABLE, REST_STOP…), `partner` |
| Giá | `service` (lớp, đơn vị, phạm vi, tự áp dụng hay không, thu hộ hay không), `price_list`, `price_item`, `partner_rate`, `exchange_rate` |
| Khách và ngựa | `user`, `party` (các bên), `horse` (chip, thẻ tai, cân nặng, dị ứng, giá trị khai báo kèm loại tiền) |
| Đơn | `booking` (loại, ngày xuất phát đề xuất, hạn phải có mặt, hạn mức ủy quyền chi khẩn cấp, `return_of_booking_id`, `scheduled_departure_id`), `booking_party`, `booking_horse` (chỉ dẫn chế độ ăn), `booking_horse_service`, `booking_status_history`, `terms_acceptance` (loại điều khoản, phiên bản) |
| Vận hành | `shipment_leg` (thứ tự, chiều, bên vận hành, đối tác, điểm đi và đến, giờ dự kiến và thực tế, `trip_id`), `trip`, `vehicle` (vị trí hiện tại, hub gốc, số khoang), `staff`, `crew_assignment`, `horse_stay`, `border_clearance`, `handover`, `horse_health_log`, `horse_journey_log` |
| Bảo hiểm | `horse_insurance` (1–1 với ngựa trong đơn; lựa chọn, đơn vị bảo hiểm, phí, hạn báo giá, số hợp đồng, hiệu lực, trạng thái, cột xác nhận từ chối bảo hiểm), `insurance_document` |
| Tiền | `quotation` (phiên bản, hiệu lực, ngày đón, ngày xuất phát, ngày giao đã chốt), `quotation_line`, `payment` (cọc / số dư / phát sinh / hoàn tiền; gắn với báo giá) |
| Sự cố | `incident` (gắn với đoạn; nguyên nhân), `cost_item` (hạng mục, số tiền, chứng từ, bên chịu, người duyệt; khoản do khách chịu nối sang thanh toán phát sinh), `claim` (có hạn chót) |
| Chứng từ | `document` dùng chung, gắn vào ngựa, đơn, đoạn, lưu trú, cửa khẩu, khoản chi. Có FK tới `document_requirement` để đối chiếu đã đủ giấy chưa |
| Giai đoạn 2 | `scheduled_departure`, `vehicle_permit`, `staff_document`, `booking_companion` |

## 16. Tham số cấu hình

Đây là giá trị cấu hình, **không ảnh hưởng thiết kế DB**. Chỗ chưa có giá trị thì tạm theo logic hiện tại.

| Tham số | Giá trị hiện tại / đề xuất |
|---|---|
| Hiệu lực báo giá | Hiện tại: 24 giờ. IRT: 30 ngày. Nhóm chốt |
| Tỷ lệ cọc | 30% |
| Hạn trả số dư | N ngày trước ngày đón (IRT Đức: 3 ngày) |
| Biểu phí hủy trước khi nhận ngựa | Theo quy tắc khấu trừ cọc hiện tại |
| Trần trách nhiệm | Đề xuất: bằng tổng phí của đơn |
| Số giờ chờ miễn phí khi giao | Hiện tại: 2 giờ |
| Hạn mức ủy quyền chi khẩn cấp | Mức chung, hoặc khách tự đặt theo đơn |
| Hạn khiếu nại | 7 ngày kể từ khi giao |
| Số ngày cách ly trước xuất và sau khi đến | Theo nước và chiều đi |
| Số ngày làm giấy | Việt Nam 1 + 1–5 ngày làm việc; Lào 5; nhập vào Việt Nam 4; Campuchia: tham số |
| Hành lang tuyến cố định (khi làm ghép chuyến) | 80 km, đi vòng ≤ 1,5 lần (giữ như hiện tại) |

## 17. So với mô hình cũ

| Cũ | Mới |
|---|---|
| Một đơn ứng với một chuyến xe từ trụ sở duy nhất | Nhiều đoạn, có đại lý, xe về trụ sở hoặc hub. Ghép chuyến có thể làm sau |
| Không có kiểm dịch vận chuyển | Kiểm dịch liên tỉnh và xuất nhập khẩu theo TT 01/2026 |
| Khách chọn ngày khởi hành | Khách đề xuất, công ty tính lùi và chốt trong báo giá |
| Báo giá hiệu lực 24 giờ | N ngày, có điều chỉnh giá |
| Đủ 70% lúc xuất phát | Số dư trước ngày đón N ngày |
| Công ty chịu toàn bộ chi phí sự cố và chờ | Theo nguyên nhân, có hạn mức ủy quyền, có trần trách nhiệm |
| 3 gói thức ăn, 3 mức cữ nước | Chỉ dẫn chế độ ăn và dịch vụ cho ăn uống |
| Bảo hiểm là một dịch vụ trong danh mục | Bảng bảo hiểm riêng theo ngựa, kèm điều khoản rủi ro thuộc chủ ngựa |
| Xe bị giữ theo từng đơn từ lúc `under_review` | Xe gắn với chuyến. Thời gian cách ly không giữ xe |
| Chưa có | Lưu trú và cách ly, cửa khẩu, bàn giao giữa đoạn, khiếu nại, các bên tách riêng |

## 18. Việc còn mở

1. **Ghép chuyến:** làm ở bản này hay để giai đoạn 2.
2. Chốt các tham số ở mục 16.
3. Xác minh (trong lúc chờ thì theo logic hiện tại):
   - số ngày cách ly và xét nghiệm bắt buộc với ngựa ở Lào và Campuchia;
   - ngựa đua đã có chip có phải bấm thẻ tai theo Điều 27 không;
   - văn bản đang hiệu lực về giấy phép liên vận tuyến Việt Nam – Lào;
   - nội dung Nghị định 644/GoL của Lào.
4. Đối chiếu điều khoản trần trách nhiệm và hoàn tiền với luật Việt Nam trước khi đưa vào chính sách chính thức ([docs/policy-release](policy-release/)).

## Nguồn

**IRT và Peden**
- [IRT (UK) Limited – Trading Conditions](https://irt.com/media/1034/irt_uk_trading_conditions.pdf)
- [IRT (Europe) GmbH – General Terms and Conditions](https://www.irt.com/media/1166/irt-ger_trading-conditions_english.pdf)
- [IRT Úc – SLI Export General và Trading Conditions 2006](https://www.irt.com/media/1031/irt_aus_trading_conditions_oct2006.pdf)
- [IRT Mỹ – SLI Export và Trading Conditions](https://www.irt.com/media/1151/sli-usa-export.pdf)
- [IRT – SLI Export General (2014)](https://www.irt.com/media/1059/sli_export_general_may14.pdf)
- [irt.com](https://irt.com)
- [Eurodressage – IRT Q&A](https://eurodressage.com/2019/04/04/irt-safely-flying-horses-all-over-globe-qa-about-horse-transport)
- [Horse & Hound – All about import and export (Peden)](https://www.horseandhound.co.uk/archives/all-about-import-and-export-56154)
- [Equanine – Peden Bloodstock](https://equanine.com/supplier/peden-bloodstock/)
- [Horse Racing Sense – Cost to fly a horse overseas](https://horseracingsense.com/how-much-does-it-cost-to-fly-a-horse-overseas/)

**Việt Nam**
- [Thông tư 01/2026/TT-BNNMT – bản gốc](https://datafiles.chinhphu.vn/cpp/files/vbpq/2026/01/01-bnnmt.pdf)
- [Thông tư 01/2026/TT-BNNMT – luatvietnam.vn](https://luatvietnam.vn/tai-nguyen/thong-tu-01-2026-tt-bnnmt-quy-dinh-kiem-dich-dong-vat-san-pham-dong-vat-tren-can-423507-d1.html)
- [Thông tư 39/2015/TT-BGTVT](https://vanban.chinhphu.vn/default.aspx?pageid=27160&docid=181921)
- [Văn bản hợp nhất 24/VBHN-BGTVT 2022](https://luatvietnam.vn/giao-thong/van-ban-hop-nhat-24-vbhn-bgtvt-226877-d5.html)
- [Thông tư 63/2013/TT-BGTVT](https://moj.gov.vn/vbpq/lists/vn%20bn%20php%20lut/view_detail.aspx?itemid=29366)
- [Thông tư 88/2014/TT-BGTVT](https://caselaw.vn/van-ban-phap-luat/264117-thong-tu-88-2014-tt-bgtvt-huong-dan-thuc-hien-hiep-dinh-va-nghi-dinh-thu-thuc-hien-hiep-dinh-tao-dieu-kien-thuan-loi-cho-phuong-tien-co-gioi-duong-bo-qua-lai-bien-gioi-giua-viet-nam-lao-do-bo-truong-bo-giao-thong-van-tai-ban-hanh)

**Campuchia và Lào**
- [WOAH – Cambodia: Practices of international trade of animals](https://rr-asia.woah.org/app/uploads/2023/12/4-3-cambodia_practices-of-internation-trade.pdf)
- [USDA FAS – FAIRS Country Report Cambodia 2024](https://www.fas.usda.gov/data/gain-report/2025/01/FAIRS%20Country%20Report%20Annual_Phnom%20Penh_Cambodia_CB2024-0005.pdf)
- [WTO – Cambodia Notification No. 3525](https://www.importlicensing.wto.org/sites/default/files/members/23/Notification%20No.%203525%20-%20Importing%20live%20animals%2C%20meat%20and%20animal%20products_25.04.2017.pdf)
- [Lao Trade Portal – Import License for Live Animals](https://www.laotradeportal.gov.la/en-gb/search-procedure/view/66)
- [Lao Trade Portal – Documents for import/export/transit](https://www.laotradeportal.gov.la/en-gb/site/display/1719)
- [WOAH – Laos country report 2024](https://rr-asia.woah.org/app/uploads/2024/04/3.2-Laos_country-report_Laphinh.pdf)
