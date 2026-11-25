# Màn hình và tác động

> Tài liệu đồng bộ giữa hai luồng: mỗi thao tác diễn ra ở màn hình nào, ghi gì, chặn khi nào, vai trò khác thấy gì.
> **Trạng thái:** người dùng đã chốt các điểm ở mục 8 ngày 23/11/2026 (gồm đợt hai: cách ly, dịch vụ, ghép đơn, thanh toán, cho ăn, sự cố, đơn nhập). Các dòng còn lại là thiết kế màn hình, agent trình lại trong phương án của từng task. Phần mới so với `NGHIEP_VU_THEO_59_BANG.md` được đánh dấu **[mới]**; các quyết định ở mục 8.
> Khi mâu thuẫn, theo thứ tự ưu tiên ở mục 0 của `AGENTS.md`. Chưa kiểm chứng với IRT/Peden Bloodstock.

## 1. Cách dùng

Task nào làm tới thao tác nào thì đối chiếu dòng đó. Một dòng **chưa thiết kế xong** nếu còn trống cột nào:

| Cột | Câu hỏi |
|---|---|
| Màn hình (vai trò) | Ai bấm, ở đâu |
| Thao tác | Nút hoặc ô nhập cụ thể |
| Ghi | Bảng và cột |
| Chặn khi | Backend từ chối khi nào, báo lỗi gì |
| Tác động | Hệ thống làm tiếp gì, màn hình vai trò nào đổi |
| Khách thấy | Mốc trên đơn hoặc trên từng con ngựa |

## 2. Nguyên tắc đồng bộ

1. **Trạng thái suy ra, không chép.** Các màn hình cùng đọc một nguồn: `booking.status` cho mốc thương mại; `horse_leg`, `horse_stay`, `handover` cho vị trí và người giữ.
2. **Một thao tác là một transaction.** Ghi dữ liệu, đổi trạng thái, ghi lịch sử và thông báo cùng thành công hoặc cùng hoàn tác.
3. **Backend chặn, giao diện báo trước.** Nút mờ kèm lý do, và backend vẫn kiểm lại.
4. **Hai người bấm cùng lúc:** người sau nhận lỗi kèm trạng thái mới. Khoang khóa bằng `FOR UPDATE`.
5. **Trách nhiệm chuyển khi `handover` DONE**, theo từng con ngựa. Với khách, công ty chịu trách nhiệm suốt hành trình.
6. **Phân công theo lượt xe** (`crew_assignment`) là kế hoạch; **phụ trách theo từng con** là người ký nhận gần nhất. Chỉ dẫn và dịch vụ gắn với `booking_horse` nên đi theo con ngựa.
7. **Dịch vụ bán thêm phải trả lời được [mới]:** ai làm, khi nào, bằng chứng ở bảng nào, không làm được thì hoàn tiền ra sao.

