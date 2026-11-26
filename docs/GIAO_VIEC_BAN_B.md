# Giao bạn B: 30 điểm hở chưa có phương án

> **26/11/2026 · Việc mới cho bạn B (ưu tiên trước các điểm cũ còn lại):** mô hình bến xe, task **M4, M5, M6, M7, M8** ở mục 11 của [KE_HOACH_TRIEN_KHAI.md](KE_HOACH_TRIEN_KHAI.md). Làm ngay **M5, M6, M8** (không phụ thuộc luồng A). M7 chờ A xong M1, M4 chờ A xong M2 và M3. Đọc trước [NGHIEP_VU_6_LUONG.md](NGHIEP_VU_6_LUONG.md) bản 26/11 và [NGU_CANH_THIET_KE_LAI.md](NGU_CANH_THIET_KE_LAI.md) mục 4b. Migration của B dùng `V202612012000`–`V202612012999`. Mỗi task trình phương án (gồm lược đồ nếu đổi) cho người dùng duyệt trước khi làm.
>
> Các điểm về đại lý ở dưới (chặng đại lý, bàn giao `AGENT`) **không làm nữa**, thay bằng M7.

Ngày 25/11/2026. Người dùng giao 30 điểm này cho **bạn B** (khietnn). Luồng A tiếp tục 22 điểm đã có phương án duyệt.

Mã điểm (ví dụ 4.F1) trỏ tới mục "Điểm còn hở" trong bản lưu [luu-tru/NGHIEP_VU_6_LUONG_25-11-2026.md](luu-tru/NGHIEP_VU_6_LUONG_25-11-2026.md). Đọc điểm đó trước khi làm.

## 1. Đọc trước khi làm

