# Nghiệp vụ theo 6 luồng

Bản 26/11/2026, theo **mô hình bến xe** người dùng chốt cùng ngày. Bản chi tiết cũ (25/11, có mã điểm như 4.F1, T35) lưu ở [luu-tru/NGHIEP_VU_6_LUONG_25-11-2026.md](luu-tru/NGHIEP_VU_6_LUONG_25-11-2026.md). Bảng và cột: [NGHIEP_VU_THEO_59_BANG.md](NGHIEP_VU_THEO_59_BANG.md).

- Đánh dấu **[Mới]**: đã chốt 26/11/2026, **code chưa làm**. Không có dấu là code đang chạy.
- Số ghi **(cấu hình)** quản trị sửa được. Số ghi **(số mẫu)** là dữ liệu mẫu.

## Bức tranh chung

```
 Nhánh đón              TRỤC (tuyến và lịch cố định)                 Nhánh giao
 nhà khách ─┐                                                       ┌─ nơi giao
 nhà khách ─┼─▶ HUB A ══ trạm nghỉ ══ (cửa khẩu) ══ trạm nghỉ ══ HUB B ─┼─ nơi giao
 tự chở   ──┘                                                       └─ tự tới lấy
```

- **Như bến xe:** trục chỉ chạy giữa **hai hub cố định** theo tuyến đã cài. Từ mỗi hub có **nhánh** đón và giao.
- **Mọi hub là của công ty**, kể cả hub ở Lào, Campuchia [Mới]. Hub nước ngoài giống hệt hub trong nước: vùng phục vụ, ô chuồng, xe, nhân viên, quy trình. "Đại lý" chỉ là cách gọi chi nhánh nước ngoài.
- **Cửa khẩu là điểm dừng làm thủ tục giữa tuyến** [Mới]. Xe công ty chạy thẳng sang hub bên kia, không bàn giao cho ai ở cửa khẩu.
- **Mọi đơn cùng một hình:** nhánh đón → hub → trục → hub → nhánh giao. Hai đầu chung hub thì **chạy thẳng**, không qua trục.
- **Ba lớp không trộn:** đơn (mốc mua bán), lượt xe (xe đang ở đâu), con ngựa (đang trong tay ai, khỏe không).

| Từ | Nghĩa |
|---|---|
| Chuyến | Một lần chạy của tuyến trục vào một ngày. Một chuyến **điều được nhiều xe** [Mới] |
| Lượt xe | Một chiếc xe chạy một lần: đón, trục, giao, chạy thẳng, chuyển xe |
| Chặng | Một đoạn đường của **một con ngựa** trên một lượt xe |
| Xe ghép | Ngựa nhiều đơn đi chung xe. Giá theo chỗ |
| Xe riêng | Ngựa của đơn **không đi chung xe với đơn khác ở mọi chặng**. Đi đúng lịch trục như xe ghép. Giá theo nguyên xe |
| Giờ chạy | Giờ xe trục rời hub đầu. Mọi hạn "trước giờ chạy" đếm lùi từ đây |
| Hạn nhận đặt | 72 giờ trước giờ chạy |
| Mốc quyết định | 48 giờ trước giờ chạy |
| Hạn có mặt ở hub | 3 giờ trước giờ chạy (cấu hình) |

---

# Luồng 1. Tạo và duyệt đơn

Từ lúc khách mở tài khoản tới lúc khách trả cọc và đơn **Đã xác nhận**.

> Chờ tiếp nhận → Đang thẩm định → Đang báo giá → Chờ đặt cọc → **Đã xác nhận**

## Nghiệp vụ chính

1. Khách đăng ký bằng Gmail và số điện thoại, nhập mã xác thực (10 phút, sai tối đa 5 lần).
2. Khách khai tên, mã microchip ISO 15 chữ số, giống từ danh mục, giới tính, màu lông và năm sinh (từ 1990 tới năm hiện tại). Đặc điểm nhận dạng là tùy chọn. Mã chip cố định sau khi tạo hồ sơ. Khách có thể lưu sổ địa chỉ có ghim bản đồ.
   - Khách nộp hộ chiếu ngựa và sổ tiêm phòng ngay trong hồ sơ. Giấy chờ duyệt được tính là đã nộp khi hiển thị hồ sơ sẵn sàng đặt. Giấy bị trả lại hiện kèm lý do và có thể nộp lại. Bản chờ duyệt hoặc đã duyệt còn hạn chặn việc nộp trùng.
   - Giấy xét nghiệm và kiểm dịch theo từng con tiếp tục nộp trong đơn.
