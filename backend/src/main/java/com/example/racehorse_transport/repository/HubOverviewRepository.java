package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.ScheduleStatus;
import com.example.racehorse_transport.entity.enums.VehicleServiceRole;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.entity.enums.VehicleTripStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

// Truy vấn chỉ đọc cho màn Các hub. Mỗi lần đọc lấy dữ liệu cho tất cả hub đang hiển thị.
public interface HubOverviewRepository extends Repository<Location, Long> {
    @Query("""
            SELECT s FROM HorseStay s JOIN FETCH s.location
            JOIN FETCH s.bookingHorse line JOIN FETCH line.booking JOIN FETCH line.horse
            WHERE s.location.id IN :hubIds AND s.actualStartAt IS NOT NULL AND s.actualEndAt IS NULL
            ORDER BY s.actualStartAt, s.id
            """)
    List<HorseStay> openStays(Collection<Long> hubIds);

    interface StallCounts {
        Long getHubId();
        long getTotal();
        long getAvailable();
    }

    @Query("""
            SELECT stall.location.id AS hubId, COUNT(stall.id) AS total, COUNT(stall.id) - COUNT(stay.id) AS available
            FROM HubStall stall LEFT JOIN HorseStay stay ON stay.hubStall = stall AND stay.location = stall.location
                AND stay.actualStartAt IS NOT NULL AND stay.actualEndAt IS NULL
            WHERE stall.location.id IN :hubIds AND stall.active = true
            GROUP BY stall.location.id
            """)
    List<StallCounts> stallCounts(Collection<Long> hubIds);

    interface StaffCount {
        Long getHubId();
        String getRoleCode();
        long getStaffCount();
    }

    @Query("""
            SELECT s.homeHubLocation.id AS hubId, s.user.role.code AS roleCode, COUNT(s) AS staffCount
            FROM Staff s WHERE s.homeHubLocation.id IN :hubIds AND s.employmentStatus = :working
            GROUP BY s.homeHubLocation.id, s.user.role.code
            ORDER BY s.user.role.code
            """)
    List<StaffCount> staffCounts(Collection<Long> hubIds, EmploymentStatus working);

    interface FleetScheduleRun {
        long getOriginHubId();
        long getDestinationHubId();
        boolean isOutbound();
        int getDurationMinutes();
        short getDayOfWeek();
        LocalTime getDepartureTime();
        int getVehicleCount();
        int getMaxStallCount();
    }

    @Query("""
            SELECT r.origin.id AS originHubId, r.destination.id AS destinationHubId, r.fleetOutbound AS outbound,
                r.estimatedDurationMinutes AS durationMinutes, sd.id.dayOfWeek AS dayOfWeek,
                s.departureTime AS departureTime, s.baseVehicleCount AS vehicleCount,
                s.baseVehicleClass.maxStallCount AS maxStallCount
            FROM DepartureScheduleDay sd JOIN sd.departureSchedule s JOIN s.route r
            WHERE s.status = :scheduleStatus AND r.status = :routeStatus
                AND s.validFrom <= :today AND (s.validTo IS NULL OR s.validTo >= :today)
            """)
    List<FleetScheduleRun> fleetScheduleRuns(LocalDate today, ScheduleStatus scheduleStatus, ActiveStatus routeStatus);

    interface FleetVehicleCount {
        Long getHubId();
        VehicleServiceRole getServiceRole();
        long getVehicleCount();
    }

    @Query("""
            SELECT v.homeHubLocation.id AS hubId, v.serviceRole AS serviceRole, COUNT(v) AS vehicleCount
            FROM Vehicle v WHERE v.homeHubLocation.id IN :hubIds AND v.status IN :statuses
            GROUP BY v.homeHubLocation.id, v.serviceRole
            """)
    List<FleetVehicleCount> fleetVehicleCounts(Collection<Long> hubIds, Collection<VehicleStatus> statuses);

    // Chỉ lấy lượt đang chạy và lượt đã kết thúc gần nhất của mỗi xe.
    @Query("""
            SELECT t FROM VehicleTrip t JOIN FETCH t.vehicle v JOIN FETCH t.startLocation JOIN FETCH t.endLocation
            WHERE t.status = :running OR (t.status = :completed AND NOT EXISTS (
                SELECT later.id FROM VehicleTrip later WHERE later.vehicle = v AND later.status = :completed
                AND (COALESCE(later.actualEndAt, later.plannedEndAt) > COALESCE(t.actualEndAt, t.plannedEndAt)
                    OR (COALESCE(later.actualEndAt, later.plannedEndAt) = COALESCE(t.actualEndAt, t.plannedEndAt)
                        AND later.id > t.id))))
            ORDER BY v.licensePlate, t.id
            """)
    List<VehicleTrip> currentVehicleTrips(VehicleTripStatus running, VehicleTripStatus completed);

    @Query("""
            SELECT d FROM Departure d JOIN FETCH d.route r JOIN FETCH r.origin o JOIN FETCH r.destination dest
            WHERE (o.id IN :hubIds OR dest.id IN :hubIds)
                AND COALESCE(d.revisedDepartureAt, d.plannedDepartureAt) >= :from
                AND COALESCE(d.revisedDepartureAt, d.plannedDepartureAt) < :to
            ORDER BY COALESCE(d.revisedDepartureAt, d.plannedDepartureAt), d.id
            """)
    List<Departure> departures(Collection<Long> hubIds, Instant from, Instant to);

    interface HubIncident {
        Long getHubId();
        Incident getIncident();
    }

    @Query("""
            SELECT hub.id AS hubId, i AS incident FROM Incident i
            LEFT JOIN i.vehicleTrip trip LEFT JOIN i.horseLeg leg LEFT JOIN leg.vehicleTrip legTrip
            JOIN Location hub ON hub.id IN :hubIds
            WHERE i.status <> :closed AND (
                hub.id = COALESCE(trip.startLocation.id, legTrip.startLocation.id)
                OR hub.id = COALESCE(trip.endLocation.id, legTrip.endLocation.id)
                OR EXISTS (SELECT s.id FROM HorseStay s WHERE s.location = hub
                    AND s.actualStartAt IS NOT NULL AND s.actualEndAt IS NULL
                    AND s.bookingHorse.id = COALESCE(i.bookingHorse.id, leg.bookingHorse.id)))
            ORDER BY i.reportedAt DESC, i.id DESC
            """)
    List<HubIncident> openIncidents(Collection<Long> hubIds, IncidentStatus closed);
}
