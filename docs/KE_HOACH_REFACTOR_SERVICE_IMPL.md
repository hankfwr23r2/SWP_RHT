# Kế hoạch refactor tầng service của RaceHorse Transport

Phạm vi ban đầu gồm 24 lớp triển khai service trong `backend/src/main/java/com/example/racehorse_transport/service/impl/`. Mục tiêu là để người đọc tìm được nơi kiểm tra điều kiện, ghi dữ liệu và phát sinh tác dụng phụ của từng thao tác nghiệp vụ. Các service tra cứu có thể dùng chung repository. Tách lớp khi có quy tắc cần dùng chung hoặc một luồng đủ độc lập để kiểm thử.

## Kiến trúc triển khai

Controller và scheduler gọi hợp đồng trong `service/`. Các lớp trong `service/impl/` triển khai những hợp đồng này và điều phối transaction, quyền, repository cùng các service liên quan.

| Nghiệp vụ | Service chịu trách nhiệm |
| --- | --- |
| Tạo đơn, thẩm định, báo giá và áp dụng tiền | BookingService. Kiểm tra khả dụng của xe và kíp qua FleetService. |
| Tra cứu booking, chuẩn bị dữ liệu phản hồi và tracking | BookingQueryService. Quyền xem, dữ liệu JSON cũ và truy vấn phụ được xử lý khi chuẩn bị phản hồi. |
| Cập nhật diễn biến chuyến và bàn giao | TripProgressService chuyển phần bàn giao cho DeliveryService. DeliveryService dùng HorseDeliveryLocationService để ghi vị trí ngựa. |
| Khởi tạo hồ sơ khách | CustomerProfileService, dùng chung cho đăng ký, tạo booking và quản lý ngựa. |
| Xem trước lộ trình | RoutePreviewService điều phối RoadRoutingService, RouteCacheService và TerritoryLookupService. GeoPointDto và RoadRouteDto ở `dto/route/`; thuật toán hình học và lịch nghỉ ở `util/`. |
| Biến đổi dữ liệu đầu vào | DTO có phương thức tạo entity hoặc cập nhật entity đã tải. Service xác thực, tìm quan hệ, cấp ID, lưu và audit. Ví dụ, CreateServiceRequestDto.toEntity(id, category) và applyTo(service, category). |

Bộ lập lịch dọn OTP ở `scheduler/`, kiểm tra truy cập tracking và Turnstile ở `security/`, khóa lưu OTP ở `repository/`. Các phần sau ghi lại kế hoạch và điểm xuất phát của quá trình refactor.

## Điểm xuất phát và ưu tiên

- `BookingServiceImpl` khai báo trực tiếp ít nhất 19 repository. Cùng một lớp vừa tạo đơn, lập đội, báo giá, xử lý tiền, cập nhật hành trình, vừa trả dữ liệu tracking. Tách dần qua interface hiện có để controller và scheduler tiếp tục gọi được sau từng đợt.
- `BookingServiceImpl.updateTripProgress` và `DeliveryServiceImpl.completeHandover` đều ghi trạng thái `delivered_pending_settlement`. Đây là ứng viên đầu tiên để đối chiếu hai đường ghi. Trùng trạng thái đích chưa đủ để kết luận trùng quyền hoặc điều kiện đầu vào.
- `AuthServiceImpl.register` tạo hồ sơ khách khi đăng ký. Luồng tạo booking và luồng quản lý ngựa còn có nhánh tạo hồ sơ cho tài khoản hiện hữu. Cần kiểm tra dữ liệu cũ, giá trị mặc định và ràng buộc duy nhất trước khi hợp nhất.
- Các phát hiện trên dựa trên mã nguồn. Khả năng ghi đè đồng thời, tạo hồ sơ trùng và gửi thông báo hai lần là các tình huống cần kiểm thử.

## Lộ trình theo thứ tự triển khai

### Đợt 0 — Ghi nhận hành vi hiện tại

