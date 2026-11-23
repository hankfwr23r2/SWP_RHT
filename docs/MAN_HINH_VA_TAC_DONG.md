# Màn hình và tác động

> Tài liệu đồng bộ giữa hai luồng: mỗi thao tác diễn ra ở màn hình nào, ghi gì, chặn khi nào, vai trò khác thấy gì.
> **Trạng thái:** người dùng đã chốt 7 điểm ở mục 8 ngày 23/11/2026. Các dòng còn lại là thiết kế màn hình, agent trình lại trong phương án của từng task. Phần mới so với `NGHIEP_VU_THEO_59_BANG.md` được đánh dấu **[mới]**; các quyết định ở mục 8.
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
6. **Hộ tống theo lượt xe** (`crew_assignment`), không theo đơn. Chỉ dẫn và dịch vụ gắn với `booking_horse` nên đi theo con ngựa.
7. **Dịch vụ bán thêm phải trả lời được [mới]:** ai làm, khi nào, bằng chứng ở bảng nào, không làm được thì hoàn tiền ra sao.

## 3. Trước khi nhận ngựa (luồng A)

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 3.1 | Hồ sơ ngựa (khách) | Thêm, sửa ngựa | `horse` | Chip sai hoặc trùng; đổi chip | Chọn được khi đặt đơn | Danh sách ngựa |
| 3.2 | Đặt đơn, bước 1 (khách) | Chọn nơi đón, nơi giao, chuyến | Chưa ghi | Hết khoang, quá giờ chốt, không kịp làm giấy | Xem trước giấy phải nộp | Chuyến phù hợp, hạn chốt |
| 3.3 | Bước 2–3 (khách) | Chọn ngựa, khoang, chỉ dẫn ăn uống, dịch vụ thêm, bảo hiểm | Chưa ghi | Ngựa đang ở đơn chưa kết thúc | Xem trước giá từ backend | Giá tạm tính |
| 3.4 | Bước 4 (khách) | Khai các bên, đồng ý điều khoản, **Gửi đơn** | `booking`, `booking_party`, `booking_address`, `booking_horse`, `booking_horse_service`, `horse_insurance`, `terms_acceptance`, `booking_status_history` | Một trong các điều kiện trên đổi khi gửi | Giữ khoang; đơn vào hàng chờ chuyên viên và điều phối | "Chờ tiếp nhận" |
| 3.5 | Thẩm định hồ sơ (chuyên viên) | Tải giấy COMPANY, CONSIGNEE, AGENT, duyệt hoặc từ chối từng giấy, xác nhận hoặc mở lại bảo hiểm | `document`, `horse_insurance`, `audit_log` | Từ chối hoặc mở lại thiếu lý do. Chỉ mở lại khi đơn UNDER_REVIEW hoặc NEEDS_RESUBMISSION | Giấy tải lên chờ duyệt. Khi thẩm định, chuyên viên có thể trả đơn thiếu hồ sơ khách về NEEDS_RESUBMISSION. Ở CONFIRMED, khách có thể nộp lại giấy CUSTOMER bắt buộc khi thiếu bản còn hiệu lực | Giấy nào thiếu, ai nộp và cần ở mốc nào |
| 3.6 | Kế hoạch đơn (điều phối) | Xác nhận hoặc đổi chuyến; lập lưu trú dự kiến | `booking.departure_id`, `horse_stay` | Chuyến hết chỗ; trại không còn vai trò hiệu lực | Đổi chuyến thì nhả và giữ khoang cùng lúc. Ngày xuất phát mới dùng để kiểm bảo hiểm PERSONAL khi bắt đầu báo giá | Lịch dự kiến |
| 3.7 | Duyệt báo giá (quản lý) | Xem dòng giá, điều chỉnh kèm lý do, **Gửi** | `quotation`, `quotation_line` | Khi bắt đầu báo giá, thiếu giấy CUSTOMER bắt buộc, chưa xác định yêu cầu theo cửa khẩu, thiếu chuyến trục hoặc bảo hiểm ngựa ACTIVE chưa hợp lệ | Đơn sang `AWAITING_DEPOSIT`, đếm giờ hết hạn | Báo giá, hạn chấp nhận |
| 3.8 | Báo giá và thanh toán (khách) | **Chấp nhận**, trả cọc | `quotation`, `payment`, `payment_transaction` | Báo giá hết hạn hoặc đã có bản mới | Trả xong: `CONFIRMED`, cấp vận đơn. Hết hạn: nhả khoang | Vận đơn |
| 3.9 | Điều độ (điều phối) | Gom đơn, tạo lượt xe, gán xe, tài xế, hộ tống, ngựa | `vehicle_trip`, `trip_stop`, `crew_assignment`, `horse_leg` | Quá khoang, trùng giờ, xe không ở điểm đầu | Nhân viên thấy lượt xe mới | Ngày giờ đón |
| 3.10 | Lượt xe của tôi (tài xế, hộ tống) | **Xác nhận nhận việc** | `crew_assignment` ACKNOWLEDGED | Đã bị gỡ khỏi lượt xe | Điều phối thấy đã nhận; thiếu thì cảnh báo | |

