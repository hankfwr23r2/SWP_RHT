package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HorseLegRepository extends JpaRepository<HorseLeg, Long> {
    List<HorseLeg> findByBookingHorse_IdOrderBySequenceNumber(Long bookingHorseId);

    boolean existsByVehicleTrip_IdAndStatusNot(Long vehicleTripId, HorseLegStatus status);

    // Chặng của các con ngựa (kèm lượt xe và điểm lên xuống) để hiện ở bảng điều độ
    @EntityGraph(attributePaths = {"vehicleTrip", "fromLocation", "toLocation", "fromBookingAddress", "toBookingAddress", "boardTripStop", "alightTripStop"})
    List<HorseLeg> findByBookingHorse_IdInOrderByBookingHorse_IdAscSequenceNumberAsc(Collection<Long> bookingHorseIds);

    // Ngựa đang trên một lượt xe (chưa hủy, chưa thất bại): kiểm sức chứa và hiện danh sách
    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.stallType", "bookingHorse.booking",
            "boardTripStop", "alightTripStop", "fromLocation", "toLocation", "fromBookingAddress", "toBookingAddress", "vehicleTrip"})
    @Query("SELECT l FROM HorseLeg l WHERE l.vehicleTrip.id = :tripId AND l.status NOT IN :inactive ORDER BY l.boardTripStop.sequenceNumber, l.id")
    List<HorseLeg> findActiveOnTrip(@Param("tripId") Long tripId, @Param("inactive") Collection<HorseLegStatus> inactive);

    // Chặng trục của các ngựa cùng đơn (quy tắc #30)
    @Query("""
            SELECT l.vehicleTrip.id FROM HorseLeg l
            WHERE l.bookingHorse.booking.id = :bookingId AND l.legType = com.example.racehorse_transport.entity.enums.HorseLegType.LINEHAUL
              AND l.status NOT IN :inactive AND l.bookingHorse.id <> :bookingHorseId
            """)
    List<Long> findLinehaulTripIdsOfBooking(@Param("bookingId") Long bookingId, @Param("bookingHorseId") Long bookingHorseId,
                                            @Param("inactive") Collection<HorseLegStatus> inactive);

    @EntityGraph(attributePaths = {"vehicleTrip", "bookingHorse"})
    @Query("SELECT l FROM HorseLeg l WHERE l.id = :id")
    Optional<HorseLeg> findWithTrip(@Param("id") Long id);

    // Ngựa lên xe (đón, ra hub) hoặc xuống xe (giao, vào hub) ở một điểm dừng
    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking"})
    List<HorseLeg> findByBoardTripStop_IdAndStatusIn(Long tripStopId, Collection<HorseLegStatus> statuses);

    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking"})
    List<HorseLeg> findByAlightTripStop_IdAndStatusIn(Long tripStopId, Collection<HorseLegStatus> statuses);

    long countByVehicleTrip_IdAndStatus(Long vehicleTripId, HorseLegStatus status);

    // Quy tắc #16: số con ACTIVE của đơn chưa có chặng giao hoặc chạy thẳng đã xong
    @Query("""
            SELECT count(h) FROM BookingHorse h
            WHERE h.booking.id = :bookingId AND h.status = com.example.racehorse_transport.entity.enums.BookingHorseStatus.ACTIVE
              AND NOT EXISTS (SELECT l FROM HorseLeg l WHERE l.bookingHorse = h
                    AND l.legType IN (com.example.racehorse_transport.entity.enums.HorseLegType.DELIVERY, com.example.racehorse_transport.entity.enums.HorseLegType.DIRECT)
                    AND l.status = com.example.racehorse_transport.entity.enums.HorseLegStatus.COMPLETED)
            """)
    long countActiveHorsesNotDelivered(@Param("bookingId") Long bookingId);
}
