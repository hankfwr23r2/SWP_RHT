-- Run immediately after 001, with all backend instances still stopped.
-- A pre-cutover backup and successful restore rehearsal are required.
BEGIN;
LOCK TABLE email_verification_otps, password_reset_otps, location_change_log,
    document_requirement_change_log, email_otp, system_log IN ACCESS EXCLUSIVE MODE;
DO $$ BEGIN
    IF EXISTS (
        SELECT 1 FROM (
            SELECT lower(btrim(email)) email,'VERIFY_EMAIL' purpose,otp_hash,expires_at,last_sent_at,attempts FROM email_verification_otps
            UNION ALL
            SELECT lower(btrim(email)),'RESET_PASSWORD',otp_hash,expires_at,last_sent_at,attempts FROM password_reset_otps
        ) o LEFT JOIN email_otp n USING(email,purpose)
        WHERE n.id IS NULL OR (n.otp_hash,n.expires_at,n.last_sent_at,n.attempts)
            IS DISTINCT FROM (o.otp_hash,o.expires_at,o.last_sent_at,o.attempts)
    ) THEN RAISE EXCEPTION 'OTP reconciliation failed'; END IF;
    IF EXISTS (SELECT 1 FROM location_change_log c WHERE NOT EXISTS (
        SELECT 1 FROM system_log s WHERE s.source_table='location_change_log' AND s.source_id=c.id
        AND s.target_recordid=c.locationid AND s.created_at=c.changed_at AND s.actor_name=c.changed_by
        AND s.old_data::jsonb=jsonb_build_object(c.field_name,c.old_value)
        AND s.new_data::jsonb=jsonb_build_object(c.field_name,c.new_value)))
    OR EXISTS (SELECT 1 FROM document_requirement_change_log c WHERE NOT EXISTS (
        SELECT 1 FROM system_log s WHERE s.source_table='document_requirement_change_log' AND s.source_id=c.id
        AND s.target_recordid=c.requirement_id AND s.created_at=c.changed_at AND s.actor_name=c.changed_by
        AND s.old_data::jsonb=jsonb_build_object(c.field_name,c.old_value)
        AND s.new_data::jsonb=jsonb_build_object(c.field_name,c.new_value))) THEN
        RAISE EXCEPTION 'Legacy audit reconciliation failed';
    END IF;
END $$;
DROP TABLE email_verification_otps;
DROP TABLE password_reset_otps;
DROP TABLE location_change_log;
DROP TABLE document_requirement_change_log;
COMMIT;
