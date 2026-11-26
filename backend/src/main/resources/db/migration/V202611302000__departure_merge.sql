ALTER TABLE departure
    ADD COLUMN merged_into_departure_id bigint,
    ADD CONSTRAINT fk_departure_merged_into_departure_id FOREIGN KEY (merged_into_departure_id) REFERENCES departure (id),
    ADD CONSTRAINT ck_departure_merge CHECK (merged_into_departure_id IS NULL OR (
        status = 'CANCELLED' AND departure_schedule_id IS NOT NULL AND merged_into_departure_id <> id
    ));
