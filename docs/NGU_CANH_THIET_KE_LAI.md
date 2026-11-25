# Ngữ cảnh đợt thiết kế lại nghiệp vụ, mô hình và DB

> Ghi lại để các phiên làm việc sau (người hoặc agent) nắm được **vì sao** thiết kế như hiện tại.
> Ngày ghi: 22/11/2026. Nhánh lúc ghi: `feat/insurance-resubmission`. Tất cả tài liệu ở đây **chưa commit**.

## 1. Bối cảnh

- Nhóm **bảo vệ không đạt**. Giảng viên đọc từ góc nhìn DB và cho rằng thiết kế sai. Nhóm vẽ ERD từ entity JPA chứ không thiết kế DB trước.
- Góp ý của thầy: **"học lại kiến thức nền"**, và nghiệp vụ còn nhiều chỗ chưa sát thực tế.
- Câu hỏi nhóm không trả lời được: *DB thể hiện việc tính tiền theo km hay theo điểm dừng như thế nào?* Câu trả lời thật là DB **không thể hiện**. Giá được tính ở frontend rồi lưu thành JSON.

## 2. Chẩn đoán gốc rễ

| Vấn đề | Biểu hiện trong code cũ |
|---|---|
| Dữ liệu nghiệp vụ nằm trong JSON | `DetailsJson`, `quote_lines_json`: báo giá, bảo hiểm, giấy tờ đều nằm trong cột text |
| Logic giá ở frontend | `frontend/src/shared/lib/pricing.ts`, `booking.ts`, `config/booking-rules.ts`. Backend nhận dòng báo giá từ client (`BookingServiceImpl.sendQuote`), và `QuoteLinePolicy` chỉ kiểm 2 dòng. Đây là lỗi CWE-602 |
| Bảng giá dạng key–value | `system_config` chứa `TRIP_OPEN_FEE`, `KM_TIER_*`, `CLASS_FACTOR_*`, `MARGIN_RATE`… |
| Không quản lý phiên bản schema | Không có Flyway. ERD cũ lệch với DB, file SQL nguồn không còn |
| API kiểu hành động và switch | `POST /field/bookings/{id}/trip-update` xử lý khoảng 21 action trong một switch (`TripProgressServiceImpl`) |
| Trạng thái đơn bùng nổ | `booking.status` có khoảng 27 giá trị, trộn trạng thái thương mại, vận hành và các nhánh duyệt |
| Suy luận từ chuỗi | `Vehicle` lấy sức chứa bằng cách đọc chuỗi `vehicle_type` |
| Luồng trùng nhau | `DeliveryServiceImpl.completeHandover` và action `COMPLETE_DELIVERY` cùng làm một việc |

Kết luận: **cơ chế giao tiếp** (REST/JSON + JWT + refresh cookie) không sai. Phần yếu là **thiết kế API**: nên lấy OpenAPI làm hợp đồng giữa hai đầu và sinh code client từ đó. Swagger không chỉ dùng để test.

## 3. Mô hình nghiệp vụ mới (người dùng đề xuất, đã thống nhất)

1. **Gom hàng qua hub:** chặng đón → ngựa tập kết ở hub → **chuyến trục chính chạy định kỳ** → chặng giao. Không có khâu cách ly do công ty vận hành (quyết định 23/11/2026).
2. **Mạng lưới hub và tuyến cố định.** Trên tuyến vẫn giữ quy trình chăm sóc hiện tại: trạm nghỉ, kiểm tra sức khỏe, cửa khẩu.
   - Trục chính có cả **tuyến nội địa** (hub ⇄ hub, ví dụ HCM ⇄ Đà Nẵng) và tuyến quốc tế (hub → cửa khẩu).
   - **Trụ sở và hub vệ tinh:** mọi xe và nhân viên thuộc trụ sở. Hub vệ tinh có vùng phục vụ và chỗ tập kết, không có xe hay nhân viên trực thuộc. Xe trục tới hub vệ tinh thì chạy luôn lượt giao ở đó, rồi về trụ sở bằng chuyến trục chiều ngược.
   - Cách chạy quyết định theo hub, không theo loại đơn: hub đón trùng hub giao thì chạy thẳng (`DIRECT`), khác hub thì đi trục. Mỗi đơn có tối đa một chặng trục.
