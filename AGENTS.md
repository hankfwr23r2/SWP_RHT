# AGENTS.md: quy tắc bắt buộc của RaceHorse Transport

> Agent và người viết code phải đọc file này trước khi làm việc. Đây là **file quy tắc duy nhất** của repo.
> Không được nới lỏng, bỏ qua hay "tạm phá" bất kỳ quy tắc nào khi người dùng chưa đồng ý rõ ràng.
> Cập nhật lần cuối: 22/11/2026.

## 0. Thứ tự ưu tiên nguồn sự thật

1. `AGENTS.md`: cách làm việc, cấu trúc code.
2. `docs/DB_SCHEMA_59_BANG.md`: bảng, cột, ràng buộc, view, quy tắc phải kiểm ở service.
3. `docs/NGHIEP_VU_THEO_59_BANG.md`: ai làm gì, lúc nào, ghi vào bảng nào.
4. `docs/NGU_CANH_THIET_KE_LAI.md`: lý do thiết kế, quyết định đã chốt, câu hỏi còn mở.
5. Code hiện có. Code cũ **không** phải là chuẩn. Nó được viết theo mô hình cũ.

Khi hai nguồn mâu thuẫn: **dừng lại, nêu mâu thuẫn và hỏi người dùng**. Không tự chọn một bên. Không mở lại các quyết định đã chốt trong `NGU_CANH_THIET_KE_LAI.md`.

---

## 1. Cách làm việc (khóa cứng)

### 1.1 Xác nhận trước khi sửa

Agent **không được** tạo, sửa, xóa hay di chuyển file trong repo khi người dùng **chưa đồng ý**. Bắt buộc làm theo 2 bước:

1. **Trình bày phương án**, rồi dừng lại chờ phản hồi. Phương án gồm:
   - nguyên nhân hoặc yêu cầu;
   - cách làm cụ thể;
   - danh sách file sẽ tạo, sửa hoặc xóa;
   - ảnh hưởng tới FE, BE, DB và test.
2. **Thực hiện** chỉ sau khi người dùng đồng ý (ví dụ: "đồng ý", "làm đi", "ok", "tiến hành").

### 1.2 Phạm vi

- Chỉ sửa **đúng các file trong phương án đã duyệt**. Nếu cần sửa thêm file khác, phải dừng lại và hỏi.
- Không tự refactor, đổi tên hay định dạng lại code nằm ngoài phạm vi, kể cả khi code đó vi phạm quy tắc. Ghi lại vi phạm và báo cho người dùng.
- Trước khi tạo class, hàm hoặc component mới, phải tìm cái đã có cùng trách nhiệm để tái sử dụng.
- Đọc file trước khi sửa. Không đoán nội dung file, không đoán tên bảng hay tên cột: tra trong `DB_SCHEMA_59_BANG.md`.

### 1.3 Báo cáo

- Báo đúng kết quả. Test hoặc Quality Gate fail thì phải nói rõ và dán lỗi. Bước nào bỏ qua thì nói là bỏ qua.
- Không báo "đã xong" khi chưa chạy kiểm tra ở mục 10.
- Thông tin chưa kiểm chứng (pháp lý, API bên ngoài, số liệu) phải ghi rõ là **chưa kiểm chứng**.

### 1.4 Git

- Không commit, push, merge hay tạo PR khi người dùng chưa yêu cầu.
- Không làm việc trực tiếp trên `main`. Tên nhánh:
  - `feat/<tên>` cho tính năng mới;
  - `fix/<tên>` cho sửa lỗi;
  - `docs/<tên>` cho tài liệu;
  - `chore/<tên>` cho cấu hình.
- Commit theo Conventional Commits (`feat: …`, `fix: …`, `docs: …`). **Không** thêm dòng `Co-Authored-By` hay "Generated with …".
- Không commit bí mật: `.env`, mật khẩu, khóa JWT, token GitHub, khóa VNPAY hay Cloudinary.
- Không dùng `--no-verify`, `push --force` lên nhánh dùng chung, hay `reset --hard` khi chưa hỏi.

