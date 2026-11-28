ALTER TABLE handover_horse
    ADD COLUMN microchip_entry_method varchar(10) NOT NULL DEFAULT 'SCAN',
    ADD COLUMN manual_entry_reason varchar(500),
    ADD CONSTRAINT ck_handover_horse_microchip_entry_method
        CHECK (microchip_entry_method IN ('SCAN', 'MANUAL')),
    ADD CONSTRAINT ck_handover_horse_manual_entry_reason
        CHECK (
            (microchip_entry_method = 'SCAN' AND manual_entry_reason IS NULL)
            OR (microchip_entry_method = 'MANUAL' AND manual_entry_reason IS NOT NULL
                AND manual_entry_reason ~ '[^[:space:]]')
        );
