ALTER TABLE horse_stay
    ADD COLUMN failed_delivery_leg_id bigint REFERENCES horse_leg (id),
    ADD COLUMN ready_at timestamptz,
    ADD COLUMN ready_call_note varchar(500),
    ADD COLUMN earliest_redelivery_at timestamptz,
    ADD COLUMN chosen_redelivery_at timestamptz,
    ADD COLUMN collection_reported_at timestamptz,
    ADD COLUMN collection_expected_at timestamptz,
    ADD COLUMN missed_departure_id bigint REFERENCES departure (id),
    ADD CONSTRAINT ck_horse_stay_redelivery_choice CHECK (
        (earliest_redelivery_at IS NULL AND chosen_redelivery_at IS NULL)
        OR (ready_at IS NOT NULL AND earliest_redelivery_at IS NOT NULL AND chosen_redelivery_at > earliest_redelivery_at AND chosen_redelivery_at IS NOT NULL)),
    ADD CONSTRAINT ck_horse_stay_collection_window CHECK (
        (collection_reported_at IS NULL AND collection_expected_at IS NULL)
        OR (collection_reported_at IS NOT NULL AND collection_expected_at BETWEEN collection_reported_at AND collection_reported_at + interval '24 hours' AND collection_expected_at IS NOT NULL));

ALTER TABLE cost_item
    ALTER COLUMN recorded_by DROP NOT NULL,
    DROP CONSTRAINT ck_cost_item_approved,
    DROP CONSTRAINT ck_cost_item_category,
    ADD CONSTRAINT ck_cost_item_category CHECK (category IN ('TOLL', 'FUEL', 'STABLING', 'VET', 'RESCUE', 'REPAIR', 'WAITING', 'OTHER', 'REDELIVERY', 'PLANNED_STABLING')),
    ADD CONSTRAINT ck_cost_item_recorded CHECK (recorded_by IS NOT NULL OR
        (category = 'STABLING' AND horse_stay_id IS NOT NULL AND stabling_days IS NOT NULL) OR category = 'REDELIVERY' OR (category = 'PLANNED_STABLING' AND horse_stay_id IS NOT NULL)),
    ADD CONSTRAINT ck_cost_item_approved CHECK ((approved_by IS NULL) = (approved_at IS NULL)
        OR (approved_by IS NULL AND recorded_by IS NULL AND approved_at IS NOT NULL)),
    ADD CONSTRAINT ck_cost_item_redelivery CHECK (category <> 'REDELIVERY' OR (vehicle_trip_id IS NOT NULL AND booking_id IS NOT NULL));

CREATE UNIQUE INDEX ux_cost_item_redelivery_trip_booking ON cost_item (vehicle_trip_id, booking_id)
    WHERE category = 'REDELIVERY';

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value) VALUES
    ('STABLING_REDELIVERY_OFFER_HOURS', '24', 'HOUR', 'HANDOVER', 'Thời hạn đưa khung giao lại kể từ lúc khách sẵn sàng', 1, 24),
    ('STABLING_COLLECTION_ADVANCE_HOURS', '24', 'HOUR', 'HANDOVER', 'Khoảng báo trước tối đa khi khách tự đến hub', 1, 24),
    ('STABLING_COLLECTION_GRACE_HOURS', '2', 'HOUR', 'HANDOVER', 'Thời gian chờ thêm sau giờ báo tự đến hub', 1, 24);