---

## 2. Bối cảnh dự án

- **Monorepo:**
  - `backend/` dùng Java 21, Spring Boot, Spring Security (JWT + refresh token trong cookie HttpOnly), JPA, PostgreSQL 15 trở lên. Context path là `/api/v1`.
  - `frontend/` dùng React 19, TypeScript, Vite, React Router, Vitest. Có 2 ứng dụng: khách hàng (`/`) và nhân viên (`/backoffice`).
- **Mô hình nghiệp vụ** (chi tiết ở `NGHIEP_VU_THEO_59_BANG.md`):
  - **Gom hàng qua hub:** chặng đón → tập kết ở hub → chuyến trục định kỳ (`departure`) → lưu trú hoặc cách ly → chặng giao.
  - **Trục nội địa** (hub ⇄ hub) và **trục quốc tế** (hub → cửa khẩu, rồi bàn giao cho đại lý).
  - **Trụ sở và hub vệ tinh:** mọi xe và nhân viên thuộc trụ sở. Xe về **hub gốc** (`home_hub_id`) thì mới rảnh.
  - Hub đón trùng hub giao thì **chạy thẳng** (`DIRECT`). Mỗi đơn có tối đa một chặng trục.
  - Ba lớp không được trộn:
    - thương mại: `booking`, `quotation`, `payment`;
    - vận hành xe: `vehicle_trip`, `trip_stop`;
    - theo dõi từng con ngựa: `horse_leg`, `horse_stay`, `handover`.
  - `booking.status` chỉ có **12 mốc thương mại**. Tiến độ vận hành **suy ra** từ chặng, lưu trú, giao nhận và sự cố. Không thêm trạng thái vận hành vào `booking`.

---

## 3. Cấu trúc backend (n-layer)

### 3.1 Chiều phụ thuộc

```
Controller  →  Service  →  Repository  →  Database
    ↓             ↓
   DTO  →  Entity        Policy / Client
```

- Chỉ được gọi xuống tầng dưới. Không gọi ngược lên, không nhảy tầng.
- `Controller` **không** gọi `Repository`, `Policy` hay `Client`. Controller chỉ nhận và trả DTO, **không** trả Entity ra API.
- `Repository` chỉ truy vấn, không chứa logic nghiệp vụ.
- `Policy` là **hàm thuần**:
  - không inject Spring bean;
  - không gọi repository;
  - không đọc giờ hệ thống: nhận `Instant` hoặc `Clock` qua tham số.
- `DTO` được import `Entity` để chuyển đổi. **Entity không bao giờ import DTO.**

### 3.2 Package và tên file

Gốc package: `backend/src/main/java/com/example/racehorse_transport/`.

