# Thông báo của luồng A cho luồng B

> Agent luồng B đọc file này sau `AGENTS.md`, trước khi bắt đầu task. Mục mới nhất ở trên. Đây là thông tin phối hợp, không thay quy trình duyệt ở mục 1.1 của `AGENTS.md`.
> Cập nhật: 24/11/2026.

## 24/11/2026. A6b PR3: quyết định chạy chuyến và dữ liệu mẫu

| Nội dung | Ảnh hưởng luồng B |
|---|---|
| `departure.run_confirmed_at`, `run_confirmed_by` | Chỉ lập lượt LINEHAUL khi `run_confirmed_at` khác NULL. B thêm kiểm này ở B1 |
| Hủy chuyến | Bị chặn khi còn đơn `IN_OPERATION`. Trường hợp đó xử lý bằng sự cố |
| `booking_status_history.changed_by` | Cho phép NULL = hệ thống. Màn hình hiện "Hệ thống" |
| Seed | 6 tuyến, mỗi tuần một chuyến. HCM-DN và DN-HCM nay có 8 điểm dừng, nghỉ đêm 600 phút ở REST-TUYHOA, REST-NHATRANG: cần đối tác có chuồng (`STABLE`) ở hai điểm này. Thêm GATE-LAOBAO, đại lý Lào, `driver3.dev`, `driver4.dev`, `escort2.dev`, `escort3.dev`. Ngừng QF-CUCHI |
| Ngựa đực và ngựa cái cùng xe | Chỉ cảnh báo khi xếp khoang, không chặn |

## 24/11/2026. Hợp đồng thanh toán và loại ngựa (A7)

- Khi ghi giao nhận DONE ở nơi nhận (đón, SELF tại hub hoặc nhận từ đại lý ở cửa khẩu với IMPORT), gọi `PaymentService.requestBalance(bookingId, handedAt)` cho từng đơn CONFIRMED đã có báo giá ACCEPTED và cọc PAID. Nếu giao nhận liên quan nhiều đơn, khóa booking theo ID tăng dần trước khi ghi. Hàm tạo tối đa một khoản số dư, hạn tính từ `handedAt`, và gửi thông báo khách.
- Trước khi xe rời nơi nhận, gọi `PaymentReadinessService.isPaidInFull(bookingId)` cho từng đơn có ngựa lên xe. `StopReadinessServiceImpl` đã gọi cho điểm PICKUP. Luồng B nối thêm các đường nhận ở hub/cửa khẩu và bảo đảm thứ tự khóa.
- Nếu quá hạn `due_at` của số dư, luồng B gọi `BookingCancellationService.cancelUnpaidAtPickup(bookingId, actorUserId)` trong cùng giao dịch đang giữ khóa booking và thực hiện giao nhận RETURN. A8 trả 409 khi `PaymentCheckoutService.hasPendingBalanceConfirmation(bookingId)` còn đúng. Phiên mở trước hạn chỉ chờ thêm 5 phút sau hạn (quy tắc của hệ thống cũ); không có phiên chờ thì hủy được ngay khi quá hạn.
- Đơn rời chuyến khi báo giấy chậm, hủy chuyến hoặc hủy đơn. Luồng B hủy các `horse_leg` còn PLANNED của đơn đó; luồng A không sửa chặng.
- A8 đọc tổng chi phí phía khách đã duyệt qua `CostItemRepository.sumApprovedForBooking` và kiểm sự cố chưa đóng qua `IncidentRepository.hasIncidentForBookingOtherThan`.
- Khi không nhận một con, gọi `BookingHorseService.exclude(bookingHorseId, reason, actorUserId)` trước khi lập khoản số dư. Luồng A nhả chỗ và thay báo giá có hiệu lực ngay, giữ đơn giá đã chốt. Luồng B hủy các chặng còn lại của con bị loại trong cùng giao dịch.

## 23/11/2026. Quyết định nghiệp vụ đợt hai (người dùng đã chốt)

Đọc lại `NGHIEP_VU_THEO_59_BANG.md`, `MAN_HINH_VA_TAC_DONG.md`, quy tắc #29–#36 của `DB_SCHEMA_59_BANG.md`. **Mục này thay các ý trùng ở mục "Màn hình và tác động" bên dưới.** Code luồng A đổi ở task A6b, hiện chưa làm.

