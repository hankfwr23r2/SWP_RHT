ALTER TABLE system_config
    ADD COLUMN min_value int,
    ADD COLUMN max_value int,
    ADD CONSTRAINT ck_system_config_limits CHECK (
        (min_value IS NULL AND max_value IS NULL)
        OR (min_value IS NOT NULL AND max_value IS NOT NULL AND min_value <= max_value)
    );

DO $$
DECLARE
    config record;
    value_text text;
BEGIN
    FOR config IN
        UPDATE system_config AS sc
        SET min_value = limits.min_value, max_value = limits.max_value
        FROM (VALUES
            ('DEPARTURE_GENERATION_WEEKS', 1, 52),
            ('OTP_EXPIRY_MINUTES', 1, 30),
            ('OTP_MAX_ATTEMPTS', 1, 10),
            ('QUOTATION_VALID_HOURS', 1, 720),
            ('BALANCE_DUE_DAYS_BEFORE_PICKUP', 1, 30),
            ('HANDOVER_FREE_WAIT_MINUTES', 1, 1440)
        ) AS limits(config_key, min_value, max_value)
        WHERE sc.config_key = limits.config_key
        RETURNING sc.config_key, sc.config_value, sc.min_value, sc.max_value
    LOOP
        value_text := btrim(config.config_value, E' \t\n\r\f\013');
        IF value_text !~ '^[+-]?[0-9]+$' THEN
            RAISE EXCEPTION 'system_config % must contain an integer', config.config_key;
        END IF;

        -- numeric can represent every integer in the varchar(500) column without overflow.
        IF value_text::numeric NOT BETWEEN config.min_value AND config.max_value THEN
            RAISE EXCEPTION 'system_config % must be between % and %',
                config.config_key, config.min_value, config.max_value;
        END IF;
    END LOOP;
END;
$$;