## 3. Trước khi nhận ngựa (luồng A)

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 3.1 | Hồ sơ ngựa (khách) | Thêm, sửa ngựa | `horse` | Chip sai hoặc trùng; đổi chip | Chọn được khi đặt đơn | Danh sách ngựa |
| 3.1a | Sổ địa chỉ (khách) | Thêm, sửa, xóa địa chỉ; chọn tỉnh thì bản đồ phóng về tỉnh đó, ghim vị trí | `customer_address` | Chưa ghim; ghim cách trung tâm tỉnh quá `ADDRESS_MAX_KM_FROM_PROVINCE` (200 km) | Chọn được khi đặt đơn | Danh sách địa chỉ |
| 3.2 | Đặt đơn, bước 1 (khách) | Chọn nơi đón, nơi giao, cách đưa và nhận ngựa, chuyến trong tuần | Chưa ghi | Hết chỗ, quá giờ chốt, không kịp làm giấy, cửa khẩu không khớp nước đối tác, chặng công ty chở vượt nửa giới hạn lái mỗi ngày, gợi ý tự chở tại đầu trong nước | Xem trước giấy phải nộp. Không có tuyến thì cho xin chuyến đột xuất. Chọn xong chuyến thì kiểm ngay xe công ty tới được mỗi đầu không (`POST /pricing/transfer-options`): không được thì khóa lựa chọn và chuyển sang tự chở, bản đồ hỏng thì không chặn | Chuyến phù hợp, chỗ còn, hạn chốt, nhãn "Dự kiến" hoặc "Đủ điều kiện chạy"; khung giờ đón, giao, hạn tới hub, phút chờ miễn phí lấy từ `GET /public/transport-rules` |
| 3.2a | Đặt đơn khi không có chuyến (khách) **[25/11]** | Xem lý do: nơi đón hoặc nơi giao ngoài vùng phục vụ (kèm các tỉnh đang phục vụ), chưa có tuyến tới nước đó, chuyến không kịp làm giấy (khóa, ghi số ngày cần) | Chưa ghi | | | Biết phải đổi địa chỉ hay đổi chuyến |
| 3.3 | Bước 2–3 (khách) | Chọn ngựa, khoang, chỉ dẫn ăn uống, dịch vụ thêm, bảo hiểm, đón sớm và xác nhận cách đưa, nhận ngựa | Chưa ghi | Ngựa đang ở đơn chưa kết thúc | Xem trước giá từ backend, chia "Đã gồm", "Bắt buộc", "Bạn chọn" | Giá tạm tính |
| 3.4 | Bước 4 (khách) | Khai các bên, đồng ý điều khoản, **Gửi đơn** | `booking`, `booking_party`, `booking_address`, `booking_horse`, `booking_horse_service`, `horse_insurance`, `terms_acceptance`, `booking_status_history` | Một trong các điều kiện trên đổi khi gửi | Giữ khoang; đơn vào hàng chờ chuyên viên và điều phối. Chuông: khách "Đã nhận đơn", chuyên viên "Đơn mới chờ tiếp nhận" | "Chờ tiếp nhận"; khối "Bạn cần biết" trước nút gửi |
| 3.5 | Thẩm định hồ sơ (chuyên viên) | Tải giấy COMPANY, CONSIGNEE, AGENT, duyệt hoặc từ chối từng giấy, xác nhận hoặc mở lại bảo hiểm | `document`, `horse_insurance`, `audit_log` | Từ chối hoặc mở lại thiếu lý do. Chỉ mở lại khi đơn UNDER_REVIEW hoặc NEEDS_RESUBMISSION | Giấy tải lên chờ duyệt. Khi thẩm định, chuyên viên có thể trả đơn thiếu hồ sơ khách về NEEDS_RESUBMISSION. Ở CONFIRMED, khách có thể nộp lại giấy CUSTOMER bắt buộc khi thiếu bản còn hiệu lực | Giấy nào thiếu, ai nộp và cần ở mốc nào |

