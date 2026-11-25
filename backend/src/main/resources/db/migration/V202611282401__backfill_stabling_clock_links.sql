-- Gắn lại các mốc nguồn cho lưu trú đang mở trước khi đồng hồ mới được triển khai.
UPDATE horse_stay AS s
SET failed_delivery_leg_id = (
    SELECT l.id
    FROM horse_leg AS l
    WHERE l.booking_horse_id = s.booking_horse_id
      AND l.status = 'FAILED'
      AND l.leg_type IN ('DELIVERY', 'DIRECT')
      AND l.actual_end_at <= s.actual_start_at
      AND EXISTS (
          SELECT 1 FROM incident AS i
          WHERE i.horse_leg_id = l.id
            AND i.incident_type = 'HANDOVER_TIMEOUT'
            AND i.confirmed_cause_type = 'RECIPIENT_ABSENT'
      )
      AND NOT EXISTS (
          SELECT 1 FROM horse_leg AS completed
          WHERE completed.booking_horse_id = l.booking_horse_id
            AND completed.sequence_number > l.sequence_number
            AND completed.status = 'COMPLETED'
            AND completed.actual_end_at <= s.actual_start_at
      )
    ORDER BY l.sequence_number DESC
    LIMIT 1
)
WHERE s.stay_type = 'STABLING'
  AND s.actual_start_at IS NOT NULL AND s.actual_end_at IS NULL
  AND s.balance_hold_at IS NULL AND s.failed_delivery_leg_id IS NULL;

UPDATE horse_stay AS s
SET missed_departure_id = (
    SELECT h.previous_departure_id
    FROM booking_status_history AS h
    JOIN departure AS d ON d.id = h.previous_departure_id
    WHERE h.booking_id = bh.booking_id
      AND d.planned_departure_at = s.balance_hold_at
    ORDER BY h.changed_at DESC, h.id DESC
    LIMIT 1
)
FROM booking_horse AS bh
WHERE s.booking_horse_id = bh.id AND s.balance_hold_at IS NOT NULL
  AND s.actual_start_at IS NOT NULL AND s.actual_end_at IS NULL
  AND s.missed_departure_id IS NULL;
