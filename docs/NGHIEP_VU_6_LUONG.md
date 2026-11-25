# Nghiệp vụ theo 6 luồng

Mô tả bằng lời, đi từng bước theo một đơn. Nội dung đối chiếu với **code đang chạy** trên `redesign/system-v2` ngày 25/11/2026. Chi tiết bảng và cột: [NGHIEP_VU_THEO_59_BANG.md](NGHIEP_VU_THEO_59_BANG.md).

| Luồng | Nội dung | Tình trạng bản viết |
|---|---|---|
| 1 | Tạo và duyệt đơn | Bản 2 |
| 2 | Thủ tục hồ sơ pháp lý | Bản 1 |
| 3 | Lên kế hoạch lộ trình | Bản 1 |
| 4 | Cập nhật trạng thái chuyến đi và nhật ký sức khỏe ngựa | Bản 1 |
| 5 | Xử lý sự cố | Bản 1 |
| 6 | Bàn giao và nghiệm thu | **Bản 1, mới** |

**Cách đọc.** Mỗi luồng gồm: *Đi từng bước* (kể theo một đơn), *Trục thời gian* (mọi hạn trên một ví dụ có ngày cụ thể), *Các điểm nghiệp vụ* (đánh số để trích dẫn), *Tình huống*, *Điểm còn hở*.

- Mỗi hạn đều ghi đủ: **hạn của ai**, **đếm từ đâu**, **quá hạn thì sao**.
- Số ghi **(cấu hình)**: quản trị sửa được, không phải sửa code. Số ghi **(số mẫu)**: dữ liệu mẫu, chưa phải giá thật.

## Từ dùng chung

| Từ | Nghĩa |
|---|---|
| **Hub** | Trung tâm tập kết ngựa của công ty. Hiện có Trụ sở TP.HCM và hub vệ tinh Đà Nẵng. Mỗi hub phục vụ một nhóm tỉnh |
| **Chạy thẳng** | Xe đón ở nhà khách rồi giao thẳng, không qua hub. Dùng khi hai đầu chung một hub |
| **Đi trục** | Ngựa được gom về hub, đi **chuyến trục** tới hub hoặc cửa khẩu kia, rồi giao |
| **Chuyến** | Một lần chạy của tuyến trục vào một ngày, ví dụ TP.HCM – Đà Nẵng ngày 08/12 |
| **Giờ chạy** | Giờ xe trục rời điểm đầu của chuyến. **Mọi hạn "trước giờ chạy" đều đếm lùi từ giờ này** |
| **Hạn nhận đặt** | Giờ chuyến ngừng nhận đơn mới, 72 giờ trước giờ chạy |
| **Mốc quyết định** | 48 giờ trước giờ chạy. Lúc công ty bắt đầu quyết định chuyến có chạy hay không |
| **Giữ chỗ** | Trừ chỗ trên chuyến cho đơn. Đơn giữ chỗ từ lúc gửi, chưa cần trả tiền |
| **Thu hộ** | Tiền công ty thu thay bên khác: lệ phí nhà nước, phí bảo hiểm mua hộ |
| **Vận đơn** | Số chứng từ vận chuyển, cấp khi khách trả xong tiền cọc |

Đơn **chạy thẳng không có chuyến**, nên không có hạn nhận đặt, mốc quyết định và giữ chỗ.

---

# Luồng 1. Tạo và duyệt đơn

**Từ lúc** khách mở tài khoản **đến lúc** khách trả cọc, đơn được xác nhận và cấp vận đơn.
**Người tham gia:** khách hàng, chuyên viên, điều phối viên, quản lý.

Đường đi chính của một đơn:

> Chờ tiếp nhận → Đang thẩm định → Đang báo giá → Chờ đặt cọc → **Đã xác nhận**

Các nhánh phụ: Cần bổ sung, Báo giá hết hạn, Cần chọn lại chuyến, Bị từ chối, Đã hủy.

## 1.A. Đi từng bước

### Bước 1. Khách chuẩn bị

- Để bắt đầu, khách hàng đăng ký tài khoản bằng Gmail, số điện thoại và đồng ý Điều khoản vận chuyển.
- Hệ thống gửi **mã xác thực** về email. Khách có **10 phút** để nhập; quá giờ hoặc nhập sai 5 lần thì mã hỏng, phải xin mã mới. Chưa xác thực thì chưa đăng nhập được.
- Khách khai **hồ sơ ngựa**: tên, mã chip, giới tính, loại ngựa, năm sinh, cân nặng, giá trị khai báo. Hồ sơ dùng lại cho mọi đơn sau.
- Khách lưu **sổ địa chỉ**: chọn tỉnh thì bản đồ phóng về tỉnh đó, khách ghim đúng vị trí trại. Vị trí này dùng để tính km và tìm hub.

### Bước 2. Khách đặt đơn (4 màn)

- **Màn 1, nơi đón và nơi giao.** Khách chọn hai địa chỉ trong sổ. Khách không chọn loại đơn: hệ thống suy ra từ hai địa chỉ (trong tỉnh, liên tỉnh, xuất, nhập).
- Hệ thống tìm hub phục vụ từng địa chỉ. Hai đầu **chung hub** thì đơn chạy thẳng, khách chỉ chọn ngày đón. **Khác hub** hoặc đi nước ngoài thì đơn đi trục, khách chọn một chuyến trong danh sách.
- Danh sách chỉ có chuyến **còn nhận đặt**, tức còn hơn 72 giờ nữa mới tới giờ chạy. Mỗi chuyến hiện giờ chạy, số chỗ còn và hạn nhận đặt.
- Chuyến nào **không đủ ngày làm giấy** thì hiện nhưng bị khóa. Ví dụ đơn liên tỉnh cần 5 ngày làm giấy: từ hôm nay tới hạn nhận đặt của chuyến phải còn ít nhất 5 ngày thì khách mới chọn được.
- Với đơn đi trục, khách chọn **xe công ty đến đón** hay **tự chở tới hub**; **xe công ty giao** hay **tự đến hub nhận**. Hệ thống tính đường thật; quá xa thì khóa lựa chọn xe công ty và nói lý do.
- Khách chọn **ngày đón**, muộn nhất là một ngày trước ngày chuyến chạy, để ngựa kịp về hub nghỉ đêm. Màn 1 cũng liệt kê giấy khách phải nộp.
- **Màn 2, ngựa.** Khách chọn ngựa, loại khoang từng con (thường hoặc rộng), số bữa, chỉ dẫn ăn uống, có gửi kèm thức ăn hay không. Ngựa đang nằm trong đơn khác thì bị khóa, ghi rõ mã đơn.
- **Màn 3, dịch vụ và bảo hiểm.** Khách chọn dịch vụ thêm và cách bảo hiểm cho **từng con**: đã có, nhờ công ty mua, hoặc không mua. Không mua thì phải tích đồng ý điều khoản trách nhiệm hạn chế cho con đó.
- **Màn 4, xem lại và gửi.** Khách khai bốn vai: người gửi, người nhận, người trả tiền, liên hệ khẩn cấp. Có thể ghi lưu ý đường vào và **hạn mức chi khẩn cấp** (khi ngựa gặp sự cố trên đường, công ty được chi tới mức này mà không cần hỏi trước). Đồng ý điều khoản rồi gửi đơn.
- Suốt 4 màn, khách luôn thấy **giá tạm tính** do máy chủ tính, và bản nháp được giữ khi quay lại màn trước.
- Khi đơn được gửi, hệ thống **giữ chỗ ngay** trên chuyến, đặt trạng thái **Chờ tiếp nhận**, báo khách "Đã nhận đơn" và báo mọi chuyên viên.

### Bước 3. Công ty tiếp nhận và thẩm định

- Khi đơn được gửi, chuyên viên (hoặc quản lý) bấm **Tiếp nhận**, đơn sang **Đang thẩm định**; hoặc **Từ chối** kèm lý do.
- Chuyên viên kiểm giấy khách nộp và bảo hiểm từng con (chi tiết ở luồng 2).
- Song song, điều phối viên xem chuyến đơn đang giữ chỗ và có thể đổi đơn sang chuyến khác; khách được báo.
- Thiếu hồ sơ thì chuyên viên **trả lại để bổ sung** kèm lý do, đơn sang **Cần bổ sung**. Khách nộp thêm và gửi lại, đơn quay về Đang thẩm định; người đã trả đơn được báo.
- Đủ điều kiện thì chuyên viên bấm **Bắt đầu báo giá**, đơn sang **Đang báo giá**.
- Cả bước 3 và bước 4 có **một hạn chung: mốc quyết định, 48 giờ trước giờ chạy**. Công ty phải **gửi xong báo giá trước mốc này**. Quá mốc mà đơn vẫn chưa được gửi báo giá thì hệ thống tự nhả chỗ, đơn sang **Cần chọn lại chuyến**; khách chọn chuyến khác hoặc hủy miễn phí. Lý do: từ mốc này công ty phải chốt số ngựa để quyết định chạy, không chờ đơn chưa có giá.

### Bước 4. Quản lý báo giá

- Quản lý bấm **Lập bản nháp**. Hệ thống tự tính từng dòng theo bảng giá đang hiệu lực và chốt lịch: ngày đón, ngày xuất phát, ngày giao dự kiến.
- Quản lý không sửa giá từng dòng, chỉ thêm **dòng điều chỉnh** tăng hoặc giảm kèm lý do.
- Quản lý bấm **Gửi khách**. Báo giá khóa lại, đơn sang **Chờ đặt cọc**, khách được báo. Từ giây phút gửi, **khách có 24 giờ** để vừa chấp nhận vừa trả cọc.

### Bước 5. Khách trả lời và đặt cọc

- Trong 24 giờ đó, khách xem báo giá và **Chấp nhận** hoặc **Từ chối** kèm lý do.
- Từ chối thì đơn quay về **Đang báo giá**, vẫn giữ chỗ, quản lý lập bản mới và khách có 24 giờ mới. Riêng khi lúc đó đã qua mốc quyết định (48 giờ trước giờ chạy), đơn bị nhả chỗ như nêu ở bước 3.
- Chấp nhận thì hệ thống tạo **khoản cọc**. Khách vẫn phải trả trong **cùng 24 giờ ấy**, không được cộng thêm.
- Khách bấm trả, hệ thống mở trang VNPAY. Mỗi lần mở có **15 phút** để trả; hết 15 phút thì bấm mở lại, miễn còn trong 24 giờ.
- Khi cổng thanh toán báo đã nhận đủ, đơn sang **Đã xác nhận** và được cấp **số vận đơn**. Luồng 1 kết thúc.
- Hết 24 giờ mà chưa trả cọc thì đơn sang **Báo giá hết hạn**, nhả chỗ.

### Ví dụ tiền, từ đơn đã chạy thật

Hai con ngựa từ Củ Chi đi Điện Bàn, khoang thường, không mua bảo hiểm, chuyến TP.HCM – Đà Nẵng **(số mẫu)**:

| Khoản | Cách tính | Tiền |
|---|---|---:|
| Cước đón | 54 km × 26.250 đ | 1.417.500 |
| Cước giao | 27 km × 31.500 đ | 850.500 |
| Cước trục | 2 khoang × 12.600.000 đ | 25.200.000 |
| Nhiên liệu, cầu đường | 81 km × 9.450 đ | 765.450 |
| Thủ tục kiểm dịch | 1 đơn | 300.000 |
| Lệ phí nhà nước (thu hộ) | 1 đơn | 200.000 |
| **Tổng** | | **28.733.450** |
| **Cọc**, trả ở bước 5 | 30% × 28.533.450 + 200.000 thu hộ, làm tròn lên nghìn | **8.761.000** |
| Số dư, trả sau khi công ty nhận ngựa (luồng 6) | Tổng trừ cọc | 19.972.450 |

---

## 1.B. Trục thời gian của một đơn đi trục

Ví dụ chuyến TP.HCM – Đà Nẵng chạy **05:00 thứ Ba 08/12**, khách gửi đơn ngày 23/11, quản lý gửi báo giá 09:00 ngày 25/11.

| Thời điểm | Còn bao lâu tới giờ chạy | Hạn của ai | Chuyện gì xảy ra |
|---|---|---|---|
| 13/10 | 8 tuần | Hệ thống | Chuyến được tạo và mở bán |
| 23/11 | | Khách | Gửi đơn, **giữ chỗ từ lúc này** |
| 23/11 → 25/11 | | Chuyên viên, quản lý | Thẩm định và báo giá. Không có hạn riêng từng việc, nhưng phải gửi xong báo giá trước 05:00 06/12 |
| 25/11 09:00 | | Quản lý | Gửi báo giá |
| 26/11 09:00 | | **Khách** | **Hạn trả lời và trả cọc**: 24 giờ sau khi gửi báo giá |
| 30/11 | | Khách | Ngày cuối khách mới còn đặt được chuyến này: hạn nhận đặt 05/12 trừ 5 ngày làm giấy |
| 05/12 05:00 | 72 giờ | Khách | **Hạn nhận đặt**: chuyến ngừng nhận đơn mới, kể cả khách đổi chuyến sang đây |
| 06/12 05:00 | 48 giờ | **Công ty** | **Mốc quyết định**. Hai việc: (1) đơn chưa được gửi báo giá bị nhả chỗ; (2) quản lý được bấm **Chạy** cho chuyến, từ lúc này tới giờ chạy |
| 07/12 | 1 ngày | Khách | Ngày đón muộn nhất, ngựa về hub nghỉ đêm |
| 08/12 02:00 | 3 giờ | Công ty hoặc khách tự chở | Ngựa phải có mặt ở hub (luồng 3) |
| 08/12 05:00 | 0 | Công ty | Xe trục chạy |

Vì báo giá phải gửi trước mốc quyết định và sống tối đa 24 giờ, **hạn cọc muộn nhất có thể là 24 giờ trước giờ chạy** (với cấu hình hiện tại).

---

## 1.C. Các điểm nghiệp vụ

### Tài khoản và hồ sơ (bước 1)

- **1.1** Chỉ nhận Gmail. Số điện thoại phải thuộc Việt Nam, Campuchia hoặc Lào. Mật khẩu từ 6 ký tự.
- **1.2** Mã xác thực gồm 6 số. Dùng được trong **10 phút** kể từ lúc gửi (cấu hình), nhập sai tối đa **5 lần** (cấu hình). Muốn gửi lại phải chờ 60 giây từ lần gửi trước. Quá hạn hoặc sai quá số lần thì xin mã mới.
- **1.3** Mã chip đúng **15 chữ số** (chuẩn ISO 11784/11785), không trùng với ngựa khác, **không đổi được sau khi lưu**.
- **1.4** Giá trị khai báo của ngựa phải có cả số tiền và loại tiền, hoặc bỏ trống cả hai.
- **1.5** Khi lưu địa chỉ, vị trí ghim phải cách trung tâm tỉnh đã chọn không quá **200 km** (cấu hình), để không ghim nhầm tỉnh.

### Loại đơn và cách chạy (bước 2, màn 1)

- **1.6** Loại đơn do hệ thống suy ra:

| Hai địa chỉ | Loại đơn |
|---|---|
| Cùng một tỉnh | Trong tỉnh |
| Hai tỉnh Việt Nam | Liên tỉnh |
| Việt Nam → Lào, Campuchia | Xuất |
| Lào, Campuchia → Việt Nam | Nhập |
| Cả hai ở nước ngoài | Không nhận |

- **1.7** Cách chạy do **hub** quyết định, không do loại đơn. Ví dụ Đồng Nai → TP.HCM là liên tỉnh nhưng chung hub nên chạy thẳng. Mỗi tỉnh thuộc đúng một hub.
- **1.8** Mỗi đơn có **tối đa một chặng trục**. Vì vậy Campuchia chỉ đi và về qua hub TP.HCM (cửa khẩu Mộc Bài), Lào chỉ qua hub Đà Nẵng (cửa khẩu Lao Bảo). Đà Nẵng → Campuchia thì hệ thống báo chưa có tuyến.
- **1.9** Địa chỉ ở tỉnh chưa có hub: hệ thống nói đầu nào nằm ngoài vùng phục vụ và liệt kê các tỉnh đang phục vụ.
- **1.10** Khách chỉ được tự chở ở **đầu trong nước của đơn đi trục**. Đơn chạy thẳng, đầu đón của đơn nhập và đầu giao của đơn xuất luôn do công ty hoặc đại lý lo.
- **1.11** Xe công ty chỉ nhận đón hoặc giao khi **đường một chiều giữa địa chỉ và hub** không quá **5 giờ lái**, để xe đi và về trong giới hạn 10 giờ lái mỗi ngày của tài xế (cấu hình). Đơn chạy thẳng thì cả chặng không quá 10 giờ. Nếu bản đồ không trả lời được lúc đặt, hệ thống không chặn khách; quản lý sẽ bị chặn khi lập báo giá nếu thật sự quá xa.

### Chuyến và giữ chỗ (bước 2)