## 4. Nhận ngựa và tập kết (luồng B)

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 4.1 | Lượt xe (tài xế) | **Xuất phát** | `vehicle_trip` IN_PROGRESS | Còn nợ, thiếu giấy, bảo hiểm chưa xác nhận, tài xế chưa nhận việc | Điều phối thấy xe chạy | "Xe đang tới đón" |
| 4.2 | Điểm dừng: đón (hộ tống, điện thoại) | Quét chip, khám nhanh, chụp ảnh, nhận giấy gốc | `handover` PICKUP, `handover_horse`, `horse_health_log`, `handover_document`, `document` | Chip sai thì không nhận con đó | Sự cố `DOCUMENT` | |
| 4.3 | Như trên | Ký, **Nhận ngựa** | `handover` DONE, `horse_leg` | Thiếu biên bản | Con đầu tiên: `IN_OPERATION`, hết quyền hoàn tiền. **Trách nhiệm sang tổ xe** | "Đã nhận ngựa" |
| 4.4 | Như trên | **Không nhận**, kèm lý do | `booking_horse` EXCLUDED | Không ghi lý do | Nhả khoang, hủy chặng sau, báo giá mới chờ quản lý. Con khác vẫn đi | Con nào bị loại, vì sao |
| 4.5 | Điểm dừng: hub (hộ tống) | Quét chip, **Giao vào hub** | `handover` HUB_IN, `horse_stay` ASSEMBLY | Chip sai | Xe đón rảnh. Người ở `handled_by` phụ trách tới HUB_OUT **[mới]** | "Ở hub, chờ chuyến" |
| 4.6 | Ngựa ở hub (hộ tống) | Đo, ghi ăn uống theo chỉ dẫn | `horse_health_log` (`horse_stay_id`) | Không còn lưu trú | Con chưa ghi hôm nay hiện đỏ **[mới]** | Chỉ số mới nhất |
| 4.7 | Dịch vụ thú y (điều phối) | Chọn đối tác VET, nơi khám; tải phiếu khám **[mới]** | `document` gắn `booking_horse_id` | Đối tác không có vai trò VET | Chưa có phiếu thì không cho lên xe trục; không khám được thì trừ khi quyết toán | Phiếu khám |

