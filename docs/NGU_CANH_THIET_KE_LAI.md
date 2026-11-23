# Ngữ cảnh đợt thiết kế lại nghiệp vụ, mô hình và DB

> Ghi lại để các phiên làm việc sau (người hoặc agent) nắm được **vì sao** thiết kế như hiện tại.
> Ngày ghi: 22/11/2026. Nhánh lúc ghi: `feat/insurance-resubmission`. Tất cả tài liệu ở đây **chưa commit**.

## 1. Bối cảnh

- Nhóm **bảo vệ không đạt**. Giảng viên đọc từ góc nhìn DB và cho rằng thiết kế sai. Nhóm vẽ ERD từ entity JPA chứ không thiết kế DB trước.
- Góp ý của thầy: **"học lại kiến thức nền"**, và nghiệp vụ còn nhiều chỗ chưa sát thực tế.
- Câu hỏi nhóm không trả lời được: *DB thể hiện việc tính tiền theo km hay theo điểm dừng như thế nào?* Câu trả lời thật là DB **không thể hiện**. Giá được tính ở frontend rồi lưu thành JSON.

## 2. Chẩn đoán gốc rễ (chi tiết: `DANH_SACH_LOI_THIET_KE.md`, 50 lỗi)

| Vấn đề | Biểu hiện trong code cũ |
|---|---|
| Dữ liệu nghiệp vụ nằm trong JSON | `DetailsJson`, `quote_lines_json`: báo giá, bảo hiểm, giấy tờ đều nằm trong cột text |
| Logic giá ở frontend | `frontend/src/shared/lib/pricing.ts`, `booking.ts`, `config/booking-rules.ts`. Backend nhận dòng báo giá từ client (`BookingServiceImpl.sendQuote`), và `QuoteLinePolicy` chỉ kiểm 2 dòng. Đây là lỗi CWE-602 |
| Bảng giá dạng key–value | `system_config` chứa `TRIP_OPEN_FEE`, `KM_TIER_*`, `CLASS_FACTOR_*`, `MARGIN_RATE`… |
| Không quản lý phiên bản schema | Không có Flyway. ERD ở `docs/database` đã cũ, file SQL nguồn không còn |
| API kiểu hành động và switch | `POST /field/bookings/{id}/trip-update` xử lý khoảng 21 action trong một switch (`TripProgressServiceImpl`) |
| Trạng thái đơn bùng nổ | `booking.status` có khoảng 27 giá trị, trộn trạng thái thương mại, vận hành và các nhánh duyệt |
| Suy luận từ chuỗi | `Vehicle` lấy sức chứa bằng cách đọc chuỗi `vehicle_type` |
| Luồng trùng nhau | `DeliveryServiceImpl.completeHandover` và action `COMPLETE_DELIVERY` cùng làm một việc |

Kết luận: **cơ chế giao tiếp** (REST/JSON + JWT + refresh cookie) không sai. Phần yếu là **thiết kế API**: nên lấy OpenAPI làm hợp đồng giữa hai đầu và sinh code client từ đó. Swagger không chỉ dùng để test.

## 3. Mô hình nghiệp vụ mới (người dùng đề xuất, đã thống nhất)

1. **Gom hàng qua hub:** chặng đón → ngựa tập kết ở hub → **chuyến trục chính chạy định kỳ** → trại cách ly → chặng giao.
2. **Mạng lưới hub và tuyến cố định.** Trên tuyến vẫn giữ quy trình chăm sóc hiện tại: trạm nghỉ, kiểm tra sức khỏe, cửa khẩu.
   - Trục chính có cả **tuyến nội địa** (hub ⇄ hub, ví dụ HCM ⇄ Đà Nẵng) và tuyến quốc tế (hub → cửa khẩu).
   - **Trụ sở và hub vệ tinh:** mọi xe và nhân viên thuộc trụ sở. Hub vệ tinh có vùng phục vụ và chỗ tập kết, không có xe hay nhân viên trực thuộc. Xe trục tới hub vệ tinh thì chạy luôn lượt giao ở đó, rồi về trụ sở bằng chuyến trục chiều ngược.
   - Cách chạy quyết định theo hub, không theo loại đơn: hub đón trùng hub giao thì chạy thẳng (`DIRECT`), khác hub thì đi trục. Mỗi đơn có tối đa một chặng trục.