- **Chưa điểm nào có phương án được duyệt.** Cột "Gợi ý" là ý của luồng A, **không phải quyết định**. Làm theo mục 1.1 của [AGENTS.md](../AGENTS.md): trình phương án, chờ duyệt. Thứ tự tham khảo: IRT, nguồn ngoài, nghiệp vụ cũ.
- **Bắt đầu từ** `redesign/system-v2` mới nhất. Đã gộp: 1a ([PR 448](https://github.com/hankfwr23r2/SWP_RHT/pull/448)), 4a ([PR 449](https://github.com/hankfwr23r2/SWP_RHT/pull/449)), 3a ([PR 450](https://github.com/hankfwr23r2/SWP_RHT/pull/450)).
- **Số migration:** luồng A dùng `V2026112815xx` đến `V2026112819xx`. Bạn B dùng từ **`V202611290900`** trở đi (đổi số vì G1 đã gộp `V2026112709xx`; Flyway đòi số tăng dần, chưa gộp thì đổi tên file).
- Sửa xong thì ghi **Đã sửa** vào điểm tương ứng và đánh dấu ở mục 5.

## 2. Vùng luồng A đang sửa, đừng sửa cùng lúc

Mọi phần của luồng A **đã gộp** (#457 đến #474). Hiện luồng A không sửa dở file nào.

## 3. Nên làm trước

| Nhóm | Điểm | Vì sao |
|---|---|---|
| Kẹt đơn, kẹt xe | **2.F4**, **5.G5** | Đơn hoặc xe không đi tiếp được |
| An toàn của ngựa | **4.F1**, **4.F2**, **4.F6**, **3.G6**, **3.G7** | Không thấy sức khỏe ở hub, không biết cho ăn gì, nghỉ đêm thiếu giờ |
| Xe không an toàn | **5.G16**, **4.F10** | Xe hỏng vẫn được xếp lượt |

## 4. Danh sách 30 điểm

Đường dẫn máy chủ nằm trong `backend/src/main/java/com/example/racehorse_transport/`.

### Luồng 1 (1 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **1.F5** | **Đã sửa.** Ba hạn nội bộ (tiếp nhận, thẩm định, gửi báo giá) tính theo giờ; hoàn tiền giữ `REFUND_DUE_DAYS` và lịch nhắc sẵn có. Hệ thống quét mỗi giờ và nhắc đúng người phụ trách tối đa một lần mỗi bước mỗi đơn trong ngày | | `BookingDeadlineReminderScheduler`, `BookingDeadlineReminderServiceImpl` | |

### Luồng 2 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **2.F2** | **Đã sửa.** Khách tải tệp hợp đồng khi khai báo hoặc bổ sung bảo hiểm tự có cho từng con. Chuyên viên xem tệp mới nhất và chỉ xác nhận được khi có tệp. | | `InsuranceServiceImpl`, màn thẩm định | |
| **2.F4** | **Đã sửa.** Chuyên viên cập nhật và xác nhận bảo hiểm mua hộ khi đơn Đang báo giá. Quản lý lập lại bản nháp theo phí mới | | `InsuranceServiceImpl`, `InsurancesCard.tsx` | |
| **2.F5** | **Đã sửa.** Chuyên viên thu hồi giấy đã duyệt trước khi nhận ngựa, ghi lý do, lưu kiểm toán và báo bên nộp. Thiếu giấy thì chặn xe. | | `DocumentReviewServiceImpl`, `DocumentReviewPolicy` | Đã nhận ngựa thì xử lý qua sự cố giấy tờ |
| **2.F7** | **Đã sửa.** Đơn xuất phải đủ giấy bắt buộc khi bàn giao cho đại lý ở cửa khẩu. Thiếu giấy thì ngựa vẫn trên xe. | | `HandoverOutcomeServiceImpl` | Trùng 4.F12 |
| **2.F8** | **Giữ nguyên.** Giấy cấu hình riêng theo nước và chiều; dữ liệu mẫu của Lào giữ như hiện có, quản trị sửa được khi cần | | `db/seed-dev/R__dev_seed_base.sql` (`document_requirement`) | Người dùng chốt 26/11 |
| **2.F9** | **Đã sửa.** Giấy theo ngựa đã duyệt, còn hạn được dùng cho mọi quy định yêu cầu cùng loại giấy. | | `DocumentCompletenessPolicy` | Giấy theo đơn hoặc theo ngựa trong đơn chỉ dùng cho đơn đó |
| **2.F10** | **Đã sửa.** Chỉ nhận ngựa nhập từ đại lý ở cửa khẩu khi bảo hiểm từng con đã được xác nhận. | | `HandoverOutcomeServiceImpl` | |

### Luồng 3 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **3.G4** | **Đã sửa.** Điều phối phụ trách đổi xe hoặc dời giờ lượt `PLANNED`. Lượt trục chỉ đổi xe. Hệ thống kiểm lại kế hoạch và báo tổ lái, khách có ngựa trên lượt. | | `DispatchServiceImpl`, `TripPlanEditServiceImpl`, `TripEditModal.tsx` | |
| **3.G6** | **Đã sửa.** Lượt chở ngựa phải có hộ tống đã nhận việc mới xuất phát được; lượt chạy rỗng không cần | | `TripExecutionPolicy`, `TripExecutionServiceImpl` | |
| **3.G7** | **Đã sửa.** Xếp ngựa vào lượt đón, giao nhập giờ tay cũng kiểm thời gian ngồi xe theo kế hoạch: tối đa nửa giới hạn lái mỗi ngày (5 giờ), chạy thẳng tối đa 10 giờ | | `HorseLegServiceImpl`, `TripSchedulePolicy` | |
| **3.G8** | **Đã sửa.** Màn đặt hẹn hiện hạn có mặt ở hub. Giờ hẹn tự chở muộn hơn hạn bị từ chối | | `HorseStayServiceImpl` (`planSelfDropOff`) | 1b đã gộp, làm được |
| **3.G9** | **Đã sửa.** Khi xác nhận chạy có thể giảm sức chứa đến số chỗ đã giữ. Dồn chuyến theo lịch cùng tuyến thì khách được đề xuất đổi chuyến giữ giá và tự xác nhận. | | `DepartureDecisionServiceImpl`, `DepartureServiceImpl`, `RescheduleCard.tsx` | |
| **3.G10** | **Đã sửa.** Điều phối lập lượt `TRANSFER` rỗng từ nơi xe đang ở về hub gốc, không cần chuyến bán hay quản lý xác nhận chạy. | | `TripScheduleServiceImpl`, `CreateTripModal.tsx` | |
| **3.G11** | **Giữ nguyên.** Giờ lái mỗi ngày tính theo ngày lịch | | `DispatchPolicy`, `CrewAssignmentServiceImpl` | Người dùng chốt 26/11 |

### Luồng 4 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **4.F1** | **Đã sửa.** Người đang phụ trách con ngựa ghi sức khỏe ở hub, chuồng; điều phối ghi hộ kèm người báo và kênh báo; xem sổ sức khỏe ở Theo dõi | | `HealthLogServiceImpl`, `StayHealthDialog` | |
| **4.F2** | **Đã sửa.** Tổ xe và màn Hub thấy chỉ dẫn ăn uống, số bữa, ai cấp thức ăn | | `HorseFeedingDetails` | |
| **4.F3** | **Đã sửa.** Chủ đơn xem nhật ký sức khỏe từng con; nhân viên và tra cứu công khai không nhận mục này | | `TrackingEvidenceServiceImpl` | |
| **4.F6** | **Đã sửa.** Điểm có giờ dừng kế hoạch từ OVERNIGHT_REST_HOURS trở lên phải nghỉ đủ số giờ đó; rời sớm phải ghi lý do và hệ thống báo điều phối | | `StopReadinessServiceImpl`, `HealthLogPolicy` | |
| **4.F10** | **Đã sửa.** Tài xế báo tình trạng xe khi kết thúc lượt; cần bảo dưỡng thì xe sang bảo dưỡng | | `TripExecutionServiceImpl`, `VehicleServiceImpl` | Làm cùng 5.G16 (#482) |
| **4.F11** | **Ngoài phạm vi.** Không làm vị trí xe theo thời gian thực; vị trí là điểm dừng gần nhất tổ xe đã bấm Tới nơi | | màn Theo dõi | Người dùng chốt 26/11 |
| **4.F12** | **Đã sửa.** Tổ xe chỉ hoàn tất bàn giao cho đại lý ở cửa khẩu khi đơn xuất đủ giấy bắt buộc. | | `HandoverOutcomeServiceImpl` | Trùng 2.F7 |

### Luồng 5 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **5.G5** | **Đã sửa.** Lượt đón, giao, chạy thẳng phải kết thúc ở hub; máy chủ chặn khi lập lượt, màn lập lượt báo ngay ở điểm cuối | | `DispatchPolicy`, `DispatchServiceImpl`, `CreateTripModal.tsx` | |
| **5.G14** | **Đã sửa.** Sự cố Nặng chưa có phương án được duyệt thì khóa Tới nơi, Rời đi; duyệt phương án tự mở, điều phối mở khóa kèm lý do | | `IncidentPolicy.blocksTrip`, `TripReadinessServiceImpl` | |
| **5.G15** | **Đã sửa.** Ghi kết quả thú y vào sổ sức khỏe, gửi đúng chuồng tại điểm dừng mạng lưới, hủy chặng và đưa ngựa về hub tập kết không phí. | | `IncidentActionServiceImpl`, `HandoverCustodyServiceImpl`, `IncidentReturnServiceImpl` | |
| **5.G16** | **Đã sửa.** Đổi xe có ảnh biên bản đã ký, xe hỏng sang bảo dưỡng ngay | | `IncidentActionServiceImpl`, `VehicleServiceImpl` | Làm cùng 4.F10 (#482) |
| **5.G17** | **Đã sửa.** Chỉ hộ tống của lượt xác nhận đủ sức và phải có ảnh xác nhận; điều phối xác nhận thay vẫn cần kết quả thú y | | `IncidentServiceImpl`, `TripIncidentPanel` | |
| **5.G18** | **Đã sửa.** Hộ tống bấm Bắt đầu cho ngựa nghỉ; 30 phút đếm từ lúc đó | | `IncidentPolicy`, `incident.rest_started_at` | |
| **5.G22** | **Đã sửa.** Sự cố có mức Nhẹ, Vừa, Nặng (mặc định theo loại, điều phối đổi được); danh sách xếp Nặng lên đầu | | `incident.severity`, màn sự cố | |

### Luồng 6 (1 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **6.G15** | **Đã sửa.** Giao nhận của tổ xe lưu cách nhập chip từng con, bắt buộc lý do khi gõ tay và hiện nhãn cho điều phối | | `HandoverServiceImpl`, `handover_horse`, `TripRunPage` | |

## 5. Theo dõi

| Điểm | Phương án | Duyệt | PR |
|---|---|---|---|
| 1.F5 | Đặt hạn theo giờ cho tiếp nhận, thẩm định, gửi báo giá. Quét mỗi giờ, nhắc theo vai trò và giữ trạng thái đơn | Đã duyệt | #504 |
| 2.F2 | Bắt buộc tệp `INSURANCE_POLICY` cho từng bảo hiểm tự có khi khai báo hoặc bổ sung. Chuyên viên được giao đơn xác nhận sau khi kiểm hợp đồng. | Đã duyệt | #507 |
| 2.F8 | Giữ nguyên dữ liệu mẫu | Đã duyệt | |
| 2.F5 | Thu hồi giấy đã duyệt trước khi nhận ngựa, chuyển sang từ chối kèm lý do, ghi kiểm toán và báo bên nộp | Đã duyệt | #506 |
| 2.F9 | Dùng lại giấy theo ngựa đã duyệt và còn hạn khi yêu cầu cùng loại giấy | Đã duyệt | #503 |
| 2.F7 | Kiểm đủ giấy bắt buộc khi bàn giao đơn xuất cho đại lý ở cửa khẩu | Đã duyệt | #496 |
| 2.F10 | Kiểm bảo hiểm đã xác nhận khi nhận ngựa nhập từ đại lý ở cửa khẩu | Đã duyệt | #496 |
| 2.F4 | Cập nhật và xác nhận bảo hiểm mua hộ khi đơn Đang báo giá | Đã duyệt | #479 |
| 3.G4 | Đổi xe, dời đều cả lượt ngoài trục hoặc sửa từng điểm của lượt nhập tay. Chỉ điều phối phụ trách được sửa lượt `PLANNED`. Dùng lại các kiểm tra khi lập lượt và báo tổ lái, khách. | Đã duyệt | #508 |
| 3.G9 | Giảm sức chứa khi xác nhận chạy. Dồn hai chuyến theo lịch cùng tuyến trong hạn giữ giá, đủ chỗ và bắt buộc có lý do. Khách đồng ý qua luồng đổi chuyến hiện có hoặc chọn phương án khác. | Đã duyệt | |
| 3.G11 | Giữ nguyên, tính theo ngày lịch | Đã duyệt | |
| 3.G10 | Điều phối lập lượt `TRANSFER` về hub gốc, tính giờ theo tuyến chiều về hoặc theo bản đồ | Đã duyệt | #501 |
| 3.G6, 3.G7 | Bắt buộc hộ tống đã nhận việc; kiểm giờ ngồi xe khi nhập giờ tay | Đã duyệt | #480 |
| 3.G8 | Hiện hạn có mặt ở hub bằng giờ xuất phát hiệu lực trừ `HUB_CHECKIN_HOURS` và từ chối giờ hẹn muộn hơn hạn | Đã duyệt | #500 |
| 4.F1, 4.F2, 4.F3 | Luồng A làm thay theo yêu cầu người dùng | Đã duyệt | |
| 4.F11 | Bỏ khỏi phạm vi, giữ vị trí theo điểm dừng gần nhất | Đã duyệt | |
| 4.F12 | Kiểm đủ giấy bắt buộc khi bàn giao đơn xuất cho đại lý ở cửa khẩu | Đã duyệt | #496 |
| 4.F6 | Điểm nghỉ đêm phải nghỉ đủ OVERNIGHT_REST_HOURS, rời sớm ghi lý do và báo điều phối | Đã duyệt | #480 |
| 4.F10 | Báo tình trạng xe cuối lượt, đưa vào bảo dưỡng khi cần | Đã duyệt | #482 |
| 5.G15 | Thực hiện một lần theo phiên bản mới nhất đã duyệt. Ghi kết quả thú y, gửi cho đúng chuồng tại điểm mạng lưới, hoặc đưa ngựa về hub và hủy các chặng phía sau chưa chạy. | Đã duyệt | #490 |
| 5.G14, 5.G22 | Mức độ sự cố; sự cố Nặng chưa duyệt phương án thì khóa xe, điều phối mở khóa có lý do | Đã duyệt | #487, #488 |
| 5.G17, 5.G18 | Chỉ hộ tống xác nhận đủ sức, bắt buộc ảnh; 30 phút đếm từ lúc bắt đầu nghỉ | Đã duyệt | #486 |
| 5.G5 | Lượt đón, giao, chạy thẳng phải kết thúc ở hub | Đã duyệt | #478 |
| 5.G16 | Phương án (a), ảnh biên bản đã ký và đưa xe cũ vào bảo dưỡng | Đã duyệt | #482 |
| 6.G15 | Lưu `SCAN` hoặc `MANUAL` cho từng con, yêu cầu lý do gõ tay và hiển thị trong hành trình của điều phối. Request cũ mặc định `SCAN` | Đã duyệt | #502 |

## 6. Luồng A giữ lại (22 điểm, đừng làm)

Một phần giao cho thành viên khác theo [KE_HOACH_GIAO_THANH_VIEN.md](KE_HOACH_GIAO_THANH_VIEN.md) (đợt 3, 4, 5). Bạn B không làm các điểm này.

| Đợt | Điểm |
|---|---|
| 2 | 1.F1, 5.G9, 5.G10, 5.G11, 5.G12, 5.G13, 6.G23 |
| 3 | 6.G18, 6.G24 |
| 4 | 3.G5, 4.F4, 4.F9, 5.G7, 5.G8, 5.G19, 6.G17, 6.G19, 6.G20 |
| 5 | 1.F6, 3.G2, 4.F7, 5.G23 |

Ngoài ra A làm nốt 1b (5.G21, 6.G3, 6.G4, 6.G5, 6.G6, 6.G21) và ba điểm mới sửa một phần (4.F5, 6.G13, 6.G16).
