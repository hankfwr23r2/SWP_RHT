ALTER TABLE booking_horse
    ADD COLUMN pickup_missed_at timestamptz,
    ADD COLUMN pickup_choice_due_at timestamptz,
    ADD COLUMN pickup_miss_reason varchar(500),
    ADD COLUMN pickup_miss_liability varchar(20)
        CHECK (pickup_miss_liability IN ('CUSTOMER', 'COMPANY', 'FORCE_MAJEURE')),
    ADD COLUMN pickup_miss_choice varchar(24)
        CHECK (pickup_miss_choice IN ('DROP_HORSE', 'RETRY_PICKUP', 'SELF_DELIVERY',
            'CHANGE_DEPARTURE', 'RESCHEDULE_PICKUP', 'CANCEL_BOOKING'));

CREATE INDEX ix_booking_horse_pending_pickup_choice ON booking_horse (pickup_choice_due_at)
    WHERE pickup_miss_choice IS NULL AND pickup_choice_due_at IS NOT NULL;

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('PICKUP_CHOICE_LONG_HOURS', '12', 'HOUR', 'HANDOVER', 'Hạn chọn sau đón hụt khi còn nhiều thời gian', 1, 24),
       ('PICKUP_CHOICE_SHORT_HOURS', '1', 'HOUR', 'HANDOVER', 'Hạn chọn sau đón hụt sát chuyến', 1, 12);