| 3.5b | Đơn thiếu giấy (chuyên viên, quản lý) **[A8]** | Xem danh sách; tới mốc 48 giờ quản lý chọn nguyên nhân khách, công ty hoặc cơ quan | `booking_status_history.cause_type`, `previous_departure_id`, `booking.departure_id` | Thiếu nguyên nhân hoặc lý do, chưa tới mốc quyết định, hoặc giấy đã đủ | Nhắc khách từ 5 ngày trước. Nhả chỗ, đơn sang `SCHEDULE_CHANGE_REQUIRED` | Giấy thiếu, chuyến tuần sau, tiền hoàn nếu không đi |
| 3.5c | Nhận và trả ngựa tại hub (nhân viên hub) | Xác nhận lịch hẹn, kiểm giấy khi nhận và ký giao nhận | `handover` PICKUP hoặc DELIVERY gắn `horse_stay` | Thiếu giấy bắt buộc khi nhận | Quá hạn nhận thì gửi chuồng, báo mức phí theo ngày bằng thông báo tự động. Đặt hoặc đổi giờ hẹn thì báo khách, các con cùng giờ chỉ báo một lần | Giờ hẹn và biên bản |
| 3.6 | Kế hoạch đơn (điều phối) | Xem chuyến đang giữ chỗ, đổi chuyến trước khi cọc (đơn đang thẩm định hoặc chờ bổ sung) | `booking.departure_id`, `horse_stay` | Chuyến hết chỗ | Đổi chuyến thì nhả và giữ khoang cùng lúc. Ngày xuất phát mới dùng để kiểm bảo hiểm PERSONAL khi bắt đầu báo giá | Lịch dự kiến |
| 3.7 | Duyệt báo giá (quản lý) | Xem dòng giá, điều chỉnh kèm lý do, **Gửi** | `quotation`, `quotation_line` | Khi bắt đầu báo giá, thiếu giấy CUSTOMER bắt buộc, chưa xác định yêu cầu theo cửa khẩu, thiếu chuyến trục hoặc bảo hiểm ngựa ACTIVE chưa hợp lệ. Khi gửi, tổng hoặc tiền cọc bằng 0 hay báo giá bảo hiểm mua hộ đã hết hạn | Đơn sang `AWAITING_DEPOSIT`, đếm giờ hết hạn | Báo giá, hạn chấp nhận |
| 3.7a | Chuyến cần quyết định (quản lý) **[mới]** | Từng tuyến trong tuần: **Chạy**, dùng xe nhỏ, dồn chuyến, **Hủy** | `departure`, `booking` | Trước mốc 48 giờ (chỉ chặn **Chạy**); dồn hoặc hủy thiếu lý do | Hiện chỗ đã cọc trên sức chứa, cảnh báo dưới 30%, tuyến sau cần xe này, lượt về rỗng. Đơn chưa gửi báo giá bị nhả chỗ tại mốc quyết định | Xác nhận chạy, hoặc đổi chuyến và hoàn 100% |
| 3.7b | Xin chuyến đột xuất (khách, quản lý) **[mới]** | Khách yêu cầu, quản lý tạo chuyến | `departure` không có lịch | Không có xe hoặc tổ xe | Đơn gắn chuyến mới | Ngày, giá riêng |
| 3.8 | Báo giá và thanh toán (khách) | **Chấp nhận** rồi trả cọc, hoặc **Từ chối** kèm lý do | `quotation`, `payment`, `payment_transaction` | Báo giá hết hạn hoặc đã có bản mới | Trả xong: `CONFIRMED`, cấp vận đơn. Từ chối: về `QUOTING` và giữ chỗ. Hết hạn: nhả chỗ sau thời gian chờ xác nhận nếu có phiên VNPAY chưa phản hồi | Vận đơn |
| 3.9 | Điều độ (điều phối) | Duyệt nhóm đón, giao do hệ thống gợi ý; gán xe, tài xế, hộ tống, ngựa; xếp xe nối nhiều tuyến trong tuần | `vehicle_trip`, `trip_stop`, `crew_assignment`, `horse_leg` | Quá chỗ, quá 4 giờ, trùng giờ, xe hoặc người không ở điểm đầu, xe bảo dưỡng hoặc ngừng sử dụng, ngựa cùng đơn ở hai xe trục | Nhân viên thấy lượt xe mới. Xe bị khóa hiện lý do. Gán ngựa vào lượt đón, giao, chạy thẳng thì báo khách khung giờ 1 giờ, mỗi đơn mỗi lượt một lần; gỡ rồi gán lượt lệch quá `SCHEDULE_CHANGE_NOTICE_MINUTES` so với giờ đã báo thì báo "Giờ dự kiến thay đổi" | Khung giờ đón, giao từng con trong hành trình |
| 3.9b | Tính giờ lượt xe (điều phối) | Chọn điểm và ngựa, xem giờ từng điểm, sửa giờ xuất phát, **Lập lượt xe** kèm ngựa một lần | `vehicle_trip`, `trip_stop`, `horse_leg` | Ngựa ở trên xe quá giới hạn; tới hub quá hạn; lái quá giờ mỗi ngày | Lỗi thì không lưu gì | |
| 3.9c | Đưa xe về (điều phối) **[25/11]** | Bảng tuần ghi nơi xe đang ở khi xe ngoài hub gốc; **Lập lượt về** mở sẵn lượt trục chiều ngược | `vehicle_trip` | Chuyến chiều ngược chưa được quản lý xác nhận chạy; không có chuyến xuất phát từ nơi xe đang ở | Xe về hub gốc | |
| 3.9a | Sơ đồ khoang của lượt xe (điều phối) **[mới]** | Xem sơ đồ hệ thống đã xếp, **Xếp lại tự động**, đổi chỗ, **Lưu** | `horse_leg.stall_position` | Trùng vị trí, khoang rộng không đủ hai vị trí liền nhau, vượt số chỗ, lượt xe đã bắt đầu, người khác vừa lưu | Cảnh báo đực cạnh cái, con xuống trước bị chắn. Tổ xe thấy sơ đồ mới | "Khoang số…" sau khi xếp, chỉ xem |
| 3.10 | Lượt xe của tôi (tài xế, hộ tống) | **Xác nhận nhận việc** | `crew_assignment` ACKNOWLEDGED | Đã bị gỡ khỏi lượt xe | Điều phối thấy đã nhận; thiếu thì cảnh báo | |
| 3.11 | Xe (quản trị viên) **[mới]** | Thêm, sửa thuộc tính; đưa vào bảo dưỡng, đưa lại hoạt động, ngừng sử dụng | `vehicle`, `audit_log` | Trùng biển số, quá số chỗ của hạng xe; xe đang chạy hoặc còn lượt chưa hoàn thành; xe đã ngừng sử dụng | Xe ẩn hoặc hiện khi gán | |
| 3.12 | Xe về hub gốc (tài xế) **[mới]** | Báo bình thường hoặc cần bảo dưỡng | `vehicle_trip`, `vehicle` | Xe chưa về hub gốc | Cần bảo dưỡng: xe sang MAINTENANCE | |

