# Sửa nhóm tài khoản và hồ sơ

> Tài liệu giao việc cho agent. Người dùng đã đồng ý hướng thiết kế ngày 23/11/2026.
> **Đã thực hiện** ở `V202611231200__account_profiles.sql`. Các điểm khác tài liệu này: [BAN_GIAO_TAI_KHOAN_VA_HO_SO.md](BAN_GIAO_TAI_KHOAN_VA_HO_SO.md).
> `DB_SCHEMA_59_BANG.md` và `NGHIEP_VU_THEO_59_BANG.md` đã cập nhật theo tài liệu này.

## 1. Vấn đề

| Hiện tại | Hậu quả |
|---|---|
| Khách là một dòng `party`, nhân viên có bảng `staff` | Hai loại hồ sơ không đối xứng |
| "Là khách" lưu ở cả `user_role` và `party_role` | Hai nơi có thể lệch nhau |
| `booking` và `horse` trỏ tới `party` | DB không chặn được việc lấy một phòng khám thú y làm người đặt đơn |
| Cột giấy tờ, `user_id` của `party` chỉ dùng cho khách | Nhiều cột trống ở đối tác |

## 2. Thiết kế đích

Lấy cấu trúc tài khoản của project cũ (`users` → `CUSTOMER`/`STAFF`, PK dùng chung) và vá các chỗ thiếu.

```
role ──1–N── app_user ─┬─ 1–0..1 ─ customer ──1–N── customer_address
                       └─ 1–0..1 ─ staff

party ──1–N── party_role     đối tác, không có tài khoản
booking_party                các bên trong một đơn, chỉ là bản chụp
```

Quy tắc:
- Mỗi tài khoản có **đúng một** vai trò (`app_user.role_code`).
- `CUSTOMER` có đúng một dòng `customer`, không có `staff`. Vai trò khác có đúng một dòng `staff`, không có `customer`. Service kiểm và ghi trong cùng transaction.
- Email đăng nhập chỉ ở `app_user`, không lặp ở `customer`.
- `party` chỉ chứa **đối tác**. Khách, người gửi, người nhận **không** tạo dòng `party`.

**Tổng vẫn 59 bảng:** thêm `customer`, bỏ `user_role`, `party_address` thành `customer_address`.

## 3. Thay đổi lược đồ

### 3.1 `app_user`

| Cột | Kiểu | Null | Ràng buộc |
|---|---|---|---|
| `role_code` (mới) | `varchar(30)` | | FK → role, có index |

### 3.2 `customer` (mới)

| Cột | Kiểu | Null | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| `user_id` | `bigint` | | PK, FK → app_user | Kế thừa khóa của tài khoản |
| `customer_type` | `varchar(30)` | | CK `PERSON, ORGANIZATION` | |
| `full_name` | `varchar(200)` | | | Tên người hoặc tổ chức |
| `phone` | `varchar(20)` | có | | |
| `address` | `varchar(500)` | có | | Địa chỉ liên lạc hoặc xuất hóa đơn |
| `country_code` | `char(2)` | | FK → country | |
| `identity_document_type` | `varchar(30)` | có | CK `CCCD, PASSPORT, BUSINESS_REG` | |
| `identity_document_number` | `varchar(50)` | có | | |
| `identity_document_country_code` | `char(2)` | có | FK → country | |
| `tax_code` | `varchar(30)` | có | | Với tổ chức |

Ràng buộc bảng:
- `CHECK (num_nonnulls(identity_document_type, identity_document_number, identity_document_country_code) IN (0, 3))`
- `UNIQUE (identity_document_type, identity_document_number, identity_document_country_code)`

Index: `country_code`, `identity_document_country_code`.
Không có cột `status`: trạng thái lấy từ `app_user.status`.

### 3.3 `customer_address` (đổi từ `party_address`)

`party_id` → `customer_id`, FK → customer(user_id), CASCADE. Các cột khác giữ nguyên. Địa chỉ mặc định: unique index trên `customer_id` với điều kiện `is_default`.

### 3.4 `party`, `party_role`

| Bảng | Thay đổi |
|---|---|
| `party` | Bỏ `user_id`, `id_type`, `id_number`, `id_country` cùng ràng buộc của chúng. Giữ `kind`, `display_name`, `phone`, `email`, `address`, `country_code`, `tax_code`, `status` |
| `party_role` | CK `role_type` còn `DEST_AGENT, QUARANTINE_FARM, STABLE, VET, RESCUE, INSURER` |

### 3.5 Các bảng tham chiếu