- **1.12** Mỗi tuyến chạy **một chuyến mỗi tuần** theo lịch. Hệ thống tự tạo chuyến và mở bán trước giờ chạy **8 tuần** (cấu hình). Ngoài lịch, quản lý có thể tạo **chuyến đột xuất**.
- **1.13** **Hạn nhận đặt** của mỗi chuyến là **72 giờ trước giờ chạy** (đặt trong lịch chạy của tuyến). Sau hạn này không ai đặt mới hay đổi sang chuyến đó được; đơn đã giữ chỗ từ trước không ảnh hưởng.
- **1.14** Khoang thường chiếm **1 chỗ**, khoang rộng chiếm **2 chỗ** trên chuyến. Chuyến mẫu có 9 chỗ. Khách tự chọn khoang, không bắt buộc theo giới tính ngựa.
- **1.15** Chỗ được **giữ ngay khi gửi đơn**, chưa cần cọc. Chỗ chỉ được nhả khi: đơn bị từ chối, bị hủy, hết hạn báo giá, đổi sang chuyến khác, hoặc bị nhả ở mốc quyết định. Hai khách đặt cùng lúc thì người tới sau nhận thông báo hết chỗ; không bao giờ vượt sức chứa.
- **1.16** **Thời gian làm giấy** đếm từ hôm khách gửi đơn tới **hạn nhận đặt** của chuyến (không phải tới giờ chạy): liên tỉnh **5 ngày**, xuất **21 ngày**, nhập **37 ngày** (số mẫu, đặt theo quy định từng nước). Không đủ thì không chọn được chuyến đó. Đơn liên tỉnh chạy thẳng tính đến ngày đón.
- **1.17** **Ngày đón** của đơn liên tỉnh đi trục và đơn xuất phải **trước ngày chuyến chạy ít nhất 1 ngày**. Khách muốn đưa ngựa tới sớm hơn 1 ngày đó thì tính **phí lưu chuồng** cho từng ngày thừa, theo con (800.000 đ, số mẫu). Đơn nhập thì ngày đón không được sau ngày chuyến chạy.
- **1.18** Một con ngựa chỉ nằm trong **một đơn chưa kết thúc**. Đơn kết thúc là: hoàn thành, hủy, bị từ chối, báo giá hết hạn. Ngựa phải đang hoạt động và thuộc khách đó.

### Giá và báo giá (bước 2 và bước 4)

- **1.19** **Máy chủ là nơi duy nhất tính tiền.** Khách chỉ gửi lựa chọn, không gửi số tiền. Giá tạm tính ở bước 2 và báo giá ở bước 4 dùng cùng một công thức.
- **1.20** Giá lấy từ **bảng giá đang hiệu lực vào ngày lập báo giá**. Cước đón, giao, trục, chạy thẳng cộng biên lợi nhuận **5%**; nhiên liệu cộng dự phòng **5%** (số mẫu, nằm trong bảng giá).
- **1.21** Km tính theo đường xe tải thật. Cước đón, giao, chạy thẳng tra theo hạng xe nhỏ nhất chở đủ số chỗ và theo bậc km (0–50, 50–200, trên 200).
- **1.22** Dòng **tự thêm**, khách không bỏ được: thủ tục kiểm dịch và lệ phí nhà nước với đơn không phải trong tỉnh; thú y tại chuồng khi nước đến bắt buộc xét nghiệm.
- **1.23** **Khoản thu hộ** tách riêng, không cộng lời. Phí bảo hiểm mua hộ lấy nguyên theo báo giá của hãng.
- **1.24** Ghép nhiều đơn lên cùng xe **không làm đổi giá** của ai.
- **1.25** Điều chỉnh giảm không được làm tổng thấp hơn phần thu hộ. Chỉ gửi được báo giá khi tổng và tiền cọc đều lớn hơn 0.
- **1.26** Nếu chuyến hoặc ngày đón của đơn đổi **sau khi lập nháp và trước khi gửi**, hệ thống bắt lập lại bản nháp.
- **1.27** Mỗi lần báo giá là một phiên bản. Gửi bản mới thì bản cũ hết tác dụng và khoản cọc cũ chưa trả bị hủy. Mỗi đơn chỉ có một báo giá được chấp nhận.

### Hạn trả lời và đặt cọc (bước 5)

- **1.28** **Khách chỉ có một hạn cho cả hai việc: chấp nhận báo giá và trả cọc.** Hạn này đếm từ lúc quản lý bấm Gửi khách, là mốc **sớm nhất** trong ba mốc: (a) **24 giờ** sau khi gửi (cấu hình); (b) hết ngày đón đã chốt; (c) hết hiệu lực báo giá của hãng bảo hiểm, nếu nhờ công ty mua hộ.
- **1.29** **Tiền cọc = 30% cước dịch vụ + toàn bộ phần thu hộ**, làm tròn lên nghìn đồng, không vượt tổng. Tỷ lệ nằm trong bảng giá. Phần còn lại (số dư) khách trả sau khi công ty nhận ngựa, xem luồng 6.
- **1.30** Mỗi lần khách bấm trả cọc, trang VNPAY mở trong **15 phút** và không vượt hạn ở 1.28. Quá 15 phút thì bấm lại, không giới hạn số lần khi còn hạn.
- **1.31** Nếu khách trả **trước hạn** nhưng ngân hàng báo kết quả về muộn, hệ thống chờ thêm **5 phút sau hạn** để ghi nhận. 5 phút này chỉ để nhận kết quả, không phải gia hạn: trả **sau hạn** thì không được tính.
- **1.32** Tiền chỉ được ghi nhận khi chữ ký cổng đúng, số tiền khớp, đúng khoản thu. Cổng gửi trùng thì chỉ ghi một lần.

### Ai được làm gì

| Việc | Khách | Chuyên viên | Điều phối | Quản lý |
|---|:-:|:-:|:-:|:-:|
| Tạo đơn, nộp lại, chấp nhận hoặc từ chối báo giá, trả tiền | ✓ | | | |
| Tiếp nhận, từ chối đơn, trả lại để bổ sung | | ✓ | | ✓ |
| Bắt đầu báo giá | | ✓ | | |
| Đổi chuyến cho đơn đang thẩm định hoặc cần bổ sung | | | ✓ | |
| Lập, điều chỉnh, gửi báo giá | | | | ✓ |
| Tạo chuyến đột xuất, xác nhận chạy, hủy chuyến | | | | ✓ |
| Hủy đơn | ✓ (của mình) | | | ✓ |
| Ghi đã hoàn tiền | | | | ✓ |

## 1.D. Tình huống và cách hệ thống xử lý

| # | Ở bước | Tình huống | Hệ thống sẽ |
|---|---|---|---|
| T1 | 2, lúc bấm Gửi đơn | Chuyến vừa hết chỗ, hoặc vừa qua hạn nhận đặt (72 giờ trước giờ chạy) | Không tạo đơn, báo lý do, giữ bản nháp để khách chọn chuyến khác |
| T2 | 2, lúc bấm Gửi đơn | Một ô nhập sai | Báo ngay dưới đúng ô, bản nháp còn nguyên |
| T3 | 2, màn chọn ngựa | Con ngựa vừa vào một đơn khác | Báo bỏ chọn con đó, kèm mã đơn đang giữ |
| T4 | 3 | Công ty không nhận vận chuyển | Từ chối được khi đơn Chờ tiếp nhận hoặc Đang thẩm định, bắt buộc có lý do. Nhả chỗ, báo khách |
| T5 | 3 hoặc 4 | Tới **mốc quyết định (48 giờ trước giờ chạy)** mà đơn chưa được gửi báo giá, dù đang Chờ tiếp nhận, Đang thẩm định, Cần bổ sung hay Đang báo giá | Tự nhả chỗ trong vòng 15 phút, đơn sang **Cần chọn lại chuyến**, báo khách. Khách chọn chuyến khác hoặc hủy miễn phí |
| T6 | 5 | Tới mốc quyết định mà khách **đã nhận báo giá**, đang trong 24 giờ trả lời | Không bị nhả, vẫn giữ chỗ tới hết hạn trả lời |
| T7 | 5 | Hết hạn 24 giờ (1.28) mà khách chưa trả cọc, dù đã bấm Chấp nhận hay chưa | Đơn sang **Báo giá hết hạn**, nhả chỗ, ngựa được tự do để đặt đơn khác |
| T8 | 5 | Hết hạn đúng lúc khách đang ở trang VNPAY | Chờ thêm 5 phút nhận kết quả rồi mới cho hết hạn |
| T9 | 5, sau hạn | Tiền về sau khi đơn đã hết hạn hoặc đã hủy | Không khôi phục đơn. Lưu giao dịch, báo quản lý số tiền và mã giao dịch để hoàn; khách thấy "Đã nhận tiền, cần đối soát" |
| T10 | 5, khi khách từ chối báo giá lúc **đã qua mốc quyết định** | Đơn quay về Đang báo giá | Bị nhả chỗ như T5, vì lúc này không còn thời gian báo giá lại |
| T11 | Bất kỳ, trước khi nhận ngựa | Công ty hủy chuyến | Mọi đơn trên chuyến sang **Cần chọn lại chuyến**, nguyên nhân công ty. Khách đổi miễn phí hoặc hủy và hoàn 100%. Chuyến đã có đơn nhận ngựa thì không hủy được (luồng 5) |
| T12 | Cần chọn lại chuyến, đơn **đã cọc** | Khách chọn chuyến mới | Phải cùng tuyến và chạy trong **30 ngày** (cấu hình) tính từ ngày chạy của chuyến vừa lỡ thì được **giữ nguyên giá**. Chuyến mới phải còn chỗ, còn hạn nhận đặt, còn đủ ngày làm giấy; bảo hiểm tự có phải phủ ngày mới. Đơn về **Đã xác nhận** ngay |
| T13 | Cần chọn lại chuyến, đơn **chưa cọc** | Khách chọn chuyến mới | Đơn về **Đang thẩm định**, đi lại từ bước 3 |

### Hủy đơn và tiền hoàn

"Cần chọn lại chuyến" có ba nguyên nhân khi đơn **đã cọc** bị lỡ chuyến. Hủy chuyến thì hệ thống tự ghi nguyên nhân công ty; thiếu giấy thì quản lý chọn nguyên nhân: **công ty** (hủy chuyến hoặc công ty chậm giấy), **khách** (khách chậm giấy), **cơ quan** (cơ quan cấp giấy chậm). Đơn chưa cọc thì luôn hủy miễn phí.

| Đơn đang ở đâu | Khách tự hủy | Công ty hủy |
|---|---|---|
| Chưa cọc | Miễn phí | Được |
| Đã cọc, công ty chưa nhận ngựa | Được, **mất cọc** (gồm cả phần thu hộ) | Hoàn 100% |
| Công ty đã nhận ngựa | Không tự hủy được. Quá hạn trả số dư thì tổ xe trả ngựa tại chỗ, đơn bị hủy và mất cọc (luồng 6) | Không hủy, xử lý theo luồng 5 |
| Đã cọc, cần chọn lại chuyến do **công ty** | Hoàn 100% | Hoàn 100% |
| Đã cọc, cần chọn lại chuyến do **khách** | Mất cọc. Hoặc đổi chuyến: miễn phí 1 lần cho mỗi đơn | Hoàn 100% |
| Đã cọc, cần chọn lại chuyến do **cơ quan** | Đổi chuyến luôn miễn phí. Hủy ở lần lỡ đầu thì mất cọc; từ lần lỡ thứ **2** (cấu hình) thì hoàn cọc vận chuyển, trừ phần thu hộ | Hoàn 100% |

- **1.33** Trước khi khách xác nhận hủy, màn hình cho xem **số tiền được hoàn và số tiền mất**. Lý do hủy bắt buộc, tối đa 500 ký tự.
- **1.34** Hủy thì nhả chỗ ngay, hủy các khoản chưa trả, báo khách và điều phối viên.
- **1.35** Hoàn tiền **chuyển khoản ngoài hệ thống**: hệ thống tạo khoản hoàn chờ xử lý, quản lý chuyển tiền rồi ghi **mã chuyển khoản** (không trùng) và ảnh chứng từ nếu có. Hiện **không có hạn** phải hoàn trong bao lâu.

## 1.E. Bảng tra nhanh các hạn

| Hạn | Của ai | Đếm từ | Dài bao lâu | Quá hạn thì | Sửa ở đâu |
|---|---|---|---|---|---|
| Nhập mã xác thực (bước 1) | Khách | Lúc gửi mã | 10 phút, 5 lần sai | Xin mã mới | Cấu hình |
| Mở bán chuyến | Hệ thống | Lùi từ giờ chạy | 8 tuần | | Cấu hình |
| Đặt kịp làm giấy (bước 2) | Khách | Hôm đặt tới hạn nhận đặt | 5 / 21 / 37 ngày | Chuyến bị khóa, chọn chuyến sau | Quy định từng nước |
| Hạn nhận đặt (bước 2) | Khách | Lùi từ giờ chạy | 72 giờ | Không đặt, không đổi sang chuyến đó được | Lịch chạy |
| Mốc quyết định (bước 3, 4) | Công ty | Lùi từ giờ chạy | 48 giờ | Đơn chưa gửi báo giá bị nhả chỗ. Từ đây quản lý được xác nhận chạy | Cấu hình |
| Trả lời và trả cọc (bước 5) | Khách | Lúc gửi báo giá | 24 giờ, hoặc sớm hơn (1.28) | Báo giá hết hạn, nhả chỗ | Cấu hình |
| Một lần mở trang VNPAY (bước 5) | Khách | Lúc bấm trả | 15 phút | Bấm mở lại | Cài đặt máy chủ |
| Chờ kết quả ngân hàng (bước 5) | Hệ thống | Sau hạn trả cọc | 5 phút | Cho đơn hết hạn | Cài đặt máy chủ |
| Giữ giá khi đổi chuyến (T12) | Khách | Ngày chạy của chuyến vừa lỡ | 30 ngày | Không chọn được chuyến muộn hơn | Cấu hình |

## 1.F. Điểm còn hở, cần nhóm quyết

Tìm thấy khi đối chiếu code để viết luồng này:

1. **Báo giá hết hạn là ngõ cụt** (T7). Bảng trạng thái cho phép quay lại báo giá nhưng chưa có nút nào làm việc đó; khách phải đặt đơn mới. Hỏi: giữ vậy, hay cho quản lý mở lại nếu chuyến còn chỗ?
2. **Đã sửa.** Báo quản lý khi đơn sang Đang báo giá và khi khách chấp nhận hoặc từ chối báo giá. Báo khách khi báo giá hết hạn và khi hệ thống xác nhận đã nhận cọc.
3. **Đã sửa.** Khi đặt đơn liên tỉnh chạy thẳng, hệ thống kiểm số ngày làm giấy theo quy định đến ngày đón.
4. **Đã sửa.** Nút Từ chối đơn chỉ hiện ở Chờ tiếp nhận và Đang thẩm định, theo điều kiện của máy chủ.
5. **Không có hạn cho việc của công ty**: tiếp nhận, thẩm định, báo giá không có hạn riêng, chỉ bị chặn chung ở mốc quyết định. Hoàn tiền cũng không có hạn. Hỏi: có cần đặt hạn nội bộ (ví dụ tiếp nhận trong 4 giờ làm việc) để nhắc nhân viên?
6. **Xử lý chuyến trễ** theo cách hàng không: đã có đề xuất, chờ duyệt, chưa làm. Sẽ viết ở luồng 3 và 5.
7. **Đã sửa.** Chi tiết đơn của khách ẩn ô đánh giá khi đơn đã có đánh giá.

---

# Luồng 2. Thủ tục hồ sơ pháp lý

**Từ lúc** khách chọn hành trình (hệ thống biết cần giấy gì) **đến lúc** đủ giấy và bảo hiểm để công ty được phép **nhận ngựa**.
**Người tham gia:** khách hàng, chuyên viên, quản lý; công ty bảo hiểm, đại lý, người nhận (không có tài khoản, chuyên viên làm thay trên hệ thống).

Luồng này **chạy song song với luồng 1**, rồi kéo dài tới lúc xe đi đón:

| Chặng giấy tờ | Nằm ở đâu trong đơn | Cần xong gì |
|---|---|---|
| Đợt 1 | Đơn Đang thẩm định (luồng 1, bước 3), **trước khi báo giá** | Giấy **do khách nộp** và bảo hiểm từng con |
| Đợt 2 | Đơn Đã xác nhận, **trước khi nhận ngựa** | Giấy **do công ty, người nhận, đại lý** lo |

**Công ty làm gì, không làm gì.** Công ty lo hồ sơ kiểm dịch, giấy phép, hải quan, thú y lấy mẫu tại chuồng. Chủ ngựa hoặc bên nhận lo **nơi cách ly**. Công ty chỉ chở ngựa **đã đủ điều kiện, thể hiện bằng giấy**.

## Từ dùng trong luồng 2

| Từ | Nghĩa |
|---|---|
| **Quy định của hành trình** | Bộ giấy phải có và số ngày làm giấy, theo nước và chiều (liên tỉnh, xuất, nhập). Quản trị khai, có ngày hiệu lực |
| **Bên nộp** | Ai chịu trách nhiệm có giấy đó: khách, công ty, người nhận hoặc đại lý |
| **Giấy bắt buộc** | Thiếu thì không được nhận ngựa. Giấy không bắt buộc chỉ để tham khảo |
| **Giấy theo ngựa** | Hộ chiếu, sổ tiêm: gắn với hồ sơ con ngựa |
| **Giấy theo ngựa trong đơn** | Phiếu xét nghiệm, giấy kiểm dịch: làm riêng cho từng con của đơn này |
| **Giấy theo đơn** | Tờ khai hải quan, giấy phép nhập: một bản cho cả đơn |
| **Đạt** | Giấy đã được **duyệt** và **còn hạn vào ngày chuyến chạy hoặc ngày đón nếu chạy thẳng** |

## 2.A. Đi từng bước

### Bước 1. Hệ thống cho biết cần giấy gì

- Ngay khi khách chọn nơi đón, nơi giao và ngày đón (luồng 1, màn 1), hệ thống tra quy định đang hiệu lực **vào ngày đón** và hiện danh sách giấy khách phải nộp. Giấy khác do công ty và đại lý lo, khách không phải tìm.
- Đơn **trong tỉnh** không cần giấy nào.
- Đơn quốc tế: có giấy phụ thuộc cửa khẩu, nên danh sách chỉ chắc chắn khi đơn đã gắn chuyến.
- Khách **chưa phải nộp ngay**: gửi đơn xong mới nộp, trong trang chi tiết đơn.

### Bước 2. Khách nộp giấy

