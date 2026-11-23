ALTER TABLE system_config ADD COLUMN audit_target_id bigint GENERATED ALWAYS AS IDENTITY;
ALTER TABLE system_config ADD CONSTRAINT uk_system_config_audit_target_id UNIQUE (audit_target_id);