1. Lần từ controller, scheduler và các caller nội bộ đến từng phương thức sắp di chuyển. Ghi vào mô tả PR các đầu vào, vai trò, điều kiện sở hữu, trạng thái trước/sau, bảng được ghi, thông báo, audit và lỗi trả về. Kiểm tra cả overload, mã booking cũ, ID số và các cập nhật entity qua JPA dirty checking.
2. Đối chiếu endpoint và DTO với `frontend/src/shared/api/`, màn hình Customer Portal và Backoffice đang gọi. Chạy Quality Gate ở cuối tài liệu để xác định kết quả ban đầu.
3. Với mỗi thao tác có nhiều điểm vào, so sánh transaction, cách tải booking, khóa và hành vi khi gọi lại. Nếu hai đường đang cho kết quả khác nhau, ghi rõ khác biệt và tách quyết định thay đổi nghiệp vụ khỏi PR di chuyển code.

### Đợt 1 — Tách phần đọc và xác nhận bàn giao

| Lớp | Việc thực hiện | Điều kiện nghiệm thu |
| --- | --- | --- |
| BookingServiceImpl | Chuyển truy vấn danh sách, phân trang, đội phụ trách và tracking sang thành phần đọc. Sau đó lần riêng các nhánh bàn giao của `updateTripProgress` đến phần ghi log, chứng từ, vị trí ngựa và booking. | So sánh phân trang, quyền xem và trường trả về của khách, nhân viên và người xem tracking công khai. |
| TripReportServiceImpl | Giữ `findAll` là truy vấn tổng hợp booking, nhân sự và sự cố. Chỉ dùng chung phép ánh xạ khi hai DTO có cùng ý nghĩa và quyền hiển thị. | Báo cáo giữ đúng số chuyến, dữ liệu nhân sự và sự cố cho cùng bộ dữ liệu. |
| DeliveryServiceImpl | Đối chiếu `arriveDelivery`, `completeHandover`, `reportDeliveryTimeout`, `returnToHq` với các nhánh tương ứng bên booking. Đưa phần chuyển trạng thái và tác dụng phụ thực sự giống nhau vào một thao tác dùng chung, giữ kiểm tra đặc thù tại từng điểm vào. | Chạy hai đường bàn giao riêng, gọi lặp và gọi đồng thời trên cùng booking. Kiểm tra mốc chờ 2 giờ, log và thời điểm xe/kíp được giải phóng sau khi về HQ. |

### Đợt 2 — Tài khoản, hồ sơ khách và ngựa

| Lớp | Việc thực hiện | Điều kiện nghiệm thu |
| --- | --- | --- |
| AuthServiceImpl | Giữ đăng ký, xác thực, refresh token và xác minh email trong luồng auth. Đối chiếu nhánh tạo Customer với booking và ngựa, trích phần khởi tạo chung nếu mặc định tương thích. | Đăng ký thành công tạo đúng một hồ sơ. Lỗi tạo hồ sơ rollback phần tài khoản theo transaction hiện hành. |
| AdminAccountServiceImpl | Giữ cập nhật User, hồ sơ theo role và audit trong cùng thao tác quản trị. Kiểm tra caller của các overload đổi trạng thái và reset mật khẩu trước khi gom logic. | Quyền quản trị, trạng thái tài khoản và danh tính người ghi audit khớp ở từng overload. |
| CustomerProfileServiceImpl | Phân biệt tra cứu thống kê ngựa/booking với cập nhật profile và ngôn ngữ. Tái sử dụng cách tìm hồ sơ đã xác định ở trên khi cùng yêu cầu về tài khoản thiếu hồ sơ. | Kiểm tra khách mới, khách có lịch sử, quyền cập nhật và giá trị ngôn ngữ. |
| PasswordResetServiceImpl | Giữ xác minh OTP, cập nhật số lần thử, đổi mật khẩu và tiêu thụ OTP trong luồng reset. Kiểm tra hiệu lực transaction khi phương thức ném lỗi. | OTP sai, hết hạn, dùng lại và đổi mật khẩu thành công cho kết quả như hợp đồng hiện tại. |
| HorseServiceImpl | Chuyển nhánh tìm/tạo Customer sang logic chung với booking. Nhóm đọc lịch sử riêng với cập nhật vị trí và xóa ngựa. Lần caller của cả `deleteById` và `deleteHorse`. | Tài khoản cũ thiếu hồ sơ vẫn sử dụng được. Kiểm tra quyền sở hữu và ràng buộc ngựa đã tham gia đơn ở từng đường xóa. |
| HorseDocumentServiceImpl | Giữ xác minh chủ ngựa, upload, review và xóa chứng từ ở luồng tài liệu. Đối chiếu thứ tự ghi DB và thao tác Cloudinary, cả khi một bước lỗi. | Kiểm tra khách khác chủ, hai overload review, upload lỗi và kết quả khi xóa tệp lỗi. |
| HorseHealthLogServiceImpl | Gom logic chung giữa các overload `updateHorseHealthStatusByEscort`, giữ liên kết ngựa, booking, nhân viên và nhật ký. | Kiểm tra nhân viên được phân công, lịch sử theo ngựa và tra cứu theo mã booking. |
| HorseTypeServiceImpl | Giữ cập nhật loại ngựa và giá trị bảo hiểm tại đây. Đối chiếu quy tắc tính bảo hiểm dùng ở bước thẩm định với `HorseInsurancePolicy`. | Kiểm tra xóa loại đang được dùng và đơn hiện hữu sau khi thay đổi giá trị bảo hiểm. |
| CloudinaryStorageServiceImpl | Giữ adapter upload/delete dùng chung cho caller. Lập bảng ánh xạ lỗi và định danh file mà luồng chứng từ cần trước khi đổi thứ tự thao tác. | Dùng storage giả lập thành công/lỗi để xác nhận caller nhận đúng kết quả và có thể xử lý file còn sót. |

