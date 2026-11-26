# Bàn giao: sửa nhóm tài khoản và hồ sơ

> Ngày ghi: 23/11/2026. Thiết kế gốc: [SUA_TAI_KHOAN_VA_HO_SO.md](SUA_TAI_KHOAN_VA_HO_SO.md).
> Phần tài khoản và hồ sơ hoàn tất trong commit `698fd29` của nhánh `rd/account-profiles` và đã merge vào `origin/redesign/system-v2` qua PR #350.
> Đọc `AGENTS.md` trước. Phương án đã được duyệt ngày 23/11/2026 với phạm vi ở mục 2 và 4. Cần sửa file khác thì dừng lại và hỏi.

## 1. Trạng thái

| Bước (mục 7 của tài liệu gốc) | Trạng thái |
|---|---|
| 1. Cập nhật tài liệu đặc tả | Xong |
| 2. Migration và test migration | Xong |
| 3. Entity, repository, service, seed, test | Xong |
| 4. Quality Gate backend | `mvnw verify`: 200 test, 0 lỗi, 1 bỏ qua (`RoutingLiveTest`, gọi Valhalla thật). Frontend không đổi nên không chạy |

## 2. Đã làm

- `V202611231200__account_profiles.sql`: kiểm dữ liệu, `app_user.role_code`, `customer`, `customer_address`, đổi FK, sửa `ck_handover_parties`, bỏ `user_role`. Vẫn 59 bảng.
- Entity mới: `Customer`, `CustomerAddress`, enum `CustomerType`. Xóa `PartyAddress`, `UserRole`, `UserRoleId`.
- `AuthServiceImpl`, `CustomerRegistrationServiceImpl`, `RegisterRequestDto.toCustomer()`. API và claim `role`, `roles` không đổi.
- Seed `R__dev_seed_base.sql`, `R__dev_seed_bookings.sql`.
- Test: `AccountProfilesMigrationTest` (DB đã có dữ liệu và 12 trường hợp dữ liệu sai), `RegisterRequestDtoTest`; cập nhật `AuthFlowTest`, `EntityMappingTest`, `DepartureServiceTest`, `MigrationTest`, `TestData`.

Các điểm khác tài liệu gốc, đã được duyệt:

1. Migration dừng thêm khi: tài khoản nội bộ không có `staff`; `party` của khách `INACTIVE` mà tài khoản `ACTIVE`; chứng từ của bên vừa là khách vừa là đối tác; chứng từ của khách có loại giấy `PARTY`.
2. Chỉ xóa `party` từng gắn tài khoản hoặc từng là `CUSTOMER`/`CONSIGNEE`. Đối tác chưa gán vai trò được giữ.
3. `handover` chỉ đặt NULL ở bên không có vai trò đối tác.

Phát sinh khi làm, chưa có trong phương án: migration cũng dừng khi chứng từ gắn `party` của tài khoản nội bộ, hoặc địa chỉ thuộc `party` của tài khoản nội bộ.

## 3. Lưu ý của người dùng (23/11/2026)

> Nhóm tài khoản, customer: ưu tiên giữ cách thiết kế cũ, chỉ bổ sung để quản lý những thứ bản trước gộp quá nhiều vào booking.

| Bản cũ (nhánh `main`) | Bản mới | Nhận xét |
|---|---|---|
| `users` 1–1 `CUSTOMER`, khóa chung `UserID` | `app_user` 1–1 `customer`, khóa chung `user_id` | Giữ |
| `CUSTOMER`: FullName, Phone, Address | `full_name`, `phone`, `address` | Giữ |
| `BOOKING.CustomerID`, `HORSE.CustomerID` | `booking.customer_id`, `horse.owner_customer_id` | Giữ |
| Consignor/Consignee (tên, điện thoại, giấy tờ, địa chỉ) trong `BOOKING` | `booking_party` | Tách khỏi booking |
| PickupAddress, DropoffAddress trong `BOOKING` | `booking_address`; sổ địa chỉ ở `customer_address` | Tách khỏi booking |
| Không có | `customer`: `customer_type`, `country_code`, 3 cột giấy tờ, `tax_code` | Có trong tài liệu gốc. Người dùng đã đồng ý giữ (23/11/2026) |

## 4. Tài liệu đã cập nhật

Tài liệu đặc tả dùng **tên mới** (xem [BANG_DOI_TEN.md](BANG_DOI_TEN.md)); code dùng tên cũ.

- [x] `DB_SCHEMA_59_BANG.md`:
  - khung cảnh báo đầu file; danh sách bảng nhóm 2 và bậc tạo: bỏ `user_role`, thêm `customer`, `party_address` → `customer_address` (`customer` sau `app_user` và `country`, trước `customer_address`, `horse`, `booking`);
  - mục 2.1 thêm `role_code`; bỏ 2.3; thêm `customer`; sửa `party`, `party_role`, sổ địa chỉ;
  - `booking.customer_id`, `horse.owner_customer_id` và index; `booking_party` bỏ `party_id`; `booking_address.source_customer_address_id`; `document.customer_id`; `document_type.owner_type` thêm `CUSTOMER`;
  - `handover`: CHECK mới và bảng bên giao, bên nhận ở mục 3.6 tài liệu gốc; mục "Ngựa đang trong tay ai" phải xét cả `handover_type`;
  - quy tắc #26 bỏ phần `CUSTOMER`; thêm quy tắc vai trò khớp hồ sơ.
- [x] `NGHIEP_VU_THEO_59_BANG.md`: mục 2.1–2.3, bảng dữ liệu nền, GĐ1 (đăng ký, sổ địa chỉ), GĐ6 (PICKUP: `from_party_id` NULL), bảng mục 7, đối chiếu entity cũ (`User` → `app_user`, `Customer` → `customer`), các chỗ nhắc `user_role`, `party_address`.
- [x] `KE_HOACH_TRIEN_KHAI.md`: chủ sở hữu nhóm 2 (`customer`, `customer_address` thuộc `CustomerService`, P0 tạo khi đăng ký, A2 quản lý; `party`, `party_role` thuộc `PartyService`), các chỗ nhắc `user_role`, đăng ký, `JwtUtils`.
- [x] `BANG_DOI_TEN.md`: bỏ dòng giấy tờ của `party`; `source_address_id` → `source_customer_address_id` đánh dấu đã đồng bộ ở mục 4; sửa mục 5.
- [x] `NGU_CANH_THIET_KE_LAI.md`, `SUA_TAI_KHOAN_VA_HO_SO.md`: đổi "Chưa thực hiện" thành đã thực hiện.
- [x] Tìm sót: `grep -rnE "user_role|party_address|customer_party_id|owner_party_id|to_party_id. NULL" docs`.

## 5. Sau đó

1. Báo cáo theo mục 1.3 của `AGENTS.md`. Chỉ commit, push khi người dùng yêu cầu. Commit gợi ý: `feat(account): tách hồ sơ khách, mỗi tài khoản một vai trò`. Không thêm dòng `Co-Authored-By`.
2. Báo luồng B trước khi merge: `TestData.customer()` trả về `Customer`, `booking` và `horse` đổi khóa ngoại, không còn `user_role`. Entity nhóm 9–15 làm theo mục 3 tài liệu gốc.
3. Đổi tên theo `BANG_DOI_TEN.md` là **việc riêng**, chưa được giao.

## 6. Chạy kiểm tra

Docker Desktop phải đang chạy.

```powershell
cd backend
.\mvnw.cmd verify
```

Các dòng `Migration ... failed! Changes successfully rolled back` trong log là chủ đích của `AccountProfilesMigrationTest`.
