DROP INDEX uk_incident_complaint_booking;

CREATE UNIQUE INDEX uk_incident_open_complaint_booking ON incident (booking_id)
    WHERE incident_type = 'COMPLAINT' AND status <> 'CLOSED';
