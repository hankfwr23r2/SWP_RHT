package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.DepartureVehicle;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepartureVehicleRepository extends JpaRepository<DepartureVehicle, Long> {
    @Query("""
            select a from DepartureVehicle a join fetch a.vehicleClass
            where a.departure.id = :departureId order by a.id
            """)
    List<DepartureVehicle> findAllotments(Long departureId);

    interface SpareStock {
        Long getVehicleClassId();
        int getStalls();
        int getVehicleCount();
    }

    @Query(value = """
            SELECT vc.id AS "vehicleClassId", vc.max_stall_count AS stalls,
                   CAST(count(*) - coalesce(busy.vehicle_count, 0) AS int) AS "vehicleCount"
            FROM vehicle v JOIN vehicle_class vc ON vc.id = v.vehicle_class_id
            LEFT JOIN (
                SELECT a.vehicle_class_id, sum(a.vehicle_count) AS vehicle_count
                FROM departure_vehicle a
                JOIN departure d ON d.id = a.departure_id
                JOIN route r ON r.id = d.route_id
                WHERE a.allotment_type = 'SPARE' AND d.status <> 'CANCELLED'
                  AND r.origin_location_id = :hubId
                  AND d.planned_departure_at <= :busyUntil
                  AND d.planned_departure_at
                      + (2 * r.estimated_duration_minutes) * interval '1 minute'
                      + :restHours * interval '1 hour' >= :startsAt
                GROUP BY a.vehicle_class_id
            ) busy ON busy.vehicle_class_id = vc.id
            WHERE v.home_hub_location_id = :hubId AND v.status = 'ACTIVE' AND v.service_role = 'SPARE'
            GROUP BY vc.id, busy.vehicle_count
            HAVING count(*) > coalesce(busy.vehicle_count, 0)
            """, nativeQuery = true)
    List<SpareStock> findSpareStock(Long hubId, Instant startsAt, Instant busyUntil, int restHours);
}