3. **Đặt đơn:**
   - chọn điểm đón và điểm giao cụ thể; **mỗi con có thể giao một nơi**;
   - hệ thống tra hub của từng địa chỉ, ra tuyến (ví dụ TP.HCM – Phnom Penh) và loại đơn (liên tỉnh, xuất, nhập);
   - chọn chuyến: chuyến hiện **Còn chỗ / Sắp đầy / Hết chỗ** [Mới] và hạn nhận đặt; chuyến không kịp làm giấy thì khóa;
   - chọn **xe ghép** hoặc **xe riêng** trước danh sách chuyến. Nhãn xe riêng dựa trên xe phù hợp còn rảnh tại hub xuất phát;
   - mỗi đầu trong nước chọn **tự đưa tới hub** hoặc **công ty đón, giao**. Công ty chỉ nhận khi đường một chiều tới hub ≤ 5 giờ lái;
   - chọn ngựa, loại khoang, chế độ ăn; bảo hiểm từng con (tự có, mua hộ, không mua); khai người gửi, người nhận, người trả tiền, liên hệ khẩn cấp, hạn mức chi khẩn cấp;
   - giá tạm tính luôn do máy chủ tính.
4. Gửi đơn: **giữ chỗ ngay**, đơn **Chờ tiếp nhận**.
5. Quản lý **tiếp nhận** và phân chuyên viên. Chuyên viên duyệt hoặc trả lại giấy hồ sơ trong đơn có con ngựa đó, rồi thẩm định bảo hiểm (luồng 2). Đơn sau dùng lại giấy đã duyệt nếu còn hạn vào ngày chạy. Đơn nội tỉnh có thể hiển thị giấy hồ sơ đã nộp dưới dạng tùy chọn. Khi đủ điều kiện, chuyên viên bấm **Bắt đầu báo giá**.
6. Quản lý lập báo giá theo bảng giá hiệu lực, chỉ thêm dòng điều chỉnh có lý do, bấm **Gửi khách**.
7. Khách có **24 giờ** để vừa chấp nhận vừa trả cọc. **Cọc = 30% cước + toàn bộ thu hộ**. Trả qua VNPAY.
8. Nhận đủ cọc: đơn **Đã xác nhận**, cấp số vận đơn.

**Hạn chính:** chuyến mở bán trước 6 tuần (cấu hình); tiếp nhận 4 giờ, thẩm định 24 giờ, gửi báo giá 24 giờ (nhắc nội bộ); báo giá phải gửi trước **mốc quyết định**; số ngày làm giấy tính tới hạn nhận đặt: liên tỉnh 5, xuất 21, nhập 37 (số mẫu).

## Xử lý ngoại lệ

| Tình huống | Hệ thống |
|---|---|
| Lúc gửi, chuyến vừa hết chỗ hoặc qua hạn nhận đặt | Không tạo đơn, giữ bản nháp để chọn chuyến khác |
| Ngựa đang nằm trong đơn khác | Khóa con đó, ghi mã đơn đang giữ |
| Địa chỉ ở tỉnh chưa có hub | Nói đầu nào ngoài vùng phục vụ, liệt kê tỉnh đang phục vụ |
| Xe nền đầy | Tự gắn xe để sẵn còn rảnh của hub vào chuyến, báo quản lý; hết xe để sẵn thì chuyến Hết chỗ, gợi ý chuyến sau [Mới] |
| Công ty không nhận đơn | Từ chối kèm lý do khi Chờ tiếp nhận hoặc Đang thẩm định, nhả chỗ |
| Tới mốc quyết định mà chưa gửi báo giá | Tự nhả chỗ, đơn **Cần chọn lại chuyến**, khách đổi chuyến hoặc hủy miễn phí |
| Hết 24 giờ chưa trả cọc | **Báo giá hết hạn**, nhả chỗ. Quản lý mở lại được nếu còn chỗ và kịp giấy |
| Hết hạn đúng lúc khách đang ở VNPAY | Chờ thêm 5 phút nhận kết quả |
| Tiền về sau khi đơn hết hạn hoặc đã hủy | Không khôi phục đơn, báo quản lý đối soát và hoàn |
| Khách từ chối báo giá | Quay về Đang báo giá, vẫn giữ chỗ; đã qua mốc quyết định thì nhả chỗ |
| Công ty hủy chuyến trước khi nhận ngựa | Mọi đơn **Cần chọn lại chuyến**, khách đổi miễn phí hoặc hoàn 100% |
| Đổi chuyến khi đã cọc | Cùng tuyến, trong 30 ngày từ chuyến lỡ thì giữ giá (cấu hình) |

**Hủy đơn và hoàn tiền**

