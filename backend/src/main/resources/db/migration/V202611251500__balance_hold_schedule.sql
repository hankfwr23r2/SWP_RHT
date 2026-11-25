ALTER TABLE horse_stay ADD COLUMN balance_hold_at timestamptz;
ALTER TABLE horse_stay ADD CONSTRAINT ck_horse_stay_balance_hold
    CHECK (balance_hold_at IS NULL OR (stay_type = 'STABLING' AND actual_start_at IS NOT NULL));

ALTER TABLE cost_item ADD COLUMN stabling_deposit_credit numeric(15,2) NOT NULL DEFAULT 0;
ALTER TABLE cost_item ADD CONSTRAINT ck_cost_item_stabling_deposit_credit
    CHECK (stabling_deposit_credit >= 0 AND stabling_deposit_credit <= amount
        AND (stabling_deposit_credit = 0 OR (category = 'STABLING' AND horse_stay_id IS NOT NULL)));

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES
    ('BALANCE_PICKUP_REMINDER_MID_MINUTES', '15', 'MINUTE', 'HANDOVER', 'Lần nhắc giữa giờ chờ tại nơi đón', 1, 24),
    ('BALANCE_PICKUP_REMINDER_LAST_MINUTES', '25', 'MINUTE', 'HANDOVER', 'Lần nhắc cuối tại nơi đón', 25, 29),
    ('BALANCE_HUB_LOADING_HOURS', '1', 'HOUR', 'HANDOVER', 'Mốc xếp ngựa trước giờ chạy', 1, 2),
    ('BALANCE_HUB_FINAL_REMINDER_HOURS', '3', 'HOUR', 'HANDOVER', 'Mốc nhắc số dư trước giờ chạy', 3, 24),
    ('BALANCE_HUB_GATEWAY_GRACE_MINUTES', '5', 'MINUTE', 'HANDOVER', 'Chờ kết quả VNPAY trước khi nhả chỗ', 1, 5),
    ('BALANCE_IMPORT_HUB_HOURS', '24', 'HOUR', 'HANDOVER', 'Thời gian thanh toán sau khi ngựa nhập về hub', 1, 72),
    ('STABLING_WRITTEN_NOTICE_DAYS', '7', 'DAY', 'HANDOVER', 'Gửi thông báo nhận lại ngựa sau số ngày giữ', 3, 30),
    ('STABLING_MANAGER_NOTICE_DAYS', '45', 'DAY', 'HANDOVER', 'Báo quản lý sau số ngày từ thông báo bằng văn bản', 1, 90);
