# Bảng đổi tên: tên cũ → tên mới

> Tài liệu (`DB_SCHEMA_59_BANG.md`, `NGHIEP_VU_THEO_59_BANG.md`, `KE_HOACH_TRIEN_KHAI.md`, `NGU_CANH_THIET_KE_LAI.md`, `AGENTS.md`) đã dùng **tên mới**.
> Migration, entity và code hiện còn dùng **tên cũ**, người dùng sẽ đồng bộ sau. Ngày ghi: 23/11/2026.
>
> - Trước khi đồng bộ: viết code theo tên đang có trong migration.
> - Khi đồng bộ: tạo migration mới (`ALTER TABLE … RENAME`), không sửa migration đã merge. Đổi entity, repository, DTO, `shared/api/generated/` và test trong cùng thay đổi.
> - Đồng bộ xong bảng nào thì đánh dấu `[x]` ở bảng đó.

## 1. Đổi tên bảng

| Xong | Tên cũ | Tên mới | Lý do |
|---|---|---|---|
| [ ] | `service` | `billable_service` | Trùng annotation `@Service` của Spring |
| [ ] | `handover_item` | `handover_document` | Mỗi dòng là một giấy tờ gốc |

Bảng `booking_horse_service` giữ tên.

## 2. Đổi tên cột

Tên bảng ghi theo tên mới.

### Nhóm 1. Danh mục địa lý

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `country` | `default_currency` → `default_currency_code` |
| [ ] | `location` | `type` → `location_type`<br>`opening_minutes` → `opening_time`<br>`closing_minutes` → `closing_time` |

### Nhóm 2. Tài khoản, nhân sự, các bên

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `app_user` | `email_verified` → `is_email_verified` |
| [ ] | `staff` | `home_hub_id` → `home_hub_location_id`<br>`driver_license_expiry` → `driver_license_expiry_date` |
| [ ] | `party` | `kind` → `party_type` |

### Nhóm 3. Ngựa

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `horse` | `passport_no` → `passport_number`<br>`gender` → `sex`<br>`declared_currency` → `declared_currency_code` |

### Nhóm 4. Mạng lưới và lịch chạy

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `route` | `est_duration_minutes` → `estimated_duration_minutes` |
| [ ] | `route_stop` | `seq` → `sequence_number`<br>`offset_minutes` → `arrival_offset_minutes` |
| [ ] | `departure_schedule` | `capacity_stalls` → `stall_capacity` |
| [ ] | `departure_schedule_day` | `schedule_id` → `departure_schedule_id`<br>`weekday` → `day_of_week` |
| [ ] | `departure` | `schedule_id` → `departure_schedule_id`<br>`capacity_stalls` → `stall_capacity`<br>`booked_stalls` → `booked_stall_count`<br>`planned_depart_at` → `planned_departure_at` |

### Nhóm 5. Xe

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `vehicle_class` | `max_stalls` → `max_stall_count` |
| [ ] | `vehicle` | `standard_stalls` → `standard_stall_count`<br>`wide_stalls` → `wide_stall_count`<br>`registration_expiry` → `registration_expiry_date`<br>`home_hub_id` → `home_hub_location_id` |

### Nhóm 6. Dịch vụ và bảng giá

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `billable_service` | `auto_apply` → `is_auto_applied` |
| [ ] | `price_list` | `currency` → `currency_code` |
| [ ] | `price_item` | `service_id` → `billable_service_id`<br>`stall_type` → `stall_type_code` |

### Nhóm 7. Quy định và giấy tờ yêu cầu

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `country_rule` | `cert_lead_days` → `certificate_lead_days` |
| [ ] | `document_type` | `subject` → `owner_type` |
| [ ] | `document_requirement` | `rule_id` → `country_rule_id`<br>`submitted_by` → `submitter_type` |

### Nhóm 8. Đơn hàng

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `booking` | `waybill_no` → `waybill_number`<br>`emergency_spend_limit` → `emergency_spending_limit`<br>`return_of_booking_id` → `original_booking_id` |
| [ ] | `booking_party` | `role` → `booking_role`<br>`id_type` → `identity_document_type`<br>`id_number` → `identity_document_number`<br>`id_country` → `identity_document_country_code` |
| [ ] | `booking_address` | `role` → `address_type` |
| [ ] | `booking_horse` | `stall_type` → `stall_type_code`<br>`feed_times_per_day` → `feedings_per_day`<br>`owner_supplies_feed` → `is_feed_supplied_by_owner` |
| [ ] | `booking_horse_service` | `service_id` → `billable_service_id` |
| [ ] | `terms_acceptance` | `terms_kind` → `terms_type` |