| Đơn đang ở đâu | Khách tự hủy | Công ty hủy |
|---|---|---|
| Chưa cọc | Miễn phí | Được |
| Đã cọc, chưa nhận ngựa | Mất cọc | Hoàn 100% |
| Đã nhận ngựa | Không tự hủy được | Xử lý bằng sự cố (luồng 5) |
| Lỡ chuyến do công ty | Hoàn 100% | Hoàn 100% |
| Lỡ chuyến do khách | Mất cọc, hoặc đổi chuyến miễn phí 1 lần | Hoàn 100% |
| Lỡ chuyến do cơ quan cấp giấy | Đổi miễn phí; hủy lần đầu mất cọc, từ lần thứ 2 hoàn cọc vận chuyển | Hoàn 100% |

Hoàn tiền bằng chuyển khoản ngoài hệ thống, hạn 7 ngày (cấu hình), quản lý ghi mã chuyển khoản.

---

# Luồng 2. Giấy tờ và bảo hiểm

Từ lúc hệ thống biết cần giấy gì tới lúc công ty đủ điều kiện nhận ngựa và qua cửa khẩu.

## Nghiệp vụ chính

1. **Danh sách giấy** lấy theo **quy định hiệu lực vào ngày đón**: theo nước và chiều (liên tỉnh, xuất, nhập), gồm **giấy chung** cộng **giấy riêng của cửa khẩu** mà tuyến đi qua (như bản cũ). Cửa khẩu lấy từ **điểm dừng cửa khẩu trên tuyến** [Mới]. Đơn trong tỉnh không cần giấy.
2. **Đợt 1, trước báo giá:** khách nộp giấy của mình (PDF, JPG, PNG ≤ 5 MB); chuyên viên duyệt hoặc từ chối kèm lý do; mỗi con có bảo hiểm được chuyên viên xác nhận.
3. **Đợt 2, sau khi cọc:** chuyên viên lo giấy của công ty và nộp thay giấy của người nhận. Giấy phía nước ngoài do **hub nước ngoài** chuẩn bị, chuyên viên nhập [Mới]. Thú y tới chuồng lấy mẫu nếu nước đến bắt buộc.
4. Giấy **đạt** khi đã duyệt và còn hạn vào ngày chạy (chạy thẳng: ngày đón). Giấy theo ngựa (hộ chiếu, sổ tiêm) dùng lại cho đơn sau nếu còn hạn.
5. **Kiểm ở các mốc:** trước báo giá (giấy khách, bảo hiểm); trước khi nhận ngựa (đủ giấy bắt buộc mọi bên); tại cửa khẩu (giấy riêng của cửa khẩu).
6. Từ 5 ngày trước giờ chạy, 08:00 mỗi ngày nhắc đúng bên còn thiếu giấy.

## Xử lý ngoại lệ

| Tình huống | Hệ thống |
|---|---|
| Nộp lại giấy đang chờ duyệt | Không nhận |
| Giấy bị từ chối hoặc thu hồi (duyệt sai, giấy giả) | Báo đúng bên nộp kèm lý do, cho nộp lại |
| Bảo hiểm tự có thiếu hợp đồng hoặc không phủ ngày chạy | Không xác nhận được, yêu cầu khách bổ sung |
| Báo giá hãng bảo hiểm hết hạn trước khi gửi báo giá | Chặn gửi; chuyên viên cập nhật, quản lý lập lại bản nháp |
| Giấy hết hạn sau khi đã cọc | Đơn hiện lại ở **Đơn thiếu giấy**, nộp bản mới |
| Tới mốc quyết định vẫn thiếu giấy | Quản lý báo **giấy không kịp**, chọn nguyên nhân, đơn nhả chỗ, **Cần chọn lại chuyến** |
| Tới ngày đón vẫn thiếu giấy | Xe không xuất phát được; xe gom nhiều đơn thì chặn cả lượt tới khi gỡ đơn thiếu |
| Khách tự chở tới hub mà thiếu giấy | Không nhận ngựa |
| Tới cửa khẩu thiếu giấy riêng của cửa khẩu | Xe chưa qua được; mở sự cố loại Giấy tờ (luồng 5) [Mới] |

Nguyên nhân giấy không kịp quyết định quyền lợi khách (bảng hủy ở luồng 1): khách chậm, công ty chậm, cơ quan chậm.

---

# Luồng 3. Lên kế hoạch xe

Từ lúc đơn đã cọc tới lúc mỗi con có đủ xe, tổ lái, vị trí khoang cho mọi chặng. Đơn vẫn **Đã xác nhận**.

## Nghiệp vụ chính