## 4. Nhận ngựa và tập kết (luồng B)

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 4.1 | Lượt xe (tài xế) | **Xuất phát** | `vehicle_trip` IN_PROGRESS | Thiếu giấy (gồm `QUARANTINE_CLEARANCE` đơn xuất), bảo hiểm chưa xác nhận, tài xế chưa nhận việc | Điều phối thấy xe chạy | "Xe đang tới đón" |
| 4.2 | Điểm dừng: đón (hộ tống, điện thoại) | Quét chip, khám nhanh, chụp ảnh, nhận giấy gốc | `handover` PICKUP, `handover_horse`, `horse_health_log`, `handover_document`, `document` | Chip sai thì không nhận con đó. Chip đúng thì hiện "Khoang số…" để dẫn ngựa vào | Sự cố `DOCUMENT` | |
| 4.3 | Như trên | Ký, **Nhận ngựa** | `handover` DONE, `payment` BALANCE nếu còn nợ | Thiếu biên bản | Gửi yêu cầu trả số dư bằng tổng báo giá đã chấp nhận trừ số đã trả. Hạn tính theo `BALANCE_WAIT_MINUTES`. **Trách nhiệm sang tổ xe** | "Đã nhận ngựa, chờ thanh toán", nút thanh toán nếu còn nợ |
| 4.3a | Như trên **[mới]** | **Rời điểm đón** | `trip_stop`, `horse_leg`, `booking` IN_OPERATION | Còn đơn ở điểm này chưa trả đủ | Hết quyền tự hủy | "Đang vận chuyển" |
| 4.3a · hub, cửa khẩu | Điểm dừng của tổ xe | **Hoàn tất lên xe tại hub** hoặc **Rời cửa khẩu về hub** | `handover`, `horse_leg`, `trip_stop` | Lên xe tại hub cần trả đủ số dư | Đơn nhập chưa trả đủ vẫn Đã xác nhận khi rời cửa khẩu | Hiện mã đơn khi xác nhận HUB_OUT bị chặn do chưa trả đủ |
| 4.3b | Như trên | **Trả ngựa tại chỗ** khi hết giờ chờ thanh toán | `handover` RETURN, `handover_document`, `booking` SCHEDULE_CHANGE_REQUIRED | Chưa hết giờ chờ hoặc giao dịch còn trong thời gian xác nhận | Nhả chỗ, giữ khoản số dư, khách có 30 ngày chọn lại lịch. Đơn khác đi tiếp | Cần chọn lại chuyến |
| 4.11 · chờ số dư | Theo dõi (điều phối) | Ghi kết quả gọi khách và liên hệ khẩn cấp. Gia hạn một lần hoặc xác nhận đưa về hub | `audit_log`, `payment.due_at`, `horse_leg` | Gia hạn phải còn kịp tới hub và trong giới hạn ngồi xe. Đưa về hub yêu cầu đã hết giờ chờ và có điểm hub an toàn | Ngựa vẫn hiện trên bảng chăm sóc | Hiện kết quả thao tác |
| 3.5c · giữ do số dư | Hub (điều phối), Chi tiết đơn (khách) | Xem mốc tính phí và hướng dẫn chọn lại lịch. Trả tại hub sau khi xác nhận chip | `horse_stay`, `handover` RETURN, `cost_item`, `payment` | Kiểm tra chip và còn lưu trú | Trả ngựa dù khách còn nợ. Phí được chốt sau khi giao nhận hoàn tất và cấn cọc nếu đã hủy | Lưu trú đã kết thúc, khoản thu thêm nếu có |
| 4.4 | Như trên | **Ghi đón hụt**, chọn nguyên nhân và chụp ảnh | `booking_horse`, `document`, `audit_log` | Thiếu lý do hoặc ảnh. Người gửi vắng cần đủ thời gian và đủ ba kết quả gọi | Gỡ chặng kế hoạch của con hụt, giữ chỗ trên chuyến cho tới quyết định hoặc hết hạn | Con nào chưa nhận được, hạn chọn và bên chịu |
| 4.5 | Điểm dừng: hub (hộ tống) | Quét chip, **Giao vào hub** | `handover` HUB_IN, `horse_stay` ASSEMBLY | Chip sai | Xe đón rảnh. Người ở `handled_by` phụ trách tới HUB_OUT **[mới]** | "Ở hub, chờ chuyến" |
| 4.6 | Ngựa ở hub (hộ tống) | Cho ăn bữa tối, bữa sáng theo chỉ dẫn; đo, ghi ăn uống | `horse_health_log` (`horse_stay_id`, `water_intake`, `feed_intake`) | Không còn lưu trú | Con chưa ghi bữa hiện đỏ **[mới]** | Chỉ số mới nhất |
| 4.7 | Thú y tại chuồng (chuyên viên) | Chọn đối tác VET, hẹn ngày; tải phiếu khám, phiếu xét nghiệm **[mới]** | `document` gắn `booking_horse_id` | Đối tác không có vai trò VET | Kiểm phiếu bắt buộc trước khi xe đi đón hoặc khi nhận tại hub nếu SELF; không khám được thì trừ khi quyết toán | Phiếu khám |
| 4.8 | Ngựa của tôi (hộ tống, điện thoại) **[mới]** | Thẻ từng con kèm việc sắp tới, một chạm để ghi | `horse_health_log` | Không phụ trách con đó | Việc trễ hiện đỏ, báo điều phối | Chỉ số mới nhất |
| 4.9 | Nhận bàn giao (hộ tống mới) **[mới]** | Xem phiếu tóm tắt, quét chip, khám, ký | `handover` SHIFT, `handover_horse`, `horse_health_log` | Chip sai; chưa xem cảnh báo; không trong ca | Thẻ chuyển sang người mới | Ai đang chăm sóc |
| 4.10 | Kết thúc ca, lượt xe (hộ tống) **[mới]** | **Kết thúc** | `vehicle_trip`, `crew_assignment` | Còn ngựa chưa có người nhận | Rảnh để nhận việc mới | |
| 4.11 | Bảng phụ trách (điều phối) **[mới]** | Xem mọi con: ở đâu, ai giữ, việc trễ | | | Con không có người trong ca hiện đỏ đầu bảng | |
| 4.11a | Đại lý đã giao (điều phối, trang Theo dõi) **[25/11]** | **Đại lý đã giao** trên con ngựa đại lý đang giữ | `horse_leg`, `booking` | Chặng chưa bàn giao ở cửa khẩu | Giao hết các con thì đơn `DELIVERED` | Đã giao |

