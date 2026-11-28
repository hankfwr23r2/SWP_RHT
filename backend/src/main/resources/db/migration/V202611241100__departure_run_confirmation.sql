-- Quản lý xác nhận chạy chuyến từ mốc DEPARTURE_DECISION_HOURS. Điều phối chỉ lập lượt trục khi đã xác nhận.
ALTER TABLE departure
    ADD COLUMN run_confirmed_at timestamptz,
    ADD COLUMN run_confirmed_by bigint REFERENCES app_user(id),
    ADD CONSTRAINT ck_departure_run_confirmed CHECK ((run_confirmed_at IS NULL) = (run_confirmed_by IS NULL));
CREATE INDEX ix_departure_run_confirmed_by ON departure (run_confirmed_by);

-- NULL = hệ thống tự đổi trạng thái (scheduler), giống audit_log.actor_user_id
ALTER TABLE booking_status_history ALTER COLUMN changed_by DROP NOT NULL;

-- Tốc độ tính tuyến xe tải 80 km/h (người dùng chốt 23/11/2026). Không ghi đè giá trị quản trị viên đã sửa.
UPDATE system_config SET config_value = '80' WHERE config_key = 'TRUCK_TOP_SPEED_KMH' AND config_value = '60';
