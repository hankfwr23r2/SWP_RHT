ALTER TABLE booking_horse_service ADD COLUMN completed_at timestamptz;

ALTER TABLE quotation_line ADD COLUMN pickup_deposit_floor boolean NOT NULL DEFAULT false;
ALTER TABLE quotation_line ADD CONSTRAINT ck_quotation_line_pickup_deposit_floor
    CHECK (NOT pickup_deposit_floor OR (line_type = 'ADJUSTMENT' AND NOT is_pass_through AND unit_price > 0));
