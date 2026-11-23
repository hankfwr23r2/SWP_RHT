package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseHealthLog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorseHealthLogRepository extends JpaRepository<HorseHealthLog, Long> {

    @EntityGraph(attributePaths = {"recordedBy", "tripStop", "tripStop.location", "horseStay", "horseStay.location"})
    List<HorseHealthLog> findByBookingHorse_IdOrderByRecordedAtDesc(Long bookingHorseId);

    @EntityGraph(attributePaths = {"recordedBy", "bookingHorse", "bookingHorse.horse"})
    List<HorseHealthLog> findByTripStop_IdOrderByRecordedAt(Long tripStopId);

    // Lần ghi gần nhất của con ngựa ở nơi khác (điểm dừng hoặc lưu trú trước)
    Optional<HorseHealthLog> findFirstByBookingHorse_IdOrderByRecordedAtDesc(Long bookingHorseId);

    // Báo cáo chuyến: các lần ghi sức khỏe trên một lượt xe
    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse"})
    List<HorseHealthLog> findByVehicleTrip_IdOrderByRecordedAt(Long vehicleTripId);
}