- Sau khi gửi đơn, trang chi tiết hiện "Bạn còn thiếu N giấy bắt buộc". Khách bấm **Nộp giấy** ở từng dòng, chọn tệp PDF hoặc ảnh (tối đa 5 MB), ghi ngày hết hạn nếu giấy có.
- Giấy vừa nộp ở trạng thái **Chờ duyệt**. Trong lúc chờ, khách không nộp đè được.
- Khách nộp được khi đơn Chờ tiếp nhận, Đang thẩm định hoặc Cần bổ sung.

### Bước 3. Chuyên viên duyệt giấy

- Khi tiếp nhận đơn, chuyên viên mở từng tệp và bấm **Duyệt giấy** hoặc **Từ chối giấy** kèm lý do. Từ chối thì khách được báo lý do và nộp lại giấy đó.
- Thiếu nhiều thứ thì chuyên viên bấm **Yêu cầu khách nộp** ở dòng thiếu, hoặc **Trả lại để bổ sung** cả đơn. Đơn sang **Cần bổ sung**; khách nộp xong thì gửi lại.
- Giấy đã kết luận (duyệt hoặc từ chối) thì **không sửa kết luận được**; muốn khác thì phải có bản mới.

### Bước 4. Bảo hiểm cho từng con

Lúc đặt đơn khách đã chọn một trong ba cách cho mỗi con. Chuyên viên xử lý tiếp:

- **Khách tự có.** Khách đã khai số hợp đồng và thời hạn. Chuyên viên chọn công ty bảo hiểm, ghi số tiền bảo hiểm theo chứng từ. Thời hạn phải phủ **cả ngày đón và ngày chuyến chạy**. Sai hoặc thiếu thì bấm **Yêu cầu bổ sung** kèm lý do; khách sửa trong trang đơn, chuyên viên được báo và kiểm lại.
- **Nhờ công ty mua hộ.** Chuyên viên xin giá từ công ty bảo hiểm, ghi số tiền bảo hiểm, **phí** và **hạn hiệu lực của báo giá hãng**. Phí này vào báo giá của đơn thành khoản thu hộ, khách trả đủ ngay trong tiền cọc.
- **Không mua.** Khách đã ký cam kết trách nhiệm hạn chế cho riêng con đó lúc đặt đơn. Chuyên viên chỉ kiểm có cam kết.
- Cả ba trường hợp, chuyên viên phải bấm **Xác nhận bảo hiểm** cho từng con. Xác nhận nhầm thì **mở lại** kèm lý do, hệ thống ghi nhật ký.

### Bước 5. Chốt đợt 1, chuyển sang báo giá

- Chuyên viên bấm **Bắt đầu báo giá**. Hệ thống kiểm bốn điều kiện, thiếu điều nào nói điều đó:
  1. mọi giấy bắt buộc **do khách nộp** đều đạt, cho từng con;
  2. đã biết cửa khẩu nếu giấy phụ thuộc cửa khẩu;
  3. mọi con có bảo hiểm đã xác nhận, hợp đồng tự có phủ đủ ngày, báo giá mua hộ còn hạn;
  4. đơn đi trục đã có chuyến.
- Từ đây bảo hiểm **khóa lại**, không sửa được nữa.

### Bước 6. Sau khi khách cọc: công ty làm giấy còn lại (đợt 2)

- Đơn đã cọc mà còn thiếu giấy xuất hiện ở trang **Đơn thiếu giấy** của chuyên viên và quản lý, xếp theo giờ chạy.
- Chuyên viên lo giấy của công ty, và **nộp thay** giấy của người nhận, đại lý (họ không có tài khoản), rồi duyệt.
- Với nước đến bắt buộc xét nghiệm, công ty cho thú y tới **tận chuồng lấy mẫu**; phí thú y đã nằm sẵn trong báo giá.
- Nếu giấy của khách hết hạn trong lúc chờ, khách được nộp lại bản mới.
- **Từ 5 ngày trước giờ chạy hoặc ngày đón nếu chạy thẳng**, lúc 08:00 hệ thống nhắc đúng bên phụ trách giấy còn thiếu, tối đa một lần mỗi bên mỗi ngày.

### Bước 7. Không kịp giấy

- Từ **mốc quyết định (48 giờ trước giờ chạy)**, nếu đơn đã cọc vẫn thiếu giấy và công ty chưa nhận ngựa, **quản lý** bấm **báo giấy không kịp**, chọn nguyên nhân và ghi lý do.
- Đơn nhả chỗ, sang **Cần chọn lại chuyến**, khách được báo lý do. Khách đổi chuyến hoặc hủy; được miễn phí hay mất tiền tùy nguyên nhân (bảng ở 2.C).

### Bước 8. Kiểm lần cuối trước khi nhận ngựa

Không ai bấm được cho qua. Hệ thống tự kiểm **mọi giấy bắt buộc của cả bốn bên** và **bảo hiểm từng con** ở đúng nơi công ty nhận ngựa (đơn nhập chỉ kiểm giấy, xem điểm còn hở 10):

| Cách đưa ngựa | Kiểm lúc nào | Thiếu thì |
|---|---|---|
| Xe công ty đến đón, chạy thẳng | Tài xế bấm **Xuất phát** lượt đón | Xe không xuất phát được, nói rõ đơn nào thiếu |
| Khách tự chở tới hub | Điều phối bấm **Nhận ngựa** ở hub | Không nhận ngựa |
| Đơn nhập | Hoàn tất nhận ngựa từ đại lý **ở cửa khẩu** | Không nhận, ngựa ở lại với đại lý |

- Lúc nhận ngựa, hộ tống nhận **bản gốc** từng giấy và đánh dấu; lúc giao thì trả lại (luồng 6).

## 2.B. Danh sách giấy theo hành trình

Dữ liệu đang khai **(số mẫu, quản trị sửa được)**. K = khách nộp, C = công ty, N = người nhận, Đ = đại lý.

| Giấy | Gắn với | Liên tỉnh | Xuất Campuchia | Xuất Lào | Nhập từ Campuchia | Nhập từ Lào |
|---|---|:-:|:-:|:-:|:-:|:-:|
| Hộ chiếu ngựa | Ngựa | K | K | K | K | K |
| Sổ tiêm phòng | Ngựa | K | K | | | |
| Giấy chứng nhận kiểm dịch vận chuyển | Ngựa trong đơn | C | | | | |
| Giấy chứng nhận kiểm dịch xuất khẩu | Ngựa trong đơn | | C | C | | |
| Phiếu xét nghiệm (EIA/EVA) | Ngựa trong đơn | | C | C | C | |
| Xác nhận đạt điều kiện cách ly | Ngựa trong đơn | | C | C | | |
| Tờ khai hải quan | Đơn | | C | C | | |
| Giấy phép nhập của nước đến | Đơn | | N | N | | |
| Sổ ATA Carnet (không bắt buộc) | Đơn | | | K | | |
| Văn bản đồng ý và hướng dẫn kiểm dịch | Ngựa trong đơn | | | | Đ | Đ |
| Giấy kiểm dịch của nước xuất | Ngựa trong đơn | | | | Đ | Đ |
| Giấy vận chuyển động vật nhập khẩu (mẫu 14a) | Ngựa trong đơn | | | | Đ | Đ |
| **Số ngày làm giấy** | | **5** | **21** | **21** | **37** | **37** |

Số ngày gồm ngày xin giấy phép cộng ngày làm chứng nhận: xuất 14 + 7, nhập 30 + 7.

**Căn cứ pháp lý** (chi tiết và phần chưa kiểm chứng: [CAN_CU_PHAP_LY_VA_IRT.md](CAN_CU_PHAP_LY_VA_IRT.md)):
- Việt Nam, Thông tư 01/2026/TT-BNNMT: Điều 5 (ra khỏi tỉnh, cấp trong 5 ngày làm việc), Điều 8–9 (xuất), Điều 11 (nhập, chủ hàng tự bố trí nơi cách ly).
- Danh sách giấy thật của Campuchia và Lào **chưa kiểm chứng đủ**.

## 2.C. Trục thời gian giấy tờ

Tiếp ví dụ chuyến chạy **05:00 thứ Ba 08/12** (liên tỉnh), khách gửi đơn 23/11.

| Thời điểm | Còn bao lâu tới giờ chạy | Hạn của ai | Chuyện gì xảy ra |
|---|---|---|---|
| 30/11 | | Khách | Ngày cuối đặt được chuyến này, vì cần 5 ngày làm giấy trước hạn nhận đặt 05/12 |
| 23/11 → trước khi báo giá | | **Khách** và chuyên viên | Đợt 1: nộp và duyệt giấy của khách, bảo hiểm. Không có hạn riêng, chặn chung bởi mốc quyết định |
| 03/12 → 08/12, 08:00 mỗi ngày | 5 ngày | Hệ thống | Báo khách hoặc chuyên viên theo bên nộp giấy còn thiếu |
| 06/12 05:00 | 48 giờ | **Quản lý** | Từ lúc này được báo giấy không kịp |
| 07/12, lúc xe đi đón | 1 ngày | **Công ty** | Kiểm lần cuối. Thiếu giấy thì xe không xuất phát |

### Giấy không kịp: nguyên nhân quyết định khách được gì

| Nguyên nhân quản lý chọn | Ví dụ | Đổi chuyến | Khách không đi nữa |
|---|---|---|---|
| **Khách** | Khách nộp hộ chiếu trễ | Miễn phí **1 lần** cho mỗi đơn | Mất cọc, gồm cả thu hộ |
| **Công ty** | Chuyên viên nộp hồ sơ kiểm dịch trễ | Miễn phí | Hoàn 100% |
| **Cơ quan** | Cơ quan thú y cấp giấy trễ | Miễn phí, không tính vào lượt của khách | Lần lỡ đầu: mất cọc. Từ lần lỡ thứ **2** (cấu hình): hoàn cọc vận chuyển; thu hộ và phí thú y không hoàn |

## 2.D. Các điểm nghiệp vụ

### Quy định và danh sách giấy

- **2.1** Danh sách giấy lấy từ **quy định đang hiệu lực vào ngày đón**, không phải ngày đặt. Quy định đổi thì đơn có ngày đón sau ngày đổi theo quy định mới.
- **2.2** Đơn trong tỉnh: không cần giấy. Đơn liên tỉnh: theo quy định Việt Nam. Đơn xuất: theo nước đến. Đơn nhập: theo nước đi.
- **2.3** Cùng một loại giấy có thể khác nhau theo cửa khẩu; có bản riêng cho cửa khẩu thì dùng bản đó. Chưa biết cửa khẩu thì **coi như chưa đủ giấy**.
- **2.4** Quản trị khai quy định, loại giấy, bên nộp, bắt buộc hay không, số ngày làm giấy.

### Nộp và duyệt

- **2.5** Màn hình chỉ cho chọn PDF, JPG, PNG; máy chủ chặn tệp quá **5 MB**.
- **2.6** Mỗi yêu cầu chỉ có **một bản đang hiệu lực**: đang chờ duyệt hoặc đã duyệt còn hạn thì không nộp thêm; bị từ chối hoặc hết hạn thì nộp lại được.
- **2.7** Giấy **đạt** khi đã duyệt và chưa quá ngày hết hạn **tính vào ngày chuyến chạy hoặc ngày đón nếu chạy thẳng**. Giấy không ghi ngày hết hạn thì coi như không hết hạn.
- **2.8** Ai nộp được gì, khi nào:

| Ai | Giấy nào | Khi đơn |
|---|---|---|
| Khách | Giấy bên nộp là khách | Chờ tiếp nhận, Đang thẩm định, Cần bổ sung. Đã xác nhận thì chỉ giấy **bắt buộc** |
| Chuyên viên nộp thay | Giấy của công ty, người nhận, đại lý | Từ Đang thẩm định tới Đã xác nhận |
| Chuyên viên duyệt, từ chối | Mọi giấy | Từ Đang thẩm định tới Đã xác nhận |

- **2.9** Chuyên viên **không nộp thay giấy của khách**. Chỉ chuyên viên được duyệt; quản lý và điều phối viên chỉ xem.
- **2.10** Từ chối giấy bắt buộc ghi lý do. Kết luận rồi thì không đổi được.
- **2.11** Giấy **theo ngựa** (hộ chiếu, sổ tiêm) đã duyệt thì **dùng lại được cho đơn sau có cùng quy định**, khách không phải nộp lại cho tới khi hết hạn.
- **2.12** Một con bị loại khỏi đơn thì giấy của con đó không còn bị kiểm.

### Bảo hiểm

- **2.13** Mỗi con phải có **một lựa chọn** bảo hiểm và phải được **xác nhận** trước khi báo giá.
- **2.14** Bảo hiểm tự có phải phủ ngày đón và ngày chuyến chạy. Đơn chạy thẳng chỉ xét ngày đón.
- **2.15** Bảo hiểm mua hộ: báo giá hãng tính bằng VND và còn hạn **lúc xác nhận, lúc bắt đầu báo giá và lúc gửi báo giá**. Hạn này cũng giới hạn thời gian khách trả cọc (điểm 1.28).
- **2.16** Phí mua hộ **thu trọn trong tiền cọc**, vì phải nộp đủ cho hãng thì hợp đồng mới có hiệu lực. Công ty không cộng lời.
- **2.17** Không mua bảo hiểm: khách ký cam kết **cho từng con**, hệ thống lưu phiên bản điều khoản, thời điểm và địa chỉ mạng.
- **2.18** Công ty bảo hiểm phải là đối tác đang có vai trò bảo hiểm còn hiệu lực.
- **2.19** Giá trị khai báo của ngựa **không có nghĩa là đã mua bảo hiểm**.
- **2.20** Chỉ sửa bảo hiểm khi đơn Đang thẩm định hoặc Cần bổ sung.

### Chặn và nhắc

- **2.21** Hai cửa chặn: **trước báo giá** chỉ cần giấy của khách và bảo hiểm; **trước khi nhận ngựa** cần giấy của cả bốn bên và bảo hiểm.
- **2.22** Chưa trả đủ tiền **không** chặn xe đi đón; thiếu giấy thì chặn.
- **2.23** Nhắc thiếu giấy áp dụng cho đơn **đã cọc, có chuyến hoặc liên tỉnh chạy thẳng**, trong cửa sổ **5 ngày** (cấu hình), mỗi bên nhận nhiều nhất một lần mỗi ngày.
- **2.24** Quản lý chỉ báo giấy không kịp khi đủ cả bốn: đơn đã cọc, còn thiếu giấy, chưa nhận ngựa, đã tới mốc quyết định. Bắt buộc chọn nguyên nhân và ghi lý do.

## 2.E. Tình huống và cách hệ thống xử lý

| # | Ở bước | Tình huống | Hệ thống sẽ |
|---|---|---|---|
| T14 | 2 | Khách nộp lại giấy đang chờ duyệt | Không nhận, báo giấy đang chờ duyệt hoặc còn hiệu lực |
| T15 | 3 | Giấy bị từ chối | Báo khách lý do, mở lại nút Nộp giấy cho dòng đó |
| T16 | 4 | Hợp đồng khách tự có không phủ ngày chuyến chạy | Không cho xác nhận. Chuyên viên yêu cầu bổ sung, khách sửa hợp đồng |
| T17 | 4 | Báo giá của hãng bảo hiểm hết hạn khi chưa gửi báo giá cho khách | Không cho gửi, báo tên con ngựa. Hiện đơn bị kẹt ở đây (điểm còn hở 4) |
| T18 | 5 | Đơn quốc tế chưa gắn chuyến nên chưa biết cửa khẩu | Coi là chưa đủ giấy, chưa cho báo giá |
| T19 | 6 | Sổ tiêm của khách hết hạn sau khi đã cọc | Đơn hiện lại ở Đơn thiếu giấy; khách nộp bản mới, chuyên viên duyệt |
| T20 | 7 | Tới mốc quyết định vẫn thiếu giấy | Quản lý báo không kịp, đơn nhả chỗ, sang Cần chọn lại chuyến |
| T21 | 8 | Tới ngày đón vẫn thiếu giấy mà quản lý chưa báo | Tài xế bấm Xuất phát bị chặn, hệ thống nói đơn nào thiếu. Một xe gom nhiều đơn thì **cả lượt bị chặn** cho tới khi gỡ đơn thiếu ra |
| T22 | 8 | Đơn nhập thiếu giấy vận chuyển mẫu 14a tại cửa khẩu | Không nhận ngựa, ngựa ở lại với đại lý |
| T23 | 8 | Khách tự chở tới hub nhưng thiếu giấy hoặc chưa xác nhận bảo hiểm | Không nhận ngựa tại hub |

## 2.F. Điểm còn hở, cần nhóm quyết

1. **Đã sửa.** Nhắc thiếu giấy và báo giấy bị từ chối theo bên nộp. Giấy khách nộp báo khách. Giấy của công ty, người nhận hoặc đại lý báo chuyên viên.
2. **Không có chỗ tải tệp hợp đồng bảo hiểm.** Khách tự có chỉ khai số hợp đồng và ngày; chuyên viên không có tệp để kiểm. Loại giấy "Hợp đồng bảo hiểm" đã khai nhưng chưa dùng.
3. **Đã sửa.** Giấy được duyệt phải còn hiệu lực đến hết mốc ngày chạy của chuyến hoặc ngày đón của đơn chạy thẳng.
4. **Báo giá bảo hiểm hết hạn lúc đơn Đang báo giá thì kẹt** (T17): bảo hiểm đã khóa, đơn không quay về thẩm định được; chỉ còn hủy hoặc chờ bị nhả chỗ ở mốc quyết định. Đề xuất: cho sửa bảo hiểm mua hộ khi Đang báo giá.
5. **Không rút lại được giấy đã duyệt** nếu phát hiện giả hoặc sai.
6. **Đã sửa phần nhắc và danh sách.** Đơn liên tỉnh chạy thẳng được nhắc theo ngày đón và hiện trong danh sách Đơn thiếu giấy sau khi đã cọc. Thao tác báo giấy không kịp vẫn áp dụng cho đơn có chuyến.
7. **Sau khi xe trục rời hub, giấy không được kiểm lại** ở cửa khẩu đơn xuất. Tài liệu có nêu cán bộ cửa khẩu xác nhận giấy, code chưa có.
8. **Danh sách giấy mẫu lệch giữa Campuchia và Lào**: xuất Lào thiếu sổ tiêm, nhập Lào thiếu xét nghiệm. Cần nhóm xác nhận cố ý hay thiếu.
9. **Giấy theo ngựa chỉ dùng lại trong cùng quy định**: hộ chiếu đã duyệt cho đơn liên tỉnh vẫn phải nộp lại cho đơn xuất. Đề xuất: giấy theo ngựa dùng chung cho mọi quy định.
10. **Đơn nhập không kiểm bảo hiểm ở cửa khẩu**, trong khi xe đón và hub có kiểm. Ít rủi ro vì bảo hiểm đã xác nhận trước báo giá, nhưng nên thống nhất.
11. **Đã sửa.** Máy chủ kiểm chữ ký định dạng PDF, JPEG hoặc PNG trong nội dung tệp giấy tờ khi khách hoặc chuyên viên tải lên.