### Đợt 3 — Danh mục, cấu hình, lập tuyến và thẩm định

| Lớp | Việc thực hiện | Điều kiện nghiệm thu |
| --- | --- | --- |
| ServiceCatalogServiceImpl | Giữ Service và Category trong thao tác quản lý danh mục. Tách riêng việc kiểm tra sinh ID bằng `findMaxId() + 1` khi tạo đồng thời. | Kiểm tra mã trùng, hai lần tạo song song, tra cứu theo mã và theo ID cũ từ booking. |
| LocationServiceImpl | Giữ quyền ghi địa điểm và đổi trạng thái tại đây. Rà cách route và booking đọc địa điểm ngừng hoạt động. | Tuyến lịch sử vẫn đọc đúng điểm dừng, danh sách chọn mới tuân theo trạng thái hiện hành. |
| RouteServiceImpl | Giữ cập nhật Route và thứ tự LocationRoute trong một transaction. Lần caller của CRUD cơ bản và các phương thức quản lý điểm dừng. | Cập nhật nhiều điểm, lỗi giữa chừng và vô hiệu hóa tuyến đã sử dụng. |
| SystemConfigServiceImpl | Đối chiếu mặc định, đơn vị và chuyển kiểu số với `service/impl/RoutePreviewServiceImpl`, nhất là `MAX_CONTINUOUS_HOURS` và `MIN_REST_MINUTES`. Tái sử dụng cách đọc có kiểu nếu nhiều caller diễn giải cùng khóa. | So sánh kết quả JourneyPlanner trước/sau cho cấu hình mặc định, đã sửa và thiếu khóa. |
| DocumentRequirementServiceImpl | Giữ quản lý mẫu yêu cầu tại đây. Dùng `DocumentRequirementPolicy` và `ClearanceDocumentPolicy` nơi phù hợp khi booking và specialist áp dụng mẫu. | Kiểm tra yêu cầu đang hoạt động, theo địa điểm và hồ sơ chuyến đã lập trước khi đổi mẫu. |
| FleetServiceImpl | Dùng chung quy tắc khả dụng giữa bước xem xe/kíp và bước phân công. Kiểm tra lại trong transaction phân công để xử lý hai đơn chọn cùng tài nguyên. | Hai yêu cầu trùng lịch, tài nguyên đang quay về HQ và tìm theo ID/mã trả kết quả đúng. |
| SpecialistServiceImpl | Nhóm thao tác theo duyệt y tế, bảo hiểm và clearance. Giữ quy tắc tài liệu riêng từng bước. Đối chiếu thao tác hoàn thành duyệt với bước điều phối trong booking. | Hai thứ tự hoàn thành Specialist/Coordinator đều hợp lệ. Báo giá chỉ mở sau khi cả hai đã xong. |

Trong đợt này, di chuyển phần lập kế hoạch, phân công và duyệt báo giá tương ứng ra khỏi BookingServiceImpl sau khi các quy tắc dùng chung đã có caller và test. Kiểm tra việc giữ phân công nếu bước duyệt song song hoàn tất cùng lúc.