## 5. Cách ly

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 5.1 | Điểm dừng: trại (hộ tống) | Quét chip, trại ký, **Bàn giao** | `handover` STAY_IN (`to_party_id` = trại), `horse_stay` QUARANTINE | Chip sai, thiếu biên bản | **Trách nhiệm sang trại**; xe rảnh | "Đang cách ly, ngày 1/n" |
| 5.2 | Ngựa đang cách ly (hộ tống) | Mỗi ngày: đo, ghi ăn uống theo chỉ dẫn, ảnh sổ trại | `horse_health_log`, `document` | Không trong thời gian lưu trú | Con chưa ghi hôm nay hiện đỏ; điều phối thấy danh sách thiếu **[mới]** | Chỉ số hôm nay, ảnh |
| 5.3 | Như trên | Chọn Bệnh, Nguy kịch | `horse_health_log`, `incident` | | Mở sự cố (mục 7) | Cảnh báo kèm hướng xử lý |
| 5.4 | Kết quả cách ly (chuyên viên) **[mới]** | **Đạt** | `quarantine_result` PASSED | Chưa đủ ngày, thiếu ngày ghi, thiếu giấy, có sự cố mở | Mở nút nhận ngựa 5.7 | "Đạt cách ly" |
| 5.5 | Như trên | **Không đạt** (hộp xác nhận nêu hậu quả) | `quarantine_result` FAILED | Không ghi lý do | Loại ngựa, nhả khoang, hủy chặng sau, tạo chặng trả về, báo giá mới chờ quản lý. Con khác vẫn đi | Lý do, kế hoạch trả ngựa |
| 5.6 | Như trên | **Kéo dài** | `planned_end_at`, `cost_item` | Ngày mới không sau ngày cũ | Quản lý duyệt phụ phí. Trễ chuyến thì điều phối đổi chuyến cho đơn đó | Ngày mới, phụ phí |
| 5.7 | Điểm dừng: rời trại (hộ tống xe trục) | Quét chip, ký, **Nhận ngựa** | `handover` STAY_OUT (`from_party_id` = trại), `actual_end_at`, `horse_leg` | Kết quả chưa PASSED | **Trách nhiệm sang xe trục** | "Lên chuyến trục" |

Người đo và người kết luận là hai vai trò khác nhau. Phí lưu chuồng, cách ly theo ngày đã gồm ăn uống, nên không bán thêm "cho ăn theo ngày".

## 6. Trục, cửa khẩu, giao, đóng đơn

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 6.1 | Lượt xe trục (hộ tống) | Nhận ngựa từ hub | `handover` HUB_OUT, `horse_leg` | Chip sai, chưa xong dịch vụ trước chuyến | Danh sách ngựa của mọi khách kèm chỉ dẫn từng con | "Đang đi trục" |
| 6.2 | Điểm dừng: trạm nghỉ (tài xế, hộ tống) | **Tới nơi**, đo từng con, **Rời đi** | `trip_stop`, `horse_health_log` | Còn con chưa đo ở điểm kiểm tra **[mới]** | Điều phối thấy giờ trễ | Trạm, chỉ số từng con |
| 6.3 | Điểm dừng: cửa khẩu (tài xế) | Tải giấy, **Bàn giao đại lý** | `document`, `handover` AGENT | Thiếu giấy; không có đối tác ở ít nhất một bên | **Trách nhiệm sang đại lý**; chặng đại lý bắt đầu | "Đã qua cửa khẩu" |
| 6.4 | Chặng đại lý (điều phối) | Nhập mốc và ảnh đại lý gửi | `horse_leg`, `document` | Mốc lùi thời gian | | Mốc, ghi rõ "theo đại lý" |
| 6.5 | Điểm dừng: giao (tài xế) | **Bắt đầu chờ**, quét chip, ký, trả giấy gốc, **Hoàn tất** | `handover` DELIVERY, `handover_document`, `booking_horse` DELIVERED | Chip sai, thiếu chữ ký | Con cuối cùng xong: `DELIVERED` | "Đã giao", biên bản |
| 6.6 | Như trên | **Không gặp người nhận** | `handover` TIMEOUT, `incident`, `horse_stay` STABLING | Chưa hết giờ chờ miễn phí | Điều phối chọn gửi chuồng hoặc về hub; phí chờ tính cho người nhận | Ngựa ở đâu, phí |
| 6.7 | Quyết toán (quản lý) | Duyệt từng chi phí, lập khoản thu thêm | `cost_item`, `payment` EXTRA_CHARGE | Khoản của khách chưa hỏi ý kiến, vượt hạn mức khẩn cấp | Hết nợ: `COMPLETED` | Bảng kê, nút thanh toán |
| 6.8 | Đánh giá (khách) | Chấm điểm | `booking_rating` | Đơn chưa hoàn tất, đã đánh giá | | |