---

# Luồng 3. Lên kế hoạch lộ trình

**Từ lúc** đơn đã cọc (Đã xác nhận) **đến lúc** mỗi con ngựa có đủ xe, tổ lái, vị trí khoang cho mọi chặng và tổ lái đã nhận việc.
**Người tham gia:** quản lý (quyết định chuyến có chạy), điều phối viên (xếp xe), tài xế và hộ tống (nhận việc).

Trạng thái đơn **không đổi** trong cả luồng này, vẫn là Đã xác nhận. Đơn chỉ đổi khi ngựa đã được nhận và xe rời đi (luồng 4).

## Từ dùng trong luồng 3

| Từ | Nghĩa |
|---|---|
| **Chặng** | Một đoạn đường của **một con ngựa** trên một xe, hoặc trong tay đại lý |
| **Lượt xe** | Một lần **một chiếc xe** ra đi, ghé nhiều điểm, chở nhiều con của nhiều đơn |
| **Tổ lái** | Tài xế và hộ tống của một lượt xe |
| **Trụ sở** | Hub TP.HCM. Mọi xe và nhân viên thuộc trụ sở, xe phải về đây |
| **Hub vệ tinh** | Hub Đà Nẵng: có chỗ tập kết ngựa, không có xe hay nhân viên trực thuộc |
| **Khung giờ đón, giao** | 06:00–18:00 (cấu hình) |
| **Hạn có mặt ở hub** | 3 giờ trước giờ xe trục chạy (cấu hình) |

Năm loại lượt xe:

| Lượt | Đi đâu | Ai cần |
|---|---|---|
| **Đón** | Trụ sở → các nhà khách → hub | Đơn đi trục, khách chọn xe công ty đến đón |
| **Trục** | Hub → hub hoặc cửa khẩu, theo tuyến có sẵn trạm nghỉ | Mọi đơn đi trục |
| **Giao** | Hub → các nhà khách → hub | Đơn đi trục, khách chọn xe công ty giao |
| **Chạy thẳng** | Trụ sở → nhà đón → nhà giao → trụ sở | Đơn chung hub |
| **Chuyển xe** | Giữa hai điểm, không chở ngựa | Đưa xe về khi không có chuyến chiều ngược |

## 3.A. Đi từng bước

### Bước 1. Điều phối nhìn việc cần làm

- Ngay khi khách trả cọc, đơn hiện trong trang **Điều độ** của điều phối viên, ở tuần có ngày đón hoặc ngày chuyến chạy. Tuần đang xem không còn đơn chờ thì có nút mở tuần gần nhất còn đơn.
- Mỗi đơn ghi **còn thiếu chặng nào**: Cần Đón, Cần Trục, Cần Giao, hoặc Cần Chạy thẳng. Đầu khách tự chở thì không cần chặng xe; đầu nước ngoài vẫn phải lập chặng cho đại lý.
- Bên phải là **lịch tuần theo xe**: xe nào bận lúc nào, xe nào đang ở ngoài trụ sở, lượt nào chưa có tài xế.

### Bước 2. Quản lý quyết định chuyến có chạy không

- Chuyến có lịch không có nghĩa là chắc chắn chạy. **Từ 48 giờ trước giờ chạy tới giờ chạy**, quản lý mở trang Chuyến cần quyết định và bấm **Chạy** hoặc **Hủy chuyến**. Trước mốc 48 giờ chỉ hủy được, chưa bấm Chạy được.
- Màn hình cho biết **đã cọc bao nhiêu chỗ trên sức chứa**. Dưới **30%** (cấu hình) thì cảnh báo "dưới mức tối thiểu", nhưng quyết định là của quản lý.
- Bấm Chạy thì điều phối viên được báo để lập lượt trục. Bấm Hủy thì các đơn sang Cần chọn lại chuyến, nguyên nhân công ty (luồng 1).

### Bước 3. Xếp lượt đón

- Điều phối bấm **Gom tự động**, chọn ngày, hub, loại lượt Đón. Hệ thống gợi ý các **nhóm đơn cùng hướng** đón chung một xe, kèm số chỗ, số km, số giờ ước lượng.
- Chọn xe rồi bấm **Xem giờ và bản đồ**: hệ thống tính giờ **theo đường xe tải thật**, tự cộng thời gian lên ngựa và nghỉ bắt buộc.
- Giờ xuất phát do hệ thống tính ngược: **tới nhà khách đầu tiên lúc 06:00 ngày khách chọn**. Điều phối chỉ sửa được giờ xuất phát rồi bấm tính lại.
- Bấm **Lập lượt xe**: tạo lượt, điểm dừng, gắn ngựa và xếp vị trí khoang trong một lần.

### Bước 4. Xếp lượt trục

- Chỉ làm được **sau khi quản lý bấm Chạy**. Điều phối bấm **Lập lượt xe**, loại **Trục**, chọn xe rồi chọn chuyến. Danh sách chỉ có chuyến xuất phát **từ nơi xe đang ở**; chuyến chưa được xác nhận chạy thì hiện nhưng khóa.
- Điểm dừng và trạm nghỉ **chép từ tuyến**, giờ cộng từ giờ chạy của chuyến. Điều phối không sửa.
- Xếp từng con vào xe. Ngựa cùng một đơn phải chung một xe trục. Chuyến đông có thể chia hai xe.

### Bước 5. Xếp lượt giao

- Gom tự động, loại Giao, chọn hub đích và ngày xe trục tới. Hệ thống chỉ gợi ý con **đã có chặng trục** tới hub đó.
- Giờ rời hub là lúc xe trục tới **cộng thời gian xuống ngựa**. Nếu có nhà khách nào rơi ngoài khung 06:00–18:00 thì cả lượt dời sang **06:00 hôm sau**, ngựa nghỉ ở hub.
- Ở hub vệ tinh, dùng **chính chiếc xe trục vừa tới** để giao.

### Bước 6. Đơn chạy thẳng, tự chở và quốc tế

- **Chạy thẳng:** lập một lượt Chạy thẳng, có thể gom nhiều đơn cùng vùng. Không cần chờ quản lý quyết định chuyến.
- **Khách tự chở tới hub:** điều phối bấm **Hẹn nhận tại hub**, chọn giờ; khách được báo giờ và tên hub. Vẫn phải xếp con ngựa vào lượt trục trước.
- **Người nhận tự đến hub:** hẹn giờ sau khi ngựa tới hub (luồng 6).
- **Đơn xuất:** sau khi có lượt trục tới cửa khẩu, lập **chặng đại lý** từ cửa khẩu tới nhà người nhận, chọn đại lý của đúng nước đó.
- **Đơn nhập:** lập chặng đại lý **trước**, từ nhà người gửi tới cửa khẩu nơi chuyến trục xuất phát; rồi mới xếp trục và giao.

### Bước 7. Phân công tổ lái

- Mở lượt xe, chọn vai trò (tài xế hoặc hộ tống) rồi chọn người. Người được chọn nhận thông báo lượt xe, giờ bắt đầu.
- Người đó mở **Lượt xe của tôi** trên điện thoại và bấm **Xác nhận nhận việc**. Chưa có **ít nhất một tài xế** xác nhận thì lượt xe không xuất phát được.

### Bước 8. Chốt sơ đồ khoang

- Khách chỉ chọn **loại khoang**, không chọn vị trí. Hệ thống tự xếp vị trí ngay khi gắn ngựa: số 1 ở đầu xe, số lớn nhất ở cửa sau.
- Điều phối xem sơ đồ, đổi chỗ, bấm **Xếp lại tự động** nếu cần. Cảnh báo chỉ để tham khảo: ngựa đực đứng cạnh ngựa cái, con xuống trước bị chắn cửa.
- Mọi con, kể cả con sẽ lên ở điểm sau, phải có vị trí thì xe mới xuất phát. **Xe xuất phát thì khóa cả sơ đồ.**

### Bước 9. Báo khách

- Khi con ngựa được xếp vào lượt đón, giao hoặc chạy thẳng, khách được báo **khung một giờ** xe dự kiến đến. Giờ dời **quá 30 phút** (cấu hình) so với lần báo trước thì báo lại; lệch ít hơn thì không làm phiền.

### Bước 10. Đưa xe về trụ sở

- Xe trục giao xong ở hub vệ tinh thì lịch tuần ghi "Đang ở Hub Đà Nẵng". Điều phối bấm **Lập lượt về**: hệ thống mở sẵn lượt trục chiều ngược Đà Nẵng – TP.HCM. Chuyến đó phải được quản lý xác nhận chạy, kể cả không có ngựa.

## 3.B. Ví dụ lịch của một đơn, từ đơn đã chạy thật

Hai con ngựa Củ Chi → Điện Bàn, chuyến TP.HCM – Đà Nẵng chạy 05:00 ngày 08/12:

| Lượt | Xe | Giờ | Điểm |
|---|---|---|---|
| **Đón** 07/12 | 51C-100.02 | 03:25 | Rời trụ sở, lái 155 phút |
| | | 06:00–06:40 | Trại Củ Chi: 20 phút + 10 phút × 2 con |
| | | 08:28–09:08 | Trụ sở, ngựa vào hub nghỉ đêm. Hạn có mặt là 02:00 ngày 08/12 |
| **Trục** 08–09/12 | 51C-100.03 | 05:00–05:30 | Trụ sở |
| | | 07:57 – 13:57 | Phan Thiết, Phan Rang, Nha Trang, mỗi nơi nghỉ 30–45 phút |
| | | 16:23–02:23 | Tuy Hòa: **nghỉ đêm 10 giờ** |
| | | 04:12 – 08:27 | Quy Nhơn, Quảng Ngãi |
| | | 10:41 | Hub Đà Nẵng |
| **Giao** 09/12 | 51C-100.03 (chính xe trục) | 10:41–11:21 | Hub: xuống ngựa 40 phút |
| | | 12:07–12:47 | Điện Bàn. Khách được báo xe đến 12:00–13:00 |
| | | 13:33 | Về hub Đà Nẵng |

## 3.C. Trục thời gian của việc lên kế hoạch

Cùng chuyến 05:00 ngày 08/12:

| Thời điểm | Còn bao lâu tới giờ chạy | Hạn của ai | Chuyện gì xảy ra |
|---|---|---|---|
| Ngay sau khi khách cọc | | Điều phối | Được xếp lượt **đón**, hẹn giờ tự chở. Không phải chờ quản lý |
| 06/12 05:00 | 48 giờ | **Quản lý** | Từ lúc này được bấm **Chạy**. Không bấm thì không lập được lượt trục |
| Sau khi bấm Chạy | | **Điều phối** | Lập lượt trục, rồi giao |
| 07/12 06:00–18:00 | | Xe đón | Đón ở nhà khách, đưa ngựa về hub trong khung giờ |
| 08/12 02:00 | 3 giờ | **Điều phối, khách tự chở** | **Hạn có mặt ở hub**. Lượt đón nào tới hub muộn hơn thì hệ thống không cho xếp |
| 08/12 05:00 | 0 | Tổ lái | Xe trục chạy. Muốn xuất phát: có tài xế nhận việc, đủ vị trí khoang |

## 3.D. Các điểm nghiệp vụ

### Quyết định chuyến

- **3.1** Chỉ đếm chỗ của đơn **đã cọc** khi quyết định. Đơn chưa cọc không tính.
- **3.2** Xác nhận chạy chỉ được trong **48 giờ cuối, trước giờ chạy**. Qua giờ chạy thì không xác nhận được nữa.
- **3.3** Đã có đơn mà công ty **đã nhận ngựa** thì không hủy chuyến được, phải xử lý bằng sự cố (luồng 5).
- **3.4** Ngoài lịch, quản lý tạo **chuyến đột xuất**: chọn tuyến, ngày giờ, hạn nhận đặt, số chỗ.

### Chọn xe

- **3.5** Chỉ dùng xe **đang hoạt động**. Xe bảo dưỡng hoặc ngừng sử dụng bị khóa.
- **3.6** Một xe không có hai lượt **trùng giờ**.
- **3.7** Lượt mới phải bắt đầu **ở nơi lượt trước của xe kết thúc**; chưa có lượt nào thì ở trụ sở. Chèn một lượt vào giữa thì lượt sau của xe cũng phải khớp. Sai thì hệ thống nói xe đang ở đâu.
- **3.8** Tại **mọi đoạn đường**, tổng chỗ ngựa trên xe không vượt sức chứa; khoang rộng tính 2 chỗ. Xe mẫu: 2, 6, 9, 9 chỗ.
- **3.9** Ngựa **cùng một đơn đi chung một xe trục**, vì giấy của đơn chỉ có một bản gốc.
- **3.10** Ngựa chỉ lên xe trục ở điểm đầu tuyến và xuống ở điểm cuối tuyến.

### Thứ tự chặng của mỗi con ngựa

- **3.11** Phải xếp theo thứ tự **đón → trục → giao**: chặng mới bắt đầu ở nơi và sau giờ chặng trước kết thúc.
- **3.12** Gỡ thì ngược lại: gỡ chặng sau trước. Chỉ gỡ được khi lượt xe chưa chạy.
- **3.13** Chỉ xếp đơn **Đã xác nhận** hoặc **Đang vận chuyển**; con bị loại khỏi đơn không xếp được.
- **3.14** Điểm lên phải là địa chỉ đón của chính đơn đó, điểm xuống phải là địa chỉ giao của chính đơn đó.

### Giờ giấc và quãng đường

- **3.15** Mỗi điểm đón, giao dừng **20 phút + 10 phút mỗi con** (cấu hình).
- **3.16** Lái liên tục tối đa **4 giờ** thì nghỉ ít nhất **30 phút** (cấu hình). Lượt đón, giao: thời gian nghỉ được cộng vào giờ đi đường. Dừng nhận ngựa từ 30 phút trở lên cũng tính là nghỉ.
- **3.17** Mỗi con ngựa ngồi xe **đón hoặc giao không quá 5 giờ lái**, chạy thẳng không quá **10 giờ**, tính cả đoạn đi vòng ghé nhà khác. Quá thì **chặn**, phải tách lượt.
- **3.18** Mỗi tài xế lái không quá **10 giờ một ngày** (cấu hình), **gộp mọi lượt**, tính theo ngày lịch. Xe có hai tài xế thì mỗi người vẫn bị tính đủ.
- **3.19** Lượt đón phải đưa ngựa tới hub **trước hạn có mặt** (3 giờ trước giờ chạy). Muộn thì **chặn**.
- **3.20** Đón hoặc giao **ngoài khung 06:00–18:00** chỉ **cảnh báo**, điều phối quyết định.
- **3.21** Tuyến trục do quản trị khai và được kiểm khi lưu: mỗi đoạn chạy tối đa 4 giờ, trạm nghỉ ít nhất 30 phút, chạy đủ 10 giờ phải có điểm **nghỉ đêm 10 giờ**.
- **3.22** Dịch vụ bản đồ hỏng thì điều phối **nhập giờ tay**.

### Gom đơn

- **3.23** Gợi ý gom khi: cùng hub, cùng ngày, đủ chỗ, mỗi đơn **đi vòng không quá 1,5 lần** so với đi riêng, con lên đầu tiên không ngồi quá 5 giờ. Một đơn luôn được là một nhóm.
- **3.24** Số km và giờ lúc gợi ý là **ước lượng nhanh**; số thật chỉ có khi bấm Xem giờ và bản đồ.
- **3.25** Gom chỉ là gợi ý. Điều phối luôn là người bấm lập lượt xe. Gom không làm đổi giá của khách.

### Tổ lái

- **3.26** Tài xế phải là tài khoản tài xế, hộ tống phải là tài khoản hộ tống, đang làm việc.
- **3.27** Một người không có hai lượt **trùng giờ**, và lượt mới phải bắt đầu ở **nơi lượt trước của người đó kết thúc**.
- **3.28** Lượt đang chạy chỉ thêm được **hộ tống thay ca**, người đó tự tới điểm dừng để nhận bàn giao.
- **3.29** Không gỡ người đang phụ trách ngựa trên xe; người thay phải nhận bàn giao trước.

### Sơ đồ khoang

- **3.30** Vách di động: khoang rộng ghép **hai vị trí liền nhau bất kỳ**. Hai con trên xe cùng lúc không trùng vị trí.
- **3.31** Đủ chỗ nhưng lẻ vị trí thì hệ thống tự dồn các con chưa lên xe; không dồn được thì từ chối và nêu con vướng.
- **3.32** Hai điều phối cùng sửa một sơ đồ: người lưu sau bị báo sơ đồ đã đổi, phải xem bản mới.

### Đại lý và tự chở

- **3.33** Đại lý phải đang có vai trò đại lý còn hiệu lực và **ở đúng nước** của địa chỉ. Đơn nhập phải chọn **đúng cửa khẩu** chuyến trục xuất phát.
- **3.34** Giờ hẹn tự chở phải trước giờ chạy của chuyến. Mỗi con chỉ có một lịch hẹn.