1. **Lịch chạy và số xe hub giữ cho trục** [Mới]
   - Mỗi tuyến có lịch cố định. Ví dụ TP.HCM → Hà Nội chạy thứ Tư, Hà Nội → TP.HCM chạy thứ Hai.
   - Từ lịch, hệ thống tính **mỗi hub phải giữ bao nhiêu xe để chạy hết các chuyến trục xuất phát từ hub đó**. Một xe chạy xong chuyến phải **nghỉ ít nhất 12 giờ** (cấu hình) mới chạy chuyến tiếp. Ví dụ: TP.HCM → Hà Nội mất gần 3 ngày, xe đi thứ Tư chưa kịp về cho chuyến thứ Tư tuần sau, nên tuyến này cần 2 xe.
   - Cộng thêm **xe dự phòng**: 20% (cấu hình), làm tròn lên, để thay xe hỏng hoặc chở thêm khi đông khách.
   - Tổng hai phần là **số xe giữ cho trục** của hub. Quản trị lưu lịch mà hub không đủ xe thì hệ thống cảnh báo. Các xe còn lại của hub chạy nhánh đón và giao, không lấy xe giữ cho trục.
2. **Mỗi chuyến bán bao nhiêu chỗ** [Mới]
   - Lịch ghi sẵn **xe nền** của mỗi chuyến, ví dụ "1 xe 9 chỗ". Chuyến mở bán đúng số chỗ của xe nền.
   - Khi khách gửi đơn mà xe nền đã đầy, hệ thống **tự gắn một xe để sẵn** còn rảnh của hub xuất phát vào chuyến rồi nhận đơn, báo quản lý và điều phối. Khách không phải chờ ai duyệt.
   - Hết xe để sẵn thì chuyến **Hết chỗ**, khách chọn chuyến khác.
   - Khách không thấy con số chỗ, chỉ thấy **Còn chỗ**, **Sắp đầy** (xe nền đã đặt từ 80% và hết xe để sẵn) hoặc **Hết chỗ**. Số xe, người và lịch theo [QUY_CHE_DOI_XE.md](QUY_CHE_DOI_XE.md).
3. **Quản lý quyết định chuyến** trong 48 giờ cuối: **Chạy**, **Hủy** hoặc **Dồn** sang chuyến khác cùng tuyến.
   - Số chỗ đã cọc dưới 30% số chỗ đang bán thì cảnh báo, quyết định vẫn là của quản lý.
   - Khi bấm Chạy, quản lý **bỏ bớt xe thừa** được nếu số chỗ đã bán vẫn đủ. Ví dụ đã bán 8 chỗ trên hai xe 9 và 4 chỗ thì bỏ xe 4 chỗ.
4. **Nhánh đón:** xe của **hub phụ trách địa chỉ đón** đi đón. Hệ thống gợi ý gom các đơn cùng hướng vào một xe. Điều phối chọn xe, xem giờ theo đường xe tải thật và lập lượt. Ngựa phải về hub trước hạn có mặt.
5. **Chia ngựa vào các xe trục ngày đi**
   - Dùng các xe đã giữ cho chuyến (xe nền và xe thêm). Điều phối đổi được loại xe, miễn tổng chỗ không ít hơn số chỗ đã bán.
   - Hệ thống gợi ý chia theo thứ tự: (1) ngựa **cùng đơn** luôn chung xe, vì giấy gốc chỉ có một bản; (2) ngựa **xe riêng** đi xe riêng của đơn đó, chọn hạng xe nhỏ nhất còn rảnh và đủ chỗ (2, 6 hoặc 9 chỗ); (3) ngựa xe ghép xếp theo **cụm điểm giao gần nhau ở hub đích**; (4) chọn xe **vừa số chỗ** nhất.
   - Ví dụ chuyến TP.HCM → Hà Nội có 11 con xe ghép: 8 con giao trong nội thành Hà Nội, 3 con giao ở Hải Phòng. Gợi ý xe 9 chỗ chở 8 con nội thành, xe 6 chỗ chở 3 con Hải Phòng.
   - Tới hub đích ngựa vẫn dỡ xuống và khám trước khi giao (luồng 4). Chia theo cụm từ đầu để mỗi xe trục có thể dùng lại làm xe giao cho đúng cụm của nó.
   - Với chuyến đã xác nhận chạy, điều phối xem gợi ý, chọn biển số theo hạng xe và lập đồng thời các lượt trục. Gợi ý chưa ghi dữ liệu. Nếu thiếu chỗ để xếp trọn đơn, hệ thống báo số chỗ chưa xếp được.
