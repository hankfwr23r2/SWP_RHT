INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('EXTRA_CHARGE_DUE_DAYS', '7', 'DAY', 'PAYMENT', 'Hạn thanh toán khoản thu thêm', 1, 30)
ON CONFLICT (config_key) DO NOTHING;

INSERT INTO document_type (code, name, owner_type)
VALUES ('VET_REPORT', 'Kết quả thú y', 'INCIDENT')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE cost_item ADD COLUMN stabling_days bigint;
ALTER TABLE cost_item ADD CONSTRAINT ck_cost_item_stabling_days
    CHECK (stabling_days IS NULL OR (stabling_days > 0 AND category = 'STABLING' AND horse_stay_id IS NOT NULL));