## 3.E. Tình huống và cách hệ thống xử lý

| # | Ở bước | Tình huống | Hệ thống sẽ |
|---|---|---|---|
| T24 | 2 | Chuyến chỉ có 1/9 chỗ đã cọc | Cảnh báo dưới 30%, vẫn cho bấm Chạy |
| T25 | 2 | Quản lý quên bấm Chạy tới giờ xuất phát | Không xác nhận được nữa, đơn đã cọc **đứng yên** (điểm còn hở 1) |
| T26 | 3 | Nhà khách quá xa, ngựa ngồi xe quá 5 giờ | Chặn lập lượt, đề nghị bỏ bớt điểm hoặc tách lượt |
| T27 | 3 | Lượt đón tính ra tới hub sau hạn có mặt | Chặn, đề nghị xuất phát sớm hơn |
| T28 | 4 | Xe trục còn ở Đà Nẵng | Không cho lập, nói xe đang ở đâu. Điều phối bấm Lập lượt về hoặc chọn xe khác |
| T29 | 4 | Xếp trục khi chưa xếp đón | Từ chối, nhắc xếp lần lượt đón, trục, giao |
| T30 | 5 | Xe trục tới hub 17:00, không kịp giao trong ngày | Cả lượt giao dời sang 06:00 hôm sau, ngựa nghỉ ở hub, công ty chịu chi phí |
| T31 | 7 | Chọn tài xế đã lái 8 giờ hôm đó | Từ chối, nói sẽ lái bao nhiêu giờ |
| T32 | 7 | Tài xế không bấm nhận việc | Lượt xe không xuất phát được; lịch tuần ghi "chưa có tài xế" nếu chưa phân công ai |
| T33 | 8 | Hai con khoang rộng không còn hai vị trí liền nhau | Tự dồn; không được thì từ chối, nêu con vướng |
| T34 | Bất kỳ | Cần bỏ một lượt xe chưa chạy | Gỡ hết ngựa rồi mới hủy được, bắt buộc có lý do; tổ lái được gỡ khỏi lượt |

## 3.F. Đã ghi nhận, chưa làm: xử lý chuyến trễ

Theo cách hàng không, đã trình bày và chờ duyệt cuối:

| Mốc | Hệ thống sẽ | Khách được |
|---|---|---|
| Trước giờ chạy 24 giờ mà chưa quyết | Báo đỏ, nhắc quản lý và điều phối | |
| Đến giờ chưa quyết, hoặc chưa chạy quá 30 phút | Ghi **Chậm**, nguyên nhân công ty | Được báo ngay |
| Quản lý nhập giờ mới và nguyên nhân | Giữ giờ gốc, dời các giờ phía sau | Thấy giờ cũ, giờ mới, lý do |
| Trễ từ 2 giờ | Mở nút đổi chuyến | Đổi miễn phí, giữ giá |
| Trễ từ 5 giờ | Mở nút hoàn tiền | Hoàn 100% hoặc chờ tiếp |
| 5 giờ sau giờ gốc vẫn không có giờ mới | Tự hủy chuyến | Đổi hoặc hoàn 100% |

## 3.G. Điểm còn hở, cần nhóm quyết

1. **Đã sửa** · Đơn rời chuyến được gỡ chặng chưa chạy, vị trí khoang và lịch hẹn hub.
2. **Quên xác nhận chạy thì đơn đứng yên** (T25). Đã có đề xuất ở 3.F.
3. **Đã sửa** · Chuyến cập nhật khi xe trục đầu tiên rời điểm đầu và khi các lượt trục hoàn tất.
4. **Không sửa được giờ của lượt đã lập**: muốn đổi giờ hoặc đổi xe phải gỡ ngựa, hủy lượt, lập lại.
5. **Hủy lượt xe hoặc gỡ người thì tổ lái không được báo.**
6. **Không bắt buộc có hộ tống**: chỉ cần một tài xế nhận việc là xe chạy được, dù chở ngựa.
7. **Nhập giờ tay không kiểm giới hạn 5 giờ ngồi xe**; chỉ lượt do hệ thống tính giờ mới kiểm. Giới hạn 10 giờ của tài xế thì vẫn kiểm khi phân công.
8. **Giờ hẹn khách tự chở chỉ kiểm trước giờ chạy**, không kiểm hạn có mặt 3 giờ như xe công ty.
9. **Dồn chuyến, chạy xe nhỏ hơn, một khách thuê cả chuyến**: tài liệu có nêu, chưa có nút riêng. Hiện chỉ có Chạy, Hủy, Tạo chuyến đột xuất.
10. **Đưa xe về phải nhờ quản lý xác nhận một chuyến không có ngựa.** Hỏi: cho điều phối tự lập lượt về rỗng?
11. **Giờ lái mỗi ngày tính theo ngày lịch**, không theo 24 giờ liên tục: lái 8 giờ tối hôm trước và 8 giờ sáng hôm sau vẫn hợp lệ.

---

# Luồng 4. Cập nhật trạng thái chuyến đi và nhật ký sức khỏe ngựa

**Từ lúc** tài xế bấm Xuất phát lượt xe đầu tiên **đến lúc** lượt xe cuối cùng kết thúc.
**Người tham gia:** tài xế, hộ tống (dùng điện thoại), điều phối viên (theo dõi), khách hàng (xem hành trình), đại lý (điều phối nhập hộ).

Các lần **ký giao nhận** (nhận ngựa ở nhà khách, vào hub, ra hub, giao cho người nhận) nằm ở **luồng 6**. Luồng này chỉ nhắc chúng xảy ra lúc nào. **Sự cố** nằm ở luồng 5.

Ba lớp trạng thái không trộn vào nhau:

| Lớp | Cho biết | Ai làm thay đổi |
|---|---|---|
| **Đơn** | Mốc mua bán: Đã xác nhận → Đang vận chuyển → Đã giao → Hoàn thành | Hệ thống, theo việc tổ lái làm |
| **Lượt xe** | Xe đang ở đâu: Đã lập → Đang chạy → Hoàn thành | Tổ lái bấm theo điểm dừng |
| **Con ngựa** | Đang trong tay ai, ở chặng nào, khỏe không | Mỗi lần quét chip, mỗi lần ghi sức khỏe |

## Từ dùng trong luồng 4

| Từ | Nghĩa |
|---|---|
| **Điểm dừng** | Một nơi lượt xe ghé: hub, nhà khách, trạm nghỉ, cửa khẩu. Tổ lái bấm theo **từng điểm dừng, đúng thứ tự** |
| **Phân công** | Ai **dự kiến** đi lượt xe này. Điều phối đổi được |
| **Phụ trách** | Ai **đang giữ** con ngựa này. Là người **ký nhận gần nhất**, không sửa tay được |
| **Bàn giao ca** | Hộ tống mới quét chip, ký nhận từng con ngay trên xe |
| **Mức sức khỏe** | Bình thường, Cần theo dõi, Ốm, Nguy cấp |
| **Mức ăn, uống** | Bình thường, Ít, Bỏ |

## 4.A. Đi từng bước

### Bước 1. Xuất phát

- Tài xế hoặc hộ tống mở **Lượt xe của tôi**, vào lượt, bấm **Xuất phát**.
- Hệ thống kiểm rồi mới cho chạy: người bấm thuộc tổ lái, có ít nhất một tài xế đã nhận việc, mọi con đã có vị trí khoang. Nếu lượt có **đón ngựa ở nhà khách** thì kiểm thêm giấy tờ và bảo hiểm (luồng 2, bước 8).
- Lượt xe sang **Đang chạy**. Sơ đồ khoang khóa, không thêm bớt ngựa được nữa.

### Bước 2. Đi theo từng điểm dừng

- Đến nơi, tổ lái bấm **Tới nơi**; xong việc bấm **Rời đi**. Hệ thống ghi giờ thật bên cạnh giờ kế hoạch.
- Phải đi đúng thứ tự: chưa rời điểm trước thì chưa tới điểm sau được. Còn lần giao nhận đang dở thì chưa rời được.

### Bước 3. Ở nơi nhận ngựa

- Tổ lái quét chip từng con, nhận giấy gốc, chụp biên bản, hoàn tất (chi tiết ở luồng 6). Quét đúng chip thì màn hình hiện **số khoang** để dẫn ngựa vào đúng chỗ.
- Nhận xong, khách được báo "Đã nhận ngựa" và yêu cầu trả số dư.
- Bấm **Rời đi**: hệ thống chỉ cho rời khi mọi đơn có ngựa lên xe ở đây **đã trả đủ tiền**. Lúc xe rời, đơn sang **Đang vận chuyển** và khách hết quyền tự hủy.
- "Nơi nhận ngựa" là nhà khách (xe đến đón), **hub** (khách tự chở), hoặc **cửa khẩu** (đơn nhập).

### Bước 4. Về hub, chờ chuyến

- Tổ xe giao ngựa vào hub, lượt đón kết thúc, xe rảnh. Từ lúc đó con ngựa ở trạng thái **Tập kết ở hub**, chờ xe trục.
- Người ký giao vào hub **phụ trách** con ngựa cho tới lần ký kế tiếp. Con ngựa ở hub cần được khám **2 lần mỗi ngày**.

### Bước 5. Chạy trục, nghỉ trạm, ghi sức khỏe

- Tổ xe trục nhận ngựa từ hub rồi chạy qua các trạm nghỉ của tuyến.
- Tới **mỗi trạm nghỉ**, tổ lái cho uống, cho ăn rồi ghi **từng con**: mức sức khỏe, **uống**, **ăn**, nhiệt độ nếu đo. Uống và ăn là bắt buộc.
- Bấm **Rời đi** ở trạm chỉ được khi: (1) mọi con đang trên xe đã ghi uống và ăn **ở trạm này**; (2) đã nghỉ đủ **30 phút** kể từ lúc tới. Nghỉ chưa đủ mà cần đi thì phải **ghi lý do**, hệ thống lưu nhật ký.
- Con nào **bỏ uống hai lần ghi liền nhau** thì hệ thống tự nâng lên **Cần theo dõi** và báo mọi điều phối viên.

### Bước 6. Nghỉ đêm

- Ở điểm nghỉ đêm, tổ xe có thể **gửi ngựa cho chuồng đối tác**: chọn chuồng, quét chip từng con, hoàn tất. Con ngựa chuyển sang chuồng đó giữ.
- Sáng hôm sau **Nhận lại từ chuồng**, quét chip từng con. Còn con chưa nhận lại thì xe **không rời trạm được**.

### Bước 7. Thay hộ tống giữa đường

- Điều phối thêm hộ tống mới vào lượt **đang chạy**. Người đó tự tới điểm dừng, bấm **Nhận bàn giao ca**, quét chip các con và ký.
- Ngựa vẫn trên xe, chặng không đổi, chỉ đổi **người phụ trách**. Người cũ chưa bàn giao hết thì điều phối không gỡ khỏi lượt được.

### Bước 8. Qua cửa khẩu (đơn quốc tế)

- **Đơn xuất:** tổ xe bàn giao ngựa cho đại lý ở cửa khẩu. Chặng của đại lý tự bắt đầu, khách được báo "Đã qua cửa khẩu". Khi đại lý báo đã giao, **điều phối viên** bấm **Đại lý đã giao** ở trang Theo dõi.
- **Đơn nhập:** điều phối ghi đại lý đã nhận ngựa ở nước ngoài; tới cửa khẩu tổ xe nhận ngựa từ đại lý, chặng đại lý tự kết thúc, khách được báo.
- Sau khi bàn giao cho đại lý, công ty **vẫn chịu trách nhiệm với khách**.

### Bước 9. Kết thúc lượt xe

- Bấm **Kết thúc lượt** khi đã tới điểm cuối và **không còn ngựa trên xe**. Lượt xe sang Hoàn thành, xe và tổ lái rảnh cho lượt sau.
- Con ngựa cuối cùng của đơn được giao thì đơn tự sang **Đã giao** (luồng 6).

### Bước 10. Điều phối và khách theo dõi

- **Điều phối viên** mở trang **Theo dõi**: mọi con công ty hoặc đối tác đang giữ, ở đâu, ai phụ trách, khám lúc nào, hạn khám tới. Con **trễ khám** lên đầu, tô đỏ. Trang tự tải lại mỗi phút.
- **Khách** mở chi tiết đơn, xem **theo từng con ngựa** một dòng thời gian: từng chặng, từng lần lưu trú, từng lần giao nhận, đang trong tay ai, khung giờ xe dự kiến đến.
- **Người không có tài khoản** tra cứu bằng **mã đơn hoặc số vận đơn** cùng **4 số cuối điện thoại người gửi**; chỉ thấy trạng thái, ngựa đang ở đâu và ngày giao dự kiến.

## 4.B. Trạng thái đổi khi nào

| Việc tổ lái làm | Đơn | Chặng của con ngựa | Ngựa trong tay | Báo khách |
|---|---|---|---|---|
| Nhận ngựa ở nhà khách | Đã xác nhận | Đón: đang chạy | Công ty | Đã nhận ngựa |
| Rời nơi nhận (đã trả đủ) | **Đang vận chuyển** | | Công ty | |
| Giao vào hub | | Đón: xong; tập kết ở hub | Công ty (hub) | |
| Nhận từ hub | | Trục: đang chạy | Công ty | |
| Gửi chuồng đêm, nhận lại | | Trục: vẫn đang chạy | Chuồng đối tác, rồi công ty | |
| Bàn giao đại lý (xuất) | | Trục: xong; đại lý: đang chạy | Đại lý | Đã qua cửa khẩu |
| Nhận từ đại lý (nhập) | Đã xác nhận | Đại lý: xong; trục: đang chạy | Công ty | Đã nhận ngựa ở cửa khẩu |
| Giao người nhận | **Đã giao**, khi là con cuối | Giao: xong | Người nhận | Đã giao ngựa |

## 4.C. Các hạn trong luồng 4

| Hạn | Của ai | Đếm từ | Dài bao lâu | Quá hạn thì |
|---|---|---|---|---|
| Trả số dư sau khi nhận ngựa | Khách | Lúc hoàn tất nhận ngựa | 30 phút (cấu hình) | Xe chưa rời được; tổ xe có quyền trả ngựa (luồng 6) |
| Nghỉ ở mỗi trạm | Tổ lái | Lúc bấm Tới nơi | Ít nhất 30 phút (cấu hình) | Muốn đi sớm phải ghi lý do |
| Khám con đang **trên xe** | Tổ lái | Lần ghi gần nhất, hoặc lúc nhận ngựa | 4 giờ (cấu hình) | Tô đỏ Trễ khám ở trang Theo dõi |
| Khám con đang **ở hub hoặc chuồng** | Người phụ trách | Như trên | 12 giờ (2 lần mỗi ngày) | Tô đỏ Trễ khám |
| Coi là **trễ điểm dừng** trong báo cáo | Tổ lái | Giờ kế hoạch tới điểm đó | 30 phút | Tính vào báo cáo vận hành, không báo ngay |
| Tra cứu công khai sai | Người tra cứu | Lần sai đầu | 5 lần trong 15 phút | Khóa tra cứu đơn đó |

## 4.D. Các điểm nghiệp vụ

### Lượt xe và điểm dừng

- **4.1** Chỉ người **trong tổ lái** của lượt, chưa bị gỡ, mới thao tác được. Mọi người trong tổ lái có quyền như nhau, trừ bàn giao ca chỉ hộ tống nhận.
- **4.2** Một lượt chỉ xuất phát một lần. Lượt đã hủy hoặc đã chạy không xuất phát lại.
- **4.3** Điểm dừng đi **đúng thứ tự**, không bỏ qua điểm nào.
- **4.4** Loại giao nhận phải **hợp với điểm dừng**: nhận ở nhà khách, vào ra hub ở hub, bàn giao đại lý ở cửa khẩu, gửi chuồng ở trạm nghỉ hoặc cửa khẩu. Bàn giao ca ở bất kỳ điểm dừng nào.
- **4.5** Xe **không rời nơi nhận** khi còn đơn chưa trả đủ. Đơn khác đã trả đủ trên cùng xe cũng phải chờ hoặc đơn kia bị trả ngựa.
- **4.6** Kết thúc lượt khi đã tới điểm cuối và không còn ngựa trên xe, kể cả con giao không thành (phải đưa vào hub trước).
- **4.7** Giờ kế hoạch và giờ thật lưu riêng, không ghi đè.

### Ai giữ ngựa

- **4.8** Ngựa đang trong tay ai **suy ra từ lần giao nhận hoàn tất gần nhất**, không ai sửa được. Năm bên: người gửi, công ty, chuồng đối tác, đại lý, người nhận.
- **4.9** Trong công ty, người phụ trách là **người ký** lần nhận đó. Điều phối đổi phân công không làm đổi người phụ trách.
- **4.10** Không ai được rời ca khi còn phụ trách con chưa có người nhận.
- **4.11** Chip quét **sai** thì con đó không được giao nhận; phải có ít nhất một con đúng chip mới hoàn tất được.

### Sức khỏe

- **4.12** Mỗi lần ghi gắn **một con, một nơi** (điểm dừng của xe hoặc một lần lưu trú), tự lưu người ghi và giờ ghi.
- **4.13** Ở điểm dừng: chỉ ghi được khi **xe đang ở điểm đó** và **con ngựa đang trên xe**.
- **4.14** Nhật ký theo **con ngựa**, không theo người hay theo xe: qua nhiều hộ tống, nhiều xe vẫn là một dòng thời gian.
- **4.15** Giới hạn nhập: nhiệt độ 30–45 °C, nhịp tim 10–200, nhịp thở 4–100.
- **4.16** Bỏ uống hai lần ghi liền: tự nâng ít nhất lên Cần theo dõi và báo điều phối. Người ghi chọn mức nặng hơn thì giữ mức đó.
- **4.17** Dịch vụ khách mua không làm đổi việc chăm sóc: mọi con trên xe được chăm cùng một chuẩn.

