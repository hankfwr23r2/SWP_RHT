ALTER TABLE handover ADD COLUMN unclaimed_outcome varchar(40),
    ADD CONSTRAINT ck_handover_unclaimed_outcome CHECK (
        unclaimed_outcome IS NULL OR (
            unclaimed_outcome IN ('RETURNED_TO_SENDER', 'RETURNED_TO_AUTHORIZED_PERSON', 'RELEASED_BY_LEGAL_DOCUMENT')
            AND handover_type = 'RETURN' AND horse_stay_id IS NOT NULL AND status = 'DONE'
        )
    );
