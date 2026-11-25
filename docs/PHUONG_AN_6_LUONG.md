# Phương án sửa các điểm hở của 6 luồng

Ngày 25/11/2026, **bản 3** (sửa theo góp ý điểm 1, 2, 6; mục 12: mỗi con giao một nơi, cùng tuyến). **Đã duyệt 25/11/2026** (mục 7, 10, 12.8 theo cột Đề xuất), làm lần lượt theo đợt. Các điểm hở lấy từ [NGHIEP_VU_6_LUONG.md](NGHIEP_VU_6_LUONG.md).

**Thứ tự chọn quy trình:** IRT trước → nghiệp vụ ngoài (luật Việt Nam, hãng chở ngựa khác) → tự đề xuất. Chỗ nào quy trình của IRT trái với quyết định nhóm đã chốt thì **giữ quyết định đã chốt** và nói rõ.

## 1. Nguồn đã đọc

| Ký hiệu | Nguồn | Mức kiểm chứng |
|---|---|---|
| IRT Anh | [Điều kiện kinh doanh IRT (UK)](https://irt.com/media/1034/irt_uk_trading_conditions.pdf) | Đọc bản gốc 25/11 |
| IRT Đức | [Điều khoản chung IRT (Europe)](https://www.irt.com/media/1166/irt-ger_trading-conditions_english.pdf) | Đọc bản gốc 25/11 |
| IRT Úc | [Điều kiện kinh doanh 2006](https://www.irt.com/media/1031/irt_aus_trading_conditions_oct2006.pdf) | Đọc bản gốc 25/11 |
| IRT Mỹ | [Phiếu chỉ dẫn và điều kiện](https://www.irt.com/media/1151/sli-usa-export.pdf) | Đọc bản gốc 25/11 |
| LTM | Luật Thương mại 2005, [Điều 237](https://hethongphapluat.com/luat-thuong-mai-2005/dieu-237), [239](https://hethongphapluat.com/luat-thuong-mai-2005/dieu-239), [240](https://hethongphapluat.com/luat-thuong-mai-2005/dieu-240) | Đọc nguyên văn trên trang luật; **chưa kiểm chứng** đã sửa đổi hay chưa |
| BLDS | Bộ luật Dân sự 2015, [Điều 538](https://luathoanganh.vn/hoi-dap-luat-dan-su/giao-tai-san-cho-ben-nhan-tai-san-lha5022.html) | Đọc qua bài của luật sư |
| A to B | [A to B Runners](https://atobrunners-co-uk.pages.dev/terms), hãng chở ngựa Anh | Đọc trang gốc |
| Two Hearts | [Two Hearts Equine Transport](https://twoheartsequinetransport.com/transportation-agreement/), hãng chở ngựa Mỹ | Đọc trang gốc |
| Class Act | [Class Act Horse Transport](https://classacthorsetransport.com/payment-information), hãng chở ngựa Mỹ | Đọc trang gốc |
| Stealth | Stealth Horse Transport, hãng chở ngựa Anh | Chỉ qua kết quả tìm kiếm, **chưa kiểm chứng** |
| Bản cũ | Điều khoản vận chuyển, chính sách chi phí sự cố và code trên `main` | Đọc bản gốc |
| Peden | Không công bố điều khoản | Không dùng được |

Không đọc được: Quy định EU 1/2005 bản gốc, điều khoản Allmarc và Lucky Horse. Kết quả tìm kiếm có trích EU 1/2005: ngựa ốm hoặc bị thương phải tách riêng và sơ cứu sớm nhất (**chưa kiểm chứng**).

**Phát hiện khi đọc lại:** [CAN_CU_PHAP_LY_VA_IRT.md](CAN_CU_PHAP_LY_VA_IRT.md) ghi "chờ người nhận tính theo giờ, IRT Anh điều 9". Điều 9 **không có** nội dung này (chỉ nói người nhận trả chi phí sau khi tới nơi, không trả thì khách trả). Cần sửa dòng đó.

## 2. Đợt 1. Sửa các chỗ làm kẹt đơn, kẹt xe

Làm trước vì gặp là đơn không đi tiếp được. Không thêm bảng. Mục 1.2 và 1.5 lớn hơn bản 1, nên tách: **đợt 1a** gồm 1.1, 1.3, 1.4, 1.6, 1.7; **đợt 1b** gồm 1.2, 1.5.

### 1.1 Đơn rời chuyến mà ngựa vẫn nằm trên lượt xe (3.G1)

- **IRT:** không có điều khoản. **Ngoài:** lỗi kỹ thuật, tài liệu nhóm đã quy định hủy chặng.
- **Quy trình mới:** đơn bị hủy, đổi chuyến, chuyến bị hủy hoặc giấy không kịp → hệ thống gỡ ngay mọi chặng chưa chạy, bỏ lịch hẹn ở hub, báo điều phối.
- **Sửa:** A gọi hàm gỡ chặng B đã viết, ở các service hủy, đổi chuyến, quyết định chuyến. B thêm bỏ lịch hẹn hub trong cùng hàm.

### 1.2 Ngựa đã nhận mà khách chưa trả số dư (6.G3, 6.G4)

Chi tiết ở **mục 9**. Tóm tắt:

- **IRT:** không chuyển đi khi chưa trả đủ (Úc 12, Mỹ 5); ngựa chờ thanh toán thì khách chịu tiền lưu chuồng (Anh 5, Mỹ 6).
- **Bản cũ:** nhà xe **liên hệ khách** để xác định tình trạng và phương án; nếu ngừng chuyến thì trả ngựa có biên bản, chăm ngựa tới khi bàn giao.
- **Phương án:** chỉ chặn **con ngựa chưa trả tiền**, không chặn xe. Liên hệ khách, giữ ngựa có hạn như trường hợp người nhận vắng, rồi mới hủy.
- **Sửa:** B (lên xe, giữ ngựa, trả ngựa), A (số dư, đổi chuyến, hủy).

### 1.3 Thu đủ trước khi đi, không để nợ sau khi giao (6.G1)

- **Bản cũ:** trả **toàn bộ phần còn lại** khi công ty nhận xong ngựa; sau đó không thu thêm gì, mọi chi phí sự cố, lưu chuồng do công ty chịu, không giữ ngựa để đòi tiền.
- **IRT:** được giữ dịch vụ tới khi trả đủ (Úc 12, Mỹ 5). Phí sau khi tới nơi do người nhận trả, không trả thì khách trả (Anh 9, Mỹ 9).
- **Ngoài:** Two Hearts thu tiền thú y, gửi chuồng **trước khi dỡ ngựa**.
- **Phương án, giữ tinh thần bản cũ:**
  1. **Số dư thu một lần khi đón, gồm mọi khoản đã biết**: tổng báo giá, phí đón lại, lưu chuồng sớm, trừ con bị loại.
  2. Phí giữ ngựa do người nhận vắng: **khách trả trước khi xe giao lại rời hub**. Người nhận tự tới hub dắt ngựa về thì **không chặn**, phần chưa trả thành khoản thu thêm. Trong hệ thống **khách là người trả**, người nhận tự trả lại khách. **Bỏ** quy tắc 7 + 7 ngày ở bản 1.
  3. Chi phí trên đường do ngựa khách hoặc giấy khách gây ra, khách đã đồng ý: **không chặn giao ngựa** ở cửa nhà khách; lập khoản thu thêm, hạn 7 ngày.
  4. Hóa đơn về muộn sau khi giao: như dòng 3. Quá 7 ngày thì nhắc mỗi ngày, khách **chưa đặt đơn mới được** tới khi trả (IRT Anh 18 tính chung công nợ của khách).
- **Sửa:** A (số dư bổ sung, khoản thu thêm có hạn, chặn đặt đơn), B (chặn xe giao lại khi còn phí giữ).

### 1.4 Phí giữ ngựa không duyệt được (6.G2)

- **IRT:** hỏi khách trước khi chi, **trừ khi không thể hỏi** (Đức §7.3); phí lưu chuồng nằm sẵn trong điều khoản (Anh 5).
- **Quy trình mới:** phí giữ và gửi chuồng hệ thống tự tính theo mức đã công bố, **đã báo khách lúc ngựa vào hub**, nên duyệt không cần cờ "đã hỏi". Khoản thú y, cứu hộ vẫn phải hỏi hoặc nằm trong hạn mức.
- **Sửa:** B (quy tắc duyệt chi phí), A (lập khoản thu thêm dùng cùng quy tắc).

### 1.5 Không đón được một con hoặc cả đơn (6.G5, 6.G6, 6.G21)

Chi tiết ở **mục 8**. Tóm tắt: khách chọn **bỏ con đó** (trừ tiền dịch vụ của nó) hoặc **hẹn đón lại**; áp dụng cả khi không đón được con nào. Xe chỉ rời nơi nhận khi **mọi con** đã nhận hoặc đã ghi đón hụt.

### 1.6 Sự cố không đóng được (5.G1, 5.G2, 5.G3, 5.G4, 5.G6)

- **IRT:** công ty được quyết định điều trị khi không liên lạc được khách (Đức §6.4, Úc 6(iii)); báo khách ngay khi có hư hại (Đức §6.5).
- **Quy trình mới:**
  1. Sự cố **Sức khỏe, Tai nạn có ngựa** bắt buộc chọn **con ngựa**.
  2. Chỉ hai loại đó đòi xác nhận đủ sức. Người nhận vắng thì không.
  3. Lượt đã kết thúc mà sự cố chưa đóng: tổ lái vẫn xác nhận và ghi chi phí được; **điều phối** ghi được chi phí và xác nhận thay kèm **kết quả thú y**.
  4. Sự cố người nhận vắng: ẩn nút Đưa về hub.
- **Sửa:** B (chính sách sự cố, màn sự cố và màn lượt xe).

### 1.7 Chuyến không đổi trạng thái (3.G3)

- **Quy trình mới:** xe trục xuất phát → chuyến **Đang chạy**; xong lượt → **Đã xong**. Nút Hủy chuyến ẩn từ lúc chạy.
- **Sửa:** B gọi, A có hàm chuyển trạng thái chuyến.

## 3. Đợt 2. Tiền và trách nhiệm

| Điểm hở | IRT / ngoài | Quy trình đề xuất |
|---|---|---|
| Tài xế chọn nguyên nhân, quyết luôn ai trả (5.G9) | IRT không nói | Tài xế **đề xuất** nguyên nhân, **quản lý chốt** khi duyệt phương án. Chốt rồi mới sinh người trả |
| "Đã hỏi khách" chỉ là cờ (5.G10) | Đức §7.3: lấy chỉ thị của khách trước khi chi | Khách nhận thông báo có **Đồng ý / Từ chối**. Không trả lời trong 15 phút (số tự đề xuất) thì trong hạn mức được chi; ngoài hạn mức điều phối gọi và ghi lại. Từ chối thì không chi, trừ cấp cứu |
| Duyệt phương án không xét hạn mức (5.G11) | Như trên | Khoản khách chịu dự kiến vượt hạn mức mà chưa hỏi thì chưa duyệt được |
| Phí chờ (5.G13) | IRT Anh **không** tính theo giờ. A to B: 30 phút miễn phí, sau đó £10 mỗi nửa giờ. BLDS 538.3: gửi giữ, bên thuê chịu phí | **Không thu phí chờ**. 120 phút miễn phí rồi đưa về hub, thu phí giữ. Sửa tài liệu cho khớp |
| Quá 3 ngày giữ ngựa (5.G21) | IRT được giữ và định đoạt khi khách không trả (Anh 18, Úc 13). LTM 239: phải báo bằng văn bản, **45 ngày** sau mới được định đoạt; chi phí khách chịu. LTM 240: vẫn phải chăm | Dùng lịch ở **9.2 bước 6–9**: ngày 7 thông báo bằng văn bản, 45 ngày sau báo quản lý **xử lý ngoài hệ thống**. Hệ thống không bán ngựa |
| Lỗi đại lý (5.G12) | IRT loại trừ trách nhiệm của bên thầu phụ (Úc 7–9, Anh 11). **Trái quyết định đã chốt** nên không theo | Giữ: công ty trả. Thêm nhãn "đòi lại đại lý" để quản lý theo dõi |
| Đơn chạy thẳng bớt con không giảm tiền (6.G23) | IRT báo giá theo lô, không quy định | **Giữ**: cả xe vẫn phải chạy. Ghi rõ trên báo giá |
| Báo giá hết hạn là ngõ cụt (1.F1) | Đức §3.3: báo giá tự hết hạn, không nói cách mở lại. Tự đề xuất | Quản lý mở lại nếu chuyến còn chỗ và còn kịp giấy, giá theo bảng giá hiện tại |

## 4. Đợt 3. Bằng chứng, nghiệm thu, khiếu nại

| Điểm hở | IRT / ngoài | Quy trình đề xuất |
|---|---|---|
| Không biết ai nhận (6.G7) | IRT chỉ yêu cầu khai người nhận trên phiếu chỉ dẫn (Mỹ, Delivery details). Một hãng khác: người nhận phải được chủ ủy quyền bằng văn bản (chưa kiểm chứng) | Khách thấy **mã nhận ngựa 6 số** trên đơn và qua email, tự gửi cho người nhận. Người nhận đọc mã, tổ lái nhập đúng và ghi **họ tên** mới hoàn tất. Không có mã thì khách xác nhận trên app |
| Không có ảnh, tình trạng ngựa (6.G14) | Đức §14.1: hư hại nhìn thấy phải nêu **ngay lúc giao**, không thì coi như nguyên vẹn | Lúc nhận và lúc giao, từng con: ô tình trạng **bắt buộc**, **ít nhất 1 ảnh**. Người nhận chọn "Không có hư hại" hoặc ghi rõ |
| Hub, cửa khẩu không biên bản (6.G8) | Như trên | Nhận và trả tại hub, bàn giao đại lý: biên bản bắt buộc như ở nhà khách |
| Đại lý giao không bằng chứng (6.G9) | Công ty đứng ra với khách | Điều phối phải tải ảnh hoặc biên bản đại lý gửi; báo khách |
| Giấy gốc (6.G10–G12) | Đức §6.2: công ty giữ cẩn thận giấy khách giao | Phải đánh dấu từng giấy **đã nhận hoặc không nhận bản gốc** mới hoàn tất. Giao hoặc trả ngựa thì phải trả hết bản đã nhận |
| Khách không xem biên bản (6.G13) | Tài liệu nhóm | Hiện biên bản và ảnh ở từng lần giao nhận trên đơn |
| Chưa có khiếu nại (6.G18) | IRT: **7 ngày** kể từ lúc giao (Úc 18, Anh 12, Đức §14.2). LTM 237.1đ: công ty miễn trách nếu không nhận khiếu nại trong **14 ngày** | Khách gửi khiếu nại trên đơn trong **7 ngày** (đã chốt), kèm ảnh. Có khiếu nại mở thì **không đóng được đơn**. Ngày 8–14 vẫn nhận qua quản lý, đơn mở lại xét. Không thêm bảng: dùng `incident` gắn đơn, thêm loại Khiếu nại |
| Không có bước nghiệm thu (6.G24) | Tự đề xuất | Đơn Đã giao: khách bấm **Xác nhận đã nhận ngựa ổn** hoặc **Khiếu nại**. Xác nhận thì đóng sớm; không bấm thì chờ hết 7 ngày |

## 5. Đợt 4. Thông báo và các sửa nhỏ

IRT Đức §6.5: báo khách **ngay** khi biết có hư hại. Áp dụng chung cho mọi mốc khách cần biết.

- **Báo khách:** sự cố của con mình, phương án và chi phí, từ chối nhận, đại lý đã giao, khoản thu thêm, hoàn tiền xong, cọc thành công, báo giá hết hạn, xe trục chạy và tới hub đích.
- **Báo quản lý:** phương án chờ duyệt, đơn sang Đang báo giá, khách chấp nhận hoặc từ chối, đơn Đã giao chờ quyết toán.
- **Báo điều phối, tổ lái:** sự cố mới, phương án được duyệt hoặc từ chối, Ốm hoặc Nguy cấp, hủy lượt.
- **Sửa nhỏ:** nút trả ngựa chỉ hiện khi quá hạn; ô đánh giá không hiện lại; nhắc giấy đúng bên nộp; ngày hết hạn giấy so với ngày chạy; nút Từ chối đơn khớp máy chủ.

**Đợt 5:** chuyến trễ theo cách hàng không (3.F), đã ghi nhận.

## 6. Ảnh hưởng

| | Đợt 1 | Đợt 2 | Đợt 3 |
|---|---|---|---|
| Bảng | Không thêm bảng. Thêm cột ghi đón hụt (lý do, bên chịu, giờ) cho ngựa trong đơn, cấu hình các hạn mới | Cột chốt nguyên nhân, cột khách trả lời chi phí | Cột tên người nhận, mã xác nhận; loại sự cố Khiếu nại |
| Màn hình | Hub, lượt xe, sự cố, quyết toán | Sự cố, đơn của khách | Lượt xe, hub, đơn của khách |
| Luồng A làm | 1.1, 1.3, 1.7; phần tiền, đổi chuyến, màn khách của 1.2, 1.5 | Khách trả lời chi phí, mở lại báo giá | Khiếu nại, nghiệm thu, xem biên bản |
| Luồng B làm | 1.4, 1.6; phần hiện trường của 1.2, 1.5 | Chốt nguyên nhân, phương án, quá hạn giữ | Mã xác nhận, ảnh, biên bản, giấy gốc |
| Kiểm | Test từng quy tắc, chạy tay 5 loại đơn và 4 tình huống kẹt | Test, chạy tay | Test, chạy tay |

Danh sách file cụ thể được trình trước khi làm **từng đợt**, vì phần lớn thuộc luồng B và phải thống nhất với bạn B.

## 7. Đã chốt 25/11/2026

| # | Vấn đề | Đề xuất | Căn cứ |
|---|---|---|---|
| 1 | Đón hụt | Mục 8: bỏ ngựa hoặc đón lại; phí theo bảng 8.4 | Góp ý của bạn; Two Hearts, Stealth, Class Act |
| 1a | Bỏ ngựa do khách: có sàn không | Tổng mới **không thấp hơn tiền cọc đã trả** (không hoàn cọc) | Giống khách hủy đã chốt; chặn đặt nhiều rồi bỏ |
| 1b | Bỏ ngựa do khách: phí bảo hiểm mua hộ | Không trừ, trừ khi hãng hoàn | Quyết định 24/11: thu hộ không hoàn khi do khách |
| 1c | Thời gian chờ ở điểm đón | 30 phút, điều phối gia hạn nếu còn kịp | A to B, Two Hearts |
| 2 | Phí giữ do người nhận vắng | Khách trả trước khi xe giao lại rời hub; tự tới nhận thì không chặn | Bản cũ (thu đủ trước khi đi), Two Hearts |
| 3 | Phí chờ ở điểm giao sau 120 phút | Không thu, chỉ thu phí giữ ở hub | Bản cũ không thu phí chờ; BLDS 538.3 |
| 4 | Hạn khiếu nại | 7 ngày trên app, ngày 8–14 qua quản lý | IRT 7 ngày; LTM 14 ngày |
| 5 | Xác nhận người nhận | Mã 6 số trên đơn và email của khách | Tự đề xuất |
| 6 | Chưa trả số dư | Mục 9: liên hệ, lỡ chuyến thì giữ 3 ngày, cấn cọc vào phí giữ | Góp ý của bạn; bản cũ; IRT Anh 5 |
| 6a | Đơn nhập ở cửa khẩu chưa trả | Đưa về hub giữ thay vì trả đại lý. **Đổi quyết định đã chốt** | Tránh đưa ngựa quay lại biên giới |
| 6b | Phí giữ khi lỗi do khách | 1.000.000 đ/con/ngày như giao hụt, **cấn vào cọc** khi đơn hủy | Cùng mức hiện có |

---

## 8. Chi tiết: không đón được ngựa (đón hụt)

### 8.1 Khi nào là đón hụt, do ai

Xe đã tới điểm đón mà một con **không lên xe**. Tổ lái ghi từng con, chọn lý do, chụp ảnh; **quản lý chốt bên chịu** (chưa chốt thì số tiền hiện là tạm tính).

| Bên chịu | Lý do |
|---|---|
| **Khách** | Người gửi vắng quá 30 phút chờ; sai chip; thiếu giấy gốc; ngựa ốm; ngựa không chịu lên xe sau 30 phút |
| **Công ty** | Xe hỏng, xe hoặc khoang không phù hợp, tới trễ quá giờ còn kịp về hub, thiếu tổ lái |
| **Bất khả kháng** | Bão lũ, cấm đường. Công ty chịu phần xe của mình (đã chốt) |

Ngựa **ốm có phiếu thú y**: vẫn tính phí xe đã chạy, nhưng **không trừ lượt đổi chuyến miễn phí**.

Trước khi ghi đón hụt vì người gửi vắng: phải gọi người gửi, khách và người liên hệ khẩn cấp, mỗi lần gọi ghi lại.

### 8.2 Khách được chọn gì

Với **từng con**, khách chọn trên app (điều phối chọn thay qua điện thoại, có ghi lại):

| Lựa chọn | Nghĩa là | Điều kiện |
|---|---|---|
| **A. Bỏ con này** | Ra khỏi đơn, trừ tiền dịch vụ của nó (8.4) | Luôn được |
| **B. Đón lại, vẫn chuyến này** | Xe tới lần hai | Kịp về hub trước hạn có mặt (8.3). Mỗi con **một lần** mỗi chuyến |
| **C. Tự chở tới hub, vẫn chuyến này** | Khách chở | Tới hub trước hạn có mặt, đủ giấy |
| **D. Đi chuyến khác** | Cả đơn dời chuyến | Cùng tuyến, trong **30 ngày** giữ giá |

- **Đơn còn con đã nhận:** chọn A, B, C cho con hụt. Muốn con hụt đi chuyến khác thì chọn A rồi đặt đơn mới, **được giữ đơn giá cũ 30 ngày**. Không tách một đơn ra hai chuyến, vì giấy của đơn chỉ có một bản gốc và ngựa cùng đơn đi chung xe trục (3.9).
- **Không đón được con nào:** chọn B, C, D hoặc **hủy đơn**. Đơn giữ nguyên, **không tự hủy**.
- **Không còn con nào để đưa** (khách bỏ hết): đơn hủy, theo 8.4.
- **Đơn đã Đang vận chuyển** (đón bằng hai xe, xe đầu đã rời): chỉ A, B, C.
- **Đơn chạy thẳng:** không có hub và chuyến, nên chỉ có A hoặc **ngày đón mới** trong 30 ngày.
- **Đơn nhập:** nơi nhận là cửa khẩu và xe nhận chính là xe trục, nên chỉ có A hoặc D.

### 8.3 Gắn với ngày khởi hành

Ký hiệu **T** là giờ chạy. **Hạn có mặt ở hub là T − 3 giờ** (đã có).

**Điều kiện còn kịp đón lại (B):**

> giờ bấm chọn + 2 giờ chuẩn bị + đường xe tới + thời gian nhận ngựa (20 phút + 10 phút/con) + đường về hub kể cả nghỉ ≤ **T − 3 giờ**

và có xe, tài xế còn giờ lái (10 giờ/ngày). Hệ thống chỉ hiện khung giờ thỏa; điều phối xác nhận.

| Đón hụt lúc | Đón lại chuyến này | Tự chở | Chỗ trên chuyến | Hạn khách chọn |
|---|---|---|---|---|
| Còn hơn 24 giờ tới hạn có mặt | Được | Được | Giữ | 12 giờ, và trước hạn có mặt 12 giờ |
| Còn từ giờ tối thiểu ở công thức tới 24 giờ | Được nếu điều phối xác nhận có xe | Được | Giữ | 1 giờ |
| Không còn kịp theo công thức | Không | Được nếu tới trước hạn có mặt | Giữ tới hạn có mặt | 1 giờ |
| Qua hạn có mặt | Không | Không | **Nhả** | Xem dưới |

Hạn chọn luôn lấy mốc **sớm hơn** giữa số giờ trên và lúc không còn kịp.

**Hết hạn chọn hoặc qua hạn có mặt:**

- Đơn còn con khác: con hụt **tự bỏ khỏi đơn** (A). Các con đã nhận đi đúng chuyến, **không chờ**.
- Không có con nào: đơn rời chuyến, sang **Cần chọn lại chuyến**, nhả chỗ, gỡ mọi chặng. Khách có **30 ngày** (hạn giữ giá) để chọn chuyến mới; nhắc ngày 3, 7, 14, 25. Hết 30 ngày thì hủy theo nguyên nhân.

**Chọn chuyến mới (D) cần:**

1. Cùng tuyến, chuyến mới trong 30 ngày kể từ chuyến lỡ, còn chỗ, **trước hạn nhận đặt 72 giờ**.
2. **Giấy:** giấy đã duyệt còn hạn tới ngày chạy mới thì không tính lại. Chỉ tính thời gian cho giấy phải làm lại. **Cần sửa:** hệ thống hiện đòi lại đủ 5 ngày (liên tỉnh) hoặc 21 ngày (xuất), nên lỡ chuyến 08/12 sẽ **không chọn được chuyến 15/12** (từ 08/12 tới hạn nhận đặt 12/12 chỉ có 4 ngày).
3. Bảo hiểm còn hiệu lực tới ngày chạy mới.
4. Ngày đón mới trước ngày chạy ít nhất 1 ngày (1.17).
5. **Lượt đổi miễn phí:** do khách được **một lần** mỗi đơn (đã chốt); do công ty hoặc bất khả kháng không tính lượt.

**Với mốc quyết định (T − 48 giờ):** đón sớm mà đơn bỏ hết ngựa trước mốc này thì chỗ của đơn không tính vào chỗ đã cọc khi quản lý quyết định. Sau khi đã bấm Chạy thì chuyến **không quyết lại**.

### 8.4 Tiền tính ra sao

**Nguyên tắc:** mọi khoản vào **báo giá thay thế**, thu chung với số dư khi đón (điểm 1.3). Khách thấy số tiền trước khi bấm chọn. Cùng một lượt xe **không thu hai lần**.

**Bỏ con khỏi đơn (A):**

| Khoản | Do khách | Do công ty, bất khả kháng |
|---|---|---|
| Cước trục, phụ thu khoang rộng của con đó | Trừ | Trừ |
| Dịch vụ theo con **chưa làm** | Trừ | Trừ |
| Dịch vụ **đã làm** (thú y đã khám) | Không trừ | Trừ |
| Phí bảo hiểm mua hộ của con đó | Không trừ, trừ khi hãng hoàn | Trừ |
| Cước đón, giao, nhiên liệu, thủ tục, lệ phí của đơn | Giữ: xe vẫn chạy cho các con khác | Tính lại nếu xe hạng nhỏ hơn chở đủ |
| Đơn chạy thẳng | Giữ cước xe, trừ khoang rộng và dịch vụ | Tính lại theo hạng xe nhỏ nhất chở đủ |
| Sàn | Tổng mới **không thấp hơn cọc đã trả** | Không có sàn, dư thì hoàn |

**Đón lại, dời chuyến, hủy:**

| Tình huống | Do khách | Do công ty | Bất khả kháng |
|---|---|---|---|
| Lượt đón hụt đầu tiên | Đã nằm trong cước đón của báo giá | Công ty | Công ty |
| **Đón lại** (B) | Thêm **cước đón + nhiên liệu đoạn đón**, theo xe nhỏ nhất chở đủ số con đón lại, đơn giá đã chốt | Miễn phí | Miễn phí |
| **Tự chở** (C) | Không thêm | Không thêm, **trừ cước đón** nếu đón hụt cả đơn | Không thêm |
| **Dời chuyến** (D) | Như đón lại; lượt đổi đầu miễn phí | Miễn phí | Miễn phí |
| Khám lại ngựa ốm, làm lại giấy | Thực chi, hỏi trước (IRT Mỹ 3, Đức §7.3) | Công ty | Công ty |
| Đón hụt lần hai do khách | Không đón nữa: tự chở, bỏ, hoặc hủy | | |
| **Hủy đơn** | **Mất cọc** (gồm thu hộ), không thu thêm | Hoàn **100%** | Hoàn 100% |

So với ngoài: Stealth thu 50–100% cước khi không lên được xe; Two Hearts giữ 100% cọc khi ngựa không lên xe hoặc thiếu giấy; Class Act 100% khi hủy trong ngày. Phương án này **nhẹ hơn**: còn đi thì chỉ trả lượt xe thêm, chỉ mất cọc khi bỏ hẳn. Do hãng lỡ thì Two Hearts hoàn đủ nếu khách không nhận ngày mới, giống cột Công ty.

### 8.5 Ví dụ tiền

Đơn ở luồng 1: hai con, tổng **28.733.450 đ**, cọc đã trả **8.761.000 đ**. Cước đón 1.417.500 + nhiên liệu đoạn đón 54 km × 9.450 = 510.300 → một lượt đón lại **1.927.800 đ** (giả sử cùng hạng xe).

| Trường hợp | Tổng mới | Trả khi đón |
|---|---:|---:|
| Đón đủ | 28.733.450 | 19.972.450 |
| Bỏ một con, do khách | 16.133.450 | 7.372.450 |
| Đón lại một con, do khách, cùng chuyến | 30.661.250 | 21.900.250 |
| Đón hụt cả hai, do khách, dời sang 15/12 | 30.661.250 | 21.900.250 |
| Đón hụt cả hai, do công ty, dời sang 15/12 | 28.733.450 | 19.972.450 |
| Đón hụt cả hai, do khách, hủy | – | 0, mất cọc 8.761.000 |
| Đón hụt cả hai, do công ty, hủy | – | 0, hoàn 8.761.000 |

Chuyến 05:00 08/12, hẹn đón 09:00 07/12, một con sai chip: hạn có mặt 02:00 08/12, còn 17 giờ → khách chọn trong 1 giờ; đón lại trong chiều 07/12 nếu có xe; hoặc tự chở tới hub trước 02:00.

### 8.6 Số dư khi đón hai lần

- Số dư lần đầu tính cho mọi con **còn trong đơn**, gồm con đang chờ đón lại.
- Chọn đón lại sau khi đã trả số dư: lập **số dư bổ sung** đúng bằng phí đón lại, hạn 30 phút sau khi nhận con thứ hai, xe thứ hai chỉ rời khi đã trả.
- Con đón lại vẫn hụt thì bỏ khỏi đơn; phần khách trả dư thành khoản hoàn.

---

## 9. Chi tiết: ngựa đã nhận mà khách chưa trả số dư

**Nguyên tắc:** không chặn cả xe vì một đơn; luôn chăm ngựa; liên hệ khách trước khi hủy. Công ty được **ngừng chở tiếp** khi chưa trả tiền (IRT Úc 12, Mỹ 5), nhưng **không giữ ngựa để đòi tiền**: khách tới đón về lúc nào cũng trả (bản cũ).

### 9.1 Ở nhà khách (xe đến đón)

1. 30 phút như đã chốt. Trong lúc đó hệ thống nhắc app và email ở phút 0, 15, 25; điều phối **gọi khách và người liên hệ khẩn cấp**, ghi lại.
2. Khách xin thêm giờ: điều phối gia hạn **một lần 30 phút**, chỉ khi không làm lỡ hạn có mặt ở hub và không quá giờ ngồi xe của ngựa khác.
3. Hết hạn, người gửi còn ở đó: **trả ngựa tại chỗ** kèm biên bản và giấy gốc. Đơn **chưa hủy ngay**: sang Cần chọn lại chuyến, nguyên nhân khách, nhả chỗ. Khách có **30 ngày** hẹn lại như 8.3, phí đón lại như 8.4. Lần này **phải trả đủ số dư khi chọn lịch**, xe mới đi đón. Quá 30 ngày thì hủy, mất cọc.
4. Không ai nhận lại ngựa: đưa về hub, làm như 9.2.

### 9.2 Ở hub (khách tự chở), hoặc đơn nhập ở cửa khẩu

| Bước | Lúc nào | Hệ thống và công ty làm | Khách |
|---|---|---|---|
| 1 | Nhận xong ngựa | Lập số dư, hạn 30 phút. Ngựa **chưa được lên xe trục** | Trả thì đi bình thường |
| 2 | Hết 30 phút | **Không hủy.** Nhắc app, email; điều phối gọi khách, người liên hệ khẩn cấp. Ít nhất **3 lần**, ghi lại | Trả bất cứ lúc nào |
| 3 | T − 3 giờ | Nhắc lần cuối. Khách báo đang trả thì chờ tới lúc xếp ngựa xong (T − 1 giờ) | |
| 4 | T − 1 giờ, chưa trả | **Lỡ chuyến**: gỡ ngựa khỏi lượt xe, nhả chỗ, đơn sang Cần chọn lại chuyến, nguyên nhân khách. Xe và đơn khác chạy đúng giờ | |
| 5 | Từ T | Ngựa ở hub, **giữ có phí** như giao hụt: 1.000.000 đ/con/ngày (số mẫu), chăm 2 lần mỗi ngày | Báo ngay, kèm phí |
| 6 | T → T + 3 ngày | Nhắc 8:00 mỗi ngày kèm phí cộng dồn; điều phối gọi mỗi ngày | Ba lựa chọn ở 9.3 |
| 7 | T + 3 ngày, không trả lời | **Hủy đơn, mất cọc.** Phí giữ **cấn vào tiền cọc**. Ngựa vẫn được chăm | Vẫn đến đón ngựa về được |
| 8 | T + 7 ngày | Gửi thông báo bằng văn bản yêu cầu nhận lại ngựa, bắt đầu đếm 45 ngày (mượn mốc của LTM 239). Đề nghị **chở ngựa về địa chỉ người gửi**, khách trả một lượt cước đón | |
| 9 | 45 ngày sau thông báo | Báo quản lý xử lý ngoài hệ thống. Hệ thống không bán ngựa | |

Đơn nhập ở cửa khẩu: lập số dư lúc nhận từ đại lý, xe trục **vẫn chở ngựa về hub** (đơn giữ Đã xác nhận, chưa sang Đang vận chuyển). Chưa trả thì chưa lập lượt giao. Không có chuyến để lỡ, nên **T là 24 giờ sau khi ngựa vào hub**.

Hai hạn khác nhau có chủ đích: **3 ngày** khi công ty **đang giữ ngựa** (phải chăm, chiếm chuồng); **30 ngày** khi ngựa **đã về nhà** (chỉ giữ giá).

### 9.3 Khách chọn trong 3 ngày

| Lựa chọn | Phải trả | Ngựa |
|---|---|---|
| **Trả số dư, đi chuyến sau** | Số dư + phí giữ tới lúc rời hub. Ngựa về nhà rồi quay lại thì chỉ tính ngày ở hub | Đi chuyến kế, theo điều kiện 8.3 |
| **Đón ngựa về, giữ đơn** | Phí giữ tới lúc đón, cộng vào số dư. Trả đủ khi chọn lịch mới | Trả ngựa ngay; chọn chuyến trong 30 ngày |
| **Đón ngựa về, hủy đơn** | Phí giữ **cấn vào cọc**; phần vượt cọc thành khoản thu thêm, **không chặn đón ngựa** | Trả ngay |

**Ví dụ:** cọc 8.761.000 đ, hai con ở hub 3 ngày rồi đón về và hủy: phí giữ 2 × 3 × 1.000.000 = 6.000.000 đ, cấn vào cọc, khách **không trả thêm**. Nếu trả tiền và đi chuyến 15/12 mà ngựa ở lại hub 7 ngày: phí giữ 14.000.000 đ; đưa về rồi 14/12 chở lại thì chỉ trả những ngày đã ở.

### 9.4 Dùng chung một khuôn

Người nhận vắng (luồng 5), chưa trả số dư ở hub và ngựa chờ đón lại ở hub dùng **cùng một loại lưu trú giữ có phí**, cùng lịch nhắc 8:00, cùng mốc 3 ngày, 7 ngày, 45 ngày. Khác nhau: ai chịu phí và có chuyến để đi hay không.

Hai mức phí có chủ đích: **800.000 đ** là lưu chuồng **theo kế hoạch** (đưa ngựa tới sớm, đã có trong báo giá); **1.000.000 đ** là **giữ ngoài kế hoạch** (người nhận vắng, chưa trả tiền). Cả hai do máy chủ tính, **bỏ ô nhập phí tay** ở màn Hub.

---

## 10. Quyết định đã chốt bị đổi nếu bạn duyệt

| Quyết định cũ | Đổi thành |
|---|---|
| Quá 30 phút chưa trả: trả ngựa, hủy đơn, mất cọc ngay | Ở nhà khách: trả ngựa, thêm 3 ngày trước khi hủy. Ở hub: giữ ngựa tới 3 ngày sau giờ chạy |
| Đơn nhập chưa trả: trả ngựa cho đại lý | Đưa về hub giữ |
| Từ chối hết ngựa: chưa quy định, đơn treo | Khách chọn đón lại, dời chuyến hoặc hủy |
| Đổi chuyến sau khi cọc phải đủ lại thời gian làm giấy | Chỉ tính giấy phải làm lại |
| Một khoản số dư mỗi đơn | Thêm số dư bổ sung khi có phí đón lại |

---

## 11. Chỗ code phải xử lý khi làm

Codex đối chiếu bản 2 với code ngày 25/11 (chỉ đọc). Cách xử lý:

| Mục | Code hiện tại | Xử lý |
|---|---|---|
| 1.2 | Xe bị chặn theo **đơn** của con đã lên xe | Chặn ngay lúc **lên xe** ở hub; xe rời chỉ kiểm con đang trên xe |
| 1.2 | Chỉ trả được số dư khi đơn Đã xác nhận | Cho trả cả khi **Cần chọn lại chuyến**. Tại T − 1 giờ còn phiên VNPAY đang chờ thì đợi kết quả tối đa 5 phút |
| 1.2 | Rời nơi nhận là đơn sang Đang vận chuyển | Chỉ chuyển khi **đã trả đủ** |
| 1.2 | Chưa có chỗ lưu lần gọi, gia hạn, thông báo văn bản | Ghi vào nhật ký hệ thống gắn đơn, không thêm bảng |
| 1.3 | Khoản thu thêm chỉ lập, chỉ trả khi Đã giao | Phí giữ: lập và trả khi **Đang vận chuyển**. Hóa đơn muộn: lập và trả cả khi **Hoàn thành** |
| 1.3 | Phí giữ chỉ ghi khi ngựa rời hub | **Chốt tới giờ hẹn lượt giao** để khách trả trước; chênh lệch tính khi rời |
| 1.4 | Hai nơi cùng kiểm "đã hỏi khách" | Một quy tắc dùng chung, phí theo biểu không tính vào hạn mức khẩn cấp |
| 1.5 | Từ chối là loại ngay, nhả chỗ ngay | Tách **ghi đón hụt** khỏi **bỏ con**; chỉ nhả khi khách chọn hoặc hết hạn |
| 1.5 | Loại con là bỏ mọi dòng giá của nó | Giữ dịch vụ đã làm và bảo hiểm khi do khách; áp sàn tiền cọc |
| 1.5 | Đơn mới chưa biết giá cũ | Thêm liên kết tới đơn gốc để tính đơn giá giữ 30 ngày |
| 1.6 | Chỉ ẩn nút thì vẫn gọi được | Chặn cả ở máy chủ |
| 1.7 | Bấm Xuất phát khác với rời điểm đầu; một chuyến có thể có nhiều xe | Đang chạy khi **xe trục đầu tiên rời điểm đầu**; Đã xong khi **mọi lượt trục** của chuyến xong |
| Chung | AGENTS ghi 12 trạng thái đơn, code có 13 (thêm Cần chọn lại chuyến) | Sửa câu trong AGENTS khi bạn cho phép |

Danh sách file từng mục đã có (Codex lập), sẽ trình kèm khi bắt đầu từng đợt.

---

## 12. Một đơn, mỗi con giao một nơi (cùng tuyến)

**Phạm vi theo góp ý:** cả đơn **cùng nơi đón, cùng chuyến trục, cùng hub đích**; chỉ khác **chặng trung chuyển cuối** từ hub đích tới từng nơi giao. Khác tuyến thì vẫn là đơn khác.

### 12.1 Có bắt buộc làm không

- **IRT:** không. Phiếu chỉ dẫn của IRT Mỹ có **một** nơi giao cuối; ngựa đi nơi khác lập phiếu khác.
- **Luật:** cho phép. Giấy kiểm dịch ra khỏi tỉnh (Thông tư 01/2026, mẫu 1a và 12a, đọc bản gốc) có một nơi đến cuối và tối đa **3 dòng nơi giao dọc đường** kèm số lượng. Mẫu chỉ có một biển số xe; cách ghi khi đổi xe ở hub **chưa kiểm chứng** (đơn thường hiện cũng đổi xe).
- **Lợi cho khách:** một báo giá, một cọc, một bộ giấy, một lần đón. Đặt hai đơn rời thì trả hai lần cước đón và thủ tục.
- **Kết luận:** làm được, vì mọi phần lớn vẫn chung một đơn. Nên làm **sau đợt 1**.

### 12.2 Điều kiện để được gộp

| # | Điều kiện | Vì sao |
|---|---|---|
| 1 | Đơn **liên tỉnh đi trục**, công ty giao | Quốc tế: giấy phép và người nhận theo từng nơi, chặng cuối do đại lý. Chạy thẳng: không có trung chuyển |
| 2 | Mọi nơi giao thuộc **cùng hub đích** | Cùng tuyến |
| 3 | Tối đa **3 nơi giao** | Giấy kiểm dịch có 3 dòng |
| 4 | Mỗi con đúng **một** nơi giao; mỗi nơi có **người nhận và điện thoại riêng** | Giao đúng người |
| 5 | **Một xe giao** ghé hết các nơi trong một vòng, mỗi con ngồi xe không quá **5 giờ**, tài xế không quá 10 giờ cả về | Giấy gốc của đơn chỉ có một bản, phải đi theo xe |

Không thỏa điều kiện 2, 3 hoặc 5 thì hệ thống nói rõ và mời **đặt đơn riêng** cho nơi đó.

### 12.3 Đi từng bước

1. Khách chọn nơi đón, các con, rồi chọn **nơi giao cho từng con** (mặc định chung một nơi).
2. Hệ thống kiểm điều kiện, chọn **thứ tự giao ngắn nhất**, hiện giờ dự kiến từng nơi và giá.
3. Thẩm định, báo giá, cọc, đón, trục: như đơn thường.
4. Tới hub đích, điều phối lập **một lượt giao nhiều điểm**, gợi ý sẵn thứ tự.
5. Ở mỗi nơi: quét chip các con của nơi đó, biên bản, người nhận ký. Con nào giao xong thì khách thấy ngay.
6. Giấy **theo con** (hộ chiếu, sổ tiêm) trả ở nơi của con đó. Giấy **của đơn** (một bản gốc) đi theo xe, trả ở **nơi cuối**; các nơi trước nhận bản sao.
7. Con cuối giao xong thì đơn **Đã giao**.

### 12.4 Công thức tiền

> **Tổng = cước đón (một lần) + cước trục theo khoang + cước giao + nhiên liệu + thủ tục, lệ phí (một lần) + dịch vụ, bảo hiểm**

Chỉ **cước giao** và **nhiên liệu đoạn giao** khác đơn thường. Lấy cách **rẻ hơn cho khách**:

| Cách | Công thức |
|---|---|
| Tính theo vòng | km thật hub → nơi 1 → nơi 2 → nơi 3, nhân đơn giá hạng xe nhỏ nhất chở **cả** số chỗ |
| Tính riêng từng nơi | Cộng km hub → từng nơi, nhân đơn giá hạng xe chở số chỗ **của nơi đó** |

Nhiên liệu tính theo km của cách được chọn. Không thu phí điểm dừng riêng. Không phải chia tiền giữa các nơi vì **một khách trả một đơn**.

### 12.5 Ví dụ

Hai con từ Củ Chi, cùng chuyến, A giao Điện Bàn (27 km từ hub), B giao nơi cách hub 100 km (giả định).

| Khoản | Cùng hướng, vòng 105 km | Ngược hướng, vòng 154 km |
|---|---:|---:|
| Cước đón | 1.417.500 | 1.417.500 |
| Cước trục, 2 khoang | 25.200.000 | 25.200.000 |
| Cước giao | Theo vòng: 105 × 26.250 = 2.756.250 | Tính riêng rẻ hơn: 850.500 + 2.625.000 = 3.475.500 |
| Nhiên liệu | (54 + 105) × 9.450 = 1.502.550 | (54 + 127) × 9.450 = 1.710.450 |
| Thủ tục, lệ phí | 500.000 | 500.000 |
| **Tổng** | **31.376.300** | **32.303.450** |
| So với đặt hai đơn rời (34.731.250) | rẻ hơn 3.354.950 | rẻ hơn 2.427.800 |

### 12.6 Tình huống

| Tình huống | Hệ thống sẽ |
|---|---|
| Một nơi người nhận vắng | Chỉ con của nơi đó thất bại, xe đi tiếp nơi sau, rồi đưa con đó về hub (luồng 5) |
| Chờ ở nơi **chưa phải cuối** | Tối đa **60 phút** (đề xuất) thay vì 120, để con còn trên xe không quá giờ. Nơi cuối vẫn 120 phút |
| Giao lại con thất bại | Lượt giao riêng, **phí giữ và cước giao lại** của con đó |
| Bỏ một con lúc đón (mục 8) | Bỏ nơi giao của nó nếu không còn con nào tới đó, tính lại cước giao |
| Đổi nơi giao sau khi cọc | Chỉ khi chưa lập lượt giao, cùng hub, vẫn đủ điều kiện; lập báo giá thay thế, khách đồng ý, chênh lệch vào số dư |
| Đổi nơi giao khi đã trả số dư | Không nhận trên hệ thống; nơi mới khác tỉnh có thể phải đổi giấy (**chưa kiểm chứng**) |
| Một nơi đường cấm xe tải | Không cho gộp, mời khách tự tới hub nhận con đó hoặc đặt đơn riêng |

### 12.7 Phải sửa gì

| Phần | Hiện tại | Sửa |
|---|---|---|
| Bảng | Mỗi đơn một địa chỉ giao (ràng buộc duy nhất) | Cho nhiều địa chỉ **giao**; thêm cột **nơi giao** cho từng ngựa trong đơn. Không thêm bảng |
| Máy chủ A | Đặt đơn, kiểm hub, tính giá, báo giá dùng một địa chỉ giao | Đọc theo từng con; thêm tính vòng giao |
| Máy chủ B | Xếp chặng giao, gợi ý gom, tính giờ dùng một địa chỉ giao | Kiểm theo nơi của từng con; bắt buộc một lượt giao cho cả đơn |
| Màn hình | Đặt đơn, chi tiết đơn, điều phối, lượt xe | Chọn nơi theo con; hiện từng nơi. Lượt giao nhiều điểm **đã chạy được** |
| Không đổi | Trạng thái đơn (đã đếm theo từng con), cọc, số dư, quyết toán, sự cố | |

Khoảng 10 chỗ trong máy chủ và 10 màn đang dùng **một** địa chỉ giao. Đơn cũ giữ nguyên, coi như mọi con cùng một nơi.

### 12.8 Đã chốt 25/11/2026

| # | Câu hỏi | Đề xuất |
|---|---|---|
| 12a | Có làm không | Làm, **sau đợt 1** |
| 12b | Cước giao | Lấy cách rẻ hơn giữa theo vòng và tính riêng |
| 12c | Số nơi tối đa | 3 |
| 12d | Chờ ở nơi chưa phải cuối | 60 phút |
| 12e | Xuống dọc đường (Nha Trang, Quy Nhơn…) | **Không**: đó là trạm nghỉ, không có hub và xe trung chuyển; giữ quy tắc lên ở đầu tuyến, xuống ở cuối tuyến (3.10) |

---

## 13. Phí giữ ngựa và kết thúc đơn còn nợ (25/11/2026)

**Đã chốt với người dùng:** người nhận tự tới hub thì **không chặn**; phí tính theo số lần đủ 24 giờ; đơn **không được treo mãi** vì khách không trả. **Các con số bên dưới chờ duyệt, code chưa sửa.**

### 13.1 Căn cứ

| Nguồn | Nội dung | Mức kiểm chứng |
|---|---|---|
| IRT Anh 5, Mỹ 6; Úc 14; Anh 18 | Khách trả tiền lưu khi chờ; người được chỉ định không trả thì khách trả; tính chung công nợ của khách | Đọc bản gốc |
| IRT Anh 21 | Công nợ quá 30 ngày mới tính lãi | Đọc bản gốc |
| [NetSuite](https://docs.oracle.com/en/cloud/saas/netsuite/ns-online-help/section_N1220604.html), [ERPNext](https://docs.frappe.io/erpnext/user/manual/en/selling/articles/close-sales-order) | Đóng đơn không làm mất hóa đơn; **trạng thái đơn tách khỏi công nợ** | Qua kết quả tìm kiếm, **chưa kiểm chứng** |
| [Tạp chí Tòa án, BLDS 429](https://tapchitoaan.vn/index.php/thoi-hieu-khoi-kien-ap-dung-doi-voi-loai-hop-dong-co-dieu-kien-theo-quy-dinh-tai-dieu-429-blds-2015) | Thời hiệu khởi kiện hợp đồng 3 năm | Qua kết quả tìm kiếm, **chưa kiểm chứng** |
| LTM 239 | Báo bằng văn bản, sau 45 ngày mới được định đoạt | Đọc trên trang luật |
| [Horse & Hound](https://www.horseandhound.co.uk/?p=647680), BHS | Chủ trại vẫn chịu trách nhiệm chăm ngựa bị bỏ lại; muốn xử lý phải có thông báo và đúng thủ tục | Qua kết quả tìm kiếm, **chưa kiểm chứng** |

### 13.2 Cách tính phí giữ

**Mức đã chốt với người dùng:** đếm số lần **đủ 24 giờ**, mỗi lần **1.000.000 đ** (số mẫu, cấu hình). Phần lẻ chưa đủ 24 giờ **không tính**.

| Ngựa ở hub | Cách cũ | Cách mới |
|---|---:|---:|
| Dưới 24 giờ | 1.000.000 | 0 |
| Từ 24 đến dưới 48 giờ | 1.000.000 hoặc 2.000.000 | 1.000.000 |
| Từ 48 đến dưới 72 giờ | 2.000.000 hoặc 3.000.000 | 2.000.000 |
| 68 giờ | 3.000.000 | 2.000.000 |
| Đúng 72 giờ | 3.000.000 | 3.000.000 |

Không tính theo ngày lịch. Lưu chuồng **theo kế hoạch** (800.000 đ/ngày, có trong báo giá) giữ cách cũ.

**Giờ bắt đầu, đã chốt nguyên tắc, chờ duyệt chi tiết:** mốc **muộn nhất** trong hai mốc: sự việc ở bảng dưới, và lúc hệ thống **đã báo khách** (BLDS 538.3 đòi báo ngay). Báo trễ là công ty chịu.

| Trường hợp | Đồng hồ bắt đầu | Vì sao |
|---|---|---|
| Giao hụt, đưa về hub | Lúc tổ lái **hoàn tất giao ngựa vào hub** (có quét chip) | Giờ có bằng chứng, không tính quãng đường xe chở về |
| Khách hẹn tự tới hub, tới trễ | **Giờ hẹn**; ngựa vào hub muộn hơn giờ hẹn thì lấy lúc vào hub | Trước giờ hẹn là thời gian công ty lo |
| Chưa trả số dư, lỡ chuyến | **Giờ chạy T** của chuyến | Trước T ngựa vẫn chờ chuyến bình thường |
| Đơn nhập chưa trả, đưa về hub | Lúc hoàn tất giao vào hub | Bỏ mốc "24 giờ sau khi vào hub" ở mục 9.2 vì 24 giờ đầu đã không tính |
| Nhà khách không ai nhận lại | Lúc hoàn tất giao vào hub | Như giao hụt |

**Giờ dừng:** lúc hoàn tất giao ngựa ra khỏi hub. Công ty giao lại trễ thì dừng ở giờ đã hẹn. Do công ty hoặc bất khả kháng thì không tính.

**Chốt trước khi giao lại:** tính tới giờ hẹn; rời trễ do khách thì thu thêm, rời sớm làm giảm số lần đủ 24 giờ thì hoàn phần thừa.

### 13.3 Kết thúc đơn: tách việc chở khỏi công nợ

**Đơn Hoàn thành khi đủ bốn điều**, dù còn khoản thu thêm chưa trả:

1. Mọi con đã giao hoặc đã có kết quả cuối.
2. Không còn sự cố, khiếu nại mở.
3. Mọi chi phí phía khách đã duyệt hoặc bị từ chối, và khoản duyệt đã thành khoản thu.
4. Không còn khoản hoàn chờ chuyển (đây là nợ của **công ty**, không được bỏ qua).

Đủ bốn điều và qua 7 ngày từ lúc giao thì **hệ thống tự đóng**. Khoản chưa trả nằm ở **công nợ của khách**, vẫn trả được sau khi đơn đóng. Số dư chính không bao giờ thành nợ kiểu này, vì chưa trả thì xe không chở.

| Mốc, từ lúc lập khoản thu | Hệ thống | Khách |
|---|---|---|
| Ngày 0 | Báo khách | Trả trong 7 ngày |
| Ngày 7 | Quá hạn, nhắc mỗi ngày | **Chưa đặt đơn mới được** |
| Ngày 14 | Quản lý gọi; có thể **gia hạn một lần** tối đa 14 ngày hoặc **chia tối đa 3 kỳ** | Trả đúng kỳ thì được đặt đơn lại |
| Ngày 30 | Sang **Nợ khó thu**, ngừng nhắc mỗi ngày, gửi văn bản | Vẫn trả được, vẫn bị chặn |
| Từ ngày 30 | Quản lý chọn: tiếp tục đòi, hoặc **xóa nợ** (lý do, tệp quyết định) | Xóa nợ thì khoản hết hiệu lực; mở lại quyền đặt đơn do quản lý quyết riêng |

Không bao giờ tự xóa nợ. Không tính lãi. Không giữ ngựa, giấy của đơn khác để đòi.

### 13.4 Đơn mà ngựa chưa có người nhận

Treo thật sự là khi **ngựa còn ở hub**. Đơn và việc giữ ngựa có mốc kết thúc:

| Trường hợp | Kết thúc |
|---|---|
| Chưa trả số dư, hết 3 ngày | **Đơn hủy**. Việc giữ ngựa tách ra, theo dõi ở trang Theo dõi tới khi trả ngựa |
| Giao hụt, hết 3 ngày không hẹn lại | Nhắc mỗi ngày; ngày 7 gửi văn bản và đề nghị **chở về người gửi** |
| Văn bản đủ 45 ngày | Quản lý **phải** ghi kết quả: trả người gửi, trả người được ủy quyền, hoặc bàn giao theo văn bản pháp lý. Bắt buộc tệp |
| Sau khi ghi kết quả | Còn con khác đã giao: đơn **Đã giao** rồi đóng. Mọi con đều trả lại: đơn **Hủy** |

Phí giữ **ngừng cộng sau 30 ngày** (đề xuất): công ty vẫn chăm, nhưng nợ không phình quá giá trị chuyến chở.

### 13.5 Chờ duyệt

| # | Con số | Đề xuất |
|---|---|---|
| 13a | Giờ bắt đầu | Bảng ở 13.2 |
| 13b | Mức | **Đã chốt**: mỗi 24 giờ trọn vẹn 1.000.000 đ |
| 13c | Trần phí giữ | 30 ngày |
| 13d | Sang Nợ khó thu | 30 ngày |
| 13e | Gia hạn, chia kỳ | Một lần 14 ngày, hoặc 3 kỳ |
| 13f | Đơn đóng khi còn khoản thu thêm | Có |

**Đổi so với quyết định cũ:** "còn nợ bằng 0 mới đóng đơn" (6.17) thành 13.3; "lẻ ngày tính tròn một ngày" (5.25) thành 13.2; thêm đường hủy cho đơn Đang vận chuyển khi mọi con đã trả lại.
