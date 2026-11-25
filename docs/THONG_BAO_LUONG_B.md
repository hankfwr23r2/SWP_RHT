# Thông báo của luồng A cho luồng B

> Agent luồng B đọc tài liệu này sau `AGENTS.md`, trước khi bắt đầu task. Quy trình duyệt theo mục 1.1 của `AGENTS.md`.
> Cập nhật 24/11/2026. Backend A6b, A7 và A8 đã merge qua PR #394–#397. PR #398 đã nối lập số dư, kiểm thanh toán và thao tác không nhận ngựa tại điểm đón trên `origin/redesign/system-v2`.

> **25/11/2026 · 30 điểm hở giao cho bạn B:** xem [GIAO_VIEC_BAN_B.md](GIAO_VIEC_BAN_B.md), gồm gợi ý, file liên quan, vùng luồng A đang sửa và số migration dành cho B.

## Việc luồng B còn phải làm

Đọc [Nghiệp vụ](NGHIEP_VU_THEO_59_BANG.md), [Màn hình và tác động](MAN_HINH_VA_TAC_DONG.md) và các quy tắc #26, #28–#38 trong [Đặc tả DB](DB_SCHEMA_59_BANG.md).

- **B1 · Điều độ.** Chỉ lập lượt `LINEHAUL` khi `departure.run_confirmed_at` khác NULL. Chuyến còn đơn `IN_OPERATION` không được hủy, xử lý qua sự cố. Khi đơn rời chuyến do báo giấy chậm, hủy chuyến hoặc hủy đơn, B hủy các `horse_leg` còn `PLANNED` của đơn.
- **B1 · Xếp chuyến.** Tính sức chứa xe theo `standard_stall_count + 2 × wide_stall_count`. Ngựa cùng đơn đi chung lượt `LINEHAUL`. Chỉ cảnh báo khi ngựa đực đứng cạnh ngựa cái. Hệ thống gợi ý nhóm đón, giao nếu đủ chỗ, vòng ≤ 1,5 lần và ≤ 4 giờ. Điều phối duyệt. Ngựa tới hub trước giờ chạy 3 giờ, nếu trễ thì chờ hoặc dời chuyến.
- **B1 · Tính giờ lượt xe (24/11/2026, luồng A làm theo yêu cầu người dùng).** `TripScheduleService`: `POST /dispatch/trip-plans` (xem trước), `POST /vehicle-trips/planned` (lập kèm ngựa một lần). Gợi ý gom tính theo ngày khách chọn, khung giờ 06:00–18:00, giới hạn 5 giờ mỗi con. `HorseLegService.assign` chặn ngựa tới hub quá `HUB_CHECKIN_HOURS`; phân công tài xế chặn quá giờ lái mỗi ngày. API lập tay cũ giữ nguyên.
- **B1/B4 · Luồng A sửa khi chạy thử một đơn (25/11/2026), B đọc để không sửa trùng.** (1) `GET /handovers/{id}/papers` báo 500 trên PostgreSQL thật vì khóa dòng trong giao dịch chỉ đọc; bỏ `readOnly`. **Không gọi hàm có `@Lock` trong `@Transactional(readOnly = true)`**; test MockMvc nằm trong giao dịch ghi nên không bắt được. (2) Gợi ý gom lượt giao chỉ lấy con đã có chặng trục. (3) `GET /dispatch/vehicles` trả `DispatchVehicleResponseDto` có nơi xe đang ở; `GET /departures` có `runConfirmed`. (4) Chặng đại lý: đúng nước, đơn nhập đúng cửa khẩu (DB_SCHEMA #41). (5) `GET /custody-board` có `agentLegId`. (6) Dữ liệu mẫu `R__dev_seed_base.sql`: **không** xóa `route_stop` rồi nạp lại. (7) Chữ trang điều độ chuyển sang `staffDispatch.ts` (tải khi mở trang); chữ loại lượt, trạng thái lượt, vai trò dùng chung ở `tripWords`. (8) Chế độ chạy thử: `docs/CHAY_THU.md`.
- **B1 · Vị trí khoang (người dùng chốt 24/11/2026).** Thêm `horse_leg.stall_position` (DB_SCHEMA 9.4, quy tắc #39, #40; migration do B). Thuật toán xếp là hàm thuần trong `policy/` kèm test, hàm tự xếp gọi khi gán ngựa vào xe. Ba API: `GET /vehicle-trips/{id}/stall-plan` (điều phối, tài xế, hộ tống), `POST /vehicle-trips/{id}/stall-plan/suggest` (chỉ trả về, không lưu), `POST /vehicle-trips/{id}/stall-plan/apply` (lưu cả xe, khóa `vehicle_trip`, 409 khi sơ đồ đã đổi). Màn 3.9a, hiện số khoang ở 4.2 và 6.1. Cách ưu tiên ở NGHIEP_VU GĐ5.
  - Trả lời ba điểm B hỏi (người dùng chốt 24/11/2026): (1) **vách di động**, khoang rộng ghép hai vị trí liền nhau bất kỳ, hai cột số khoang chỉ để tính tổng chỗ; hàm xếp nhận danh sách đoạn của xe (đợt này một đoạn, ví dụ `[9]`) và số chỗ mỗi con cần. (2) Phát hiện sơ đồ đã đổi bằng sơ đồ đang nhìn gửi kèm request, không thêm cột phiên bản. (3) Đặc tả đã merge (#406); migration mới lấy số lớn hơn số mới nhất trên nhánh, luồng A không có migration đang chờ.
  - **Khóa sơ đồ (người dùng chốt 24/11/2026, thay quy tắc #40 cũ).** Chốt trước khi lượt xe bắt đầu: còn con chưa có vị trí thì không cho bắt đầu. Đã bắt đầu thì khóa cả sơ đồ, kể cả con đón ở điểm sau; con không lên được thì để trống vị trí. Bỏ nhánh "chỉ xếp các con lên ở điểm dừng sau".
- **B1/B2 · Xe và tổ xe.** Một xe nối nhiều tuyến. Chuyến chiều về bị hủy vẫn lập lượt về rỗng. Không quản lý giấy tờ xe. Chỉ gán xe `ACTIVE`. A6b đã thêm `VehicleTripRepository.existsByVehicle_IdAndStatusNotIn`. Nhân viên bắt đầu lượt mới ở nơi lượt trước kết thúc. Khi về hub gốc, tài xế báo xe bình thường hoặc cần bảo dưỡng.
- **B1/B2 · Đại lý.** Khi gán đại lý cho chặng, kiểm `party_role` còn hiệu lực vào ngày chạy (#26) và kiểm lại lúc điều độ. Hiện `PUT /parties/{id}/roles/{roleType}` cho rút ngắn hiệu lực dù chặng đã dùng đối tác. Trao đổi với A nếu cần chặn ở API. `handover` loại `AGENT` phải có ít nhất một bên là đối tác (#28). Với đơn nhập, nhận tại cửa khẩu qua `handover` `AGENT`, đặt `from_party_id` là đại lý và kiểm `IMPORT_TRANSPORT_CERT` trước khi nhận. Điều phối nhập hộ các mốc của đại lý.
- **B2 · Tự đưa hoặc nhận tại hub.** Dùng `handover` `PICKUP` hoặc `DELIVERY` gắn `horse_stay`. Kiểm giấy khi nhận ở hub. Đầu `SELF` không tạo chặng xe tương ứng.
- **B2/B3 · Đón hụt.** Ghi qua `HandoverOutcomeService`. Trạng thái dòng ngựa giữ ACTIVE, chỗ chuyến và báo giá giữ nguyên khi ghi. Chỉ quyết định bỏ con mới gọi `BookingHorseService.exclude` và áp dụng xử lý thương mại.
- **B2 · Lập khoản số dư.** Khi ghi giao nhận `DONE` tại điểm đón, tại hub với đầu `SELF` hoặc tại cửa khẩu với đơn `IMPORT`, gọi `PaymentService.requestBalance(bookingId, handedAt)`. Nếu giao nhận liên quan nhiều đơn, khóa booking theo ID tăng dần trước khi ghi. Hàm lập khoản số dư ban đầu hoặc bổ sung theo phần còn thiếu, tính hạn từ `handedAt` và gửi thông báo cho khách.
- **B2 · Rời nơi nhận.** Xe vẫn được đi đón khi còn số dư. Trước khi rời nơi nhận, gọi `PaymentReadinessService.isPaidInFull(bookingId)` cho từng đơn có ngựa lên xe. `StopReadinessServiceImpl` đã gọi ở điểm `PICKUP`. B nối các đường nhận ở hub và cửa khẩu, giữ thứ tự khóa booking theo ID tăng dần khi có nhiều đơn. Chuyển đơn sang `IN_OPERATION` khi xe rời nơi nhận.
- **B2 · Quá hạn số dư.** PR #399 đã nối tại điểm đón; còn hub và cửa khẩu. Sau 30 phút từ lúc nhận ngựa, gọi `BookingCancellationService.cancelUnpaidAtPickup(bookingId, actorUserId)` và thực hiện giao nhận `RETURN` trong cùng giao dịch đang giữ khóa booking. A8 trả 409 khi `PaymentCheckoutService.hasPendingBalanceConfirmation(bookingId)` còn đúng. Phiên VNPAY mở trước hạn mà chưa có kết quả chỉ giữ thêm 5 phút từ `due_at`. Có thể hủy ngay khi quá hạn nếu không có phiên chờ đủ điều kiện.
- **B2/B3 · Nghỉ đêm, chăm sóc và bàn giao ca.** Người ký `handover` `DONE` gần nhất phụ trách ngựa. Tại hub, người ở `handover.handled_by` của `HUB_IN` phụ trách tới `HUB_OUT`. Tổ xe trục ở lại hub vệ tinh, qua đêm thì gửi đối tác `STABLE`. Seed dev có 6 tuyến, mỗi tuyến một chuyến mỗi tuần. Cần đối tác `STABLE` tại `REST-TUYHOA` và `REST-NHATRANG` cho hai chiều HCM ⇄ Đà Nẵng. Ghi `water_intake`, `feed_intake` trên `horse_health_log`. Chặn rời trạm khi thiếu ghi nhận hoặc chưa nghỉ đủ 30 phút. Chăm sóc nằm trong cước. Chặn kết thúc ca hoặc lượt xe khi còn ngựa chưa bàn giao. Xem màn hình 4.8–4.11 và quy tắc #37–#38.
- **B3 · Cách ly.** Phạm vi vận hành loại trừ `horse_stay` `QUARANTINE`, `quarantine_result` và lượt `TRANSFER` tới trại.
- **B4 · Sự cố.** Theo quy trình của nhóm. Công ty chịu chi phí `FORCE_MAJEURE`. Khoản khách chịu gắn với một con hoặc một đơn. A8 đọc tổng chi phí phía khách đã duyệt qua `CostItemRepository.sumApprovedForBooking(bookingId, payers)` và kiểm sự cố chưa đóng qua `IncidentRepository.hasIncidentForBookingOtherThan(bookingId, IncidentStatus.CLOSED)`.
- **Giao diện.** Hiện “Hệ thống” khi `booking_status_history.changed_by` là NULL.
- **Chờ B xác nhận.** Có thể xóa `QUARANTINE`, `QUARANTINE_FARM` và cột `quarantine_result` khỏi DB không? A6b đã thêm `water_intake`, `feed_intake`. B xác nhận việc dùng hai cột này và việc bổ sung `SHIFT` vào `handover_type` qua migration do B phụ trách.

## Đợt 1b · Thanh toán tại nơi nhận

- Kiểm số dư dưới khóa đơn khi hoàn tất HUB_OUT. Xe nhập được rời cửa khẩu về hub và đơn giữ CONFIRMED nếu chưa trả đủ. Thao tác RETURN do nợ số dư chỉ dùng tại địa chỉ đón.
- Khoản số dư VNPAY còn mở có thể thanh toán khi đơn đang SCHEDULE_CHANGE_REQUIRED. Sau khi trả ngựa tại nhà, đơn sang trạng thái này và giữ 30 ngày chọn lịch.
- `BalanceHoldScheduler` gọi service nhắc khách, nhả chỗ ở T − 1 giờ và đóng HUB_OUT rỗng. Giữ nguyên các lượt quét và biên bản. Mốc tính phí `horse_stay.balance_hold_at` dùng chung loại STABLING. Theo dõi vẫn hiện ngựa đang giữ sau khi đơn hủy. Điều phối ghi cuộc gọi, gia hạn hoặc xác nhận đưa ngựa về hub ở bảng Theo dõi.
- Phí giữ do chưa thanh toán thuộc CUSTOMER. Phí giao hụt thuộc CONSIGNEE. RETURN tại hub đóng lưu trú ngay. Scheduler chốt phí dưới khóa đơn, ghi riêng phần cấn cọc và dùng nhật ký để chống chốt lặp. Bàn giao ngựa cho người đến nhận thực hiện độc lập với thanh toán.

## Đợt 1a · 25/11/2026

- Luồng A gọi `HorseLegService.cancelPlannedLegsOfBooking` qua `BookingStatusService`. Luồng B bỏ cả lịch hẹn tập kết chưa bắt đầu và báo điều phối trong cùng giao dịch.
- Luồng B gọi `DepartureService.linehaulDeparted/linehaulCompleted` theo mốc lượt trục. Luồng A giữ khóa chuyến và quyết định trạng thái.
- Chặng giao thay thế dùng `StablingChargeService` chốt phí theo ngày. Luồng A lập khoản thu `EXTRA_CHARGE`, đặt hạn và báo khách. HUB_OUT kiểm khoản phí đã chốt. Người nhận tự tới hub vẫn lấy ngựa được.
- Điều phối tải chứng cứ `VET_REPORT` trước khi xác nhận đủ sức thay tổ lái.

## Đợt 1b bước 3 · Đón hụt

- `HandoverOutcomeService` chuyển lần ghi hụt sang `PickupMissService`. Ghi hụt hủy chặng kế hoạch của con hụt và giữ chỗ trục. Quyết định bỏ con mới gọi xử lý thương mại.
- Điều phối lập lượt đón lại và phân công tài xế trước khi B xuất hiện. Lượt đón lại phải chờ khách hoặc điều phối ghi lựa chọn. C dùng lịch hẹn tập kết theo con.
- Kiểm mọi con tại điểm đón/cửa khẩu trước hoàn tất giao nhận và trước lúc rời. Đơn đã nhận một phần tiếp tục đi. Scheduler gọi service xử lý hết hạn dưới khóa đơn.
- Một con hết hạn không nhả chỗ của cả đơn khi con khác vẫn còn lượt đón hoặc phương án B/C đang có hiệu lực. Với B, kiểm lại giờ tới hub khi hoàn tất nhận. Với C, hủy chặng đón đã xếp cho con đó.

## Đợt 1b bước 4 · Báo giá thay thế và số dư bổ sung

Giao nhận lần sau gọi lại `PaymentService.requestBalance` lúc nhận, kể cả đơn IN_OPERATION. Service khóa đơn, đọc báo giá đã chấp nhận và chỉ lập khoản số dư khi có tiền thiếu và chưa có số dư chờ. Trước khi rời điểm đón lại, kiểm trả đủ dưới khóa đơn. Giá theo lượt đón lại gắn với chặng thuộc lượt xe đã xếp. Luồng A xử lý phiên bản báo giá, đối soát số dư và khoản hoàn trong giao dịch của lựa chọn.

Chuyên viên ghi số phí bảo hiểm đã hoàn lũy kế theo chứng từ. Nếu con đã bị loại do khách, luồng A lập báo giá thay thế và đối soát trong giao dịch giữ khóa đơn.

## Trả lời ba việc luồng B chờ · 25/11/2026

1. **Người nhận tự tới hub lấy ngựa khi còn phí giữ.** Người dùng chốt: **không chặn**. Hàm đã có từ đợt 1a: `StablingChargeService.chargeThrough` chốt phí và lập khoản thu (`ExtraChargeService.createStablingCharge`, được khi đơn `IN_OPERATION`) ngay lúc ngựa rời hub; khách trả trong 7 ngày. Chỉ xe công ty **giao lại** mới bị chặn ở `HUB_OUT` khi phí chưa trả. Có test `DeliveryFailureFlowTest.consigneeCanCollectAtTheHubWhileTheRedeliveryChargeIsUnpaid`. B không gọi thêm gì. Cách tính phí sẽ đổi theo mục 13 của PHUONG_AN_6_LUONG, luồng A làm.
2. **Miền Bắc trong dữ liệu mẫu.** Thêm hub vệ tinh Hà Nội phục vụ 17 tỉnh (gồm Thanh Hóa, Nghệ An, Hà Tĩnh; Điện Biên cố ý để ngoài vùng phục vụ để còn tỉnh thử nhánh báo chưa phục vụ), trạm Đồng Hới, Vinh, Thanh Hóa, bốn tuyến `DN-HN`, `HN-DN`, `HCM-HN`, `HN-HCM` mỗi tuyến một chuyến mỗi tuần (thứ Năm, Bảy, Tư, Hai, 05:00), giá mẫu, hai xe 9 chỗ, hai tài xế, một hộ tống, sáu chuồng gửi. Km và giờ chạy tính bằng Valhalla 25/11/2026 cho xe tải 10 tấn, 80 km/h; thỏa 4 giờ liên tục, 10 giờ giữa hai lần nghỉ đêm. Vẫn là **số mẫu**. Xe và nhân viên vẫn thuộc trụ sở nên xe đi Bắc phải lập lượt về. Miền Bắc đi Lào, Campuchia: chưa có tuyến.
3. **Màn quản trị báo lỗi.** `useStaffFormAction` hiện lý do máy chủ trả khi bị từ chối (4xx); lỗi 5xx hoặc mất mạng vẫn báo chung.