## 5. Giấy đủ điều kiện cách ly

Công ty không vận hành nơi cách ly. Điều kiện cách ly thể hiện bằng giấy, kiểm ở các màn hình dưới đây.

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 5.1 | Đặt đơn (khách) | Xem danh sách giấy theo nước đến | Chưa ghi | Không kịp ngày làm giấy | | Giấy nào, ai nộp, cần trước mốc nào |
| 5.2 | Thẩm định (chuyên viên) | Tải và duyệt `QUARANTINE_CLEARANCE` từng con (đơn EXPORT) | `document` | Không đạt mà thiếu lý do | Còn thiếu thì đơn nằm ở danh sách 3.5b | "Đã có xác nhận cách ly" từng con |
| 5.3 | Lượt xe (tài xế, điều phối) | Xem danh sách con đủ, con thiếu | | Thiếu giấy thì nút **Xuất phát** khóa kèm tên ngựa và tên giấy | | |
| 5.4 | Cửa khẩu chiều nhập (hộ tống) | Tải giấy vận chuyển mẫu 14a, **Nhận ngựa từ đại lý** | `document` IMPORT_TRANSPORT_CERT, `handover` AGENT (`from_party_id` = đại lý) | Thiếu giấy bắt buộc, chip sai | Trả 70% như 4.3; xe về hub | "Đã nhận ở cửa khẩu" |