| Package | Hậu tố bắt buộc | Chứa | Không được chứa |
|---|---|---|---|
| `controller/` | `*Controller` | Nhận HTTP, `@Valid` DTO, lấy người dùng từ `Authentication`, gọi **một** service, trả `ApiResponseDto` | Logic nghiệp vụ, `if` theo trạng thái, tính toán, gọi repository |
| `service/` | `*Service` | Interface: hợp đồng nghiệp vụ | Code triển khai |
| `service/impl/` | `*ServiceImpl` | Điều phối use case: kiểm quyền nghiệp vụ, gọi policy và repository, chuyển đổi DTO, quản lý transaction | Công thức chi tiết (đặt ở `policy/`), gọi HTTP trực tiếp (đặt ở `client/`) |
| `policy/` | `*Policy` | Quy tắc nghiệp vụ thuần: tính giá, điều kiện chuyển trạng thái, sức chứa xe, chọn cách chạy | Spring bean, repository, I/O |
| `repository/` | `*Repository` | Interface Spring Data JPA, `@Query` | Logic nghiệp vụ, `default` method có tính toán |
| `entity/` | Tên thực thể, không hậu tố | Ánh xạ bảng. Enum đặt ở `entity/enums/` | Logic nghiệp vụ, tách chuỗi để suy ra thuộc tính, import DTO |
| `dto/<feature>/` | `*RequestDto`, `*ResponseDto` | Dữ liệu vào/ra API, annotation validate, method chuyển đổi (mục 5) | Logic nghiệp vụ, truy vấn DB, tính tiền |
| `client/` | `*Client` | Gọi API bên ngoài: Valhalla, mail, VNPAY, Cloudinary, Turnstile | Logic nghiệp vụ |
| `scheduler/` | `*Scheduler` | Kích hoạt định kỳ, chỉ gọi service | Logic nghiệp vụ |
| `security/` | | JWT, filter, cấu hình phân quyền | Nghiệp vụ |
| `exception/` | `*Exception`, `GlobalExceptionHandler` | Exception nghiệp vụ, xử lý lỗi tập trung | |
| `config/` | `*Config`, `*Properties` | Cấu hình Spring bean và thuộc tính | **Migration, seed dữ liệu, sửa lược đồ**, nghiệp vụ |
| `util/` | | Tiện ích không mang nghiệp vụ: định dạng, chuỗi, ngày, hình học | Quy tắc nghiệp vụ, tính tiền, kiểm trạng thái |

- **Không** có package `mapper/`. **Không** dùng ModelMapper.
- Class biết về nghiệp vụ thì **không** được đặt trong `util/` hay `config/`.

---

## 4. Cơ sở dữ liệu

- **DB-first:** bảng phải có trong `DB_SCHEMA_59_BANG.md` trước, sau đó mới viết entity. Entity phải khớp DB, không làm ngược lại.
- Mọi thay đổi lược đồ đi qua **Flyway**: `backend/src/main/resources/db/migration/V<n>__<mo_ta>.sql`. Dữ liệu nền dùng file seed của Flyway.
- **Cấm:**
  - sửa lược đồ hoặc seed dữ liệu bằng class Java chạy lúc khởi động (`*Migration.java`, `*Seeder.java`, `CommandLineRunner` chạy DDL);
  - đổi `spring.jpa.hibernate.ddl-auto` khỏi `validate`;
  - sửa file migration đã merge (phải tạo file mới).
- Đổi lược đồ thì cập nhật `DB_SCHEMA_59_BANG.md` trong cùng thay đổi. ERD được **sinh từ DB thật**, không vẽ tay.
- **Cấm lưu dữ liệu nghiệp vụ dạng JSON/TEXT** (danh sách, trạng thái, tiền, tham chiếu). Chỉ có 2 ngoại lệ: `payment_transaction.raw_payload` và `audit_log.old_data/new_data`.
- Mọi tham chiếu tới bản ghi khác **phải là FK** (`@ManyToOne` + `@JoinColumn`). Không lưu ID hay tên dạng chuỗi.
- Trạng thái và loại dùng Java enum với `@Enumerated(EnumType.STRING)`, kèm `CHECK` trong migration.
- Không lưu giá trị suy ra được (tổng, số đã trả, số khoang còn trống). Dùng view trong `DB_SCHEMA_59_BANG.md`. Snapshot có chủ đích phải ghi comment `// snapshot: <lý do>`.
- Quy ước:
  - tên bảng và cột dùng `snake_case`, tên bảng số ít;
  - PK là `bigint` identity ↔ `Long`;
  - `timestamptz` ↔ `Instant`, `date` ↔ `LocalDate`;
  - tiền là `numeric(15,2)` ↔ `BigDecimal`.
- Field quan hệ đặt theo tên đối tượng (`private Party customer;`), **không** `customerId`.
- Quy tắc cần so nhiều bảng (mục "Ràng buộc phải kiểm ở service" trong `DB_SCHEMA_59_BANG.md`) phải có trong `policy/` hoặc service, và phải có test.

