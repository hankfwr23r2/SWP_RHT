# AGENTS.md: quy tắc bắt buộc của RaceHorse Transport

> Agent và người viết code phải đọc file này trước khi làm việc. Đây là **file quy tắc duy nhất** của repo.
> Không được nới lỏng, bỏ qua hay "tạm phá" bất kỳ quy tắc nào khi người dùng chưa đồng ý rõ ràng.
> Cập nhật lần cuối: 24/11/2026.

## 0. Thứ tự ưu tiên nguồn sự thật

1. `AGENTS.md`: cách làm việc, cấu trúc code.
2. `docs/DB_SCHEMA_59_BANG.md`: bảng, cột, ràng buộc, dữ liệu dẫn xuất, quy tắc phải kiểm ở service.
3. `docs/NGHIEP_VU_THEO_59_BANG.md`: ai làm gì, lúc nào, ghi vào bảng nào.
4. `docs/NGU_CANH_THIET_KE_LAI.md`: lý do thiết kế, quyết định đã chốt, câu hỏi còn mở.
5. `docs/KE_HOACH_TRIEN_KHAI.md`: task, chủ sở hữu bảng, hợp đồng gọi chéo giữa hai luồng, quy trình cho agent. Nhận task nào thì đọc đúng task đó. **Agent luồng B** đọc thêm `docs/THONG_BAO_LUONG_B.md` trước khi bắt đầu task.
6. Code hiện có. Code cũ **không** phải là chuẩn. Nó được viết theo mô hình cũ.

Khi hai nguồn mâu thuẫn: **dừng lại, nêu mâu thuẫn và hỏi người dùng**. Không tự chọn một bên. Không mở lại các quyết định đã chốt trong `NGU_CANH_THIET_KE_LAI.md`.

**Nghiệp vụ khó quyết định** mà các nguồn trên chưa nói: tham khảo mô hình của IRT (International Racehorse Transport) hoặc Peden Bloodstock trước, kế đến là nghiệp vụ cũ đã có (nhánh `main`). Phương án phải nêu nguồn; không tìm được thì ghi **chưa kiểm chứng**. Việc tham khảo không thay bước xin duyệt ở mục 1.1.

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
  - **Gom hàng qua hub:** chặng đón → tập kết ở hub → chuyến trục định kỳ (`departure`) → lưu trú → chặng giao.
  - **Trục nội địa** (hub ⇄ hub) và **trục quốc tế** (hub → cửa khẩu, rồi bàn giao cho đại lý).
  - **Trụ sở và hub vệ tinh:** mọi xe và nhân viên thuộc trụ sở. Xe về **hub gốc** (`home_hub_location_id`) thì mới rảnh.
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
- **Không có view chứa logic nghiệp vụ.** Mọi phép tính nằm trong code.
- Cột dẫn xuất chỉ được lưu khi là **snapshot của chứng từ đã chốt** (ví dụ tiền của `quotation`) hoặc **bộ đếm có khóa bảo vệ** (ví dụ `departure.booked_stall_count`). Danh sách cột được phép nằm ở mục "Dữ liệu dẫn xuất" của `DB_SCHEMA_59_BANG.md`.
  - Mỗi cột dẫn xuất chỉ do **một service** ghi, trong cùng transaction với thao tác gốc. Entity ghi comment `// derived: <service ghi>`.
  - Giá trị đổi liên tục (đã trả, còn nợ, ngựa đang ở đâu, đủ giấy chưa) thì **tính khi đọc**: repository truy vấn tổng hợp, policy tính kết quả.
- Snapshot có chủ đích phải ghi comment `// snapshot: <lý do>`.
- Quy ước:
  - tên bảng và cột dùng `snake_case`, tên bảng số ít;
  - mọi định danh dùng tiếng Anh, theo quy tắc N1–N10 ở mục 0 của `DB_SCHEMA_59_BANG.md`. Code và lược đồ hiện hành đã đồng bộ tên theo đặc tả. Bảng đối chiếu tên cũ đã xóa. Không tự đổi tên khi người dùng chưa giao việc đồng bộ;
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

