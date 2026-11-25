-- Older approved plans implicitly used the cause on the incident.
WITH latest_decision AS (
    SELECT DISTINCT ON (incident_id) incident_id, decided_by, decided_at
    FROM incident_plan
    WHERE status IN ('APPROVED', 'DONE') AND decided_at IS NOT NULL
    ORDER BY incident_id, version DESC
)
UPDATE incident i
SET confirmed_cause_type = i.cause_type,
    cause_confirmed_at = d.decided_at,
    cause_confirmed_by = d.decided_by
FROM latest_decision d
WHERE d.incident_id = i.id AND i.cause_confirmed_at IS NULL;

-- Legacy pending staff flags are not customer consent. Approved costs retain their snapshot.
UPDATE cost_item c
SET is_customer_consulted = false,
    customer_response_due_at = CURRENT_TIMESTAMP + make_interval(mins => settings.config_value::integer)
FROM system_config settings
WHERE settings.config_key = 'CUSTOMER_COST_RESPONSE_MINUTES'
  AND c.approved_at IS NULL AND c.customer_response_at IS NULL AND c.customer_response_due_at IS NULL
  AND c.booking_id IS NOT NULL AND c.payer_type IN ('CUSTOMER', 'CONSIGNEE')
  AND NOT EXISTS (
      SELECT 1 FROM horse_stay s
      WHERE s.id = c.horse_stay_id AND s.stay_type = 'STABLING' AND c.category = 'STABLING'
  );

UPDATE cost_item c
SET agent_recovery_required = true
FROM incident i
WHERE i.id = c.incident_id AND c.payer_type = 'COMPANY'
  AND coalesce(i.confirmed_cause_type, i.cause_type) = 'AGENT_FAULT';
