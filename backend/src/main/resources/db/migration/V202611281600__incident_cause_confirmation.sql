ALTER TABLE incident
    ADD COLUMN confirmed_cause_type varchar(30),
    ADD COLUMN cause_confirmed_by bigint REFERENCES staff(user_id),
    ADD COLUMN cause_confirmed_at timestamptz,
    ADD CONSTRAINT ck_incident_confirmed_cause CHECK (
        confirmed_cause_type IN ('COMPANY_FAULT', 'AGENT_FAULT', 'FORCE_MAJEURE',
                                 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'RECIPIENT_ABSENT')),
    ADD CONSTRAINT ck_incident_cause_confirmation CHECK (
        (confirmed_cause_type IS NULL AND cause_confirmed_at IS NULL AND cause_confirmed_by IS NULL)
        OR (confirmed_cause_type IS NOT NULL AND cause_confirmed_at IS NOT NULL));

UPDATE incident
SET confirmed_cause_type = cause_type, cause_confirmed_at = reported_at
WHERE incident_type = 'HANDOVER_TIMEOUT';

ALTER TABLE cost_item
    ADD COLUMN customer_agreed boolean,
    ADD COLUMN customer_response_by bigint REFERENCES app_user(id),
    ADD COLUMN customer_response_at timestamptz,
    ADD COLUMN customer_response_due_at timestamptz,
    ADD COLUMN phone_consent_note varchar(500),
    ADD COLUMN emergency_override_reason varchar(500),
    ADD COLUMN agent_recovery_required boolean NOT NULL DEFAULT false,
    ADD COLUMN agent_recovered_at timestamptz,
    ADD CONSTRAINT ck_cost_item_customer_response CHECK (
        (customer_agreed IS NULL AND customer_response_by IS NULL AND customer_response_at IS NULL)
        OR (customer_agreed IS NOT NULL AND customer_response_by IS NOT NULL AND customer_response_at IS NOT NULL)),
    ADD CONSTRAINT ck_cost_item_agent_recovery CHECK (agent_recovered_at IS NULL OR agent_recovery_required);

ALTER TABLE incident_plan
    ADD COLUMN customer_agreed_by bigint REFERENCES app_user(id),
    ADD COLUMN customer_agreed_at timestamptz,
    ADD CONSTRAINT ck_incident_plan_customer_agreement CHECK (
        (customer_agreed_by IS NULL) = (customer_agreed_at IS NULL));

INSERT INTO system_config (config_key, config_value, unit, config_group, description, min_value, max_value)
VALUES ('CUSTOMER_COST_RESPONSE_MINUTES', '15', 'MINUTE', 'PAYMENT', 'Thời gian khách trả lời chi phí sự cố', 5, 1440);