- Ngoại lệ cho mục 5 và 6: DTO cấu hình bảng giá của `ADMIN` và `MANAGER` được nhận `unitPrice`, `marginRate`, `fuelBufferRate` và `depositRate` để service kiểm tra và ghi.
- `SPECIALIST` được nhập phí và số tiền bảo hiểm theo chứng từ của công ty bảo hiểm, policy kiểm tra và service ghi.
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
- Đặt chỗ vào `departure` phải khóa dòng `departure` (`SELECT … FOR UPDATE`) rồi mới đọc và cập nhật `booked_stall_count`.
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

## 8. Frontend

React 19, TypeScript, Vite, React Router, Vitest. Có hai ứng dụng build riêng: khách hàng (`/`, `index.html`) và nhân viên (`/backoffice`, `backoffice.html`). Gói của khách không được chứa code của nhân viên.

### 8.1 Cấu trúc thư mục

```
frontend/src/
├─ apps/
│  ├─ customer/    main.tsx · routes.tsx · layouts/ · features/<tính-năng>/
│  └─ backoffice/  main.tsx · routes.tsx · layouts/ · features/<vai-trò>/<tính-năng>/
└─ shared/
   ├─ api/         generated/ · client.ts · <tài-nguyên>.ts
   ├─ ui/          component cơ bản, không biết nghiệp vụ
   ├─ components/  component nghiệp vụ dùng chung giữa các ứng dụng
   ├─ hooks/       hook dùng chung
   ├─ lib/         hàm thuần không mang nghiệp vụ
   ├─ auth/  routing/  i18n/  motion/
   ├─ styles/      tokens.css · base.css
   └─ config/      cấu hình runtime
```

| Thư mục | Chứa | Không được chứa |
|---|---|---|
| `apps/<app>/routes.tsx` | Bảng route: đường dẫn, trang (`lazyPage`), `loader`, vai trò, tiêu đề, layout | Logic hiển thị |
| `apps/<app>/layouts/` | Khung trang: header, sidebar, topbar, menu theo vai trò | Gọi API của tính năng |
| `apps/<app>/features/…/` | Một tính năng (mục 8.2) | Code dùng cho tính năng khác |
| `shared/api/generated/` | Kiểu dữ liệu và client **sinh từ OpenAPI** của backend | Bất kỳ chỉnh sửa tay nào. Sinh lại khi backend đổi DTO |
| `shared/api/client.ts` | Axios instance, token, refresh, xử lý 401, chuẩn hóa lỗi | Gọi endpoint cụ thể |
| `shared/api/<tài-nguyên>.ts` | Hàm gọi API mỏng, dùng kiểu trong `generated/` | Chuyển đổi dữ liệu mang nghiệp vụ, tự đặt mã hay trạng thái, giữ dữ liệu giả |
| `shared/ui/` | Component cơ bản có biến thể: `Button`, `Input`, `Select`, `Modal`, `ConfirmDialog`, `Table`, `Badge`, `Card`, `Tabs`, `Stepper`, `Skeleton`, `EmptyState`, `ErrorState`, `Toast` | Import `api`, `components`, kiểu nghiệp vụ |
| `shared/components/` | Component nghiệp vụ **cả hai ứng dụng** cùng dùng: bảng báo giá, timeline hành trình ngựa, chọn địa chỉ, bản đồ | Component chỉ một tính năng dùng (đặt ở tính năng đó) |
| `shared/hooks/` | `useLoad`, `useAutoRefresh`, `useNow`, `usePagination`… | Hook gắn với một tính năng |
| `shared/lib/` | Định dạng tiền và ngày, xử lý chuỗi, hình học bản đồ, kiểm tra đầu vào sớm | Tính giá, cọc, hạn chót, trạng thái, mọi quy tắc nghiệp vụ |
| `shared/config/` | Biến môi trường, dữ liệu khởi động | Bảng giá, quy định, danh mục địa lý chép cứng (lấy từ API) |
| `shared/styles/` | `tokens.css` (design token), `base.css` (reset, kiểu chữ nền) | Style riêng của một trang |

