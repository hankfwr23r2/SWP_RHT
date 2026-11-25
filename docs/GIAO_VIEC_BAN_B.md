# Giao bạn B: 30 điểm hở chưa có phương án

Ngày 25/11/2026. Người dùng giao 30 điểm này cho **bạn B** (khietnn). Luồng A tiếp tục 22 điểm đã có phương án duyệt.

Mã điểm (ví dụ 4.F1) trỏ tới mục "Điểm còn hở" trong [NGHIEP_VU_6_LUONG.md](NGHIEP_VU_6_LUONG.md). Đọc điểm đó trước khi làm.

## 1. Đọc trước khi làm

- **Chưa điểm nào có phương án được duyệt.** Cột "Gợi ý" là ý của luồng A, **không phải quyết định**. Làm theo mục 1.1 của [AGENTS.md](../AGENTS.md): trình phương án, chờ duyệt. Thứ tự tham khảo: IRT, nguồn ngoài, nghiệp vụ cũ.
- **Bắt đầu từ** `redesign/system-v2` mới nhất. Đã gộp: 1a ([PR 448](https://github.com/hankfwr23r2/SWP_RHT/pull/448)), 4a ([PR 449](https://github.com/hankfwr23r2/SWP_RHT/pull/449)), 3a ([PR 450](https://github.com/hankfwr23r2/SWP_RHT/pull/450)).
- **Số migration:** luồng A dùng `V2026112815xx` đến `V2026112819xx`. Bạn B dùng từ **`V202611290900`** trở đi (đổi số vì G1 đã gộp `V2026112709xx`; Flyway đòi số tăng dần, chưa gộp thì đổi tên file).
- Sửa xong thì ghi **Đã sửa** vào điểm tương ứng và đánh dấu ở mục 5.

## 2. Vùng luồng A đang sửa, đừng sửa cùng lúc

1b, đợt 2 và nhiều nơi giao **đã gộp**. Đang làm, cập nhật 25/11/2026:

| Việc | File |
|---|---|
| Đồng hồ dừng, cước giao lại (13.2) | `Stabling*`, `HorseStayServiceImpl`, màn Hub, chi tiết đơn của khách, `PricingPolicy`, `QuotationDraft*` |
| Đơn không người nhận (13.4), 6.G19, 6.G20 | `BookingCancellation*`, `BalanceHoldNotice*`, `CustodyBoard*`, `DeliveryComplaint*` |
| Giờ giao và xe trễ (mục 14) | `TripExecutionServiceImpl`, `DeliveryFailureServiceImpl`, `TrackingServiceImpl`, màn sự cố |

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
| **1.F5** | Tiếp nhận, thẩm định, báo giá, hoàn tiền không có hạn riêng | Hạn nội bộ trong cấu hình, quá hạn thì nhắc nhân viên. Không tự đổi trạng thái đơn | `BookingReviewServiceImpl`, `RefundServiceImpl`, `scheduler/` | |

### Luồng 2 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **2.F2** | Khách tự có bảo hiểm chỉ khai số hợp đồng, không tải tệp | Dùng loại giấy `INSURANCE_POLICY` đã khai, bắt buộc tệp khi khách tự mua | `InsuranceServiceImpl`, màn thẩm định | |
| **2.F4** | Báo giá bảo hiểm hết hạn lúc đơn Đang báo giá thì kẹt | Cho sửa bảo hiểm mua hộ khi Đang báo giá, lập lại bản nháp | `InsuranceServiceImpl`, `InsuranceReviewPolicy`, `QuotationDraftServiceImpl` | **Chờ** 1b (`QuotationDraft*`). Ưu tiên |
| **2.F5** | Không rút được giấy đã duyệt khi phát hiện giả hoặc sai | Chuyên viên thu hồi, bắt buộc lý do, báo bên nộp; chặn xe như thiếu giấy | `DocumentReviewServiceImpl`, `DocumentReviewPolicy` | Giấy đã theo xe thì cần quy định riêng |
| **2.F7** | Giấy không kiểm lại ở cửa khẩu đơn xuất | Kiểm đủ giấy lúc bàn giao đại lý, giống chiều nhập | `HandoverOutcomeServiceImpl` | 1b đã gộp, làm được. Trùng 4.F12 |
| **2.F8** | Xuất Lào thiếu sổ tiêm, nhập Lào thiếu xét nghiệm so với Campuchia | Hỏi nhóm cố ý hay thiếu; tra [CAN_CU_PHAP_LY_VA_IRT.md](CAN_CU_PHAP_LY_VA_IRT.md) | `db/seed-dev/R__dev_seed_base.sql` (`document_requirement`) | Pháp lý Lào **chưa kiểm chứng** |
| **2.F9** | Hộ chiếu đã duyệt cho đơn liên tỉnh vẫn phải nộp lại cho đơn xuất | Giấy theo ngựa còn hạn dùng chung cho mọi quy định | `DocumentCompletenessPolicy` | Giấy theo đơn thì không dùng chung |
| **2.F10** | Đơn nhập không kiểm bảo hiểm ở cửa khẩu | Kiểm như ở điểm đón và hub | `HandoverOutcomeServiceImpl` | 1b đã gộp, làm được |

### Luồng 3 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **3.G4** | Không sửa được giờ, xe của lượt đã lập | Cho sửa khi lượt chưa chạy, kiểm lại mọi quy tắc, báo tổ lái và khách | `DispatchServiceImpl`, `TripScheduleServiceImpl` | Phối hợp đợt 4 (thông báo) |
| **3.G6** | Chỉ cần một tài xế nhận việc là xe chạy, không bắt buộc hộ tống | Bắt buộc có hộ tống đã nhận việc khi chở ngựa | `TripExecutionPolicy`, `TripExecutionServiceImpl` | 1b đã gộp, làm được |
| **3.G7** | Nhập giờ tay không kiểm 5 giờ ngồi xe | Dùng chung quy tắc với lượt hệ thống tính giờ | `TripSchedulePolicy`, `DispatchPolicy` | |
| **3.G8** | Khách tự chở chỉ bị kiểm trước giờ chạy, không kiểm hạn có mặt 3 giờ | Giờ hẹn phải trước hạn có mặt ở hub | `HorseStayServiceImpl` (`planSelfDropOff`) | 1b đã gộp, làm được |
| **3.G9** | Chưa có nút dồn chuyến, chạy xe nhỏ hơn, một khách thuê cả chuyến | Chọn phần thật sự cần; tài liệu có nêu | `DepartureDecisionServiceImpl` | **Chờ** đợt 5 |
| **3.G10** | Đưa xe về phải nhờ quản lý xác nhận chuyến không có ngựa | Điều phối tự lập lượt về rỗng, không gắn chuyến bán | `DispatchServiceImpl` | THONG_BAO_LUONG_B đã nêu "vẫn lập lượt về rỗng" |
| **3.G11** | Giờ lái tính theo ngày lịch, không theo 24 giờ liên tục | Cửa sổ trượt 24 giờ | `DispatchPolicy`, `CrewAssignmentServiceImpl` | Tra luật giao thông, hiện **chưa kiểm chứng** |

### Luồng 4 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **4.F1** | Không có màn ghi sức khỏe ở hub, chuồng; ngựa ở hub luôn bị tô Trễ khám | Thêm màn ghi, máy chủ đã nhận | `HealthLogServiceImpl`, màn Theo dõi | Ưu tiên. Màn Hub **chờ** 1b |
| **4.F2** | Chỉ dẫn ăn uống của khách không hiện cho tổ lái | Hiện chỉ dẫn và số bữa từng con ở màn lượt xe | `booking_horse` (chỉ dẫn, số bữa), `TripRunPage` | Ưu tiên. Màn **chờ** 1b |
| **4.F3** | Khách không xem được nhật ký sức khỏe | Hiện trong hành trình từng con, chỉ chủ đơn | `TrackingServiceImpl`, `HorseJourney` | Giống cách 3a hiện biên bản |
| **4.F6** | Điểm nghỉ đêm 10 giờ chỉ bị kiểm 30 phút | Kiểm theo thời gian dừng kế hoạch của điểm; rời sớm phải có lý do và báo điều phối | `StopReadinessServiceImpl`, `HealthLogPolicy` | 1b đã gộp, làm được. Ưu tiên |
| **4.F10** | Chưa báo tình trạng xe khi về trụ sở | Tài xế chọn bình thường hoặc cần bảo dưỡng khi kết thúc lượt | `TripExecutionServiceImpl`, `VehicleServiceImpl` | 1b đã gộp, làm được. Làm cùng 5.G16 |
| **4.F11** | Không có vị trí xe thời gian thực | Cân nhắc bỏ khỏi phạm vi; hiện dùng điểm dừng gần nhất | màn Theo dõi | Hỏi người dùng trước |
| **4.F12** | Giấy không kiểm ở cửa khẩu đơn xuất | Như 2.F7 | | Trùng 2.F7, làm một lần |

### Luồng 5 (7 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **5.G5** | Lượt giao không có hub phía sau thì con giao hụt không có chỗ vào, lượt không kết thúc được | Lượt giao phải kết thúc ở hub khi lập; hoặc cho thêm điểm hub vào lượt đang chạy | `DispatchServiceImpl`, `DispatchPolicy` | Ưu tiên. Gợi ý gom đã tự thêm hub cuối |
| **5.G14** | Đang có sự cố, tài xế vẫn Tới nơi, Rời đi được | Chỉ khóa với sự cố nặng; có cách mở lại | `TripExecutionServiceImpl`, `IncidentPolicy` | **Chờ** 1b và đợt 2 |
| **5.G15** | Gửi chuồng gần, Thú y, Hủy chặng duyệt xong không có thao tác | Gửi chuồng mở lưu trú ở đối tác; hủy chặng gỡ chặng; thú y ghi nhật ký | `IncidentActionServiceImpl`, `HorseStayServiceImpl` | Đợt 2 đã gộp, làm được |
| **5.G16** | Đổi xe không ký lại giao nhận, xe hỏng không sang bảo dưỡng | Xe hỏng sang bảo dưỡng ngay; hộ tống ký `SHIFT` tại chỗ | `IncidentActionServiceImpl`, `VehicleServiceImpl` | Đợt 2 đã gộp, làm được. Làm cùng 4.F10 |
| **5.G17** | Tổ lái xác nhận đủ sức không cần ảnh | Bắt buộc ảnh; tài xế không tự xác nhận | `IncidentServiceImpl` | Đợt 2 đã gộp, làm được. Điều phối xác nhận thay đã cần `VET_REPORT` (1a) |
| **5.G18** | 30 phút đếm từ lúc báo, không từ lúc ngựa nghỉ | Đếm từ lúc bắt đầu nghỉ | `IncidentPolicy` | Đợt 2 đã gộp, làm được |
| **5.G22** | Sự cố không có mức nặng nhẹ | Thêm mức, sắp danh sách theo mức | `incident` (cột mới), màn sự cố | Đợt 2 đã gộp, làm được. Cần cập nhật đặc tả |

### Luồng 6 (1 điểm)

| Điểm | Đang thế nào | Gợi ý (chưa duyệt) | Xem ở | Lưu ý |
|---|---|---|---|---|
| **6.G15** | Không phân biệt quét chip với gõ tay | Lưu cách nhập; gõ tay thì ghi lý do | `HandoverServiceImpl`, `handover_horse` | 1b đã gộp, làm được |

## 5. Theo dõi

| Điểm | Phương án | Duyệt | PR |
|---|---|---|---|
| 1.F5 | | | |
| 2.F2, 2.F4, 2.F5, 2.F7, 2.F8, 2.F9, 2.F10 | | | |
| 3.G4, 3.G6, 3.G7, 3.G8, 3.G9, 3.G10, 3.G11 | | | |
| 4.F1, 4.F2, 4.F3, 4.F6, 4.F10, 4.F11, 4.F12 | | | |
| 5.G5, 5.G14, 5.G15, 5.G16, 5.G17, 5.G18, 5.G22 | | | |
| 6.G15 | | | |

## 6. Luồng A giữ lại (22 điểm, đừng làm)

Một phần giao cho thành viên khác theo [KE_HOACH_GIAO_THANH_VIEN.md](KE_HOACH_GIAO_THANH_VIEN.md) (đợt 3, 4, 5). Bạn B không làm các điểm này.

| Đợt | Điểm |
|---|---|
| 2 | 1.F1, 5.G9, 5.G10, 5.G11, 5.G12, 5.G13, 6.G23 |
| 3 | 6.G18, 6.G24 |
| 4 | 3.G5, 4.F4, 4.F9, 5.G7, 5.G8, 5.G19, 6.G17, 6.G19, 6.G20 |
| 5 | 1.F6, 3.G2, 4.F7, 5.G23 |

Ngoài ra A làm nốt 1b (5.G21, 6.G3, 6.G4, 6.G5, 6.G6, 6.G21) và ba điểm mới sửa một phần (4.F5, 6.G13, 6.G16).