3. **Tách lịch thương mại khỏi lượt chạy thật:**
   - `departure_schedule` → `departure`: khách đặt chỗ vào chuyến theo ngày;
   - `vehicle_trip`: xe thật chạy, có thể nhiều xe cho một chuyến.
4. **Theo dõi theo từng con ngựa:**
   - `horse_leg`: chặng di chuyển;
   - `horse_stay`: thời gian lưu ở một nơi không trên xe (tập kết, cách ly, qua đêm);
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
| Giữ `horse_stay` dù đã có `trip_stop` | `trip_stop` gắn với xe và chỉ dài vài phút đến vài giờ. Cách ly kéo dài nhiều ngày và không có xe |
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
| Tài khoản và hồ sơ theo cách cũ: `app_user` (một vai trò) 1–0..1 `customer`/`staff`. `party` chỉ chứa đối tác (23/11/2026, **thay** quyết định gộp khách vào `party`) | Người dùng chốt. Vai trò khách chỉ lưu một nơi, FK chặn gán sai loại. Đã thực hiện ở `V202611231200`, xem `SUA_TAI_KHOAN_VA_HO_SO.md` |
| Thiết kế tới mức màn hình và tác động, xem `MAN_HINH_VA_TAC_DONG.md`. Trách nhiệm chăm ngựa chuyển theo `handover` của từng con, hộ tống theo lượt xe (23/11/2026) | Đơn được ghép nên không gắn hộ tống với đơn. Mỗi thao tác phải có màn hình, bằng chứng và tác động |
| Ngừng dịch vụ cho ăn tính tiền, giữ `VET_CHECK`; chăm sóc chuẩn nằm trong cước (23/11/2026) | Phí lưu chuồng đã gồm ăn uống; chỉ dẫn ăn uống là yêu cầu chăm sóc, không phải hàng bán |
| Giữ tên `location` (23/11/2026) | Người dùng chốt. Chỉ chứa điểm cố định của mạng lưới |
| Giữ vai trò `ESCORT` (nhân viên hộ tống), không đổi thành groom | Người dùng chốt. Tên đã dùng thống nhất trong lược đồ, thư mục `backoffice/features/escort` và code cũ |
| Tên trong code (bảng, cột, enum, class, biến, endpoint) dùng tiếng Anh theo chuẩn đặt tên. Chữ hiển thị cho người dùng đi qua i18n (`vi`, `en`) | Người dùng chốt. Tài liệu viết tiếng Việt nhưng mọi định danh phải là tiếng Anh |
| `MO_HINH_NGHIEP_VU_MOI.md` và `MO_HINH_NGHIEP_VU_MUC_1_2.md` chỉ là tài liệu tham khảo, không phải yêu cầu | Yêu cầu chi tiết lấy từ `DB_SCHEMA_59_BANG.md` và `NGHIEP_VU_THEO_59_BANG.md` |
| Backend theo n-layer: Controller → Service → Repository, cộng Policy và Client | DTO có `from()`, `toEntity()`, `applyTo()`. Không dùng ModelMapper. Chi tiết: `AGENTS.md` |

## 5. Câu hỏi còn mở

- [ ] Bỏ `departure_schedule_day` (thành 58 bảng) hay giữ?
- [ ] Rà lại các giá trị trạng thái mà agent tự đặt cho `vehicle_trip`, `horse_leg`, `payment`, `incident`, `incident_plan`, `document`… để khớp nghiệp vụ.
- [ ] Đánh giá chỉ cho cả đơn, hay cho từng tài xế/chuyến? Nếu cho tài xế/chuyến thì `booking_rating` phải đổi.
- [ ] Viết `DocumentCompletenessPolicy` (đơn đã đủ giấy chưa) và unit test với nhiều trường hợp.
- [x] ~~Một hub hay nhiều hub~~: đã chốt trụ sở + hub vệ tinh (mục 4). `AGENTS.md` đã ghi "xe về hub gốc".
- [ ] Bổ sung **dữ liệu nền đầy đủ**: điểm mạng lưới, vùng phục vụ của từng hub, tuyến (mỗi chiều một tuyến), lịch chạy, bảng giá, quy định. Các ví dụ trong `NGHIEP_VU_THEO_59_BANG.md` mục 3 chỉ là minh họa.