**Không có** `shared/services/` hay `shared/types/`. Kiểu dữ liệu API lấy từ `shared/api/generated/`. Kiểu chỉ dùng ở frontend đặt cạnh nơi dùng.

### 8.2 Cấu trúc một tính năng

```
features/<vai-trò>/<tính-năng>/
├─ XxxPage.tsx        trang: nhận dữ liệu từ loader hoặc hook, ghép component
├─ components/        component riêng của tính năng
├─ useXxx.ts          hook riêng (state, gọi api, xử lý form)
├─ loaders.ts         loader của route
├─ Xxx.module.css     style riêng
└─ *.test.ts(x)       test đặt cạnh file được test
```

- Mỗi file trang tối đa **300 dòng**, mỗi component tối đa **200 dòng**. Vượt ngưỡng thì phải tách component hoặc hook trước khi thêm code.
- Một tính năng chỉ có `components/` khi có từ 3 component riêng trở lên. Ít hơn thì đặt cạnh file trang.
- Tên file: component và trang dùng `PascalCase.tsx`; hook dùng `useXxx.ts`; hàm thường dùng `camelCase.ts`; style dùng `Xxx.module.css`.

### 8.3 Chiều phụ thuộc (khóa bằng lint)

```
apps/<app>/features  →  shared/components  →  shared/ui
         ↓                     ↓
     shared/api, shared/hooks, shared/lib, shared/i18n
```

- `apps/customer` và `apps/backoffice` **không** import lẫn nhau.
- Tính năng **không** import tính năng khác. Cần dùng chung thì chuyển lên `shared/components` hoặc `shared/hooks`.
- `shared/*` **không** import `apps/*`. `shared/ui` **không** import `shared/api` hay `shared/components`.
- Component **không** gọi API trực tiếp. Trang lấy dữ liệu qua `loader` hoặc hook, rồi truyền xuống bằng props.
- Mọi lời gọi HTTP đi qua `shared/api/`. Không gọi `fetch` hay `axios` ở chỗ khác.
- Import bằng alias `@shared/...`, `@customer/...` (trỏ tới `apps/customer`) và `@backoffice/...` (trỏ tới `apps/backoffice`), không dùng đường dẫn tương đối quá một cấp (`../../`).

### 8.4 Dữ liệu và nghiệp vụ

- Frontend **chỉ hiển thị** giá trị backend trả về. **Cấm**:
  - tính giá, cọc, số dư, phụ phí, chi phí sự cố;
  - tự suy trạng thái vận hành, vị trí ngựa, hạn chót;
  - chép cứng bảng giá, quy định, danh mục địa lý, danh sách trạng thái kèm quy tắc chuyển trạng thái.
- Muốn xem trước giá thì gọi endpoint preview của backend.
- Kiểm tra đầu vào ở frontend chỉ để báo lỗi sớm. Backend vẫn kiểm lại, và lỗi validate của backend phải hiện ngay dưới đúng ô nhập.
- **Cấm** rẽ nhánh code chạy thật theo môi trường test (`import.meta.env.MODE === 'test'`) hay giữ dữ liệu giả trong code chạy thật. Test thì mock ở tầng `apiClient`.
- Chuỗi hiển thị cho người dùng đi qua i18n (`shared/i18n`), có đủ cả `vi` và `en`.

### 8.5 Giao diện (UI)