6. **Phân tổ lái** từng xe: tài xế, hộ tống. Người được phân xác nhận nhận việc; chưa có tài xế và hộ tống nhận việc thì xe không xuất phát.
7. **Sơ đồ khoang:** khách chỉ chọn loại khoang; hệ thống tự xếp vị trí, điều phối đổi được; xe chạy thì khóa.
8. **Nhánh giao:** ở hub đích, ngựa có điểm giao gần nhau xếp chung xe; xe riêng không ghép. Ngoài khung 06:00–18:00 thì cả lượt dời sang 06:00 hôm sau.
9. Khách được báo khung một giờ xe tới; giờ dời quá 30 phút thì báo lại.
10. **Xe về** (M6)
   - Lượt đang chạy hiện **Đang về** khi mọi chặng ngựa đã xử lý xong và xe chưa tới điểm dừng cuối. Ngựa giao hụt chưa được bàn giao vào hub vẫn tính là còn trên xe.
   - Giờ về dự kiến lấy giờ đến kế hoạch của điểm dừng cuối, cộng độ trễ dương của giờ rời điểm gần nhất đã rời. Qua giờ dự kiến quá `RETURN_LATE_MINUTES` (mặc định 30 phút) thì hiện **Trễ**. Khi xe đã tới hub, giờ tới thực tế xác định cờ trễ trong lúc chờ xác nhận. Hệ thống kiểm tra mỗi 5 phút và báo điều phối phụ trách một lần nếu xe vẫn đang về.
   - Tài xế bấm **Đã về hub** tại hub cuối của lượt, kèm **ảnh chụp tại chỗ** và **tình trạng xe**. Xe trục có thể chạy chuyến chiều ngược sau khi xác nhận ở hub cuối chuyến đi.
   - Điều phối có thể lập lịch và phân tổ lái trước. Xe, tài xế và hộ tống còn lượt đang chạy thì chưa được xuất phát lượt kế tiếp.

**Quy tắc giờ:** mỗi điểm dừng 20 phút + 10 phút mỗi con; lái 4 giờ nghỉ 30 phút; tài xế lái ≤ 10 giờ một ngày; ngựa ngồi xe đón, giao ≤ 8 giờ, chạy thẳng ≤ 10 giờ. Nếu ngựa ngồi xe liên tục quá 4 giờ, hệ thống chèn trạm nghỉ đang hoạt động với quãng đường đi thêm ít nhất và tính lại giờ; trục chạy đủ 10 giờ thì nghỉ đêm 10 giờ. Lượt mới của xe và của người bắt đầu ở nơi lượt trước kết thúc.

## Xử lý ngoại lệ

| Tình huống | Hệ thống |
|---|---|
| Lịch trục cần nhiều xe hơn hạn mức của hub | Báo quản trị khi lưu lịch hoặc tuyến [Mới] |
| Chuyến đông hơn xe nền | Hệ thống tự gắn xe để sẵn lúc nhận đơn; hết thì chuyến **Hết chỗ** [Mới] |
| Chuyến vắng | Bớt xe trước giờ chốt nếu số chỗ đã bán vẫn vừa [Mới] |
| Ngựa vượt giới hạn giờ ngồi xe, không có trạm nghỉ phù hợp, hoặc tới hub sau hạn có mặt | Chặn lập lượt, đề nghị tách lượt hoặc đi sớm hơn |
| Xe đang ở hub khác | Từ chối lượt bắt đầu sai chỗ, báo xe đang ở đâu |
| Xếp trục khi chưa xếp đón | Từ chối, nhắc thứ tự đón → trục → giao |
| Tài xế đã lái gần đủ giờ trong ngày | Từ chối, nói sẽ lái bao nhiêu giờ |
| Hai con khoang rộng không còn hai vị trí liền nhau | Tự dồn; không được thì nêu con vướng |
| Bỏ một lượt chưa chạy | Gỡ hết ngựa rồi hủy, bắt buộc lý do, báo tổ lái |
| Xe về trễ quá ngưỡng cấu hình | Cờ trễ cho tài xế, điều phối và quản lý. Báo điều phối phụ trách một lần khi xe còn đang về |

**Chuyến trễ** (theo cách hàng không): 24 giờ trước chưa quyết thì báo đỏ; tới giờ chưa chạy thì ghi **Chậm** và báo khách; quản lý nhập giờ mới; trễ từ 2 giờ khách đổi chuyến miễn phí; từ 5 giờ được hoàn 100%; 5 giờ sau giờ gốc không có giờ mới thì tự hủy chuyến.

---

# Luồng 4. Chạy xe và theo dõi sức khỏe ngựa

Từ lúc xe đầu tiên xuất phát tới lúc lượt cuối kết thúc. Giao nhận chi tiết ở luồng 6, sự cố ở luồng 5.

## Nghiệp vụ chính