## 6. Trục, cửa khẩu, giao, đóng đơn

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 6.1 | Lượt xe trục (hộ tống) | Nhận ngựa từ hub | `handover` HUB_OUT, `horse_leg` | Chip sai, chưa xong dịch vụ trước chuyến | Danh sách ngựa của mọi khách kèm chỉ dẫn và số khoang từng con | "Đang đi trục" |
| 6.1a | Lượt xe trục (tổ xe) | Rời điểm đầu, hoàn tất lượt | `departure.status` | Quản lý không thể hủy chuyến khi chuyến đã chạy hoặc đã hoàn tất | Xe đầu tiên rời điểm đầu thì chuyến đang chạy. Khi mọi lượt trục chưa hủy hoàn thành thì chuyến hoàn tất | |
| 6.2 | Điểm dừng: trạm nghỉ (tài xế, hộ tống) | **Tới nơi**, cho nước và cỏ, đo và ghi ăn uống từng con, **Rời đi** | `trip_stop`, `horse_health_log` | Còn con chưa ghi; chưa nghỉ đủ 30 phút (rời sớm phải xác nhận kèm lý do) **[mới]** | Bỏ uống 2 trạm liền: `WATCH`, báo điều phối. Điều phối thấy giờ trễ | Trạm, ăn uống, chỉ số từng con |
| 6.3 | Điểm dừng: cửa khẩu chiều xuất (tài xế) | Tải giấy, **Bàn giao đại lý** | `document`, `handover` AGENT | Thiếu giấy; không có đối tác ở ít nhất một bên | **Trách nhiệm sang đại lý**; chặng đại lý bắt đầu | "Đã qua cửa khẩu" |
| 6.4 | Chặng đại lý (điều phối) | Nhập mốc và ảnh đại lý gửi | `horse_leg`, `document` | Mốc lùi thời gian | | Mốc, ghi rõ "theo đại lý" |
| 6.5 | Điểm dừng: giao (tài xế) | **Bắt đầu chờ**, quét chip, ký, trả giấy gốc, **Hoàn tất** | `handover` DELIVERY, `handover_document`, `booking_horse` DELIVERED | Chip sai, thiếu chữ ký | Con cuối cùng xong: `DELIVERED` | "Đã giao", biên bản |
| 6.6 | Như trên | **Không gặp người nhận** | `handover` TIMEOUT, `incident`, `horse_stay` STABLING | Chưa hết giờ chờ miễn phí | Xe tự đi về hub. Lập lượt giao lại chốt phí giữ. HUB_OUT chặn khi chưa trả. Người nhận tự tới hub lấy được ngay. | Ngựa ở đâu, phí |
| 6.7 | Quyết toán (quản lý) | Duyệt từng chi phí, lập khoản thu thêm | `cost_item`, `payment` EXTRA_CHARGE | Khoản khách hoặc người nhận chưa hỏi và vượt hạn mức, trừ phí biểu phí gắn STABLING | Đóng khi hết nợ, không còn chi phí khách chịu chưa duyệt và không còn sự cố mở. Đơn không phát sinh tự đóng sau 7 ngày | Bảng kê, hạn và nút VNPAY. Nhắc 08:00 và chặn gửi đơn mới khi có khoản quá hạn. |
| 6.8 | Đánh giá (khách) | Chấm điểm | `booking_rating` | Đơn chưa ở `DELIVERED` hoặc `COMPLETED`, hoặc đã đánh giá | | |

## 7. Ngoại lệ

### Xác nhận giao ngựa và khiếu nại (G1)

- Chi tiết đơn của khách hiện phần xác nhận giao ngựa và khiếu nại sau khi đơn đã giao. Khách chọn xác nhận nhận ngựa ổn hoặc gửi khiếu nại kèm ảnh cho một con hoặc cả đơn. Khách xem được phản hồi và kết luận của quản lý tại cùng vị trí.
- Chi tiết đơn của quản lý hiển thị xác nhận, khiếu nại và ảnh. Trong hạn nhập thay, quản lý ghi nhận khiếu nại đã tiếp nhận qua điện thoại. Quản lý có thể trả lời và ghi kết luận có lý do.
- Backend trả quyền thao tác theo mốc `DELIVERED`. Khiếu nại mở chặn đóng đơn. Sau khi khách xác nhận hoặc hết hạn trên ứng dụng, đơn được xét đóng theo điều kiện quyết toán. Khiếu nại nhập muộn trên đơn đã hoàn thành được xử lý riêng.

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 7.1 | Báo sự cố (tài xế, hộ tống) | Chọn cả xe hoặc từng con, ảnh, vị trí | `incident`, `document` | Lượt chưa chạy hoặc đã kết thúc, HEALTH chưa chọn ngựa, hoặc lượt có sự cố cả xe chưa đóng khi báo cả xe | Điều phối nhận ngay. Không đổi `booking.status` | Con nào bị ảnh hưởng |
| 7.2 | Phương án (điều phối) | Chọn cách xử lý, đối tác, chi phí dự kiến | `incident_plan` | Đối tác không có vai trò phù hợp. HANDOVER_TIMEOUT không có lựa chọn Đưa về hub | Quản lý chờ duyệt | |
| 7.3 | Duyệt phương án (quản lý) | **Duyệt**, **Từ chối** kèm lý do | `incident_plan` | Từ chối không lý do; chưa hỏi khách và vượt hạn mức | Tổ xe thấy chỉ đạo | Phương án và chi phí cần đồng ý |
| 7.4 | Sự cố (tổ xe, điều phối) | Ghi chi phí, tải chứng cứ, xác nhận đủ sức đối với HEALTH và ACCIDENT có ngựa | `incident.fit_confirmed_*`, `document`, `cost_item` | Thiếu kết quả thú y khi điều phối xác nhận, phương án chưa duyệt hoặc chưa nghỉ đủ thời gian | Lượt đã xong còn sự cố chưa đóng vẫn mở được | |
| 7.5 | Chuyến (quản lý) | **Hủy chuyến** (hộp xác nhận nêu số đơn) | `departure`, `booking` | Chuyến đã chạy; còn đơn đã nhận ngựa (`IN_OPERATION`), xử lý bằng sự cố | Đơn đủ điều kiện sang `SCHEDULE_CHANGE_REQUIRED`, lịch sử ghi COMPANY và chuyến vừa hủy | Chọn chuyến khác trong hạn giữ giá hoặc hoàn 100% |
| 7.6 | Đơn của tôi (khách) | **Hủy đơn** (xem trước tiền mất và tiền hoàn) | `booking`, `payment` REFUND | Đã nhận ngựa xong hoặc đã trả đủ | Trước cọc miễn phí. Hủy sau cọc theo nguyên nhân ở GĐ5 và mục 5.4. Nhả chỗ, báo điều phối. Khoản thu hộ và VET_CHECK không hoàn khi khách hủy hoặc hủy do cơ quan; công ty hủy thì hoàn 100% | Số tiền hoàn |