- **Một ngôn ngữ thiết kế cho mọi vai trò.** Không có theme riêng đè style cho từng vai trò.
- Mọi giá trị giao diện lấy từ **design token** trong `shared/styles/tokens.css`:
  - màu theo ngữ nghĩa: `--color-bg`, `--color-surface`, `--color-text`, `--color-muted`, `--color-border`, `--color-primary`, `--color-success`, `--color-warning`, `--color-danger`, `--color-info`;
  - khoảng cách theo thang 4px (`--space-1` = 4px …), cỡ chữ theo thang (`--text-xs` … `--text-2xl`);
  - bo góc, bóng đổ, z-index, thời gian chuyển động.
- Style viết bằng **CSS Modules** cạnh component. `base.css` chỉ chứa reset và kiểu chữ nền.
- **Cấm** trong TSX:
  - mã màu (`#…`, `rgb(…)`);
  - `style={{…}}`, trừ giá trị động không biểu diễn được bằng class (tọa độ, phần trăm tiến độ, kích thước tính lúc chạy).
- Nút, ô nhập, nhãn trạng thái… dùng component trong `shared/ui` có biến thể (`<Button variant="primary" size="sm">`). Không tự viết lại.
- Màu của trạng thái nghiệp vụ được ánh xạ tại **một nơi** (component `StatusBadge`), không rải ở từng trang.

### 8.6 Trải nghiệm (UX)

- **Mỗi màn hình có đủ 4 trạng thái:**
  - đang tải: dùng `Skeleton` giữ đúng bố cục, không để trang nhảy;
  - trống: dùng `EmptyState`, nói rõ bước tiếp theo;
  - lỗi: dùng `ErrorState` có nút thử lại;
  - có dữ liệu.
- Tải lại sau khi cập nhật thì giữ dữ liệu cũ trên màn hình (`useLoad` tải ngầm), không chớp màn hình.
- Thao tác **không hoàn tác được** (gửi, hủy, duyệt, thanh toán, xóa) phải có `ConfirmDialog` nói rõ hậu quả. Nút đang xử lý thì khóa lại và hiện trạng thái để tránh bấm hai lần.
- Kết quả thao tác báo bằng `Toast`. Lỗi dùng ngôn ngữ người dùng hiểu được, không hiện mã lỗi kỹ thuật.
- Luồng nhiều bước (đặt đơn) dùng `Stepper`, giữ bản nháp khi người dùng quay lại bước trước.
- **Ứng dụng hiện trường (tài xế, groom) ưu tiên điện thoại:**
  - vùng chạm tối thiểu 44×44px;
  - thao tác theo **điểm dừng** của lượt xe;
  - quét chip và chụp ảnh chỉ cần một chạm.
- Khách theo dõi đơn **theo từng con ngựa**: một timeline gồm chặng, lưu trú và giao nhận, khớp mô hình `horse_leg`, `horse_stay`, `handover`.
- Mọi trang dùng được ở bề rộng 360px trở lên. Bảng dữ liệu trên điện thoại chuyển thành dạng thẻ.

### 8.7 Khả năng tiếp cận

- Mọi ô nhập có nhãn. Mọi nút chỉ có biểu tượng phải có `aria-label`.
- Có viền `:focus-visible` rõ ràng. Modal giữ tiêu điểm bên trong và đóng được bằng phím Escape.
- Độ tương phản chữ tối thiểu 4.5:1. Không truyền đạt thông tin chỉ bằng màu sắc: trạng thái phải có thêm chữ hoặc biểu tượng.
- Tôn trọng `prefers-reduced-motion` cho mọi hiệu ứng chuyển động.

### 8.8 Chuyển đổi code cũ

Code frontend hiện tại chưa theo các mục trên: còn `shared/services`, `shared/types`, logic giá trong `shared/lib` và `shared/config`, trang dài hơn 1000 dòng, và rất nhiều `style={{}}`. Trên nhánh `redesign/system-v2`, làm theo thứ tự:

1. Hoàn thiện `tokens.css` và các component trong `shared/ui`. Thêm alias `@customer` và `@backoffice` (hiện mới có `@shared`), cấu hình sinh `shared/api/generated/` từ OpenAPI, và các kiểm tra lint ở mục 10.3.
2. Viết lại từng tính năng khi backend của tính năng đó đã xong.
3. Khi tính năng cuối cùng đã chuyển, xóa `shared/services`, `shared/types` và các file nghiệp vụ trong `shared/lib`, `shared/config`.

Code mới phải theo quy tắc ngay. Không sửa code cũ ngoài phạm vi đã duyệt (mục 1.2).

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

Frontend cũng được khóa bằng lint trên nhánh này:
- oxlint `no-restricted-imports` cho các chiều phụ thuộc ở mục 8.3;
- một bước kiểm tra trong `npm run lint` chặn mã màu và `style={{` trong file TSX mới.

Vi phạm thì build đỏ. **Không** được tắt, nới lỏng hay thêm ngoại lệ cho các kiểm tra này khi người dùng chưa đồng ý.

---

## 11. Danh sách cấm (tóm tắt)

1. Sửa file khi chưa được duyệt, hoặc sửa ngoài danh sách đã duyệt.
2. Logic nghiệp vụ trong `controller/`, `util/`, `config/`, `repository/`, `entity/`, `dto/`, hoặc ở frontend.
3. Controller gọi repository hoặc trả entity ra API.
4. Entity import DTO. DTO gọi repository. `toEntity()` gán id, trạng thái, người thao tác hoặc số tiền.
5. ModelMapper, package `mapper/`, chép field thủ công thay cho method chuyển đổi của DTO.
6. Sửa lược đồ ngoài Flyway. Seed bằng Java. Đổi `ddl-auto`. Sửa migration đã merge.
7. Lưu dữ liệu nghiệp vụ dạng JSON. Tham chiếu dạng chuỗi thay cho FK. View chứa logic nghiệp vụ. Cột dẫn xuất ngoài danh sách được phép, hoặc do nhiều nơi cùng ghi.
8. Nhận số tiền tính sẵn từ client. Một công thức viết ở hai nơi.
9. Hằng số nghiệp vụ hard-code.
10. Endpoint dạng `action` + `switch`.
11. Thêm trạng thái vận hành vào `booking.status`.
12. Commit bí mật. Commit, push hay merge khi chưa được yêu cầu.
13. Frontend: import chéo giữa hai ứng dụng hoặc giữa các tính năng; component gọi API trực tiếp; sửa tay `shared/api/generated/`.
14. Frontend: mã màu hay `style={{}}` tĩnh trong TSX; tự viết lại nút, ô nhập, modal thay cho `shared/ui`; trang thiếu trạng thái đang tải, trống hoặc lỗi.

## 12. Checklist trước khi báo hoàn thành

- [ ] Chỉ sửa các file đã được duyệt.
- [ ] Class mới đúng package, đúng hậu tố, không phụ thuộc ngược tầng (mục 3).
- [ ] Bảng và cột khớp `DB_SCHEMA_59_BANG.md`. Đổi lược đồ thì có file Flyway mới và đã cập nhật đặc tả (mục 4).
- [ ] Response DTO có `from()`. Request DTO có `toEntity()` hoặc `applyTo()`, không gán field bị cấm (mục 5).
- [ ] Số tiền tính ở backend, trong `policy/` (mục 6).
- [ ] Method ghi có `@Transactional`. Thời gian lấy qua `Clock` (mục 7).
- [ ] DTO đổi thì đã sinh lại `shared/api/generated/` (mục 8.1).
- [ ] Frontend: file đúng thư mục, đúng chiều phụ thuộc, trang ≤ 300 dòng (mục 8.1–8.3).
- [ ] Frontend: chỉ dùng token và `shared/ui`; có đủ 4 trạng thái; thao tác không hoàn tác có xác nhận; chạy được ở bề rộng 360px (mục 8.5–8.7).
- [ ] Có test theo mục 10.1. Quality Gate xanh, hoặc đã báo rõ phần nào không chạy hay bị fail.