### Đợt 4 — Tiền, sự cố và quyết toán

| Lớp | Việc thực hiện | Điều kiện nghiệm thu |
| --- | --- | --- |
| VnpayServiceImpl | Giữ tạo URL và xác minh dữ liệu VNPay tại adapter. Lần đường callback sang cập nhật payment và booking, đối chiếu với xác nhận tiền trong BookingServiceImpl. | Callback lặp, sai số tiền, thất bại và đến sau khi báo giá hết hạn. Kiểm tra số payment và số lần phát hành vận đơn. |
| PaymentServiceImpl | Nhóm hủy, hoàn và đối soát bảo hiểm theo thao tác. Xác định nơi áp dụng khoản cọc/số dư vào booking sau khi xác minh thanh toán. Rà caller của `save` và `deleteById` trước khi thu hẹp interface. | Kiểm tra cọc 30%, số dư 70%, hủy trước/sau cọc, hoàn lặp, đối soát và khóa booking. |
| IncidentServiceImpl | Giữ vòng đời sự cố và chi phí tại đây. Đối chiếu cách khôi phục trạng thái booking khi còn nhiều sự cố mở. Dùng cùng `IncidentCostPolicy` với quyết toán. | Hai sự cố cùng chuyến, duyệt/từ chối phương án, xóa chi phí và tiếp tục hành trình sau xử lý. |
| SettlementServiceImpl | Gom tính toán chi phí và phát hành quyết toán. Tách phần chạy cho dữ liệu cũ của `migrateLegacySettlements` khỏi đường xử lý yêu cầu mới nếu dùng quy tắc đầu vào khác. | Chi phí sự cố được trừ đúng một lần. Chạy lại migration giữ nguyên số tiền. Kiểm tra quyền đánh giá sau hoàn tất. |
| NotificationServiceImpl | Giữ các thao tác đọc, đánh dấu, xóa theo người nhận. Kiểm kê điểm tạo thông báo trong booking và các luồng vừa tách, gom phần tạo chung nếu người nhận và thời điểm ghi giống nhau. | Khách chỉ thao tác trên thông báo của mình. Callback hoặc xác nhận bàn giao lặp tạo đúng số thông báo theo nghiệp vụ. |

Kết thúc đợt này, rà lại các phương thức còn trong BookingServiceImpl. Chuyển từng luồng tạo đơn, vòng đời báo giá, xác nhận tiền và cập nhật hành trình sang thành phần đã xác định trách nhiệm. Chuyển cả scheduler hết hạn báo giá và quá hạn quyết toán qua cùng thao tác nghiệp vụ tương ứng. Chỉ bỏ dependency sau khi đã lần hết caller.

## Transaction, tương thích và kiểm thử

1. Đặt transaction ở entry point ghi của từng use case. Khi tách bean, kiểm tra Spring proxy, rollback, khóa `findByIdForUpdate` và thứ tự khóa giữa callback, scheduler, API. Tránh việc hai service gọi ngược nhau bằng cách đặt phần dùng chung ở lớp mà cả hai có thể gọi.
2. Dùng test sẵn có làm mốc. Đợt 1 chạy `DeliveryServiceImplTest`, `DeliveryTimeoutTest`, `BookingEndpointsAccessTest`. Đợt 2–3 chạy `BookingWithSubmittedDocumentsTest`, `BookingScheduleFlowTest`, `BookingServiceImplTest`. Đợt 4 chạy `DepositPaymentConcurrencyTest`, `VnpayServiceImplTest`, `PaymentServiceImplTest`, `SettlementServiceImplTest`. Bổ sung test có mục tiêu khi thiếu ca nêu ở bảng, đặc biệt với hai transaction đồng thời trên PostgreSQL.
3. Đối chiếu HTTP status, response envelope, phân trang, quyền và DTO ở cả hai portal. Chạy thử hành trình từ tạo đơn tới trở về HQ và quyết toán, gồm một nhánh sự cố và một nhánh timeout. Xác nhận cọc cấp vận đơn, thu đủ số dư trước xuất phát và công ty chịu chi phí chờ bàn giao.
4. Mỗi PR triển khai chạy Quality Gate theo [hướng dẫn dự án](../frontend/docs/AGENT_GUIDE.md). Trong `backend/` dùng Java 21 và PostgreSQL của dự án, chạy `.\mvnw.cmd verify`. Trong `frontend/` lần lượt chạy `npm run lint`, `npx tsc -b`, `npm test`, `npm run build`.
5. Chia PR theo một thao tác có thể kiểm chứng độc lập, giữ interface cũ làm điểm gọi trong quá trình di chuyển. Nếu cần thay đổi schema, API hoặc quy tắc nghiệp vụ, ghi rõ tác động và phương án chuyển dữ liệu trong đề xuất riêng. Với PR chỉ di chuyển logic, có thể hoàn tác cả PR khi phát hiện hành vi lệch.