### Nghỉ đêm và đại lý

- **4.18** Chỉ gửi đêm cho đối tác đang có vai trò **chuồng gửi** còn hiệu lực.
- **4.19** Đại lý và chuồng **không có tài khoản**: tổ lái quét chip khi giao, nhận; điều phối nhập hộ mốc của đại lý.

### Tra cứu công khai

- **4.20** Mỗi lần tra tối đa **5 mã**. Sai số điện thoại thì trả lời như không tìm thấy, không cho biết đơn có tồn tại.
- **4.21** Không hiện con đã bị loại hoặc đã hủy; không hiện tên khách, địa chỉ, giá.

## 4.E. Tình huống và cách hệ thống xử lý

| # | Ở bước | Tình huống | Hệ thống sẽ |
|---|---|---|---|
| T35 | 1 | Đơn trên lượt đón thiếu giấy | Chặn xuất phát, nói mã đơn |
| T36 | 1 | Tài xế chưa bấm nhận việc | Chặn xuất phát |
| T37 | 2 | Bấm Tới nơi khi chưa rời điểm trước | Từ chối |
| T38 | 3 | Khách chưa trả số dư mà tài xế bấm Rời đi | Từ chối, nói mã đơn |
| T39 | 5 | Quên ghi ăn uống một con | Không cho rời trạm, nêu tên con ngựa |
| T40 | 5 | Cần rời trạm khi mới nghỉ 15 phút | Bắt ghi lý do, cho đi, lưu nhật ký |
| T41 | 5 | Ngựa bỏ uống ở Phan Thiết rồi bỏ uống ở Phan Rang | Nâng Cần theo dõi, báo điều phối |
| T42 | 6 | Sáng ra quên nhận lại một con ở chuồng | Không cho rời trạm, nêu tên con ngựa |
| T43 | 7 | Hộ tống ốm giữa đường | Điều phối thêm hộ tống mới; người mới nhận bàn giao ca tại điểm dừng |
| T44 | 9 | Bấm Kết thúc lượt khi còn ngựa trên xe | Từ chối, nói còn mấy con |
| T45 | 10 | Con ở hub 13 giờ chưa được khám | Lên đầu trang Theo dõi, tô đỏ |

## 4.F. Điểm còn hở, cần nhóm quyết

1. **Không có chỗ ghi sức khỏe khi ngựa ở hub hoặc chuồng.** Máy chủ nhận, màn hình chưa có; ngựa ở hub vì thế luôn bị tô Trễ khám sau 12 giờ.
2. **Chỉ dẫn ăn uống của khách không tới tay tổ lái.** Khách ghi chỉ dẫn và số bữa lúc đặt đơn, màn của tài xế và hộ tống không hiện.
3. **Khách không xem được nhật ký sức khỏe.** Dòng thời gian của khách chỉ có chặng, lưu trú, giao nhận.
4. **Ghi Ốm hoặc Nguy cấp không báo ai và không mở sự cố.** Chỉ "bỏ uống hai lần" mới báo điều phối. Đề xuất: Ốm và Nguy cấp báo ngay điều phối và gợi ý mở sự cố.
5. **Không ghi sức khỏe lúc nhận ngựa**, dù tài liệu yêu cầu khám khi nhận. Lúc quét chip chỉ có ô tình trạng, màn hình chưa hiện ô này. Nhịp tim, nhịp thở có ở máy chủ nhưng chưa có ô nhập.
6. **Nghỉ đêm không bị kiểm 10 giờ.** Điểm nghỉ đêm Tuy Hòa kế hoạch nghỉ 10 giờ nhưng chỉ bị kiểm 30 phút như trạm thường.
7. **Xuất phát không kiểm giờ kế hoạch**: có thể xuất phát trước hoặc sau nhiều giờ. Đi trễ không báo điều phối hay khách, chỉ thấy trong báo cáo.
8. **Chuyến không chuyển sang Đang chạy hay Đã xong** khi xe trục chạy (cũng nêu ở luồng 3).
9. **Khách không được báo** khi ngựa tới hub, khi xe trục chạy, khi tới hub đích; chỉ báo lúc nhận, qua cửa khẩu và giao.
10. **Chưa có màn báo tình trạng xe khi về trụ sở** (bình thường hay cần bảo dưỡng), dù tài liệu có.
11. **Không có vị trí xe theo thời gian thực**; vị trí là điểm dừng gần nhất đã bấm Tới nơi.
12. **Giấy không được kiểm lại ở cửa khẩu đơn xuất** (cũng nêu ở luồng 2).

---

# Luồng 5. Xử lý sự cố

**Từ lúc** tổ lái báo có chuyện bất thường khi lượt xe đang chạy **đến lúc** điều phối đóng sự cố và mọi chi phí phát sinh đã được duyệt.

**Người tham gia:**

- **Tài xế, hộ tống**: báo sự cố, chụp ảnh, ghi chi phí, xác nhận ngựa đủ sức đi tiếp.
- **Điều phối viên**: lập phương án, thực hiện đổi xe hoặc đưa về hub, báo đã xử lý xong, đóng sự cố.
- **Quản lý**: duyệt hoặc từ chối phương án, duyệt chi phí.
- **Hệ thống**: tự mở sự cố khi không gặp người nhận, tự xác định ai chịu chi phí, tự tính phí giữ ngựa.

**Trạng thái đơn không đổi vì sự cố.** Đơn vẫn là "Đang vận chuyển"; sự cố có trạng thái riêng của nó.

**Không nằm trong luồng này:** hủy hoặc dời cả chuyến trục (luồng 1 và 3), từ chối nhận ngựa tại điểm đón và trả ngựa vì chưa trả số dư (luồng 6).

## Từ dùng trong luồng 5

| Từ | Nghĩa |
|---|---|
| Sự cố cả lượt | Ảnh hưởng mọi con trên xe, ví dụ hỏng xe |
| Sự cố một con | Chỉ ảnh hưởng một con ngựa, ví dụ đau bụng |
| Nguyên nhân | Do ai hoặc do đâu. **Nguyên nhân quyết định ai trả tiền** |
| Phương án | Cách xử lý do điều phối lập, quản lý duyệt. Một sự cố có thể có nhiều phiên bản phương án |
| Bên xử lý | Đối tác thực hiện: thú y, chuồng gửi, cứu hộ |
| Xác nhận đủ sức | Tổ lái xác nhận ngựa khỏe để đi tiếp |
| Hạn mức chi khẩn cấp | Số tiền khách cho phép công ty chi thay khi khẩn cấp mà chưa kịp hỏi. Khách điền lúc đặt đơn, có thể bỏ trống |
| Giữ ngựa có phí | Ngựa không giao được, công ty giữ và chăm ở hub, tính tiền theo ngày |

## 5.A. Đi từng bước

Luồng 5 có ba câu chuyện. Câu chuyện 1 là quy trình chung; câu chuyện 2 và 3 là hai trường hợp có thao tác riêng.

### Câu chuyện 1. Ngựa đau bụng trên đường

**Bước 1. Tổ lái báo sự cố.** Để bắt đầu, tài xế hoặc hộ tống mở lượt xe đang chạy, bấm **Báo sự cố** và chọn:

- **phạm vi**: cả lượt xe, hoặc một con cụ thể trên lượt;
- **loại**: Sức khỏe, Hỏng xe, Tai nạn, Chậm trễ, Giấy tờ, Khác;
- **nguyên nhân**: Lỗi công ty, Tình trạng ngựa, Giấy tờ của khách, Lỗi đại lý, Bất khả kháng, Người nhận vắng;
- **tiêu đề và mô tả**, bắt buộc cả hai.

Sự cố ở trạng thái **Mới báo** và hiện trên trang **Sự cố** của điều phối viên và quản lý.

**Bước 2. Tổ lái chụp ảnh hiện trường.** Điện thoại cho phép thì ảnh tự kèm vị trí; không cho phép thì ảnh vẫn gửi được. Thêm ảnh được cho tới khi sự cố đóng.

**Bước 3. Điều phối viên lập phương án.** Điều phối gọi cho tổ lái, rồi chọn một trong sáu cách xử lý, ghi chỉ đạo (bắt buộc), chi phí dự kiến (không bắt buộc) và bên xử lý nếu cần. Sự cố chuyển sang **Chờ duyệt phương án**.

| Cách xử lý | Dùng khi | Bên xử lý | Hệ thống làm gì sau khi duyệt |
|---|---|---|---|
| Đi tiếp | Nhẹ, nghỉ rồi đi | Không có | Không có thao tác thêm |
| Thú y điều trị | Cần bác sĩ | **Bắt buộc** thú y | Không có thao tác thêm |
| Gửi chuồng gần | Phải dừng lâu | **Bắt buộc** chuồng gửi | Không có thao tác thêm |
| Đổi xe | Xe không chạy được | Cứu hộ, không bắt buộc | Mở ô chọn xe thay (câu chuyện 2) |
| Về hub | Ngựa còn trên xe nhưng không nên đi tiếp tới nơi giao | Cứu hộ, không bắt buộc | Mở nút **Đưa về hub** (điểm 5.13) |
| Hủy chặng | Không đi tiếp được | Không có | Không có thao tác thêm |

**Bước 4. Quản lý quyết định phương án.**

- **Duyệt**: sự cố chuyển sang **Đang xử lý**, phương án có hiệu lực.
- **Từ chối**: phải ghi lý do. Sự cố vẫn ở Chờ duyệt phương án, điều phối lập phiên bản tiếp theo.

**Bước 5. Tổ lái thực hiện và ghi chi phí.** Ngay trong sự cố, tổ lái ghi từng khoản: loại chi (thú y, cứu hộ, sửa xe, gửi chuồng, cầu đường, nhiên liệu, chờ, khác), mô tả, số tiền, **đã hỏi ý kiến khách hay chưa**, kèm biên lai. Hệ thống tự ghi ai chịu theo nguyên nhân của sự cố (bảng 5.B).

**Bước 6. Tổ lái xác nhận ngựa đủ sức đi tiếp.** Nút **Đủ sức đi tiếp** dùng được khi phương án đã duyệt và đã qua **ít nhất 30 phút kể từ lúc báo sự cố**. Chưa đủ 30 phút thì hệ thống từ chối.

**Bước 7. Điều phối viên bấm Đã xử lý xong.** Với sự cố sức khỏe hoặc sự cố của một con, hệ thống đòi đã có xác nhận ở bước 6. Phương án chuyển **Đã xong**, sự cố chuyển **Đã xử lý**.

**Bước 8. Quản lý duyệt chi phí.** Khoản khách hoặc người nhận chịu chỉ duyệt được khi **đã hỏi khách**, hoặc còn nằm trong hạn mức chi khẩn cấp (điểm 5.18).

**Bước 9. Điều phối viên đóng sự cố.** Đây là bước bắt buộc: **đơn còn sự cố chưa đóng thì không quyết toán được** (luồng 6).

### Câu chuyện 2. Xe trục hỏng, phải đổi xe

**Bước 10.** Tài xế báo sự cố cả lượt, loại Hỏng xe, nguyên nhân Lỗi công ty. Điều phối lập phương án **Đổi xe**, quản lý duyệt.

**Bước 11.** Điều phối chọn xe thay. Hệ thống chỉ nhận xe:

- khác xe đang hỏng và đang ở tình trạng hoạt động;
- không bận lượt khác trong phần hành trình còn lại;
- đủ ngăn cho vị trí ngăn cao nhất đang dùng.

**Bước 12.** Hệ thống đổi xe ngay trên lượt đang chạy. Điểm dừng, tổ lái, danh sách ngựa và số ngăn **giữ nguyên**; không cần ký giao nhận lại. Xe thay không phải xuất phát từ chỗ xe cũ, vì là xe cứu ứng.

**Bước 13.** Điều phối xử lý xong và đóng như câu chuyện 1. Sự cố hỏng xe cả lượt **không** đòi xác nhận đủ sức.

### Câu chuyện 3. Tới nơi giao mà không gặp người nhận

**Bước 14.** Xe tới điểm giao, không thấy người nhận. Tổ lái bấm **Bắt đầu chờ người nhận**. Đồng hồ chờ miễn phí **120 phút** chạy từ lúc bấm.

**Bước 15.** Trong 120 phút, người nhận tới thì giao bình thường. **Hết 120 phút**, tổ lái bấm **Không gặp người nhận**; quên bấm thì hệ thống tự ghi (kiểm 5 phút một lần). Khi đó:

- lần giao được ghi là **Quá giờ**;
- chặng giao của từng con chưa nhận được ghi **Thất bại**;
- hệ thống tự mở **mỗi con một sự cố**, loại Quá giờ chờ, nguyên nhân Người nhận vắng;
- điều phối viên nhận thông báo.

**Bước 16.** Xe đi tiếp tới điểm dừng hub phía sau của lượt, mang theo con chưa giao được. Điều phối lập phương án cho từng sự cố (thường là **Về hub**), quản lý duyệt; đây là phần hồ sơ. **Không** cần bấm Đưa về hub: chặng đã Thất bại ở bước 15 nên hệ thống đã cho giao con đó vào hub.

**Bước 17.** Xe tới hub, tổ lái giao ngựa vào hub. Hệ thống mở **giữ ngựa có phí** tối đa **3 ngày** và báo khách: ngựa đang ở đâu, giữ tới khi nào, phí mỗi ngày **1.000.000 đ (số mẫu)**, người nhận chịu. Lượt xe chỉ kết thúc được sau khi đã giao hết ngựa vào hub.

**Bước 18.** Điều phối hẹn lại với người nhận và lập **chặng giao mới** từ hub. Chặng mới ghi rõ thay cho chặng thất bại. Có thể giao lại hoặc để người nhận tới hub lấy.

**Bước 19.** Khi ngựa rời hub, hệ thống tự ghi phí giữ theo số ngày. **Quá 3 ngày chưa ai nhận**: 8 giờ sáng **mỗi ngày**, hệ thống nhắc khách và quản lý, kèm số phí đã cộng dồn.

**Ví dụ phí giữ.** Ngựa vào hub 14:00 thứ Hai, rời hub 10:00 thứ Năm: 2 ngày 20 giờ, tính 3 ngày, phí 3.000.000 đ.

**Trường hợp gần giống:** khách chọn **tự tới hub nhận ngựa** nhưng quá giờ hẹn chưa tới. Điều phối bấm gửi chuồng và **nhập số phí**; người nhận chịu. Phí này ghi ngay, không tính theo ngày.

## 5.B. Ai chịu chi phí

| Nguyên nhân của sự cố | Ai chịu |
|---|---|
| Lỗi công ty | Công ty |
| Lỗi đại lý | Công ty trả trước (đòi lại đại lý ngoài hệ thống) |
| Bất khả kháng | Công ty |
| Tình trạng ngựa | Khách |
| Giấy tờ của khách | Khách |
| Người nhận vắng | Người nhận |
| Chi phí không gắn sự cố (cầu đường, nhiên liệu) | Công ty |

## 5.C. Trạng thái của sự cố

| Trạng thái | Ai làm chuyển sang | Khi nào |
|---|---|---|
| Mới báo | Tổ lái, hoặc hệ thống | Vừa báo, chưa có phương án |
| Chờ duyệt phương án | Điều phối | Đã lập phương án |
| Đang xử lý | Quản lý | Đã duyệt phương án |
| Đã xử lý | Điều phối | Bấm Đã xử lý xong |
| Đã đóng | Điều phối | Bấm Đóng sự cố |

## 5.D. Các hạn trong luồng 5

| Hạn | Của ai | Đếm từ | Dài bao lâu | Quá hạn thì |
|---|---|---|---|---|
| Nghỉ và chăm trước khi xác nhận đủ sức (bước 6) | Tổ lái | Lúc báo sự cố | **Ít nhất** 30 phút (cấu hình) | Chưa đủ thì không xác nhận được. Không có hạn tối đa |
| Chờ người nhận miễn phí (bước 14) | Người nhận | Lúc tổ lái bấm Bắt đầu chờ | 120 phút (cấu hình) | Ghi giao thất bại, mở sự cố từng con, báo điều phối |
| Hệ thống tự ghi không gặp người nhận (bước 15) | Hệ thống | Sau khi hết 120 phút | Kiểm 5 phút một lần | Tự ghi thay tổ lái |
| Giữ ngựa ở hub sau giao thất bại (bước 17) | Người nhận | Lúc ngựa vào hub | 3 ngày (cấu hình) | Nhắc khách và quản lý 8:00 mỗi ngày; phí tiếp tục cộng |
| Duyệt phương án, xử lý, đóng sự cố | Điều phối, quản lý | — | **Không có hạn** | Không ai được nhắc; đơn không quyết toán được tới khi đóng |

## 5.E. Các điểm nghiệp vụ

### Báo sự cố

- **5.1** Chỉ tài xế, hộ tống **trong tổ lái của lượt đó** báo được. Người đã được rút khỏi lượt thì không.
- **5.2** Màn hình chỉ hiện phần sự cố khi **lượt đang chạy**.
- **5.3** Sự cố **cả lượt**: mỗi lúc chỉ một sự cố chưa xử lý. Sự cố **từng con**: không giới hạn.
- **5.4** Tiêu đề tối đa 200 ký tự, mô tả tối đa 4.000 ký tự, bắt buộc cả hai.
- **5.5** Ảnh hiện trường: có đủ giờ và vị trí, hoặc không có cả hai. Thêm được tới khi đóng sự cố.
- **5.6** Loại **Quá giờ chờ** chỉ hệ thống tạo, người báo không chọn được.

### Phương án