1. **Xuất phát:** kiểm tổ lái đã nhận việc, mọi con có vị trí khoang, đủ giấy (nếu đón ở nhà khách).
2. Tài xế bấm **Tới nơi**, **Rời đi** theo từng điểm dừng, đúng thứ tự. Hộ tống thấy việc tại điểm ngay sau khi tài xế ghi đã tới. Hệ thống khóa **Rời đi** khi còn việc giao nhận hoặc chăm sóc tại điểm chưa hoàn tất.
3. **Ở hub xuất phát:** ngựa lưu trú, được cho ăn và **khám 2 lần mỗi ngày**.
4. **Khám trước khi chạy:** mỗi con một lần khám ngay trước khi lên xe trục. **Chưa khám thì không cho ra hub**.
5. **Mọi lượt xe:** tới mỗi trạm nghỉ cho uống, ăn, ghi **từng con** (sức khỏe, uống, ăn, nhiệt độ nếu đo). Nghỉ ít nhất 30 phút. Nghỉ đêm có thể gửi chuồng đối tác rồi nhận lại sáng hôm sau.
6. **Cửa khẩu**: dừng làm thủ tục và kiểm dịch, kiểm giấy riêng của cửa khẩu, rồi **xe đi tiếp tới hub nước ngoài**.
7. **Tới hub đích:** dỡ ngựa vào hub theo quy trình, lưu trú, cho ăn, khám 2 lần mỗi ngày.
8. **Khám trước khi giao:** mỗi con một lần khám trước khi lên xe giao hoặc được người nhận tới hub lấy. **Chưa khám thì không cho ra hub**.
9. Thay hộ tống giữa đường: người mới tới điểm dừng, quét chip và ký nhận ca.
10. **Đã về hub** khi xe đã tới điểm dừng cuối và không còn ngựa trên xe. Tài xế tải ảnh xe tại hub rồi xác nhận tình trạng xe (Bình thường hoặc Cần bảo dưỡng). Lượt chuyển sang hoàn thành sau khi xác nhận. Xe cần bảo dưỡng phải có ghi chú.
11. **Theo dõi:**
    - điều phối: trang Theo dõi, con trễ khám tô đỏ;
    - **quản lý: màn Các hub** [Mới], mỗi hub có ngựa đang ở, ô chuồng trống, xe (rảnh, đang chạy, đang về, cần bảo dưỡng), nhân viên, chuyến đến và đi, sự cố mở;
      - Thẻ hub báo đủ hoặc thiếu. Bảng **Theo quy chế đội xe** so sánh số cần với số đang có theo hub gốc, tính xe `ACTIVE` và `MAINTENANCE` theo vai trò và nhân viên `ACTIVE` theo vai trò.
      - Dùng lịch và tuyến đang hoạt động, còn hiệu lực ở ngày xem. Xe trục tính theo số xe bận đồng thời lớn nhất khi đi, nghỉ, chờ chuyến về và nghỉ tiếp. Nếu chưa có lịch về, dùng thời gian chiều đi cho cả hai lượt. Hub đầu chiều đi giữ xe để sẵn khi sở hữu đủ số cặp tuyến theo tham số.
      - Xe nhánh 6 chỗ tính theo ngày bận nhất, chỉ cộng các chuyến xuất phát từ hub. Xe 2 chỗ, dự phòng nhánh và số tài xế, hộ tống tính theo tham số quy chế.
    - khách: dòng thời gian **theo từng con** (chặng, lưu trú, giao nhận, sức khỏe);
    - người không có tài khoản: tra bằng mã đơn và 4 số cuối điện thoại người gửi.

**Hạn khám:** trên xe 4 giờ, ở hub hoặc chuồng 12 giờ (cấu hình). Việc khám trước khi ra hub được tạo trước giờ rời dự kiến tối đa `PRE_EXIT_CHECK_HOURS` giờ (mặc định 12) và có hạn đúng giờ rời dự kiến. Quá hạn thì tô đỏ Trễ khám.

## Xử lý ngoại lệ

| Tình huống | Hệ thống |
|---|---|
| Đơn trên lượt đón thiếu giấy, hoặc tài xế chưa nhận việc | Chặn xuất phát |
| Chưa rời điểm trước mà bấm Tới nơi điểm sau | Từ chối |
| Quên ghi ăn uống một con ở trạm | Không cho rời trạm, nêu tên ngựa |
| Cần rời trạm khi chưa nghỉ đủ | Bắt ghi lý do, cho đi, lưu nhật ký |
| Ngựa bỏ uống hai lần liền | Tự nâng **Cần theo dõi**, báo điều phối |
| Ghi Ốm hoặc Nguy cấp | Báo ngay điều phối |
| Sáng ra quên nhận lại một con ở chuồng | Không cho rời trạm |
| Chưa khám trước khi chạy hoặc trước khi giao | Không cho ra hub |
| Hộ tống ốm giữa đường | Điều phối thêm hộ tống mới, người mới nhận ca tại điểm dừng |
| Kết thúc lượt khi còn ngựa trên xe | Từ chối |
| Kết thúc lượt khi chưa có ảnh xe tại hub | Từ chối và yêu cầu chụp ảnh |
| Xuất phát khi xe hoặc người trong tổ lái còn lượt đang chạy | Từ chối và nêu mã lượt chưa kết thúc |
| Xuất phát sớm hơn giờ kế hoạch | Tài xế xác nhận, ghi lý do, báo điều phối |