| Bảng | Trước (tên trong migration) | Sau |
|---|---|---|
| `booking` | `customer_party_id` → party | `customer_id` → customer(user_id), NOT NULL |
| `horse` | `owner_party_id` → party | `owner_customer_id` → customer(user_id), NOT NULL |
| `booking_party` | `party_id` → party | Bỏ cột |
| `booking_address` | `source_address_id` → party_address | `source_customer_address_id` → customer_address, SET NULL |
| `document` | `party_id` trong nhóm chủ sở hữu | Giữ `party_id` (đối tác), thêm `customer_id` → customer(user_id) vào nhóm "đúng 1 cột", có partial index |
| `document_type` | CK `subject` có `PARTY` | Thêm `CUSTOMER` |
| `user_role` | | Bỏ bảng |

Giữ nguyên, vẫn trỏ tới `party`: `horse_leg.agent_party_id`, `horse_stay.operator_party_id`, `horse_insurance.insurer_party_id`, `incident_plan.handler_party_id`, `handover.from_party_id`, `handover.to_party_id`.

### 3.6 `handover`: sửa lỗi ràng buộc

`ck_handover_parties` hiện là `from_party_id IS DISTINCT FROM to_party_id`, nên **từ chối mọi dòng có cả hai cột NULL**, ví dụ `HUB_IN`, `HUB_OUT`. Thay bằng:

```sql
CHECK (from_party_id IS NULL OR to_party_id IS NULL OR from_party_id <> to_party_id)
```

`from_party_id`, `to_party_id` chỉ dùng cho **đối tác**. NULL là công ty hoặc bên trong đơn, suy ra từ `kind`:

| `kind` | Bên giao | Bên nhận |
|---|---|---|
| `PICKUP` | `booking_party` SHIPPER | Công ty |
| `HUB_IN`, `HUB_OUT` | Công ty | Công ty |
| `STAY_IN` | Công ty hoặc đối tác (`from_party_id`) | Công ty hoặc đối tác (`to_party_id`) |
| `STAY_OUT` | Công ty hoặc đối tác (`from_party_id`) | Công ty hoặc đối tác (`to_party_id`) |
| `AGENT` | Công ty hoặc đối tác (`from_party_id`) | Công ty hoặc đối tác (`to_party_id`); ít nhất một bên là đối tác |
| `DELIVERY` | Công ty, hoặc đại lý (`from_party_id`) | `booking_party` CONSIGNEE |
| `RETURN` | Công ty, hoặc đối tác (`from_party_id`) | `booking_party` SHIPPER |

"Ngựa đang trong tay ai" phải xét cả `kind`. `to_party_id` NULL **không** còn luôn có nghĩa là công ty.

## 4. Migration

Tạo **một file mới** `V<yyyyMMddHHmm>__account_profiles.sql`. Không sửa V1–V15.

Thứ tự:

1. **Kiểm dữ liệu trước**, dùng khối `DO $$ … RAISE EXCEPTION`. Dừng lại nếu có:
   - tài khoản không có vai trò, hoặc có nhiều hơn một vai trò;
   - tài khoản `CUSTOMER` không có `party`, hoặc có dòng `staff`;
   - `booking.customer_party_id` hay `horse.owner_party_id` trỏ tới `party` không có `user_id`, hoặc tài khoản của nó không phải `CUSTOMER`;
   - `party_address` thuộc `party` không có `user_id`.
2. Thêm `app_user.role_code`, chép từ `user_role`, đặt NOT NULL, thêm FK và index.
3. Tạo `customer`, chép từ `party` có `user_id` và tài khoản là `CUSTOMER`.
4. Thêm cột mới ở `booking`, `horse`, `document`; chép theo `party.user_id`; đặt NOT NULL ở `booking` và `horse`; bỏ cột cũ. Với `document`, dòng thuộc khách chuyển từ `party_id` sang `customer_id`. Bỏ CHECK "đúng 1 cột" cũ trước khi chép và thêm lại sau đó.
5. Đổi `party_address` thành `customer_address`; đổi cột, FK, tên ràng buộc và index.
6. `booking_address`: đổi tên cột, trỏ FK sang `customer_address`.
7. `booking_party`: bỏ `party_id`.
8. `handover`: đặt NULL ở `from_party_id`/`to_party_id` đang trỏ tới khách hoặc người nhận, thay CHECK (mục 3.6).
9. Xóa vai trò `CUSTOMER`, `CONSIGNEE` trong `party_role`, rồi xóa các `party` không còn vai trò và không còn bảng nào trỏ tới. Thay CK `role_type`.
10. Bỏ cột của `party` (mục 3.4), bỏ `user_role`, thay CK `document_type.subject`.

Tên cột: bảng và cột **tạo mới** dùng tên mới. Cột cũ không liên quan giữ tên đang có, xem [BANG_DOI_TEN.md](BANG_DOI_TEN.md).

## 5. Code

Danh sách lấy từ nhánh `rd/a1-departures` ngày 23/11/2026. Chạy lại `grep` trước khi sửa, vì nhánh khác có thể đã thêm chỗ dùng.

