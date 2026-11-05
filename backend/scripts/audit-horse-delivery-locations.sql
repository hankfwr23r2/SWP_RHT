-- PostgreSQL 16+. Read-only report: no data is changed.
-- Use the latest recorded handover, not merely an order's completed status.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '15s';

WITH booking_data AS (
    SELECT bookingid, booking_type, dropoff_address,
           CASE WHEN pg_input_is_valid(details_json, 'jsonb')
                THEN details_json::jsonb ELSE '{}'::jsonb END AS details
    FROM booking
), delivery_events AS (
    SELECT b.bookingid, b.booking_type, b.dropoff_address,
           b.details ->> 'destCountry' AS destination_country,
           t -> 'run' ->> 'deliveredAt' AS delivered_at,
           CASE WHEN jsonb_typeof(t -> 'horseIds') = 'array'
                     AND jsonb_array_length(t -> 'horseIds') > 0 THEN t -> 'horseIds'
                WHEN jsonb_array_length(b.details -> 'trips') = 1 THEN
                     (SELECT jsonb_agg(h -> 'horseId')
                      FROM jsonb_array_elements(CASE WHEN jsonb_typeof(b.details -> 'horses') = 'array'
                          THEN b.details -> 'horses' ELSE '[]'::jsonb END) h)
                ELSE '[]'::jsonb END AS horse_ids
    FROM booking_data b
    CROSS JOIN LATERAL jsonb_array_elements(
        CASE WHEN jsonb_typeof(b.details -> 'trips') = 'array'
             THEN b.details -> 'trips' ELSE '[]'::jsonb END) t
    WHERE t -> 'run' ->> 'deliveredAt' ~ '^[0-9]{1,15}$'
    UNION ALL
    SELECT b.bookingid, b.booking_type, b.dropoff_address,
           b.details ->> 'destCountry', b.details -> 'handover' ->> 'handoverAt',
           (SELECT jsonb_agg(h -> 'horseId') FROM jsonb_array_elements(CASE WHEN jsonb_typeof(b.details -> 'horses') = 'array'
                          THEN b.details -> 'horses' ELSE '[]'::jsonb END) h)
    FROM booking_data b
    WHERE b.details -> 'handover' ->> 'handoverAt' ~ '^[0-9]{1,15}$'
), horse_deliveries AS (
    SELECT e.*, regexp_replace(reference, '[^0-9]', '', 'g') AS horse_reference,
           CASE upper(trim(destination_country))
               WHEN 'VN' THEN 'VN' WHEN 'VIỆT NAM' THEN 'VN'
               WHEN 'VIETNAM' THEN 'VN' WHEN 'VIET NAM' THEN 'VN'
               WHEN 'KH' THEN 'KH' WHEN 'CAMPUCHIA' THEN 'KH' WHEN 'CAMBODIA' THEN 'KH'
               WHEN 'LA' THEN 'LA' WHEN 'LÀO' THEN 'LA' WHEN 'LAOS' THEN 'LA' WHEN 'LAO' THEN 'LA'
               ELSE CASE WHEN nullif(trim(destination_country), '') IS NULL
                              AND lower(booking_type) = 'domestic' THEN 'VN' END
           END AS expected_country
    FROM delivery_events e
    CROSS JOIN LATERAL jsonb_array_elements_text(coalesce(e.horse_ids, '[]'::jsonb)) reference
), latest AS (
    SELECT DISTINCT ON (horseid) h.*, d.bookingid, d.dropoff_address,
           d.expected_country, d.delivered_at::bigint AS delivered_at
    FROM horse h
    JOIN horse_deliveries d ON ltrim(d.horse_reference, '0') = h.horseid::text
    ORDER BY horseid, d.delivered_at::bigint DESC, d.bookingid DESC
)
SELECT horseid, bookingid, current_location_name, current_country, location_source,
       location_updated_at, dropoff_address AS proposed_location,
       expected_country AS proposed_country, delivered_at,
       CASE
           WHEN location_source IN ('OWNER_DECLARED', 'OWNER_RELOCATION')
               THEN 'REVIEW_OWNER_DECLARATION'
           WHEN transit_status = 'IN_TRANSIT' OR location_updated_at > delivered_at
               THEN 'REVIEW_NEWER_OR_ACTIVE_LOCATION'
           WHEN expected_country IS NULL THEN 'REVIEW_DESTINATION_COUNTRY'
           WHEN nullif(trim(dropoff_address), '') IS NULL THEN 'REVIEW_DESTINATION_ADDRESS'
           ELSE 'CANDIDATE_FOR_REPAIR'
       END AS review_status
FROM latest
WHERE current_country IS DISTINCT FROM expected_country
   OR location_source IS NULL OR location_updated_at IS NULL
   OR current_location_name IS DISTINCT FROM dropoff_address
ORDER BY horseid;

-- No delivery evidence means the location needs manual verification.
SELECT horseid, current_location_name, current_country, location_source, transit_status
FROM horse
WHERE nullif(trim(current_location_name), '') IS NOT NULL
  AND (current_country IS NULL OR upper(trim(current_country)) NOT IN ('VN', 'KH', 'LA'))
ORDER BY horseid;

ROLLBACK;