## 8. Đã chốt (23/11/2026)

| # | Vấn đề | Quyết định |
|---|---|---|
| 1 | Trạng thái của ngựa bị loại lúc nhận | `EXCLUDED` ngay khi loại, `RETURNED` khi đã trả ngựa xong |
| 2 | Cách ly | Công ty không vận hành nơi cách ly. Chuyên viên duyệt giấy `QUARANTINE_CLEARANCE` (**thay** quyết định chuyên viên kết luận cách ly) |
| 3 | Ai phụ trách ngựa ở hub | Người ở `handover.handled_by` của HUB_IN tới HUB_OUT |
| 4 | Hub vệ tinh không có nhân viên | Tổ xe trục ở lại tới khi giao; qua đêm thì gửi đối tác `STABLE` |
| 5 | Ghi ăn uống | Cột `water_intake`, `feed_intake` của `horse_health_log` (**thay** quyết định ghi vào `note`) |
| 6 | Dịch vụ cho ăn tính tiền | Ngừng `FEEDING` (chuyển `INACTIVE` bằng migration mới, làm cùng A3); giữ `VET_CHECK` với tên "Thú y tại chuồng". Chăm sóc chuẩn nằm trong cước |
| 7 | Chặng đại lý: đại lý không có tài khoản | Điều phối nhập hộ, khách thấy "theo đại lý" |
| 8 | Nghỉ và cho ăn | Tối đa 4 giờ, nghỉ ≥ 30 phút có nước và cỏ; bữa theo chỉ dẫn ở nơi nghỉ đêm và hub; tối đa 10 giờ lái mỗi ngày |
| 9 | Thanh toán | Cọc theo tỷ lệ trên cước dịch vụ cộng đủ bảo hiểm và lệ phí thu hộ, trả trong hạn báo giá. Thu tổng trừ cọc sau khi nhận ngựa và trước khi xe rời, chờ tối đa 30 phút |
| 10 | Ghép đơn | Khoang rộng 2 chỗ; ngựa cùng đơn cùng xe trục; quyết định chạy 48 giờ trước; ngưỡng cảnh báo 30% |
| 11 | Lịch | Mỗi tuyến một chuyến mỗi tuần, 6 tuyến |
| 12 | Sự cố | Quy trình của nhóm; bất khả kháng công ty chịu; không chia theo đầu ngựa |
| 13 | Giấy không kịp sau khi cọc | Theo nguyên nhân khách, công ty, cơ quan (3.5b) |
| 14 | Đơn nhập | Ngược đơn xuất; thiếu mẫu 14a thì không nhận ở cửa khẩu |
| 15 | Xe | Chỉ thuộc tính và trạng thái, không giấy tờ xe |
| 16 | Theo dõi sức khỏe xuyên suốt | Sổ theo con ngựa; người phụ trách đổi bằng ký nhận (thêm `SHIFT`); không buông ngựa khi chưa có người nhận. Mở rộng điểm 3 |

## 9. Ai làm phần nào

