package com.example.racehorse_transport.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TripReturnPhotoFixtures {
    private TripReturnPhotoFixtures() {}

    public static void attach(JdbcTemplate jdbc, long tripId) {
        jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, vehicle_trip_id)
                SELECT id, 'test://return/' || md5(random()::text), 'return.jpg', (SELECT min(id) FROM app_user), 'UPLOADED', ?
                FROM document_type WHERE code = 'VEHICLE_RETURN_PHOTO'
                """, tripId);
    }
}
