-- Retire obsolete border handovers and agent-only legs. Remove their evidence
-- and scans first; keep financial and incident records with nullable links cleared.
CREATE TEMP TABLE retired_agent_leg ON COMMIT DROP AS
SELECT id FROM horse_leg WHERE agent_party_id IS NOT NULL AND vehicle_trip_id IS NULL;

CREATE TEMP TABLE retired_agent_handover ON COMMIT DROP AS
SELECT id FROM handover WHERE handover_type = 'AGENT';

CREATE TEMP TABLE retired_agent_document ON COMMIT DROP AS
SELECT id FROM document
WHERE handover_id IN (SELECT id FROM retired_agent_handover)
   OR horse_leg_id IN (SELECT id FROM retired_agent_leg)
   OR evidence_horse_leg_id IN (SELECT id FROM retired_agent_leg);

DELETE FROM handover_document WHERE document_id IN (SELECT id FROM retired_agent_document);
DELETE FROM document WHERE id IN (SELECT id FROM retired_agent_document);
UPDATE cost_item SET handover_id = NULL WHERE handover_id IN (SELECT id FROM retired_agent_handover);
DELETE FROM handover WHERE id IN (SELECT id FROM retired_agent_handover) AND handover_type = 'AGENT';
DELETE FROM handover_horse WHERE horse_leg_id IN (SELECT id FROM retired_agent_leg);
UPDATE quotation_line SET horse_leg_id = NULL WHERE horse_leg_id IN (SELECT id FROM retired_agent_leg);
UPDATE incident i
SET horse_leg_id = NULL, booking_id = coalesce(i.booking_id, bh.booking_id)
FROM horse_leg l JOIN booking_horse bh ON bh.id = l.booking_horse_id
WHERE i.horse_leg_id = l.id AND l.id IN (SELECT id FROM retired_agent_leg);
UPDATE horse_stay SET failed_delivery_leg_id = NULL WHERE failed_delivery_leg_id IN (SELECT id FROM retired_agent_leg);
UPDATE horse_leg SET replaced_horse_leg_id = NULL WHERE replaced_horse_leg_id IN (SELECT id FROM retired_agent_leg);
DELETE FROM horse_leg WHERE id IN (SELECT id FROM retired_agent_leg) AND agent_party_id IS NOT NULL;

ALTER TABLE horse_leg
    DROP CONSTRAINT ck_horse_leg_carrier,
    DROP CONSTRAINT ck_horse_leg_agent_stops,
    DROP CONSTRAINT ck_horse_leg_agent_stall_position,
    DROP COLUMN agent_party_id,
    ALTER COLUMN vehicle_trip_id SET NOT NULL;

ALTER TABLE handover DROP CONSTRAINT ck_handover_handover_type;
ALTER TABLE handover ADD CONSTRAINT ck_handover_handover_type CHECK (
    handover_type IN ('PICKUP', 'HUB_IN', 'HUB_OUT', 'STAY_IN', 'STAY_OUT', 'DELIVERY', 'RETURN', 'SHIFT'));

DELETE FROM party_role WHERE role_type = 'DEST_AGENT';
ALTER TABLE party_role DROP CONSTRAINT ck_party_role_role_type;
ALTER TABLE party_role ADD CONSTRAINT ck_party_role_role_type CHECK (
    role_type IN ('QUARANTINE_FARM', 'STABLE', 'VET', 'RESCUE', 'INSURER'));

UPDATE document_requirement SET submitter_type = 'COMPANY' WHERE submitter_type = 'AGENT';
ALTER TABLE document_requirement DROP CONSTRAINT ck_document_requirement_submitter_type;
ALTER TABLE document_requirement ADD CONSTRAINT ck_document_requirement_submitter_type CHECK (
    submitter_type IN ('CUSTOMER', 'CONSIGNEE', 'COMPANY'));

UPDATE cost_item SET payer_type = 'COMPANY', extra_charge_payment_id = NULL WHERE payer_type = 'AGENT';
ALTER TABLE cost_item DROP CONSTRAINT ck_cost_item_payer_type;
ALTER TABLE cost_item ADD CONSTRAINT ck_cost_item_payer_type CHECK (payer_type IN ('COMPANY', 'CUSTOMER', 'CONSIGNEE'));

UPDATE incident SET cause_type = 'COMPANY_FAULT' WHERE cause_type = 'AGENT_FAULT';
UPDATE incident SET confirmed_cause_type = 'COMPANY_FAULT' WHERE confirmed_cause_type = 'AGENT_FAULT';
ALTER TABLE incident DROP CONSTRAINT ck_incident_cause_type, DROP CONSTRAINT ck_incident_confirmed_cause;
ALTER TABLE incident
    ADD CONSTRAINT ck_incident_cause_type CHECK (
        cause_type IN ('COMPANY_FAULT', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'FORCE_MAJEURE', 'RECIPIENT_ABSENT')),
    ADD CONSTRAINT ck_incident_confirmed_cause CHECK (
        confirmed_cause_type IN ('COMPANY_FAULT', 'HORSE_CONDITION', 'CUSTOMER_DOCS', 'FORCE_MAJEURE', 'RECIPIENT_ABSENT'));