## 7. Ngoại lệ

| # | Màn hình (vai trò) | Thao tác | Ghi | Chặn khi | Tác động | Khách thấy |
|---|---|---|---|---|---|---|
| 7.1 | Báo sự cố (tài xế, hộ tống) | Chọn cả xe hoặc từng con, ảnh, vị trí | `incident`, `document` | | Điều phối nhận ngay | Con nào bị ảnh hưởng |
| 7.2 | Phương án (điều phối) | Chọn cách xử lý, đối tác, chi phí dự kiến | `incident_plan` | Đối tác không có vai trò phù hợp | Quản lý chờ duyệt | |
| 7.3 | Duyệt phương án (quản lý) | **Duyệt**, **Từ chối** kèm lý do | `incident_plan` | Từ chối không lý do; chưa hỏi khách và vượt hạn mức | Tổ xe thấy chỉ đạo | Phương án và chi phí cần đồng ý |
| 7.4 | Sự cố (hộ tống, thú y) | **Ngựa đủ sức khỏe đi tiếp** | `incident` `fit_confirmed_*` | Phương án chưa xong | Mở lại thao tác chặng | "Tiếp tục hành trình" |
| 7.5 | Chuyến (quản lý) | **Hủy chuyến** (hộp xác nhận nêu số đơn) | `departure`, `booking` | Chuyến đã chạy | Mọi đơn sang `SCHEDULE_CHANGE_REQUIRED` | Chọn chuyến khác hoặc hủy không mất phí |
| 7.6 | Đơn của tôi (khách) | **Hủy đơn** (xem trước tiền hoàn) | `booking`, `payment` REFUND | Đã nhận ngựa thì không hoàn tiền | Nhả khoang, báo điều phối | Số tiền hoàn |

## 8. Đã chốt (23/11/2026)

| # | Vấn đề | Quyết định |
|---|---|---|
| 1 | Trạng thái của ngựa trượt cách ly | `EXCLUDED` ngay khi trượt, `RETURNED` khi đã trả ngựa xong |
| 2 | Ai kết luận cách ly | Chuyên viên. Hộ tống chỉ đo và ghi |
| 3 | Ai phụ trách ngựa ở hub | Người ở `handover.handled_by` của HUB_IN tới HUB_OUT |
| 4 | Hub vệ tinh không có nhân viên | Tổ xe trục ở lại tới khi giao; qua đêm thì gửi đối tác `STABLE` |
| 5 | Ghi ăn uống | Ghi vào `horse_health_log.note`, chưa thêm cột |
| 6 | Dịch vụ cho ăn tính tiền | Ngừng `FEEDING` (chuyển `INACTIVE` bằng migration mới, làm cùng A3); giữ `VET_CHECK`. Chăm sóc chuẩn nằm trong cước và phí lưu chuồng |
| 7 | Chặng đại lý: đại lý không có tài khoản | Điều phối nhập hộ, khách thấy "theo đại lý" |

## 9. Ai làm phần nào

| Mục | Task |
|---|---|
| 3.1–3.4 | A5 |
| 3.5–3.6 | A6 (hồ sơ, bảo hiểm); lưu trú dự kiến phối hợp B3 |
| 3.7–3.8 | A7 |
| 3.9–3.10 | B1 |
| 4.1–4.5, 6.1–6.5 | B1, B2 |
| 4.6, 5.x | B3; loại ngựa gọi `BookingHorseService.exclude` của A5 |
| 4.7 | A6 và B1, cần thống nhất |
| 6.6, 7.1–7.4 | B4, B5 |
| 6.7–6.8, 7.6 | A8 |
| 7.5 | A1 (đã có backend) |