3. **Tách lịch thương mại khỏi lượt chạy thật:**
   - `departure_schedule` → `departure`: khách đặt chỗ vào chuyến theo ngày;
   - `vehicle_trip`: xe thật chạy, có thể nhiều xe cho một chuyến.
4. **Theo dõi theo từng con ngựa:**
   - `horse_leg`: chặng di chuyển;
   - `horse_stay`: thời gian lưu ở một nơi không trên xe (tập kết, qua đêm, gửi chuồng);
   - `handover`: chuỗi giao nhận, thể hiện ai đang giữ ngựa.
5. **Giao hàng đối xứng với đón:**
   - thả ngựa khỏi nơi lưu trú, giao nhận theo từng điểm dừng, quét chip khi giao;
   - quá giờ chờ thì gửi chuồng gần đó hoặc đưa về hub giao của đơn (có thể là hub vệ tinh);
   - giao lại bằng một `horse_leg` mới có `replaced_horse_leg_id`;
   - đơn coi là đã giao khi mọi chặng giao đều COMPLETED.
6. **Party chứa đối tác** (khách ở `customer`, người gửi và người nhận ở `booking_party`): đại lý, trại cách ly, thú y, cứu hộ, bảo hiểm. Vai trò của từng bên nằm ở `party_role`. Vai trò trong một đơn cụ thể nằm ở `booking_party`.
7. Thao tác **nhận ngựa** giữ nguyên như hiện tại. Luồng **bàn giao** đổi theo chuỗi giao nhận ở mục 5.

## 4. Các quyết định đã chốt và lý do