---

# Luồng 5. Xử lý sự cố

Từ lúc tổ lái báo chuyện bất thường tới lúc sự cố đóng và chi phí được duyệt. Trạng thái đơn **không đổi** vì sự cố.

> Mới báo → Chờ duyệt phương án → Đang xử lý → Đã xử lý → Đã đóng

## Nghiệp vụ chính

1. Tài xế hoặc hộ tống của lượt bấm **Báo sự cố**: cả lượt hoặc một con; loại (Sức khỏe, Hỏng xe, Tai nạn, Chậm trễ, Giấy tờ, Khác); nguyên nhân đề xuất; mô tả; ảnh hiện trường.
2. Điều phối lập **phương án**: Đi tiếp, Thú y điều trị, Gửi chuồng gần, Đổi xe, Về hub, Hủy chặng. Ghi chỉ đạo, chi phí dự kiến, bên xử lý.
3. Quản lý **duyệt** (chốt nguyên nhân) hoặc **từ chối** kèm lý do.
4. Tổ lái thực hiện, ghi chi phí kèm biên lai.
5. Sự cố sức khỏe hoặc tai nạn của một con: hộ tống cho ngựa nghỉ ít nhất 30 phút, chụp ảnh, xác nhận **Đủ sức đi tiếp**.
6. Điều phối bấm **Đã xử lý xong**, quản lý duyệt chi phí, điều phối **đóng sự cố**. Còn sự cố mở thì đơn không quyết toán được.

**Ai chịu chi phí:** lỗi công ty, bất khả kháng: công ty. Tình trạng ngựa, giấy của khách: khách. Người nhận vắng: người nhận. Khoản khách chịu cần khách đồng ý hoặc nằm trong hạn mức chi khẩn cấp.

## Xử lý ngoại lệ

| Tình huống | Hệ thống |
|---|---|
| Đã có sự cố cả lượt chưa xử lý mà báo thêm sự cố cả lượt | Từ chối; báo sự cố một con thì được |
| Thú y hoặc chuồng không chọn đối tác, hoặc đối tác hết hiệu lực | Từ chối |
| Quản lý duyệt phiên bản cũ khi đã có phiên bản mới | Không cho |
| Xác nhận đủ sức khi chưa đủ 30 phút | Từ chối |
| Khoản khách chịu vượt hạn mức khẩn cấp, chưa hỏi khách | Từ chối; phải có khách đồng ý |
| **Xe hỏng:** đổi xe | Tổ xe tải biên bản; điều phối chọn xe thay đang rảnh, đủ ngăn. Xe cũ sang bảo dưỡng, báo các lượt sắp tới của xe đó |
| Sự cố Nặng chưa có phương án | Khóa Tới nơi, Rời đi tới khi duyệt |
| **Không gặp người nhận:** chờ miễn phí 120 phút | Hết giờ: giao thất bại, mỗi con một sự cố, xe đưa ngựa về hub, giữ có phí tối đa 3 ngày (1.000.000 đ/ngày, số mẫu), nhắc 8:00 mỗi ngày, lập chặng giao lại |
| Tài xế quên bấm Không gặp người nhận | Hệ thống tự ghi trong 5 phút sau khi hết 120 phút |
| Ngựa phải về hub vì lý do khác | Tập kết không phí, chờ lập chặng mới |
| Chuyến chậm | Theo cách xử lý chuyến trễ ở luồng 3 |

---

# Luồng 6. Giao nhận và quyết toán

Từ lúc tổ lái đứng trước con ngựa ở nơi nhận tới lúc đơn **Hoàn thành** và khách đánh giá.

> Đã xác nhận → Đang vận chuyển → Đã giao → Hoàn thành

## Nghiệp vụ chính