## Phạm vi kiểm chứng

- Duyệt y tế và chốt phương án xe cùng khóa booking khi ghi detailsJson. Khi một xe hoàn tất bàn giao, DeliveryService chỉ chuyển trạng thái toàn đơn sau khi tất cả xe đã giao.
- DTO của dịch vụ danh mục giữ mã dịch vụ khi cập nhật. DTO cho tuyến cố định phân biệt tạo mới và cập nhật từng trường. Service giữ trách nhiệm thẩm định dữ liệu và ghi audit trong transaction.
- Các hạng mục cần kiểm chứng tiếp gồm gọi lặp API bàn giao cũ, hai request cùng khởi tạo hồ sơ khách, hai booking cùng chọn tài nguyên và sinh ID danh mục song song. Tách riêng các thay đổi nghiệp vụ hoặc schema phát sinh từ những ca này theo hướng dẫn dự án.

## Rà soát trách nhiệm ngoài package service

| Thành phần | Phân công sau khi refactor |
| --- | --- |
| Hiển thị booking | `BookingVisibilityService` tập trung quy tắc lọc ảnh, phí cầu đường và trường nội bộ cho khách. Booking query, bàn giao, quyết toán và chuyên viên dùng cùng service. Mỗi endpoint vẫn dựng những trường thuộc hợp đồng phản hồi của mình. |
| Xác thực JWT | `JwtAuthenticationFilter` giao phép kiểm tra nhân viên bị vô hiệu hóa cho `AuthService`. Filter phụ trách JWT và Spring Security context. |
| Dọn OTP | `ExpiredOtpCleanup` kích hoạt lịch. `EmailVerificationService` sở hữu transaction dọn bản ghi hết hạn và khoảng đệm 60 giây. |
| Đọc chi tiết booking | Booking, booking query, hành trình và chuyên viên dùng `BookingDetailsJson.readWithMedicalDefaults` khi cần trạng thái y tế mặc định. `BookingTripRules` giữ tính toán checkpoint dùng chung cho cả luồng đọc và ghi. |
| Tra cứu biên giới | `TerritoryLookupServiceImpl` quản lý dữ liệu biên giới đã nạp và xác định quốc gia của một tọa độ theo độ sâu tối thiểu. `RoutePreviewService` gọi qua interface. |

`ApiResponseDto` là cấu trúc phản hồi HTTP. `GeoPointDto` và `RoadRouteDto` biểu diễn dữ liệu. `Polyline` và `RouteGeometry` thực hiện tính toán. Seeder và migration trong `config` có vòng đời khởi tạo riêng. Các lớp này có lý do tồn tại ngoài service. Không phát hiện tham chiếu repository trong controller và DTO khi rà mã nguồn Java.

### Kết quả Quality Gate

Các lớp có file riêng trong `dto/` dùng hậu tố `Dto`. DTO đầu vào dùng `RequestDto`, DTO phản hồi dùng `ResponseDto`. `ContactPatterns` chứa các biểu thức kiểm tra dữ liệu và nằm trong `util/`. Trước khi tích hợp `origin/main` tại `1cdb7ee`, việc đổi tên đã được đối chiếu qua `/v3/api-docs`, toàn bộ `paths` và 178 schema trùng với bản trước refactor DTO.

- Backend qua `.\mvnw.cmd clean verify` trên PostgreSQL Testcontainers. Maven ghi nhận 618 test, 617 đạt và `RoutingLiveTest` được bỏ qua.
- Frontend qua `npm run lint`, `npx tsc -b`, `npm test` và `npm run build`. Vitest đạt 325/325. Lint còn cảnh báo.
