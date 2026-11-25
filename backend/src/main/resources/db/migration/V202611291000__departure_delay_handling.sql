ALTER TABLE departure
    ADD COLUMN revised_departure_at timestamptz,
    ADD COLUMN run_reminded_at timestamptz,
    ADD COLUMN delayed_at timestamptz,
    ADD COLUMN delay_reason varchar(500),
    ADD COLUMN delay_escalated_at timestamptz,
    ADD COLUMN auto_cancelled_at timestamptz,
    ADD CONSTRAINT ck_departure_revised_departure_at
        CHECK (revised_departure_at IS NULL OR revised_departure_at > planned_departure_at),
    ADD CONSTRAINT ck_departure_delay_reason
        CHECK ((delayed_at IS NULL) = (delay_reason IS NULL)),
    ADD CONSTRAINT ck_departure_auto_cancelled_at
        CHECK (auto_cancelled_at IS NULL OR (status = 'CANCELLED' AND delayed_at IS NOT NULL));

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('DEPARTURE_RUN_REMINDER_HOURS', '24', 'HOUR', 'DEPARTURE', 'Nhắc xác nhận chạy trước giờ gốc', 1, 168),
       ('DEPARTURE_DELAY_GRACE_MINUTES', '30', 'MINUTE', 'DEPARTURE', 'Thời gian chờ chuyến đã xác nhận nhưng chưa xuất phát', 1, 240),
       ('DEPARTURE_DELAY_RESCHEDULE_HOURS', '2', 'HOUR', 'DEPARTURE', 'Trễ đủ giờ để đổi chuyến miễn phí', 1, 168),
       ('DEPARTURE_DELAY_REFUND_HOURS', '5', 'HOUR', 'DEPARTURE', 'Trễ đủ giờ để yêu cầu hoàn tiền hoặc xét tự hủy', 1, 168);