1. **Nhận ngựa ở nhà khách:** hộ tống quét chip từng con (gõ tay phải ghi lý do), khám và ghi tình trạng, chụp ảnh từng con, đánh dấu giấy gốc đã nhận, tải **biên bản đã ký** và hoàn tất.
2. **Số dư:** lập ngay sau lần nhận đầu tiên. Khách có **30 phút** để trả qua VNPAY. Xe chỉ rời khi mọi đơn lên xe ở đây đã trả đủ. Xe rời thì đơn **Đang vận chuyển**, khách hết quyền tự hủy.
3. **Vào hub, ra hub:** quét chip từng con, mở và đóng lưu trú. Tự chở hoặc tự tới lấy tại hub thì điều phối giao nhận.
4. **Giao người nhận:** hộ tống quét chip, ghi tình trạng và chụp ảnh từng con, trả giấy gốc, tải biên bản đã ký, ghi tên người nhận và nhập **mã nhận ngựa 6 số** (hoặc xác nhận của chủ đơn).
5. **Đơn quốc tế**: giao nhận ở hub và nơi giao nước ngoài **y như trong nước**. Không còn bàn giao đại lý ở cửa khẩu và nút "đại lý đã giao".
6. Con cuối cùng được giao thì đơn **Đã giao**.
7. **Quyết toán:** quản lý duyệt chi phí, lập **khoản thu thêm** cho phần khách chịu; khách trả trong trang đơn.
8. **Đóng đơn:** quản lý đóng khi đã trả đúng phần báo giá, không còn sự cố hay khiếu nại mở, không còn khoản hoàn chờ. Hệ thống tự đóng sau **7 ngày** từ lúc Đã giao (cấu hình). Khoản thu thêm chưa trả không chặn đóng.
9. **Đánh giá** 1–5 sao, một lần. **Khiếu nại** sau giao.

## Xử lý ngoại lệ

| Tình huống | Hệ thống |
|---|---|
| Một con sai chip | Con đúng được nhận, con sai không lên xe |
| Không con nào đúng chip, hoặc chưa tải biên bản | Không hoàn tất được |
| **Đón hụt** (ngựa không đủ điều kiện, người gửi vắng) | Ghi lý do, ảnh, kết quả gọi. Giữ chỗ, gỡ chặng kế hoạch. Quản lý chốt bên chịu. Khách chọn: A bỏ con, B đón lại, C tự chở tới hub, D đổi chuyến (nếu chưa nhận con nào). Báo giá thay thế tự tính lại |
| **Quá 30 phút chưa trả số dư** | Nhắc ở phút 0, 15, 25; điều phối gọi và có thể gia hạn một lần. Hết hạn: trả ngựa tại chỗ kèm giấy gốc, đơn **Cần chọn lại chuyến**; không có người nhận lại thì đưa về hub. Tại hub, T − 1 giờ chưa trả thì nhả chỗ, phí giữ tính từ T |
| Khách trả ở phút 40, tổ lái chưa trả ngựa | Nhận tiền, xe được rời |
| Khách chở tới hub khi chưa xếp lượt trục, hoặc thiếu giấy | Không nhận |
| Người nhận tự tới hub quá giờ hẹn | Gửi chuồng, người nhận chịu phí |
| Đã trả số dư rồi một con bị loại | Thay báo giá, tạo khoản hoàn phần dư |
| Đóng đơn khi còn nợ hoặc còn sự cố mở | Từ chối |
| Đơn không phát sinh, quản lý quên đóng | Tự đóng lúc 00:30 sau 7 ngày |
| Đánh giá lần hai | Từ chối |

---

# Còn mở, chờ chốt

1. **Giấy phép liên vận của xe** qua Campuchia, Lào: có thêm ô "được chạy sang nước nào, hạn tới ngày" để chỉ gán xe có phép cho chuyến quốc tế không. Điều kiện cấp phép **chưa kiểm chứng**.
2. ~~Ai tăng cường xe~~: **đã chốt 26/11** hệ thống tự gắn xe để sẵn khi xe nền đầy, quản lý chỉ bỏ bớt xe thừa.
3. **Chuyến đột xuất** do quản lý tạo: đã bỏ (chốt 28/11/2026), vì tăng cường và xe riêng đã thay được.
4. **Giá xe riêng** theo nguyên xe đã giữ. Cước trục = sức chứa xe × đơn giá khoang STANDARD trên tuyến, không dùng đơn giá WIDE. Hạng xe vẫn phải đủ chỗ theo khoang thực tế đã chọn nên đổi khoang có thể làm đổi hạng xe và tổng cước. Cước đón và giao tính theo km với hạng xe riêng.
5. **Tỷ lệ dự phòng 20%** là mức tham khảo của ngành xe buýt (FTA), cần chỉnh khi có số liệu vận hành.
6. Nguyên nhân sự cố **"Lỗi đại lý"** không còn ý nghĩa khi hub nước ngoài là của công ty: đề xuất gộp vào Lỗi công ty.