---

## 5. DTO và chuyển đổi Entity ↔ DTO

- **Response DTO** có `static from(Entity)`, và `fromList(List<Entity>)` khi cần.
  - Chỉ đọc field của entity, không gọi repository, không tính nghiệp vụ.
  - Giá trị cần tính thì service tính trước rồi truyền vào làm tham số.
  - Chỉ đọc quan hệ LAZY đã được service nạp (fetch join hoặc `@EntityGraph`).
  - Không có `toEntity()`.
- **Request tạo mới** có `toEntity(...)` trả về entity mới, chưa lưu.
- **Request cập nhật** có `applyTo(Entity, ...)`, ghi lên entity đã nạp. Không tạo entity mới khi cập nhật.
- Entity liên quan (FK) do service nạp rồi truyền vào. **DTO không tự tra cứu.**
- **Cấm gán từ request:** `id`, trạng thái nghiệp vụ, người tạo/sửa, thời điểm tạo/sửa, mọi số tiền. Các field này do service hoặc policy đặt.
- Validate định dạng bằng annotation (`@NotNull`, `@NotBlank`, `@Size`, `@Pattern`). Quy tắc nghiệp vụ đặt trong policy.
- Request và Response là hai class riêng. Tạo mới và cập nhật có tập field khác nhau thì dùng hai request DTO riêng.

```java
@Getter @Builder
public class VehicleResponseDto {
    private Long id;
    private String licensePlate;
    private String vehicleClassCode;

    public static VehicleResponseDto from(Vehicle v) {
        return VehicleResponseDto.builder()
                .id(v.getId())
                .licensePlate(v.getLicensePlate())
                .vehicleClassCode(v.getVehicleClass().getCode())
                .build();
    }
}

@Getter @Setter
public class VehicleCreateRequestDto {
    @NotBlank @Size(max = 20) private String licensePlate;
    @NotNull private Long vehicleClassId;

    public Vehicle toEntity(VehicleClass vehicleClass) {
        Vehicle v = new Vehicle();
        v.setLicensePlate(licensePlate.trim().toUpperCase());
        v.setVehicleClass(vehicleClass);
        return v;                 // status do service đặt
    }
}
```

---

## 6. Tiền và quy tắc nghiệp vụ

- **Backend là nguồn sự thật duy nhất** cho mọi con số có hệ quả tiền: giá, cọc, số dư, hoàn tiền, phụ phí, điều chỉnh.
- Request từ client chỉ chứa **lựa chọn đầu vào**: chuyến, ngựa, dịch vụ, địa chỉ, khoản điều chỉnh kèm lý do. **Cấm** nhận `amount`, `total`, `subtotal`, `unitPrice` từ client làm dữ liệu chính thức.
- Mỗi công thức có **đúng một nơi**, trong `policy/`. Frontend muốn xem trước thì gọi endpoint preview.
- Tham số giá nằm trong `price_list`/`price_item`, có thời hạn hiệu lực. Không đặt ở `system_config`, không hard-code.
- Mọi lần chuyển trạng thái (đơn, báo giá, chặng, lượt xe, giao nhận, sự cố) phải qua policy kiểm tra hợp lệ. Đổi `booking.status` thì ghi `booking_status_history` trong cùng transaction.
- Hằng số nghiệp vụ (ngưỡng, số phút chờ, tỷ lệ) lấy từ DB (`system_config`, `price_list`), không hard-code.

---

## 7. Service và API

### 7.1 Service

