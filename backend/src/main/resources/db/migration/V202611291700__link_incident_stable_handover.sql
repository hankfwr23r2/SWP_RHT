ALTER TABLE handover
    ADD COLUMN incident_plan_id bigint REFERENCES incident_plan (id),
    ADD CONSTRAINT ck_handover_incident_plan CHECK (
        incident_plan_id IS NULL OR (
            handover_type = 'STAY_IN' AND trip_stop_id IS NOT NULL AND to_party_id IS NOT NULL
        )
    );

CREATE UNIQUE INDEX uk_handover_incident_plan
    ON handover (incident_plan_id)
    WHERE incident_plan_id IS NOT NULL AND status IN ('PENDING', 'DONE');