## 6. Bản đồ tài liệu

| File | Nội dung | Trạng thái |
|---|---|---|
| `docs/DB_SCHEMA_59_BANG.md` | **Đặc tả chuẩn để tạo DB**: 59 bảng, ràng buộc, thứ tự tạo, dữ liệu dẫn xuất, quy tắc ở service | Mới nhất |
| `docs/KE_HOACH_TRIEN_KHAI.md` | Kế hoạch triển khai: đợt 0, luồng A (thương mại) và B (vận hành) chạy song song, chủ sở hữu bảng, hợp đồng gọi chéo, quy trình cho agent, bảng tiến độ | Mới nhất |
| `docs/SUA_TAI_KHOAN_VA_HO_SO.md` | Việc giao agent: sửa nhóm tài khoản, hồ sơ và ràng buộc `handover` | Đã thực hiện (23/11/2026) |
| `docs/BANG_DOI_TEN.md` | Tên cũ → tên mới | Code chưa đồng bộ |
| `docs/NGHIEP_VU_THEO_59_BANG.md` | Nghiệp vụ chi tiết theo 59 bảng (10 giai đoạn, nhánh phụ, ví dụ, ai ghi bảng nào) và danh sách tận dụng từ project cũ | Mới nhất |
| `docs/DANH_SACH_LOI_THIET_KE.md` | 50 lỗi thiết kế của project cũ, nhóm A–I | **Không còn trong thư mục** (22/11/2026) |
| `AGENTS.md` (thư mục gốc) | File quy tắc duy nhất: cách làm việc, cấu trúc backend n-layer, DB, DTO, API, frontend, kiểm thử. Đã gộp `BACKEND_ARCHITECTURE_RULES.md` và `frontend/docs/AGENT_GUIDE.md` (hai file này đã xóa) | Mới nhất |
| `docs/ERD_ENTITY_MERMAID.md`, `docs/ERD_ALL_IN_ONE.md`, `docs/ERD_ENTITY_BOXES.md` | ERD bản 64 bảng | **Không còn trong thư mục.** Nên sinh lại ERD từ DB sau khi chạy Flyway |
| `docs/MO_HINH_NGHIEP_VU_MUC_1_2.md`, `docs/MO_HINH_NGHIEP_VU_MOI.md` | Kế hoạch thay đổi nghiệp vụ của người dùng | Tham khảo |
| `docs/database/*` | ERD cũ (34 bảng) | Lỗi thời |

Khi có mâu thuẫn, **`DB_SCHEMA_59_BANG.md` là nguồn đúng**.

## 7. Việc tiếp theo đề xuất

1. Chốt các câu hỏi ở mục 5.
2. Sinh ERD từ DB thật sau khi chạy Flyway (DBeaver hoặc SchemaSpy), không vẽ tay.
3. Viết Flyway migration theo các bậc trong mục "Thứ tự tạo bảng" và chạy thử trên PostgreSQL 15 trở lên. Bật `ddl-auto=validate`.
4. Chuyển toàn bộ tính giá từ frontend sang `PricingService` ở backend. Frontend chỉ hiển thị.
5. Tách API `trip-update` kiểu switch thành các endpoint theo tài nguyên (`/trips/{id}/stops/{stopId}/arrive`, `/handovers/{id}/complete`…).
6. Bật test kiến trúc (ArchUnit) khi bắt đầu nhánh `redesign/system-v2`, theo `AGENTS.md` mục 10.3.

## 8. Ràng buộc khi làm việc với repo này

- Mọi quy tắc làm việc nằm ở `AGENTS.md`: **không được sửa file khi người dùng chưa đồng ý**, chỉ sửa trong phạm vi đã duyệt, sau khi sửa code phải chạy Quality Gate (`mvnw verify`, `npm run lint`, `npx tsc -b`, `npm test`, `npm run build`).
- Commit và PR **không** thêm dòng attribution `Co-Authored-By` hay "Generated with Claude Code".