- **5.7** Thú y điều trị **phải** có đối tác thú y; Gửi chuồng gần **phải** có đối tác chuồng. Vai trò của đối tác phải còn hiệu lực hôm nay.
- **5.8** Đi tiếp và Hủy chặng không được chọn bên xử lý.
- **5.9** Lập phương án mới khi phương án cũ đang chờ duyệt: phương án cũ thành **Đã thay**.
- **5.10** Quản lý chỉ duyệt hoặc từ chối được **phiên bản mới nhất** đang chờ. Từ chối phải có lý do.
- **5.11** Mỗi sự cố chỉ **một** phương án hiệu lực. Duyệt phiên bản mới thì phiên bản đã duyệt trước đó thành Đã thay. Trong lúc chờ duyệt phiên bản mới, sự cố vẫn là Đang xử lý, nhưng **Đổi xe và Đưa về hub bị khóa** vì hệ thống chỉ thực hiện phiên bản mới nhất.

### Đổi xe và đưa về hub

- **5.12** Đổi xe chỉ khi lượt **đang chạy** và phương án Đổi xe đã duyệt. Giữ nguyên lượt, điểm dừng, ngựa, tổ lái.
- **5.13** Nút Đưa về hub dành cho con **còn đang trên xe, chặng chưa thất bại**: sự cố một con thì chỉ con đó, sự cố cả lượt thì mọi con. Lượt phải còn điểm dừng hub chưa tới, nằm sau nơi xuống của con đó. Bấm xong, chặng thành Thất bại, xe tới hub thì giao ngựa vào hub.
- **5.14** Về hub do **người nhận vắng**: giữ có phí. Về hub do **nguyên nhân khác**: tập kết, không phí, chờ lập chặng mới.

### Xử lý xong và đóng

- **5.15** Sự cố sức khỏe hoặc sự cố của một con: phải có xác nhận đủ sức mới báo xử lý xong. Sự cố khác thì không cần.
- **5.16** Chưa có phương án được duyệt thì không báo xử lý xong được. Chỉ đóng được sự cố đã xử lý.
- **5.17** Sự cố của một con chặn quyết toán **đơn của con đó**. Sự cố cả lượt chặn quyết toán **mọi đơn có ngựa trên lượt**.

### Chi phí

- **5.18** Duyệt khoản khách hoặc người nhận chịu khi **một trong hai**:
  - đã hỏi khách;
  - tổng các khoản đã duyệt mà chưa hỏi của đơn, cộng khoản này, **không vượt hạn mức chi khẩn cấp**. Khách không điền hạn mức thì luôn phải hỏi.
- **5.19** Khoản công ty chịu: duyệt không cần điều kiện trên.
- **5.20** Người trả do hệ thống xác định theo nguyên nhân; người ghi không chọn được.
- **5.21** Khoản khách hoặc người nhận chịu phải gắn với một đơn. Vì vậy phải báo **sự cố của một con**, không báo cả lượt.
- **5.22** Thêm biên lai được tới khi quản lý duyệt khoản đó.
- **5.23** Ghi chi phí **không** tự trừ tiền khách. Khoản đã duyệt mới thành phụ phí ở luồng 6.

### Không gặp người nhận

- **5.24** Phải bấm Bắt đầu chờ thì đồng hồ mới chạy. Chưa hết 120 phút không ghi Không gặp được.
- **5.25** Phí giữ tính theo ngày, **lẻ ngày tính tròn một ngày**, ít nhất một ngày. Mức phí lấy lúc ngựa rời hub.
- **5.26** Không có phí cho thời gian xe chờ: trong 120 phút là miễn phí, sau 120 phút xe đi.

## 5.F. Tình huống và cách hệ thống xử lý

| # | Ở bước | Tình huống | Hệ thống sẽ |
|---|---|---|---|
| T46 | 1 | Xe đang có sự cố cả lượt chưa xử lý, tài xế báo thêm sự cố cả lượt | Từ chối. Báo sự cố của một con thì được |
| T47 | 1 | Người ngoài tổ lái báo sự cố | Từ chối |
| T48 | 3 | Chọn Thú y điều trị nhưng không chọn thú y | Từ chối, nói cần chọn bên xử lý |
| T49 | 3 | Chọn thú y đã hết hiệu lực hợp tác | Từ chối |
| T50 | 4 | Quản lý từ chối mà không ghi lý do | Từ chối thao tác |
| T51 | 4 | Quản lý đang xem phiên bản 1, điều phối vừa lập phiên bản 2 | Không cho duyệt phiên bản 1 |
| T52 | 6 | Xác nhận đủ sức sau 15 phút | Từ chối, nói chưa đủ 30 phút |
| T53 | 7 | Báo xử lý xong khi ngựa chưa được xác nhận đủ sức | Từ chối |
| T54 | 8 | Hạn mức khẩn cấp 2 triệu, đã duyệt 1,5 triệu chưa hỏi khách, duyệt thêm 800 nghìn chưa hỏi | Từ chối vì vượt hạn mức; phải hỏi khách |
| T55 | 8 | Khách không điền hạn mức, khoản thú y 300 nghìn chưa hỏi khách | Từ chối |
| T56 | 11 | Xe thay chỉ có 2 ngăn, lượt đang dùng ngăn số 4 | Từ chối |
| T57 | 15 | Tài xế quên bấm Không gặp người nhận | Hệ thống tự ghi trong vòng 5 phút sau khi hết 120 phút |
| T58 | 15 | Bấm Không gặp người nhận ở phút thứ 90 | Từ chối |
| T59 | 3 | Phương án Về hub đã duyệt nhưng lượt không còn hub phía sau nơi xuống | Từ chối Đưa về hub |
| T60 | 17 | Bấm Kết thúc lượt khi con giao hụt còn trên xe | Từ chối |
| T61 | 19 | Ngựa ở hub ngày thứ 5, chưa ai nhận | Nhắc khách và quản lý 8:00 mỗi ngày, phí tiếp tục cộng |
| T62 | 9 | Quản lý muốn quyết toán khi sự cố mới ở Đã xử lý | Không cho; phải đóng sự cố |

## 5.G. Điểm còn hở, cần nhóm quyết

**Gây kẹt đơn thật, nên sửa trước:**

1. **Đã sửa** · HEALTH mới phải chọn ngựa. HEALTH cũ vẫn xác nhận được.
2. **Đã sửa** · Tổ lái xử lý sự cố sau khi hoàn tất lượt. Điều phối xác nhận thay với kết quả thú y.
3. **Đã sửa** · Chỉ HEALTH và ACCIDENT có ngựa yêu cầu xác nhận đủ sức.
4. **Đã sửa** · Điều phối có form ghi chi phí và tổ lái còn ghi được sau khi kết thúc lượt.
5. **Không còn hub phía sau thì con giao hụt không có chỗ vào**: không đưa về hub được và lượt cũng không kết thúc được. Điều phối phải nhớ để hub là điểm dừng cuối của lượt giao.
6. **Đã sửa** · Máy chủ từ chối Đưa về hub cho HANDOVER_TIMEOUT. Màn hình ẩn thao tác.

**Thiếu thông báo:**

7. **Báo sự cố, lập, duyệt, từ chối phương án đều không báo ai.** Điều phối, quản lý phải tự mở trang; tổ lái phải tự tải lại.
8. **Khách không biết ngựa gặp sự cố.** Ứng dụng khách không hiện sự cố; chỉ có thông báo khi ngựa vào hub giữ có phí.

**Tiền và trách nhiệm:**

9. **Tài xế chọn nguyên nhân, tức là chọn ai trả tiền, và không ai sửa được.** Ô chọn mặc định là Tình trạng ngựa (khách trả). Đề xuất: quản lý chốt nguyên nhân khi duyệt phương án.
10. **"Đã hỏi khách" chỉ là lựa chọn của nhân viên.** Khách không có nút đồng ý hay từ chối, không lưu bằng chứng.
11. **Duyệt phương án không xét hạn mức khẩn cấp** với chi phí dự kiến; chỉ xét khi duyệt chi phí, lúc tiền đã tiêu.
12. **Lỗi đại lý chỉ ghi công ty chịu**, chưa theo dõi khoản phải đòi lại.
13. **Chưa có phí chờ** dù tài liệu 59 bảng nói phí chờ và phí gửi chuồng do người nhận chịu. Hiện chỉ có phí giữ ở hub. Cần chọn một.

**Vận hành:**

14. **Sự cố không chặn xe.** Đang có sự cố, tài xế vẫn bấm Tới nơi, Rời đi được. Tài liệu có bước "mở lại thao tác chặng", nghĩa là trước đó phải khóa.
15. **Gửi chuồng gần, Thú y điều trị, Hủy chặng chỉ là chữ.** Duyệt xong hệ thống không mở lưu trú, không đổi chặng. Câu báo cho điều phối gợi ý "gửi chuồng hoặc đưa về hub" nhưng gửi chuồng không có thao tác.
16. **Đổi xe không ký lại giao nhận và không đưa xe hỏng sang bảo dưỡng.** Tài liệu yêu cầu hộ tống ký tại chỗ; xe hỏng vẫn có thể được xếp lượt khác.
17. **Xác nhận đủ sức không cần ảnh, không cần thú y**; tài xế cũng bấm được, dù tài liệu ghi thú y hoặc hộ tống kèm ảnh.
18. **30 phút đếm từ lúc báo**, không đếm từ lúc ngựa được nghỉ.
19. **Ghi sức khỏe Ốm hoặc Nguy cấp không mở sự cố** (đã nêu ở luồng 4).
20. **Máy chủ không kiểm lượt đang chạy khi báo sự cố**; hiện chỉ màn hình chặn.
21. **Quá 3 ngày giữ ngựa chỉ nhắc**, không có bước kết thúc (trả về người gửi, thanh lý hợp đồng…).
22. **Không có mức độ nặng nhẹ** của sự cố, nên ngựa nguy kịch và trễ 10 phút nằm chung một danh sách.
23. **Loại Chậm trễ chưa nối với cách xử lý chuyến trễ** (mục 3.F đã ghi nhận, chưa làm).

---

# Luồng 6. Bàn giao và nghiệm thu

**Từ lúc** tổ lái đứng trước con ngựa ở nơi nhận **đến lúc** đơn được đóng (Hoàn thành) và khách đánh giá.

**Người tham gia:**

- **Tài xế, hộ tống**: quét chip, chụp biên bản, nhận và trả giấy gốc, từ chối nhận, trả ngựa.
- **Điều phối viên**: giao nhận tại hub với khách tự chở, tự tới lấy; ghi thay đại lý.
- **Khách**: trả số dư, trả phụ phí, đánh giá.
- **Quản lý**: duyệt chi phí, lập khoản thu thêm, đóng đơn, hoàn tiền.
- **Hệ thống**: lập số dư, đổi trạng thái đơn, tự đóng đơn không phát sinh.

**Nối với luồng khác:** xe đi đến các điểm thế nào xem luồng 4; không gặp người nhận và chi phí sự cố xem luồng 5.

## Từ dùng trong luồng 6

| Từ | Nghĩa |
|---|---|
| Lần giao nhận | Một lần ngựa đổi tay ở một điểm. Có thể gồm nhiều con |
| Nơi nhận ngựa | Nơi công ty nhận ngựa lần đầu: nhà khách, hub (khách tự chở), hoặc cửa khẩu (đơn nhập) |
| Biên bản | Giấy giao nhận hai bên đã ký tay. Tổ lái chụp ảnh và tải lên |
| Số dư | Tổng báo giá đã chấp nhận trừ số đã trả. Thường là phần còn lại sau cọc |
| Còn nợ | Tổng báo giá + chi phí khách hoặc người nhận chịu đã duyệt − đã trả + đã hoàn. Số âm là khách trả dư |
| Khoản thu thêm | Khoản thu các chi phí phát sinh đã duyệt |
| Đóng đơn | Đơn từ Đã giao sang Hoàn thành |

## 6.A. Đi từng bước

### Câu chuyện 1. Một đơn liên tỉnh, công ty đón và giao tận nơi

**Bước 1. Nhận ngựa ở nhà khách.** Để bắt đầu, xe tới điểm đón, tổ lái bấm **Nhận ngựa**. Hệ thống liệt kê các con cần nhận ở điểm này. Với từng con:

- tổ lái **quét hoặc nhập số chip**; hệ thống so với chip trong hồ sơ ngựa;
- **đúng chip**: màn hình hiện số khoang để dẫn ngựa vào;
- **sai chip**: con đó không được nhận trong lần này. Quét lại được;
- con không đủ điều kiện đi: bấm **Ghi đón hụt** (câu chuyện 2).

**Bước 2. Nhận giấy gốc và ký biên bản.** Màn hình liệt kê các giấy đã duyệt của đơn; tổ lái đánh dấu từng giấy đã nhận hoặc không nhận bản gốc. Sau đó **chụp biên bản đã ký và tải lên**. Chưa có biên bản thì nút Hoàn tất bị khóa.

**Bước 3. Hoàn tất nhận ngựa.** Cần ít nhất một con đúng chip. Mỗi con còn lại phải được ghi đón hụt trước khi hoàn tất. Từ lúc này **công ty giữ ngựa**, người bấm là người phụ trách. Khách nhận thông báo **Đã nhận ngựa**.

**Bước 4. Khách trả số dư.** Cùng lúc, hệ thống lập khoản số dư và báo khách. Khách có **30 phút kể từ lúc hoàn tất nhận ngựa** để trả qua VNPAY. Đơn nhiều con nhận ở nhiều lần thì chỉ lập số dư **một lần**, ở lần nhận đầu tiên, cho cả đơn. Trong 30 phút này xe đứng chờ.

**Bước 5. Xe rời nơi nhận.** Hệ thống chỉ cho rời khi mọi đơn có ngựa vừa lên xe ở đây đã trả đủ. Xe rời thì đơn sang **Đang vận chuyển**, khách hết quyền tự hủy. Quá 30 phút chưa trả: câu chuyện 3.

**Bước 6. Giao vào hub, nhận từ hub.** Mỗi lần vào hoặc ra hub đều quét chip từng con và lưu tên người bấm thay cho chữ ký; **không cần biên bản**. Giao vào hub thì mở lưu trú tập kết; nhận ra khỏi hub thì đóng lưu trú đó.

**Bước 7. Giao cho người nhận.** Xe tới điểm giao, tổ lái bấm **Giao ngựa**, quét chip, ghi tình trạng và tải ảnh từng con, đánh dấu giấy gốc đã trả, tải biên bản đã ký, ghi họ tên người nhận và nhập mã hoặc kiểm xác nhận của khách, **Hoàn tất giao nhận**. Khách nhận thông báo **Đã giao ngựa**. Không gặp người nhận: luồng 5.

**Bước 8. Đơn sang Đã giao.** Khi **con cuối cùng** còn trong đơn được giao, đơn tự sang **Đã giao**. Con đã bị loại thì không tính.

**Bước 9. Quyết toán.** Quản lý mở đơn, xem bảng quyết toán: tổng báo giá, chi phí khách chịu đã duyệt, còn nợ. Rồi:

- duyệt từng chi phí còn lại (điều kiện ở 5.18);
- chọn các khoản khách chịu đã duyệt, bấm **lập khoản thu thêm**; khách trả qua VNPAY trong trang đơn của mình.

**Bước 10. Đóng đơn.** Quản lý bấm đóng, hoặc hệ thống tự đóng:

| Cách đóng | Điều kiện |
|---|---|
| Quản lý đóng, bất kỳ lúc nào sau khi giao | Còn nợ **bằng 0**; không còn sự cố chưa đóng; không còn chi phí khách hoặc người nhận chịu chưa duyệt; không còn khoản hoàn đang chờ |
| Hệ thống tự đóng, 00:30 mỗi ngày | Như trên, **và** đơn không có bất kỳ chi phí nào, **và** đã giao được ít nhất **7 ngày** |

Đơn sang **Hoàn thành**, khách nhận thông báo Đã đóng đơn.

**Bước 11. Đánh giá.** Từ lúc đơn Đã giao, khách chấm 1 đến 5 sao kèm nhận xét (không bắt buộc). Mỗi đơn **một lần**, không sửa được.

### Câu chuyện 2. Một con không được nhận

**Bước 12.** Tổ lái ghi đón hụt cho từng con. Chọn nguyên nhân, ghi diễn giải và chụp ảnh. Nếu người gửi vắng, ghi kết quả ba cuộc gọi và chờ đủ thời gian. Hệ thống gỡ các chặng kế hoạch của con hụt, giữ chỗ trên chuyến và báo khách, điều phối, quản lý.

Quản lý chốt bên chịu. Khách hoặc điều phối chọn A bỏ con, B đón lại, C tự chở tới hub hoặc D đổi chuyến khi đơn chưa nhận con nào. Đơn chạy thẳng có thể hẹn ngày đón mới. A loại con khỏi đơn. Các lựa chọn làm thay đổi chi phí cập nhật báo giá thay thế.

Các con khác vẫn nhận bình thường. Nếu đã ghi hụt cho mọi con tại điểm và không nhận con nào, lần giao nhận kết thúc **Không thành**. Xe có thể rời khi tất cả ngựa ở điểm này đã được xử lý. Tổ lái vẫn ghi hụt được khi xe khác đã nhận ngựa của đơn.

### Câu chuyện 3. Quá 30 phút chưa trả số dư

**Bước 13.** Hệ thống nhắc số dư ở phút 0, 15 và 25. Điều phối gọi khách và người liên hệ khẩn cấp, ghi kết quả. Điều phối có thể gia hạn một lần nếu xe vẫn kịp tới hub và các con khác không vượt thời gian ngồi xe. Khách mở trang VNPAY trước khi hết hạn mà chưa có kết quả thì hệ thống chờ thêm 5 phút.

**Bước 14.** Hệ thống trong một lần bấm:

- Nếu còn người nhận lại ở điểm đón, ghi trả ngựa và bàn giao giấy gốc. Dùng kết quả chip lúc nhận. Đơn sang **Cần chọn lại chuyến**, nhả chỗ và hủy các chặng kế hoạch. Số dư vẫn thanh toán được. Hết 30 ngày thì hủy đơn.
- Nếu không có người nhận lại, điều phối xác nhận đưa ngựa về hub. Đơn nhập từ cửa khẩu vẫn về hub.
- Tại hub, nhắc T − 3 giờ. Nếu chưa trả, nhả chỗ và gỡ khỏi lượt xe ở T − 1 giờ. Phí giữ tính từ T. Ngựa vẫn được chăm sóc sau khi hủy.

Tiền VNPAY về **sau** khi đơn đã hủy: không ghi vào đơn, báo quản lý để đối soát và hoàn.

### Câu chuyện 4. Khách tự chở tới hub, tự tới hub nhận

**Bước 15. Nhận tại hub.** Điều phối hẹn giờ trước giờ chạy của chuyến, khách được báo. Khi khách tới, **điều phối viên** (không phải tổ lái) nhập chip và bấm nhận. Hệ thống đòi: đã xếp lượt xe trục cho con đó, bảo hiểm đã xác nhận, đủ giấy bắt buộc, chip đúng. Xong thì lập số dư như bước 4. **Xe trục chỉ rời hub khi đơn đã trả đủ**, nếu ngựa đã lên xe.

**Bước 16. Trả tại hub.** Ngựa tới hub đích, điều phối hẹn giờ, khách được báo. Người nhận tới, điều phối nhập chip và bấm trả. Con cuối thì đơn Đã giao. Quá giờ hẹn thì gửi chuồng, người nhận chịu phí (luồng 5).

### Câu chuyện 5. Đơn quốc tế

**Bước 17. Đơn xuất.** Ở cửa khẩu, tổ lái bấm **Bàn giao với đại lý**, quét chip và tải biên bản đã ký để hoàn tất. Chặng của đại lý tự chạy, khách được báo. Đại lý giao xong ở nước ngoài thì **điều phối bấm Đại lý đã giao**; đủ các con thì đơn Đã giao.

**Bước 18. Đơn nhập.** Tổ lái nhận ngựa từ đại lý ở cửa khẩu, quét chip, đánh dấu bản gốc đã nhận và tải biên bản đã ký. Hệ thống **kiểm lại đủ giấy bắt buộc**, gồm giấy vận chuyển mẫu 14a; thiếu thì không nhận. Sau đó lập số dư, chờ 30 phút như bước 4.

## 6.B. Bảng các lần giao nhận

| Lần giao nhận | Ở đâu | Ai bấm | Chip | Biên bản | Giấy gốc | Sau đó |
|---|---|---|---|---|---|---|
| Nhận ngựa | Nhà khách | Tổ lái | Từng con | **Bắt buộc** | Đánh dấu đã nhận | Lập số dư |
| Nhận tại hub (khách tự chở) | Hub | Điều phối | Từng con | Không | Không | Lập số dư |
| Vào hub, ra hub | Hub | Tổ lái | Từng con | Không | Không | Mở, đóng lưu trú |
| Gửi chuồng đêm, nhận lại | Trạm nghỉ, cửa khẩu | Tổ lái | Từng con | Không | Không | Đổi bên giữ (luồng 4) |
| Bàn giao ca | Điểm dừng bất kỳ | Hộ tống mới | Từng con | Không | Không | Đổi người phụ trách |
| Bàn giao đại lý (xuất) | Cửa khẩu | Tổ lái | Từng con | **Bắt buộc** | Không | Chặng đại lý chạy |
| Nhận từ đại lý (nhập) | Cửa khẩu | Tổ lái | Từng con | **Bắt buộc** | Đánh dấu đã nhận | Lập số dư |
| Giao người nhận | Điểm giao | Tổ lái | Từng con | **Bắt buộc** | Đánh dấu đã trả | Con cuối: Đã giao |
| Trả tại hub (tự tới lấy) | Hub | Điều phối | Từng con | Không | Không | Con cuối: Đã giao |
| Đại lý đã giao | Nước ngoài | Điều phối ghi thay | Không | Không | Không | Con cuối: Đã giao |
| Trả ngựa quá hạn | Nhà khách, cửa khẩu | Tổ lái | Lấy lại kết quả cũ | Không | Không | Hủy đơn, mất cọc |

## 6.C. Các hạn trong luồng 6

| Hạn | Của ai | Đếm từ | Dài bao lâu | Quá hạn thì |
|---|---|---|---|---|
| Trả số dư (bước 4) | Khách | Lúc hoàn tất nhận ngựa | 30 phút (cấu hình 5–240) | Tổ lái được quyền trả ngựa. Khách **vẫn trả được** cho tới khi tổ lái bấm trả |
| Chờ kết quả VNPAY mở trước hạn (bước 13) | Hệ thống | Hết 30 phút | 5 phút (cài đặt máy chủ) | Cho trả ngựa |
| Mỗi lần mở trang VNPAY | Khách | Lúc bấm trả | 15 phút | Mở lại |
| Tự đóng đơn (bước 10) | Hệ thống | Lúc đơn sang Đã giao | 7 ngày (cấu hình 1–30), xét lúc 00:30 | Đơn có phát sinh thì không tự đóng, chờ quản lý |
| Trả khoản thu thêm (bước 9) | Khách | Lúc quản lý lập | **Không có hạn** | Đơn không đóng được, không ai được nhắc |
| Chuyển tiền hoàn | Quản lý | Lúc tạo khoản hoàn | **Không có hạn** | Đơn không đóng được |
| Đánh giá (bước 11) | Khách | Lúc đơn Đã giao | **Không có hạn** | — |
| Khiếu nại | Khách | Lúc giao ngựa | 7 ngày theo tài liệu, **chưa có trong hệ thống** | — |

## 6.D. Ví dụ tiền, tiếp theo ví dụ ở luồng 1

Đơn hai con, tổng 28.733.450 đ, đã trả cọc 8.761.000 đ.

| Trường hợp | Cách tính | Kết quả |
|---|---|---:|
| Nhận đủ hai con | 28.733.450 − 8.761.000 | Số dư 19.972.450 |
| Không nhận một con | Bỏ dòng cước của con đó: 28.733.450 − 12.600.000 − 8.761.000 | Số dư 7.372.450 |
| Phát sinh 3.000.000 phí giữ ngựa đã duyệt, đã trả đủ số dư | 28.733.450 + 3.000.000 − 28.733.450 | Còn nợ 3.000.000 |

Dòng hai: loại một con thì bỏ cước trục, dịch vụ và phí bảo hiểm **của con đó**. Cước đón, giao, nhiên liệu, thủ tục và lệ phí tính theo đơn nên giữ nguyên. Đơn **chạy thẳng** không có cước theo con, nên loại một con khoang thường gần như không giảm tiền.

## 6.E. Các điểm nghiệp vụ

### Giao nhận

- **6.1** Chỉ giao nhận khi xe **đang ở điểm đó**: đã bấm Tới nơi, chưa bấm Rời đi. Người bấm phải trong tổ lái.
- **6.2** So chip sau khi bỏ khoảng trắng và dấu gạch, không phân biệt hoa thường. Sai thì con đó không được tính.
- **6.3** Hoàn tất cần ít nhất một con đúng chip. Con sai chip ở lại với bên giao; mở lần giao nhận khác được khi xe chưa rời.
- **6.4** Nhận ở nhà khách, giao người nhận và giao nhận với đại lý ở cửa khẩu phải có **ảnh hoặc bản quét biên bản đã ký**.
- **6.5** Giấy gốc: nhận lúc đón, trả lúc giao. Chỉ liệt kê giấy **đã duyệt**. Đánh dấu không bắt buộc để hoàn tất.
- **6.6** Không sửa, không xóa được lần giao nhận đã hoàn tất.

### Không nhận ngựa

- **6.7** Ghi hụt khi đơn **Đã xác nhận** hoặc **Đang vận chuyển** và con vẫn chờ nhận. Lý do bắt buộc, tối đa 500 ký tự.
- **6.8** Báo giá thay thế giữ đơn giá đã chốt, bỏ các dòng của con bị loại, tự chấp nhận, không cần khách bấm.
- **6.9** Khách đã trả dư thì tạo khoản hoàn phần dư.

### Số dư

- **6.10** Số dư = tổng báo giá đang có hiệu lực − số đã trả. Bằng 0 thì không lập khoản.
- **6.11** Một đơn có thể có số dư ban đầu và số dư bổ sung khi nhận ở lượt đón lại. Trả qua VNPAY.
- **6.12** Chưa trả đủ **không** chặn xe đi đón, chỉ chặn xe **rời nơi nhận**.
- **6.13** Trả ngựa khi: đã hết 30 phút, đơn còn Đã xác nhận, và không còn phiên VNPAY mở trước hạn đang chờ kết quả (tối đa 5 phút).
- **6.14** Trả ngựa là một thao tác cho **mọi con** đã lên xe ở điểm đó, gồm cả các đơn khác cùng lên (cửa khẩu).

### Đã giao, quyết toán, đóng đơn

- **6.15** Đơn **Đã giao** khi mọi con còn trong đơn đã giao: giao tại nhà, trả tại hub, hoặc đại lý đã giao.
- **6.16** Khoản thu thêm chỉ lập khi đơn **Đã giao**; chọn chi phí đã duyệt, khách hoặc người nhận chịu, cùng tiền tệ, chưa gắn khoản thu. Số tiền do hệ thống cộng, quản lý không sửa được. Chỉ thu qua VNPAY, số tiền phải tròn đồng.
- **6.17** Còn nợ phải **đúng bằng 0**: thiếu thì chưa đóng, dư thì hoàn xong mới đóng.
- **6.18** Chi phí **công ty** chịu chưa duyệt không chặn quản lý đóng, nhưng có bất kỳ chi phí nào thì hệ thống **không tự đóng**.
- **6.19** Không có trạng thái Đang quyết toán: trong lúc quyết toán đơn vẫn là Đã giao.
- **6.20** Hoàn tiền: quản lý chuyển khoản ngoài, ghi mã chuyển khoản (không trùng, tối đa 100 ký tự) và ảnh chứng từ nếu có.
- **6.21** Phần thu hộ (lệ phí nhà nước, bảo hiểm) tách khỏi doanh thu.
- **6.22** Đánh giá 1–5 sao, một lần, khi đơn Đã giao hoặc Hoàn thành.

## 6.F. Tình huống và cách hệ thống xử lý

| # | Ở bước | Tình huống | Hệ thống sẽ |
|---|---|---|---|
| T63 | 1 | Quét sai chip một con trong hai con | Cho nhận con đúng; con sai không lên xe |
| T64 | 2 | Bấm Hoàn tất khi chưa tải biên bản | Từ chối |
| T65 | 3 | Không con nào đúng chip | Không hoàn tất được |
| T66 | 4 | Khách trả ở phút thứ 40, tổ lái chưa bấm trả ngựa | Nhận tiền, xe được rời |
| T67 | 5 | Khách chưa trả, tổ lái bấm Rời đi | Từ chối, nói mã đơn |
| T68 | 13 | Tổ lái bấm trả ngựa ở phút thứ 20 | Từ chối |
| T69 | 13 | Khách mở VNPAY ở phút 28, tổ lái bấm trả ngựa ở phút 32 | Từ chối tới phút 35, trừ khi VNPAY báo kết quả trước |
| T70 | 14 | Tiền VNPAY về sau khi đơn đã hủy | Không ghi vào đơn, báo quản lý hoàn tiền |
| T71 | 12 | Khách đã trả số dư cả hai con, rồi một con bị loại | Thay báo giá, tạo khoản hoàn phần dư, báo quản lý |
| T72 | 15 | Khách chở ngựa tới hub khi chưa xếp lượt xe trục | Từ chối nhận, nói xếp xe trước |
| T73 | 15 | Khách tới hub, đơn thiếu giấy | Từ chối nhận |
| T74 | 18 | Đơn nhập thiếu giấy 14a ở cửa khẩu | Không cho nhận |
| T75 | 8 | Đơn hai con, mới giao một con | Đơn vẫn Đang vận chuyển |
| T76 | 10 | Đóng đơn khi còn nợ hoặc còn sự cố chưa đóng | Từ chối |
| T77 | 10 | Đơn không phát sinh, giao 7 ngày trước, quản lý quên đóng | Tự đóng lúc 00:30 |
| T78 | 11 | Khách đánh giá lần hai | Từ chối |

## 6.G. Điểm còn hở, cần nhóm quyết

**Gây kẹt đơn thật, nên sửa trước:**

1. **Đã sửa** · Khách thanh toán được cả phí bên CONSIGNEE, với hạn, nhắc quá hạn và chặn gửi đơn mới.
2. **Đã sửa** · Phí hệ thống tính theo ngày được miễn điều kiện đã hỏi và không chiếm hạn mức khẩn cấp.
3. **Đã sửa** · Chặn HUB_OUT của đơn chưa trả đủ số dư. T − 1 giờ thì nhả chỗ, gỡ chặng kế hoạch và đóng HUB_OUT còn mở nếu không còn con nào chờ lên xe. Lưu trú giữ có phí, trả ngựa tại hub và các mốc nhắc, hủy, thông báo theo mục 9.
4. **Đã sửa** · Xe nhập được rời cửa khẩu về hub khi còn đơn chưa trả số dư. Chỉ đơn đã trả đủ mới sang Đang vận chuyển.
5. **Đã sửa** · Xe chỉ rời nơi nhận khi mọi con đã nhận hoặc ghi đón hụt. Quét sai chip còn cần ghi lý do đón hụt nếu không thể nhận.

   **Đã sửa** · Hết hạn chọn của một con không nhả chỗ của đơn khi con khác vẫn chờ đón. Chọn tự chở tới hub thì gỡ lượt đón lại đã xếp cho con đó.
6. **Đã sửa** phần ghi nhận và lựa chọn · Nếu hụt cả đơn, khách có thể chọn xử lý trong hạn hoặc chuyển sang Cần chọn lại chuyến khi hết hạn. Quản lý chốt bên chịu. Quy tắc tính tiền thay thế theo mục 8.4 thuộc bước tiếp theo.

**Bằng chứng giao nhận:**

7. **Đã sửa.** Mã 6 số sinh khi đơn chuyển Đang vận chuyển. Chủ đơn xem mã trên đơn và nhận email khi có cấu hình gửi thư. Tổ lái hoặc điều phối ghi họ tên người nhận và nhập mã đúng, hoặc dùng xác nhận của chủ đơn. Số lần nhập sai lấy từ cấu hình.
8. **Đã sửa.** Bắt buộc biên bản tại hub khi giao nhận với khách và tại cửa khẩu khi giao nhận với đại lý.
9. **Đã sửa.** Điều phối tải ảnh hoặc biên bản trước khi ghi đại lý đã giao. Khách nhận thông báo.
10. **Đã sửa.** Khi xác nhận trả ngựa quá hạn tại điểm đón hoặc cửa khẩu, ghi trả toàn bộ giấy gốc còn giữ của các đơn trong lần trả.
11. **Đã sửa.** Màn tổ lái hiện giấy gốc và cho đánh dấu đã nhận khi đại lý giao ngựa nhập cho xe công ty.
12. **Đã sửa.** Nhận ngựa thì đánh dấu từng giấy đã nhận hoặc không nhận bản gốc. Khi giao ngựa, xác nhận trả các bản gốc còn giữ của đơn theo lịch sử giao nhận đã hoàn tất. Khi chuyển ngựa ra cho đại lý, bàn giao các bản gốc đang giữ cùng ngựa.
13. **Đã sửa một phần.** Chủ đơn xem biên bản và ảnh đã lưu ở từng lần giao nhận hoàn tất trong hành trình. Tệp cũ gắn vào lần giao nhận chung của nhiều đơn cần được phân quyền theo từng đơn trước khi hiển thị.
14. **Đã sửa.** Trước khi hoàn tất nhận ở điểm đón, nhận từ đại lý, giao nhận tại hub với khách hoặc giao người nhận, từng con giao nhận phải có tình trạng và ít nhất một ảnh.
15. Chip có thể **gõ tay**; hệ thống chưa phân biệt quét máy với gõ.

**Tiền và đóng đơn:**

16. **Lập khoản thu thêm không báo khách**, không có hạn trả. **Hoàn tiền xong cũng không báo.**
17. **Đơn sang Đã giao không báo quản lý** để quyết toán.
18. **Chưa có khiếu nại.** Tài liệu ghi hạn 7 ngày từ lúc giao, hệ thống không có màn và quản lý đóng đơn được ngay. Đề xuất: 7 ngày đầu chỉ đóng khi khách xác nhận nhận ngựa ổn.
19. **Nút trả ngựa hiện ngay sau khi nhận**, dù chưa hết 30 phút (máy chủ vẫn chặn).
20. **Từ chối nhận không báo khách.** Chỉ báo quản lý; khách chỉ thấy báo giá đổi.
21. **Đã sửa** · Xe thứ hai vẫn có thể ghi đón hụt khi đơn đã Đang vận chuyển. Đơn giữ chuyến hiện tại, khách xử lý con hụt bằng A, B hoặc C theo điều kiện của lượt xe.
22. **Ô đánh giá hiện lại sau khi đã đánh giá** (tải lại trang), bấm thì bị từ chối.
23. **Đơn chạy thẳng loại bớt một con mà tiền gần như không giảm** (xem 6.D). Cần chốt có đúng ý không.
24. **Không có bước "người nhận nghiệm thu" riêng.** Nghiệm thu hiện là ảnh biên bản và việc giao xong. Nếu hội đồng hỏi "nghiệm thu ở đâu" thì chỉ vào bước 7 và bước 10.

**Đã sửa** · Quản lý xác nhận dịch vụ đã làm. Báo giá thay thế giữ dịch vụ đã làm và bảo hiểm khi khách chịu, áp sàn cọc đã trả, tính phí đón lại theo lượt xe và đối soát khoản hoàn. Lượt đón lại có khoản số dư bổ sung sau khi nhận nếu số dư đầu đã trả. Xe đón lại phải chờ trả đủ.