### Nhóm 9. Vận hành

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `vehicle_trip` | `kind` → `trip_type`<br>`planned_start` → `planned_start_at`<br>`planned_end` → `planned_end_at`<br>`actual_start` → `actual_start_at`<br>`actual_end` → `actual_end_at` |
| [ ] | `trip_stop` | `seq` → `sequence_number`<br>`route_seq` → `route_stop_sequence_number`<br>`planned_arrive_at` → `planned_arrival_at`<br>`planned_depart_at` → `planned_departure_at`<br>`actual_arrive_at` → `actual_arrival_at`<br>`actual_depart_at` → `actual_departure_at` |
| [ ] | `crew_assignment` | `role` → `crew_role` |
| [ ] | `horse_leg` | `seq` → `sequence_number`<br>`stage` → `leg_type`<br>`from_address_id` → `from_booking_address_id`<br>`to_address_id` → `to_booking_address_id`<br>`board_stop_id` → `board_trip_stop_id`<br>`alight_stop_id` → `alight_trip_stop_id`<br>`replaces_leg_id` → `replaced_horse_leg_id`<br>`planned_start` → `planned_start_at`<br>`planned_end` → `planned_end_at`<br>`actual_start` → `actual_start_at`<br>`actual_end` → `actual_end_at` |
| [ ] | `horse_stay` | `kind` → `stay_type`<br>`planned_from` → `planned_start_at`<br>`planned_to` → `planned_end_at`<br>`actual_from` → `actual_start_at`<br>`actual_to` → `actual_end_at`<br>`result` → `quarantine_result` |
| [ ] | `horse_health_log` | `temperature_c` → `temperature_celsius`<br>`heart_rate` → `heart_rate_bpm`<br>`respiration_rate` → `respiration_rate_per_minute` |

### Nhóm 10. Chuỗi giao nhận

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `handover` | `kind` → `handover_type`<br>`staff_id` → `handled_by` |
| [ ] | `handover_horse` | `chip_scanned` → `scanned_microchip`<br>`chip_matched` → `is_microchip_matched` |

### Nhóm 11. Bảo hiểm

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `horse_insurance` | `option` → `coverage_option`<br>`premium` → `premium_amount`<br>`currency` → `currency_code`<br>`policy_no` → `policy_number`<br>`waiver_acceptance_id` → `waiver_terms_acceptance_id` |

### Nhóm 12. Báo giá và thanh toán

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `quotation` | `currency` → `currency_code`<br>`valid_until` → `expires_at` |
| [ ] | `quotation_line` | `line_no` → `line_number`<br>`service_id` → `billable_service_id` |
| [ ] | `payment` | `kind` → `payment_type`<br>`method` → `payment_method`<br>`currency` → `currency_code`<br>`refund_of_payment_id` → `refunded_payment_id` |
| [ ] | `payment_transaction` | `gateway` → `gateway_code`<br>`gateway_txn_no` → `gateway_transaction_number` |

### Nhóm 13. Sự cố và chi phí

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `incident` | `name` → `title`<br>`cause` → `cause_type` |
| [ ] | `incident_plan` | `directive` → `instructions` |
| [ ] | `cost_item` | `payer` → `payer_type`<br>`currency` → `currency_code`<br>`customer_consulted` → `is_customer_consulted`<br>`extra_payment_id` → `extra_charge_payment_id` |

### Nhóm 14. Chứng từ

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `document` | `requirement_id` → `document_requirement_id` |

### Nhóm 15. Hỗ trợ

| Xong | Bảng | Cột cũ → cột mới |
|---|---|---|
| [ ] | `notification` | `link` → `link_url` |
| [ ] | `system_config` | `key` → `config_key`<br>`value` → `config_value` |

## 3. Thay đổi không chỉ là tên

| Cột | Thay đổi |
|---|---|
| `location.opening_time`, `closing_time` | Kiểu `int` (phút từ 0 giờ) đổi sang `time`. Migration phải chuyển dữ liệu và bỏ CHECK `0..1439` |

## 4. Đã đồng bộ trước

| Bảng | Đổi tên | Ở đâu |
|---|---|---|
| `route` | `origin_hub_id` → `origin_location_id` | `V202611231100__route_origin_location.sql` |
| `booking_address` | `source_address_id` → `source_customer_address_id` | `V202611231200__account_profiles.sql` |
| `customer_address` | bảng `party_address` → `customer_address`, `party_id` → `customer_id` | `V202611231200__account_profiles.sql` |
| `booking` | `customer_party_id` → `customer_id` | `V202611231200__account_profiles.sql` |
| `horse` | `owner_party_id` → `owner_customer_id` | `V202611231200__account_profiles.sql` |

## 5. Đã cân nhắc, giữ nguyên

| Tên | Quyết định |
|---|---|
| `location` | Giữ. Nghĩa là điểm cố định của mạng lưới. Địa chỉ của khách nằm ở `booking_address` và `customer_address` |
| `party`, `party_role`, `booking_party` | Giữ tên. `party` chỉ chứa đối tác, khách ở `customer` |