| Nội dung | Ảnh hưởng luồng B | Task |
|---|---|---|
| Không vận hành cách ly | Không làm `horse_stay` QUARANTINE, `quarantine_result`, lượt TRANSFER tới trại. **Cần B xác nhận** được xóa các giá trị `QUARANTINE`, `QUARANTINE_FARM` và cột `quarantine_result` khỏi DB hay không | B3 |
| Khoang rộng 2 chỗ | Sức chứa xe tính theo chỗ: `standard_stall_count + 2 × wide_stall_count` | B1 |
| Ghép đơn | Ngựa cùng đơn cùng lượt LINEHAUL. Hệ thống gợi ý nhóm đón, giao (đủ chỗ, vòng ≤ 1,5 lần, ≤ 4 giờ), điều phối duyệt | B1 |
| Lịch tuần, 6 tuyến | Một xe nối nhiều tuyến. Chuyến chiều về bị hủy vẫn có lượt về rỗng | B1 |
| Xe | Không giấy tờ xe. Không gán xe khác ACTIVE. Xe về hub gốc thì tài xế báo bình thường hoặc cần bảo dưỡng | B1, B2 |
| Nhân viên | Bắt đầu lượt mới ở nơi lượt trước kết thúc | B1 |
| Thanh toán | **Không** chặn xe đi đón vì còn nợ. Nhận ngựa xong thì lập số dư bằng tổng trừ tiền cọc; chưa trả đủ thì xe không rời nơi nhận; quá 30 phút thì trả ngựa tại chỗ. `IN_OPERATION` khi xe rời nơi nhận | B2 |
| Nghỉ, cho ăn | Cột `water_intake`, `feed_intake` của `horse_health_log` (A thêm migration ở A6b, **cần B đồng ý**). Chặn rời trạm khi thiếu ghi hoặc chưa đủ 30 phút | B2, B3 |
| Tự chở | B2 nhận hoặc trả ngựa tại hub bằng `handover` PICKUP hoặc DELIVERY gắn `horse_stay`. Kiểm giấy lúc nhận ở hub. Đầu SELF không tạo chặng xe tương ứng. | B2 |
| Đơn nhập | Đại lý giao ngựa ở cửa khẩu: `handover` AGENT, `from_party_id` = đại lý. Thiếu `IMPORT_TRANSPORT_CERT` thì không nhận | B2 |
| Ngựa tới hub | Trước giờ chạy 3 giờ. Trễ thì chờ hoặc dời chuyến | B1 |
| Sự cố | Theo quy trình nhóm. `FORCE_MAJEURE` do công ty chịu. Không chia chi phí theo đầu ngựa | B4 |
| Theo dõi sức khỏe | Phụ trách theo `handover` DONE mới nhất, không gán tay. Thêm `SHIFT` vào `handover_type` (migration do B, **cần B đồng ý**). Chặn kết thúc ca, lượt xe khi còn ngựa chưa bàn giao. Màn hình 4.8–4.11, quy tắc #37, #38 | B2, B3 |

## 23/11/2026. Màn hình và tác động (người dùng đã chốt)

Đọc `MAN_HINH_VA_TAC_DONG.md` trước B1–B5. Các điểm ảnh hưởng luồng B:

- Ngựa bị loại lúc nhận: gọi `BookingHorseService.exclude` (`EXCLUDED`); trả xong thì `RETURNED`.
- Ngựa ở hub: người ở `handover.handled_by` của HUB_IN phụ trách tới HUB_OUT. Hub vệ tinh: tổ xe trục ở lại; qua đêm thì gửi đối tác `STABLE`.
- Không có dịch vụ cho ăn tính tiền. Cách ghi ăn uống xem mục trên.
- Mốc của đại lý do điều phối nhập hộ.

Thấy chỗ nào không làm được thì báo lại, không tự đổi.

## 23/11/2026. AuditService và P0.3-B

- Cả A2 (`06c1375`) và `rd/p0-3b-shared-services` đều có `AuditServiceImpl`, `AuditServiceTest`. Nhánh P0.3-B (`7b57809`) đã gộp A2; kiểm bằng `git merge-tree` thì merge vào `redesign/system-v2` không xung đột.
- Bản của luồng B thay bản của A. **Giữ nguyên chữ ký** `record(actorUserId, action, targetTable, targetId, oldData, newData)`, vì `SystemConfigServiceImpl` đang dùng.
- Bản của B từ chối `targetId` NULL và tên dài quá 50 ký tự. Cần test A2 (`SystemConfigServiceTest`, `SystemConfigFlowTest`) vẫn xanh sau khi gộp. Luồng A **chưa chạy** các test này trên nhánh của B.
- `ArchitectureTest` của P0.3-B sẽ kiểm cả code luồng A. Nếu đỏ vì code A thì báo luồng A, không nới quy tắc và không tự sửa code của A.

## 23/11/2026. Giới hạn tham số kỹ thuật (nhánh `fix/system-config-limits`, chưa merge)

- `system_config` thêm `min_value`, `max_value` (migration `V202611231600`). Luồng B không dùng số migration này.
- API `/system-configs` trả thêm `minValue`, `maxValue`. Luồng B đọc tham số qua `SystemConfigService.getInt` như cũ.
- Merge sau thì phải sinh lại `frontend/src/shared/api/generated/` và `frontend/openapi/openapi.json`, không sửa tay phần xung đột.

## 23/11/2026. Tài khoản và đồng bộ tên (đã merge)

- `booking.customer_id`, `horse.owner_customer_id` trỏ tới `customer`. Không còn `user_role`, `party_address`. `party` chỉ chứa đối tác.
- `TestData.customer()` trả về `Customer`.
- `handover.from_party_id`, `to_party_id` chỉ là đối tác. NULL phải xét cùng `handover_type` (`DB_SCHEMA_59_BANG.md` mục 10.1).
- Bảng và cột dùng tên mới theo `BANG_DOI_TEN.md`.

## Việc luồng B cần xử lý khi tới task

| Task | Việc |
|---|---|
| B1 | Quy tắc #26: gán đại lý cho chặng phải kiểm `party_role` còn hiệu lực vào ngày chạy. Hiện `PUT /parties/{id}/roles/{roleType}` cho rút ngắn hiệu lực cả khi đã có chặng dùng đối tác đó: kiểm lại lúc điều độ, và bàn với luồng A nếu cần chặn ở API |
| B2 | Quy tắc #28: `handover` loại `AGENT` phải có ít nhất một bên là đối tác |

- A6b PR1 thêm hàm đọc `VehicleTripRepository.existsByVehicle_IdAndStatusNotIn`; migration V202611231900 bỏ `country_rule.quarantine_days`, `vehicle.registration_expiry_date`, thêm `location.counterpart_country_code`, `booking.pickup_method`, `delivery_method` và cập nhật danh mục giấy, dịch vụ, tham số.