- Mỗi `*ServiceImpl` phục vụ một nhóm use case. Vượt **500 dòng** hoặc **10 dependency** thì phải đề xuất tách trước khi thêm code.
- Method ghi có `@Transactional`. Method chỉ đọc có `@Transactional(readOnly = true)`.
- Lấy thời gian qua `Clock` được inject. Không gọi `Instant.now()` hay `LocalDate.now()` trực tiếp.
- Đặt chỗ vào `departure` phải khóa dòng `departure` (`SELECT … FOR UPDATE`) trước khi đọc `v_departure_capacity`.
- Ném exception riêng trong `exception/` (ví dụ `ResourceNotFoundException`). Không ném `RuntimeException` chung chung, không trả `null` để báo lỗi.

### 7.2 API

- `@RequestMapping` chỉ ghi đường dẫn tương đối (`/bookings`). **Không** ghi `/api/...`, vì context path đã là `/api/v1`.
- Thiết kế theo **resource** (danh từ số nhiều): `/bookings/{id}`, `/vehicle-trips/{id}/stops/{stopId}`.
- Hành động chuyển trạng thái dùng `POST /resources/{id}/<dong-tu>`, ví dụ `/quotations/{id}/send`. Mỗi hành động có một endpoint và một request DTO.
- **Cấm** endpoint nhận chuỗi `action` rồi `switch` để rẽ nhánh.
- Mọi request body có DTO riêng và `@Valid`. Mọi response bọc trong `ApiResponseDto` (`status`, `message`, `data`).
- Người thao tác lấy từ `Authentication`, **không** lấy từ body.
- Mọi endpoint có `@PreAuthorize` hoặc được khai báo trong `SecurityConfig`. Không dựa vào việc frontend ẩn nút.
- Hợp đồng giữa hai đầu là **OpenAPI** do backend sinh ra.

---

## 8. Cấu trúc frontend

- `frontend/src/apps/customer`: ứng dụng khách.
- `frontend/src/apps/backoffice/features/<vai-trò>`: ứng dụng nhân viên, chia theo vai trò (`admin`, `coordinator`, `driver`, `escort`, `manager`, `specialist`).
- `frontend/src/shared/`: dùng chung (`api`, `auth`, `routing`, `i18n`, `ui`, `lib`).
- Mọi lời gọi API đi qua `shared/api/` và `apiClient`. Component không gọi `fetch` hay `axios` trực tiếp.
- Kiểu dữ liệu request/response **sinh từ OpenAPI** của backend, không viết tay. Đổi DTO ở backend thì sinh lại kiểu trong cùng thay đổi.
- **Cấm:**
  - tính giá, cọc, số dư hay phụ phí ở frontend;
  - tự suy trạng thái vận hành hoặc tự tính hạn chót (frontend hiển thị giá trị backend trả về);
  - rẽ nhánh code chạy thật theo môi trường test (`import.meta.env.MODE === 'test'`) hoặc giữ dữ liệu giả trong code chạy thật. Test thì mock ở tầng `apiClient`.
- Kiểm tra đầu vào ở frontend chỉ để báo lỗi sớm. Backend vẫn phải kiểm lại.
- Chuỗi hiển thị cho người dùng đi qua i18n (`shared/i18n`).

---

## 9. Bảo mật

- Access token gửi qua header `Authorization: Bearer`. Frontend làm mới token trước hạn 60 giây. Gặp 401 thì refresh rồi gửi lại request một lần.
- Refresh token chỉ nằm trong **cookie HttpOnly**. Axios bật `withCredentials: true`.
- Phân quyền theo vai trò ở backend (`@PreAuthorize`). Route của backoffice có route guard ở frontend.
- Mọi DTO đầu vào có Bean Validation. Lỗi trả qua `GlobalExceptionHandler`, không lộ stack trace.
- Callback cổng thanh toán phải kiểm chữ ký và chống ghi trùng bằng ràng buộc UK của `payment_transaction`.
- Không ghi log mật khẩu, token, OTP hay số giấy tờ tùy thân.

---

## 10. Kiểm thử và Quality Gate

### 10.1 Test bắt buộc