| Mục | Task |
|---|---|
| 3.1–3.4 | A5 |
| 3.5–3.6 | A6 (hồ sơ, bảo hiểm); lưu trú dự kiến phối hợp B3 |
| 3.7–3.8 | A7 |
| 3.5b | A8, gồm đổi chuyến đơn đã cọc |
| 3.7a, 3.7b | A6b |
| 3.9–3.10 | B1 |
| 3.11 | A6b. 3.12: B2 |
| 4.1–4.5, 6.1–6.5 | B1, B2. Khoản BALANCE và hủy do không trả: A7, A8; loại ngựa gọi `BookingHorseService.exclude` của A5 |
| 4.6, 4.8–4.11 | B2, B3 |
| 3.5c | B2 |
| 4.7 | A6 |
| 5.1–5.2 | A4, A6b. 5.3–5.4: B2 |
| 6.6, 7.1–7.4 | B4, B5 |
| 6.7–6.8, 7.6 | A8 |
| 7.5 | A1 (đã có backend) |

### Đơn nhiều nơi giao cùng hub

Bước chọn ngựa cho chọn nơi giao và nhập người nhận cho địa chỉ khác địa chỉ giao chung. Bước xem lại và chi tiết đơn hiện nơi giao theo con. Bảng điều độ dùng nơi của từng con để gợi ý các điểm dừng và điểm xuống. Màn hiện trường tiếp tục thao tác theo điểm dừng.

## Đợt 2 · Báo giá và chi phí sự cố

- Báo giá hết hạn có nút **Mở lại** ở màn quản lý. Đơn đủ điều kiện về `QUOTING`. Báo giá chạy thẳng của cả khách và quản lý ghi rõ cước xe giữ nguyên khi bớt ngựa.
- Màn tổ lái dùng nhãn **Nguyên nhân đề xuất**. Quản lý chọn nguyên nhân chốt khi duyệt phương án.
- Chi tiết đơn của khách hiện chi phí chờ phản hồi và phương án có dự trù. Khách đồng ý hoặc từ chối từng khoản và có thể đồng ý với phương án.
- Điều phối nhập nội dung cuộc gọi khi khách đồng ý qua điện thoại. Quản lý ghi lý do cấp cứu khi duyệt khoản VET khách đã từ chối.
- Quyết toán hiện khoản phải đòi lại đại lý và nút đánh dấu đã đòi. Form chi phí bỏ phí chờ.

## Đợt 1b bước 3 · Đón hụt

| Màn | Thao tác | Tác động |
|---|---|---|
| Tổ lái, lần nhận tại điểm đón hoặc cửa khẩu | Chọn nguyên nhân, nhập diễn giải, ghi từng cuộc gọi nếu người gửi vắng, chụp ảnh | Ghi `booking_horse`, `document`, `audit_log`. Chặn hoàn tất nhận hoặc rời điểm còn con chưa xử lý |
| Chi tiết đơn của khách | Xem lý do, bên chịu và hạn. Chọn A/B/C/D theo lựa chọn máy chủ, hoặc hẹn ngày mới cho đơn chạy thẳng | Lựa chọn được xử lý dưới khóa đơn |
| Điều phối, Điều độ → Hành trình của đơn | Chọn theo xác nhận qua điện thoại, nhập nội dung cuộc gọi | Cùng hợp đồng lựa chọn với khách, nhật ký ghi tài khoản điều phối và bảng điều độ tải lại sau khi lưu |
| Chi tiết đơn của quản lý | Chốt bên chịu | Ghi kết luận và nhật ký, xem lại trên chi tiết đơn |

## Đợt 1b bước 4 · Số tiền khi xử lý đón hụt

Khách và điều phối thấy tổng báo giá, số còn phải trả và số dự kiến hoàn khi xác nhận lựa chọn đã có bên chịu. Với hủy, hộp xác nhận hiện khoản hoàn và cọc bị giữ. Khi chưa chốt bên chịu, hộp xác nhận ghi số tiền đang chờ xác định. Quản lý có thể xác nhận dịch vụ theo ngựa đã hoàn thành ngay trong mục đón hụt trước khi chốt bên chịu. Khách thanh toán số dư bổ sung qua khu vực thanh toán hiện có, kể cả khi đơn đã Đang vận chuyển.

Chuyên viên ghi phí bảo hiểm được hoàn cho con đã loại trong phần bảo hiểm của màn hình thẩm định, nhập số hoàn lũy kế và số chứng từ của hãng. Hệ thống cập nhật báo giá và khoản hoàn phần dư dưới khóa đơn.

## Đợt 1b bước 5 · Dời chuyến

Thẻ chọn lại chuyến ẩn với đơn chạy thẳng; khách chọn **ngày đón mới** hoặc hủy ở thẻ đón hụt. Máy chủ chặn ngày quá 30 ngày kể từ lần hụt, thiếu thời gian làm lại giấy, hết bảo hiểm.

## Đóng đơn tách khỏi công nợ · mục 13.3, 13.5 (1–5)

Quyết toán: nút đóng đơn theo `canClose` của máy chủ, không theo số còn nợ.
