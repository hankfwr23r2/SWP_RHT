-- Run after restoring data, with application writers stopped.
-- Repairs ascending, non-cycling SERIAL/IDENTITY sequences owned by public columns.
-- Existing rows, empty tables and sequences already ahead of the data are unchanged.
-- ALTER SEQUENCE RESTART is transactional and invalidates previously cached values.
DO $repair_sequences$
DECLARE
    target record;
    maximum_id numeric;
    sequence_value numeric;
    sequence_called boolean;
    next_value numeric;
    restart_value numeric;
BEGIN
    PERFORM set_config('lock_timeout', '10s', true);

    FOR target IN
        SELECT tables.relname AS table_name,
               columns.attname AS column_name,
               format('%I.%I', sequence_schema.nspname, sequences.relname) AS sequence_name,
               settings.seqincrement AS increment_by
        FROM pg_class sequences
        JOIN pg_namespace sequence_schema ON sequence_schema.oid = sequences.relnamespace
        JOIN pg_sequence settings ON settings.seqrelid = sequences.oid
        JOIN pg_depend ownership ON ownership.classid = 'pg_class'::regclass
            AND ownership.objid = sequences.oid
            AND ownership.refclassid = 'pg_class'::regclass
            AND ownership.deptype IN ('a', 'i')
        JOIN pg_class tables ON tables.oid = ownership.refobjid
        JOIN pg_namespace table_schema ON table_schema.oid = tables.relnamespace
        JOIN pg_attribute columns ON columns.attrelid = tables.oid
            AND columns.attnum = ownership.refobjsubid
        WHERE sequences.relkind = 'S'
            AND table_schema.nspname = 'public'
            AND settings.seqincrement > 0
            AND NOT settings.seqcycle
        ORDER BY tables.relname, columns.attname
    LOOP
        -- Block INSERT/UPDATE/DELETE while reading the table and restarting its sequence.
        EXECUTE format('LOCK TABLE public.%I IN SHARE ROW EXCLUSIVE MODE', target.table_name);
        EXECUTE format('SELECT max(%I) FROM public.%I', target.column_name, target.table_name)
            INTO maximum_id;
        IF maximum_id IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('SELECT last_value, is_called FROM %s', target.sequence_name)
            INTO sequence_value, sequence_called;
        next_value := sequence_value + CASE WHEN sequence_called THEN target.increment_by ELSE 0 END;

        IF next_value <= maximum_id THEN
            -- Keep the configured increment and its progression, including increments other than 1.
            restart_value := next_value
                + (floor((maximum_id - next_value) / target.increment_by) + 1) * target.increment_by;
            EXECUTE format('ALTER SEQUENCE %s RESTART WITH %s', target.sequence_name, restart_value);
            RAISE NOTICE 'Repaired %.%: next ID = %', target.table_name, target.column_name, restart_value;
        END IF;
    END LOOP;
END;
$repair_sequences$;
