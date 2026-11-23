# Thông báo của luồng A cho luồng B

> Agent luồng B đọc file này sau `AGENTS.md`, trước khi bắt đầu task. Mục mới nhất ở trên. Đây là thông tin phối hợp, không thay quy trình duyệt ở mục 1.1 của `AGENTS.md`.
> Cập nhật: 23/11/2026.

## 23/11/2026. Màn hình và tác động (người dùng đã chốt)

Đọc `MAN_HINH_VA_TAC_DONG.md` trước B1–B5. Các điểm ảnh hưởng luồng B:

- Ngựa trượt cách ly: gọi `BookingHorseService.exclude` (`EXCLUDED`); trả xong thì `RETURNED`.
- Chuyên viên kết luận cách ly, hộ tống chỉ đo và ghi.
- Ngựa ở hub: người ở `handover.handled_by` của HUB_IN phụ trách tới HUB_OUT. Hub vệ tinh: tổ xe trục ở lại; qua đêm thì gửi đối tác `STABLE`.
- Ăn uống ghi vào `horse_health_log.note`, không thêm cột. Không có dịch vụ cho ăn tính tiền.
- Mốc của đại lý do điều phối nhập hộ.

Thấy chỗ nào không làm được thì báo lại, không tự đổi.

## 23/11/2026. AuditService và P0.3-B

- Cả A2 (`06c1375`) và `rd/p0-3b-shared-services` đều có `AuditServiceImpl`, `AuditServiceTest`. Nhánh P0.3-B (`7b57809`) đã gộp A2; kiểm bằng `git merge-tree` thì merge vào `redesign/system-v2` không xung đột.
- Bản của luồng B thay bản của A. **Giữ nguyên chữ ký** `record(actorUserId, action, targetTable, targetId, oldData, newData)`, vì `SystemConfigServiceImpl` đang dùng.
- Bản của B từ chối `targetId` NULL và tên dài quá 50 ký tự. Cần test A2 (`SystemConfigServiceTest`, `SystemConfigFlowTest`) vẫn xanh sau khi gộp. Luồng A **chưa chạy** các test này trên nhánh của B.
- `ArchitectureTest` của P0.3-B sẽ kiểm cả code luồng A. Nếu đỏ vì code A thì báo luồng A, không nới quy tắc và không tự sửa code của A.

## 23/11/2026. Giới hạn tham số kỹ thuật (nhánh `fix/system-config-limits`, chưa merge)

- `system_config` thêm `min_value`, `max_value` (migration `V202611231600`). Luồng B không dùng số migration này.
- API `/system-configs` trả thêm `minValue`, `maxValue`. Luồng B đọc tham số qua `SystemConfigService.getInt` như cũ.
- Merge sau thì phải sinh lại `frontend/src/shared/api/generated/` và `frontend/openapi/openapi.json`, không sửa tay phần xung đột.

## 23/11/2026. Tài khoản và đồng bộ tên (đã merge)

- `booking.customer_id`, `horse.owner_customer_id` trỏ tới `customer`. Không còn `user_role`, `party_address`. `party` chỉ chứa đối tác.
- `TestData.customer()` trả về `Customer`.
- `handover.from_party_id`, `to_party_id` chỉ là đối tác. NULL phải xét cùng `handover_type` (`DB_SCHEMA_59_BANG.md` mục 10.1).
- Bảng và cột dùng tên mới theo `BANG_DOI_TEN.md`.

## Việc luồng B cần xử lý khi tới task

| Task | Việc |
|---|---|
| B1 | Quy tắc #26: gán đại lý cho chặng phải kiểm `party_role` còn hiệu lực vào ngày chạy. Hiện `PUT /parties/{id}/roles/{roleType}` cho rút ngắn hiệu lực cả khi đã có chặng dùng đối tác đó: kiểm lại lúc điều độ, và bàn với luồng A nếu cần chặn ở API |
| B2 | Quy tắc #28: `handover` loại `AGENT` phải có ít nhất một bên là đối tác |