| File | Việc |
|---|---|
| `entity/AppUser` | Thêm `@ManyToOne Role role` |
| `entity/Customer` (mới) | `@MapsId` trên `@OneToOne AppUser`, cùng cách với `Staff` |
| `entity/CustomerAddress` | Thay `PartyAddress` |
| `entity/Party`, `enums/PartyRoleType` | Bỏ field và giá trị theo mục 3.4 |
| `entity/Booking`, `Horse`, `BookingParty`, `BookingAddress` | Đổi quan hệ theo mục 3.5 |
| `entity/UserRole`, `UserRoleId`, `repository/UserRoleRepository` | Xóa |
| `repository/` | Thêm `CustomerRepository`, `CustomerAddressRepository`. Xóa `PartyAddressRepository`. Giữ `PartyRepository`, `PartyRoleRepository` |
| `service/impl/CustomerRegistrationServiceImpl` | Tạo `app_user` (vai trò `CUSTOMER`) và `customer`. Không tạo `party`, `party_role` |
| `service/impl/AuthServiceImpl` | `rolesOf` đọc `user.getRole()`. Tên khách lấy từ `customer`. Bỏ `ROLE_PRIORITY`. **Giữ** claim `role` và `roles` (danh sách một phần tử) để giao diện không phải đổi |
| DTO đăng ký | `toEntity()` **không** gán vai trò. Service gán |
| `db/seed-dev/R__dev_seed_base.sql`, `R__dev_seed_bookings.sql` | Viết lại theo bảng mới, vẫn chạy lặp được |
| Test: `AuthFlowTest`, `EntityMappingTest`, `DepartureServiceTest`, `support/TestData` | Cập nhật. Không xóa hay bỏ qua test |

Entity nhóm 9–15 chưa có trên nhánh này. Người làm các nhóm đó dùng thiết kế ở mục 3.

Frontend: không đổi API đăng nhập và đăng ký. Nếu DTO đổi thì sinh lại `shared/api/generated/`.

## 6. Kiểm thử

- [ ] Migration chạy từ đầu trên PostgreSQL thật, ứng dụng khởi động với `ddl-auto=validate`.
- [ ] Migration chạy trên DB **đã có dữ liệu** (migrate tới phiên bản trước, chèn mẫu, migrate tiếp). Kiểm `booking`, `horse`, địa chỉ vẫn trỏ đúng người.
- [ ] Dữ liệu sai ở bước 1 mục 4 làm migration dừng với thông báo rõ.
- [ ] Đăng ký tạo đúng một `app_user` và một `customer`, không tạo `party`.
- [ ] Đăng nhập khách và từng vai trò nhân viên. Khách không vào được backoffice, nhân viên không vào được cổng khách.
- [ ] Không lưu được `booking` có `customer_id` không phải khách.
- [ ] `handover` `HUB_IN` có hai cột NULL lưu được. Hai cột cùng một đối tác thì bị từ chối.
- [ ] Quality Gate ở mục 10.2 của `AGENTS.md`.

## 7. Thứ tự làm

1. **Tài liệu:**
   - `DB_SCHEMA_59_BANG.md`: nhóm 2; các bảng ở mục 3.5; `handover`; danh sách bảng; thứ tự tạo (`customer` sau `app_user` và `country`, trước `customer_address`, `horse`, `booking`); mục "Ngựa đang trong tay ai"; quy tắc #26 bỏ phần `CUSTOMER`; thêm quy tắc vai trò khớp hồ sơ (mục 2).
   - `NGHIEP_VU_THEO_59_BANG.md`: mục 2.1–2.3, bước đăng ký và sổ địa chỉ ở GĐ1, bước giao nhận ở GĐ6, bảng ở mục 7, phần đối chiếu entity cũ (`User` → `app_user`, `Customer` → `customer`).
   - `KE_HOACH_TRIEN_KHAI.md`: chủ sở hữu bảng nhóm 2, các chỗ nhắc `user_role`, `PartyService`, đăng ký.
   - `BANG_DOI_TEN.md`: bỏ các dòng không còn áp dụng (giấy tờ của `party`); `booking_address.source_address_id` → `source_customer_address_id`; sửa mục 5.
2. Migration và test migration.
3. Entity, repository, service, seed, test.
4. Quality Gate, rồi báo cáo theo mục 1.3 của `AGENTS.md`.

Làm trên **một nhánh riêng** tách từ `redesign/system-v2`, sau khi nhánh A1 đã merge. Báo hai luồng trước khi merge, vì `booking` và `horse` đổi khóa ngoại.

## 8. Giới hạn đã chấp nhận

- Mỗi tài khoản một vai trò. Nhân viên muốn đặt đơn thì dùng tài khoản khách khác.
- Mỗi tổ chức khách có một tài khoản đăng nhập.
- Chủ ngựa phải có tài khoản khách.
- Một tổ chức vừa là khách vừa là đối tác có hai hồ sơ.
