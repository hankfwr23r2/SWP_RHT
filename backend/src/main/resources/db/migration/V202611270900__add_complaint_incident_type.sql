ALTER TABLE incident
    DROP CONSTRAINT ck_incident_incident_type,
    ALTER COLUMN reported_by DROP NOT NULL,
    ALTER COLUMN cause_type DROP NOT NULL,
    ADD CONSTRAINT ck_incident_incident_type CHECK (
        incident_type IN ('HEALTH', 'VEHICLE_BREAKDOWN', 'ACCIDENT', 'DELAY',
                          'DOCUMENT', 'HANDOVER_TIMEOUT', 'OTHER', 'COMPLAINT')
    ),
    ADD CONSTRAINT ck_incident_complaint_booking CHECK (
        incident_type <> 'COMPLAINT' OR booking_id IS NOT NULL
    ),
    ADD CONSTRAINT ck_incident_operational_fields CHECK (
        incident_type = 'COMPLAINT' OR (reported_by IS NOT NULL AND cause_type IS NOT NULL)
    );