- Mỗi `*Policy` mới có unit test thuần, không cần Spring context.
- Mỗi `from()`, `toEntity()`, `applyTo()` mới có test, kiểm tra cả việc **không** gán các field bị cấm.
- Mỗi use case mới trong service có test cho luồng chính và ít nhất một luồng vi phạm quy tắc.
- Mỗi migration mới phải chạy được trên PostgreSQL thật (Testcontainers), và ứng dụng khởi động được với `ddl-auto=validate`.
- Không xóa, bỏ qua (`@Disabled`, `.skip`) hay nới lỏng test hiện có để code mới chạy qua.

### 10.2 Quality Gate

Phải xanh toàn bộ trước khi báo hoàn thành hoặc commit. CI (`.github/workflows/ci.yml`) chạy lại các bước này trên mọi PR.

```powershell
# Trong backend/ (cần Postgres local: docker compose up -d postgres)
.\mvnw.cmd verify

# Trong frontend/
npm run lint
npx tsc -b
npm test
npm run build
```

Nếu chỉ sửa tài liệu thì không cần chạy, nhưng phải nói rõ là không chạy.

### 10.3 Khóa cứng bằng test kiến trúc

Từ nhánh thiết kế lại (`redesign/system-v2`), backend có **test kiến trúc (ArchUnit)** chạy trong `mvnw verify`. Test này kiểm:
- hậu tố tên class theo package;
- chiều phụ thuộc giữa các tầng;
- entity không import DTO;
- controller không dùng repository;
- không dùng ModelMapper;
- không gọi `Instant.now()`/`LocalDate.now()` trong service và policy;
- không có `CommandLineRunner` chạy DDL.

Vi phạm thì build đỏ. **Không** được tắt, nới lỏng hay thêm ngoại lệ cho test này khi người dùng chưa đồng ý.

---

## 11. Danh sách cấm (tóm tắt)

1. Sửa file khi chưa được duyệt, hoặc sửa ngoài danh sách đã duyệt.
2. Logic nghiệp vụ trong `controller/`, `util/`, `config/`, `repository/`, `entity/`, `dto/`, hoặc ở frontend.
3. Controller gọi repository hoặc trả entity ra API.
4. Entity import DTO. DTO gọi repository. `toEntity()` gán id, trạng thái, người thao tác hoặc số tiền.
5. ModelMapper, package `mapper/`, chép field thủ công thay cho method chuyển đổi của DTO.
6. Sửa lược đồ ngoài Flyway. Seed bằng Java. Đổi `ddl-auto`. Sửa migration đã merge.
7. Lưu dữ liệu nghiệp vụ dạng JSON. Tham chiếu dạng chuỗi thay cho FK. Lưu cột dẫn xuất.
8. Nhận số tiền tính sẵn từ client. Một công thức viết ở hai nơi.
9. Hằng số nghiệp vụ hard-code.
10. Endpoint dạng `action` + `switch`.
11. Thêm trạng thái vận hành vào `booking.status`.
12. Commit bí mật. Commit, push hay merge khi chưa được yêu cầu.

## 12. Checklist trước khi báo hoàn thành

- [ ] Chỉ sửa các file đã được duyệt.
- [ ] Class mới đúng package, đúng hậu tố, không phụ thuộc ngược tầng (mục 3).
- [ ] Bảng và cột khớp `DB_SCHEMA_59_BANG.md`. Đổi lược đồ thì có file Flyway mới và đã cập nhật đặc tả (mục 4).
- [ ] Response DTO có `from()`. Request DTO có `toEntity()` hoặc `applyTo()`, không gán field bị cấm (mục 5).
- [ ] Số tiền tính ở backend, trong `policy/` (mục 6).
- [ ] Method ghi có `@Transactional`. Thời gian lấy qua `Clock` (mục 7).
- [ ] DTO đổi thì đã sinh lại kiểu ở frontend (mục 8).
- [ ] Có test theo mục 10.1. Quality Gate xanh, hoặc đã báo rõ phần nào không chạy hay bị fail.