| Quyết định | Lý do |
|---|---|
| `location` chỉ chứa điểm của mạng lưới (hub, trại, trạm nghỉ, cửa khẩu, chuồng, trường đua). Địa chỉ đón và giao của khách nằm ở `booking_address` (snapshot) | Hai loại có vòng đời khác nhau. `trip_stop` và `horse_leg` dùng exclusive arc: trỏ tới location hoặc address, chỉ một trong hai |
| **Giữ 3 bảng giá** `billable_service`, `price_list`, `price_item` | Người dùng chốt không gộp. Bảng giá có thời hạn hiệu lực, đơn giá áp theo tuyến, hạng xe, loại khoang, bậc km |
| Báo giá lưu snapshot đơn giá; `amount` là cột generated; tổng, thu hộ, cọc lưu cột trên `quotation` | Bảng giá đổi về sau không làm thay đổi báo giá cũ. Báo giá bất biến sau khi SENT nên lưu tổng không có rủi ro lệch |
| `horse_leg` dùng cho từng con ngựa, gộp ý tưởng shipment_leg và leg_horse. Không có bảng segment | Đoạn đường suy ra được từ các điểm dừng liên tiếp |
| `trip_stop` vừa là lộ trình kế hoạch vừa ghi giờ thực tế | Chép từ `route_stop`, giờ thực tế điền dần |
| Giữ `horse_stay` dù đã có `trip_stop` | `trip_stop` gắn với xe và chỉ dài vài phút đến vài giờ. Tập kết và gửi chuồng kéo dài hơn và không có xe |
| Bỏ `terms_version`, `insurance_revision`, `incident_party`, `booking_review`. Gộp `document_link` vào `document` | Đều thừa hoặc trùng thông tin, xem mục "Thay đổi" trong `DB_SCHEMA_59_BANG.md` |
| Bỏ vai trò `PICKUP_CONTACT` khỏi `booking_party` | Người liên hệ tại chỗ nằm ở `booking_address` |
| `booking.status` còn 12 mốc thương mại | Trạng thái vận hành suy ra từ chặng, sự cố và giao nhận |
| Giữ `booking_rating` | Tính năng đánh giá đã có (`CustomerRatingController`). Quan hệ 1–0..1 nên tách bảng |
| `country_rule`: bỏ REIMPORT, tính ngày lịch thay ngày làm việc, thêm EXCLUDE chống trùng thời hạn | Bỏ REIMPORT để đồng bộ với `booking_type`. Dùng ngày lịch để khỏi cần bảng ngày lễ |
| `document_requirement`: bỏ `status` và `per_horse` | Trùng với thời hạn hiệu lực, hoặc suy ra được từ `document_type.owner_type` |
| Bỏ `handover.signed_document_id` và `cost_item.invoice_document_id` | Trùng với cột chủ sở hữu trong `document`, và tạo vòng khóa ngoại |
| Giữ `departure_schedule_day`, nên tổng là **59 bảng** | Một lịch chạy nhiều ngày trong tuần. Nếu quy ước mỗi lịch chỉ chạy một ngày thì bỏ được, còn 58 |
| Tuyến trục được xuất phát từ cửa khẩu: `route.origin_hub_id` đổi thành `origin_location_id` (23/11/2026) | Người dùng chốt. Đơn IMPORT đi tuyến định kỳ từ cửa khẩu về hub, đối xứng với chiều xuất. Trước đó tài liệu nghiệp vụ ghi IMPORT có departure nhưng lược đồ chỉ cho tuyến xuất phát từ hub |
| Có trục nội địa, dùng mô hình trụ sở + hub vệ tinh (22/11/2026) | Gom hàng chỉ có ý nghĩa khi đơn liên tỉnh cũng đi trục. Hub vệ tinh giúp giữ toàn bộ xe và nhân viên ở trụ sở, nên không phải phân quyền theo hub |
| Thêm `DIRECT` vào `vehicle_trip.trip_type`, `horse_leg.leg_type`, `billable_service.layer`, `quotation_line.line_type`. Thêm `UNIQUE (province_code)` vào `hub_service_area` | Đơn cùng hub cần chặng chạy thẳng và cước riêng. Mỗi tỉnh phải tra ra đúng một hub |
| Lượt chạy mới của xe bắt đầu ở nơi lượt trước kết thúc. Ngựa chỉ lên xe trục ở đầu tuyến và xuống ở cuối tuyến | Biết xe đang ở đâu mà không cần thêm cột. Giữ `departure.booked_stall_count` đếm theo cả chuyến |
| Bỏ qua quy tắc niêm phong xe trong đợt này. Giữ `sealed_at`, `disinfected_at` làm cột ghi nhận | Người dùng chốt: project môn học chưa cần. Việc bốc dỡ lại ở hub có thể mâu thuẫn với niêm phong theo TT 01/2026, nhưng chưa kiểm chứng |
| Bỏ mọi view, logic nằm hoàn toàn trong code (22/11/2026). Lưu cột dẫn xuất cho tiền báo giá và `departure.booked_stall_count`; đã trả, còn nợ, vị trí ngựa, đủ giấy tính khi đọc | Người dùng chốt: view chứa logic thì khó test và trùng với policy. Chỉ lưu cột khi giá trị đã chốt hoặc có khóa bảo vệ. `CHECK (booked_stall_count <= stall_capacity)` giúp DB chặn bán quá chỗ |
| Tiền cọc gồm toàn bộ phần thu hộ (bảo hiểm, lệ phí): `cọc = tỷ lệ × (tổng − thu hộ) + thu hộ` | Phí bảo hiểm phải nộp đủ cho hãng trước khi hợp đồng có hiệu lực. Tỷ lệ cọc chỉ áp lên cước dịch vụ |
| Tiền cọc làm tròn **lên nghìn đồng**, không vượt tổng; tổng không đổi (23/11/2026) | Người dùng chốt. Sai số phần trăm dồn vào cọc, số dư = tổng − cọc |
| Tài khoản và hồ sơ theo cách cũ: `app_user` (một vai trò) 1–0..1 `customer`/`staff`. `party` chỉ chứa đối tác (23/11/2026, **thay** quyết định gộp khách vào `party`) | Người dùng chốt. Vai trò khách chỉ lưu một nơi, FK chặn gán sai loại. Đã thực hiện ở `V202611231200`. Giới hạn đã chấp nhận: nhân viên muốn đặt đơn thì dùng tài khoản khách khác; mỗi tổ chức khách một tài khoản; chủ ngựa phải có tài khoản khách; tổ chức vừa là khách vừa là đối tác có hai hồ sơ |
| Thiết kế tới mức màn hình và tác động, xem `MAN_HINH_VA_TAC_DONG.md`. Trách nhiệm chăm ngựa chuyển theo `handover` của từng con, hộ tống theo lượt xe (23/11/2026) | Đơn được ghép nên không gắn hộ tống với đơn. Mỗi thao tác phải có màn hình, bằng chứng và tác động |
| Ngừng dịch vụ cho ăn tính tiền, giữ `VET_CHECK`; chăm sóc chuẩn nằm trong cước (23/11/2026) | Phí lưu chuồng đã gồm ăn uống; chỉ dẫn ăn uống là yêu cầu chăm sóc, không phải hàng bán |
| Giữ tên `location` (23/11/2026) | Người dùng chốt. Chỉ chứa điểm cố định của mạng lưới |
| Giữ vai trò `ESCORT` (nhân viên hộ tống), không đổi thành groom | Người dùng chốt. Tên đã dùng thống nhất trong lược đồ, thư mục `backoffice/features/escort` và code cũ |
| Tên trong code (bảng, cột, enum, class, biến, endpoint) dùng tiếng Anh theo chuẩn đặt tên. Chữ hiển thị cho người dùng đi qua i18n (`vi`, `en`) | Người dùng chốt. Tài liệu viết tiếng Việt nhưng mọi định danh phải là tiếng Anh |
| `CAN_CU_PHAP_LY_VA_IRT.md` là tài liệu tham khảo | Yêu cầu chi tiết lấy từ `DB_SCHEMA_59_BANG.md` và `NGHIEP_VU_THEO_59_BANG.md` |
| Backend theo n-layer: Controller → Service → Repository, cộng Policy và Client | DTO có `from()`, `toEntity()`, `applyTo()`. Không dùng ModelMapper. Chi tiết: `AGENTS.md` |
| Công ty không bán chỗ cách ly, chỉ chở ngựa đã đủ điều kiện; vẫn trọn gói vận chuyển và thủ tục (23/11/2026) | Người dùng chốt. Điều kiện thể hiện bằng giấy `QUARANTINE_CLEARANCE` nên bỏ `quarantine_days`. IRT **có** làm khâu cách ly, nên không nói điểm này giống IRT |
| Đơn nhập làm ngược chiều đơn xuất: đại lý nước ngoài liên hệ cửa khẩu Việt Nam, giao ngựa cho công ty khi có giấy vận chuyển mẫu 14a (23/11/2026) | Thông tư 01/2026 Điều 11. Giả định: địa chỉ giao là nơi chủ ngựa cách ly. Ghé hub là đơn giản hóa, vì luật yêu cầu niêm phong xe về nơi cách ly. Chưa kiểm chứng Thông tư 22/2026 và việc đại lý nước ngoài đứng tên khai báo |
| Quản lý xe chỉ gồm thuộc tính và trạng thái, không có giấy tờ xe (23/11/2026) | Người dùng chốt giữ cách cũ |
| Dịch vụ theo IRT: chăm sóc nằm trong cước, khách chọn khoang và bảo hiểm, xét nghiệm do công ty lo (23/11/2026) | IRT không bán gói chăm sóc. Dịch vụ khách mua không làm đổi việc của hộ tống, nên ghép nhiều khách vẫn đơn giản |
| Ghép đơn: trục ghép theo `departure`; đón và giao hệ thống gợi ý, điều phối duyệt; giá từng khách không đổi; khoang rộng tính 2 chỗ; ngựa cùng đơn đi cùng xe trục (23/11/2026) | IRT Đức §7.4 giữ giá khi gom chung. Giấy chung của đơn chỉ có một bản gốc. Bản cũ tính khoang rộng 2 ngăn |
| Vị trí khoang do **hệ thống tự xếp, điều phối chỉnh**; khách chỉ chọn loại khoang. Thêm `horse_leg.stall_position`, không thêm bảng (24/11/2026) | Lúc đặt chuyến chưa gán xe nên chưa có sơ đồ. IRT lập "load plan" ([Horse & Hound](https://www.horseandhound.co.uk/?p=645450)); ngựa đực phía trước, cái phía sau ([VRC](https://cms.vrc.com.au/latest-news/precious-cargo)). Các nguồn chủ yếu nói về máy bay. Không bắt buộc theo giới tính, chỉ cảnh báo |
| Xe dùng **vách di động**: hai vị trí liền nhau bất kỳ ghép thành khoang rộng; `standard_stall_count`, `wide_stall_count` chỉ để tính tổng chỗ (24/11/2026) | Khách đặt theo số chỗ trước khi gán xe, nên khoang rộng cố định sẽ bán được rồi không xếp nổi. Xe chở ngựa STX 4–9 con dùng vách điều chỉnh được ([STX](https://stephexhorsetrucks.com/vehicles/stx-scania-p-420-6-horses/)); xe van Mỹ chia theo hàng ([NHCA](https://ranunculus-cod-ej7y.squarespace.com/stall-sizes)). Chưa kiểm chứng: xe ở Việt Nam, cách ghép đúng hai khoang. Mở rộng: thuật toán nhận danh sách đoạn và số chỗ mỗi con cần, đợt này một đoạn mỗi xe; mua xe chia hàng thì thêm bảng mô tả đoạn và sửa cách bán chỗ |
| Mỗi tuyến chạy **một chuyến mỗi tuần**, 6 tuyến một chiều: HCM ⇄ Mộc Bài, HCM ⇄ Đà Nẵng, Đà Nẵng ⇄ Lao Bảo. Quyết định chạy 48 giờ trước chuyến, ngưỡng cảnh báo 30% chỗ đã cọc (23/11/2026) | Người dùng chốt. IRT chỉ chạy chuyến khi đủ ngựa để có lãi; con số 30% và 48 giờ tự thiết kế. Vẫn giữ một chặng trục mỗi đơn, nên Đà Nẵng → Campuchia và TP.HCM → Lào phải đi chuyến đột xuất |
| Mỗi đầu trong nước của đơn đi trục có thể chọn công ty chở hoặc tự đưa, nhận tại hub (23/11/2026) | [IRT](https://www.irt.com/) cho biết có thể thu xếp để chủ ngựa tự đưa tới trong một số trường hợp. Dự án giới hạn công ty chở 5 giờ mỗi chiều để xe có thể đi và về trong 10 giờ lái mỗi ngày |
| Thanh toán theo bản `main`: cọc 30% trong 24 giờ; **70% sau khi nhận ngựa, trước khi xe rời nơi nhận**, chờ tối đa 30 phút (23/11/2026) | Người dùng chốt, biết rủi ro khách trả chậm làm các khách khác trên xe phải chờ. IRT thì trả đủ trước khi chở. `IN_OPERATION` bắt đầu khi xe rời nơi nhận, nên đơn không trả đủ vẫn chuyển được `CONFIRMED → CANCELLED` |
| Nghỉ và cho ăn: tối đa 4 giờ một lần nghỉ ≥ 30 phút có nước và cỏ, bữa theo chỉ dẫn ở nơi nghỉ đêm và hub; thêm `water_intake`, `feed_intake` (23/11/2026, **thay** quyết định ghi ăn uống vào `note`) | EFSA: cho ăn uống cách nhau tối đa 4 giờ, mỗi lần 30 phút. USHJA: uống mỗi 4 giờ, nhiều cỏ, không ngũ cốc, không quá 12 giờ mỗi lần đi. Giờ lái và nghỉ đêm theo bản cũ |
| Sự cố theo quy trình của nhóm; bất khả kháng do **công ty** chịu; không chia chi phí theo đầu ngựa (23/11/2026, **thay** quy định khách chịu bất khả kháng) | Người dùng chốt theo luồng sự cố của nhóm. IRT thì khách chịu. Khoản khách chịu luôn gắn với một con hoặc một đơn nên không phải chia |
| Đã cọc mà giấy không kịp: xử lý theo nguyên nhân khách, công ty hoặc cơ quan (23–24/11/2026) | Bản cũ mục 8 yêu cầu trao đổi phương án và không mặc nhiên quy lỗi cho khách. IRT Đức §9 loại quyền rút lại đối với hợp đồng có lịch cụ thể. IRT Anh khoản 5 và Mỹ khoản 6 quy chi phí lưu chuồng, thuốc thú y và kiểm tra phát sinh khi chậm ngoài kiểm soát cho bên gửi. Ngưỡng hai lần lỡ chuyến do cơ quan để hoàn cọc vận chuyển, lượt đổi và thời hạn giữ giá 30 ngày do dự án chọn. [IRT Đức §9](https://www.irt.com/media/1165/irt-ger_trading-conditions_english.pdf), [IRT Anh khoản 5](https://irt.com/media/1149/sli-uk-export.pdf), [IRT Mỹ khoản 6](https://www.irt.com/media/1033/irt_lax_trading_conditions.pdf) |
| Khách hủy hoặc hủy theo nguyên nhân khách, cơ quan thì **không hoàn khoản thu hộ**; phí thú y (`VET_CHECK`) xử lý như cước dịch vụ; công ty hủy vẫn hoàn 100% (24/11/2026, **thay** việc quản lý xét từng dòng thu hộ chưa chi) | Người dùng chốt: đơn giản, tiền đã chi hộ cho hãng và cơ quan. Cơ quan chậm đủ số lần vẫn hoàn cọc vận chuyển. Giữ cột `quotation_line.refund_payment_id` và endpoint cũ, endpoint từ chối mọi dòng được chọn |
| Cửa khẩu gắn nước đối diện (`location.counterpart_country_code`) (23/11/2026) | Trước đó đơn Lào vẫn chọn được Mộc Bài |
| Phân công (kế hoạch theo lượt xe) tách khỏi phụ trách (người ký nhận gần nhất của từng con); thêm `SHIFT` (23/11/2026) | Người dùng chốt. Hộ tống đổi liên tục nhưng chuỗi ký nhận không đứt nên luôn biết ai giữ con nào, không cần bảng phân công ngựa. IRT: mục tiêu 1 groom cho 6–9 ngựa, khám 2 lần mỗi ngày khi cách ly |
| Phí giữ ngựa ở hub do khách: mỗi **24 giờ trọn vẹn** 1.000.000 đ, đồng hồ chỉ chạy khi khách làm ngựa ở lại, cộng dồn; thu thêm một lượt giao khi giao lại do khách; người nhận tự tới hub thì không chặn; đơn đóng được khi còn khoản thu thêm (25/11/2026, **thay** lẻ ngày tròn một ngày và "còn nợ 0 mới đóng"). Chi tiết [PHUONG_AN_6_LUONG.md](PHUONG_AN_6_LUONG.md) mục 13. Code chưa sửa | Người dùng chốt. Tách công nợ khỏi trạng thái đơn để đơn không treo; IRT Đức §7.3, IRT Anh 5 |

## 5. Câu hỏi còn mở

- [ ] Bỏ `departure_schedule_day` (thành 58 bảng) hay giữ?
- [ ] Rà lại các giá trị trạng thái mà agent tự đặt cho `vehicle_trip`, `horse_leg`, `payment`, `incident`, `incident_plan`, `document`… để khớp nghiệp vụ.
- [ ] Đánh giá chỉ cho cả đơn, hay cho từng tài xế/chuyến? Nếu cho tài xế/chuyến thì `booking_rating` phải đổi.
- [x] Đã có `DocumentCompletenessPolicy` trong `backend/src/main/java/com/example/racehorse_transport/policy/` và `DocumentCompletenessPolicyTest`.
- [x] ~~Một hub hay nhiều hub~~: đã chốt trụ sở + hub vệ tinh (mục 4). `AGENTS.md` đã ghi "xe về hub gốc".
- [x] Seed dev có 10 tuyến một chiều, gồm hub Hà Nội.
- [ ] Đơn xuất: giấy gốc có bàn giao cho đại lý ở cửa khẩu không? Hiện chỉ nhận từ khách và trả người nhận.
- [ ] Danh sách chuồng gửi đêm có lọc theo nơi nghỉ không (phải thêm địa điểm cho đối tác)?
- [ ] Bổ sung dữ liệu thật về điểm mạng lưới, vùng phục vụ của hub, tuyến, lịch chạy, bảng giá và quy định.

## 6. Bản đồ tài liệu

| File | Nội dung | Trạng thái |
|---|---|---|
| `docs/DB_SCHEMA_59_BANG.md` | **Đặc tả chuẩn để tạo DB**: 59 bảng, ràng buộc, thứ tự tạo, dữ liệu dẫn xuất, quy tắc ở service | Mới nhất |
| `docs/KE_HOACH_TRIEN_KHAI.md` | Kế hoạch triển khai: đợt 0, luồng A (thương mại) và B (vận hành) chạy song song, chủ sở hữu bảng, hợp đồng gọi chéo, quy trình cho agent, bảng tiến độ | Mới nhất |
| `docs/NGHIEP_VU_THEO_59_BANG.md` | Nghiệp vụ chi tiết theo 59 bảng (10 giai đoạn, nhánh phụ, ví dụ, ai ghi bảng nào) và danh sách tận dụng từ project cũ | Mới nhất |
| `AGENTS.md` (thư mục gốc) | File quy tắc duy nhất: cách làm việc, cấu trúc backend n-layer, DB, DTO, API, frontend, kiểm thử. Đã gộp `BACKEND_ARCHITECTURE_RULES.md` và `frontend/docs/AGENT_GUIDE.md` (hai file này đã xóa) | Mới nhất |
| `docs/CAN_CU_PHAP_LY_VA_IRT.md` | Căn cứ pháp lý ba nước, đối chiếu IRT/Peden | Tham khảo |
| `docs/MAN_HINH_VA_TAC_DONG.md` | Màn hình, thao tác, chặn, tác động | Mới nhất |
| `docs/THONG_BAO_LUONG_B.md` | Thông báo của luồng A cho luồng B | Mới nhất |
| `docs/NGHIEP_VU_6_LUONG.md` | Nghiệp vụ kể theo 6 luồng, điểm hở và trạng thái sửa | Mới nhất |
| `docs/PHUONG_AN_6_LUONG.md` | Quy tắc đã chốt 25/11/2026 cho các điểm hở, mục 8–14 chi tiết | Mới nhất |
| `docs/GIAO_VIEC_BAN_B.md`, `docs/KE_HOACH_GIAO_THANH_VIEN.md`, `docs/nghiem-thu/` | Việc giao cho bạn B và thành viên, mẫu nghiệm thu | Mới nhất |
| `docs/CHAY_THU.md`, `docs/NGHIEM_THU.md` | Chạy thử một đơn; danh sách nghiệm thu theo màn | Mới nhất |
| `docs/chinh-sach/` | Bản nháp ba chính sách khách đọc, chưa duyệt | Nháp |

Đã xóa ngày 23/11/2026, còn trong lịch sử Git: `docs/database/` (ERD cũ 34 bảng), `SUA_TAI_KHOAN_VA_HO_SO.md`, `BAN_GIAO_TAI_KHOAN_VA_HO_SO.md`, `NGHIEM_THU_LUONG_A.md`, `admin-system-log.md`, `MO_HINH_NGHIEP_VU_MUC_1_2.md`, `backend/scripts/`.

Đã xóa 24/11/2026, còn trong lịch sử Git: bảng đối chiếu tên cũ, bản Markdown và PDF của hướng dẫn tích hợp nội bộ, manifest bộ chính sách.

Đã xóa 25/11/2026, còn trong lịch sử Git: `docs/BAN_GIAO_2026-11-25.md` (việc đã xong hoặc đã chuyển sang tài liệu khác), `docs/policy-release/` (bộ chính sách của quy trình cũ, script không còn chạy được).

Khi có mâu thuẫn, **`DB_SCHEMA_59_BANG.md` là nguồn đúng**.

## 7. Việc tiếp theo

1. [Phương án 6 luồng](PHUONG_AN_6_LUONG.md): đã làm hết, gồm đổi nơi giao sau cọc (12.6) và giao việc (mục 15).
2. Gói G2–G5 và 30 điểm của bạn B: [kế hoạch thành viên](KE_HOACH_GIAO_THANH_VIEN.md), [giao việc bạn B](GIAO_VIEC_BAN_B.md).
3. Nghiệm thu bằng tay theo [Kế hoạch triển khai](KE_HOACH_TRIEN_KHAI.md#8-đợt-cuối-tích-hợp-và-dọn-dẹp). Sơ đồ sinh từ DB: [ERD](database/ERD.md).

## 8. Ràng buộc khi làm việc với repo này

- Mọi quy tắc làm việc nằm ở `AGENTS.md`: **không được sửa file khi người dùng chưa đồng ý**, chỉ sửa trong phạm vi đã duyệt, sau khi sửa code phải chạy Quality Gate (`mvnw verify`, `npm run lint`, `npx tsc -b`, `npm test`, `npm run build`).
- Commit và PR **không** thêm dòng attribution `Co-Authored-By` hay "Generated with Claude Code".

### Quyết định 25/11/2026 về số dư tại nơi nhận

Tại hub, kiểm số dư khi đưa ngựa lên xe. T − 1 giờ thì nhả chỗ của đơn còn thiếu số dư và đóng HUB_OUT nếu điểm dừng hết ngựa cần lên xe. Đơn nhập chưa thanh toán đi từ cửa khẩu về hub và giữ trạng thái Đã xác nhận. Đơn Cần chọn lại chuyến được thanh toán số dư còn mở.

Tại nhà khách, trả tại chỗ khi hết giờ nếu có người nhận lại, rồi giữ cơ hội chọn lại lịch 30 ngày. Mốc 30 ngày theo mục 9.1 thay mốc ba ngày ghi trong bảng tóm tắt mục 10. Ba ngày áp dụng khi ngựa còn do công ty chăm sóc ở hub. Phí giữ phát sinh theo biểu phí STABLING và cấn cọc khi đơn đã hủy. Khách được nhận lại ngựa khi tới hub, kể cả còn thiếu tiền.

## Điều chỉnh đợt 1b bước 3 ngày 25/11/2026

Khi không đón được con nào, đơn đã xác nhận tiếp tục chờ quyết định của khách. Khi mọi con còn trong đơn đều hết hạn xử lý đón hụt, đơn sang Cần chọn lại chuyến, giải phóng chỗ cũ và gỡ chặng kế hoạch. Xe khác vẫn có thể đang trên đường tới điểm đón, nên hết hạn của một con không làm mất lịch của các con còn lại. Ghi đón hụt là một mốc vận hành của từng con, giữ tư cách ACTIVE cho đến khi áp dụng quyết định bỏ con. Quản lý chốt bên chịu. Khi chỉ hụt một phần, các con đã nhận tiếp tục đi theo lịch.

## Đợt 1b bước 4 · Quyết định về số dư

Thay quy tắc một khoản số dư mỗi đơn bằng một khoản số dư ban đầu và khoản bổ sung phát sinh khi nhận ngựa của lượt đón lại. Số đã trả luôn đối chiếu với lịch sử thanh toán. Việc đổi lịch lần đầu không có phí đổi lịch riêng. Nếu do khách và đã có lượt xe đến đón, chi phí của lượt đón lại được báo riêng theo ví dụ 8.5 của phương án.

## Đợt 1b bước 5 · Dời chuyến

Đổi quyết định cũ: sau khi cọc, đổi chuyến không đòi đủ lại thời gian làm giấy, chỉ tính giấy phải làm lại (mục 10 của PHUONG_AN_6_LUONG).

## Giờ giao · 25/11/2026

Không hẹn giờ giao cứng, chỉ hẹn ngày. Giờ từng điểm được báo khi phân lượt, tính lại khi xe rời hub và rời từng điểm, báo mọi điểm còn lại nếu lệch. Xét lỗi theo giờ báo gần nhất (mục 14 của PHUONG_AN_6_LUONG).
