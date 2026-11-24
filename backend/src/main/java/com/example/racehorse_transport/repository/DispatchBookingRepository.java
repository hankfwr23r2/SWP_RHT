package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

// Truy vấn chỉ đọc trên đơn cho điều độ (luồng B). Bảng booking do luồng A sở hữu; ở đây không ghi.
public interface DispatchBookingRepository extends Repository<Booking, Long> {

    // Đơn cần xếp xe: ngày đón khách muốn, hoặc ngày chuyến trục, nằm trong [from, to]
    @EntityGraph(attributePaths = {"departure", "departure.route"})
    @Query("""
            SELECT b FROM Booking b LEFT JOIN b.departure d
            WHERE b.status IN :statuses
              AND ((b.requestedPickupDate BETWEEN :from AND :to) OR (d.departureDate BETWEEN :from AND :to))
            ORDER BY b.requestedPickupDate, b.code
            """)
    List<Booking> findDispatchable(@Param("statuses") Collection<BookingStatus> statuses,
                                   @Param("from") LocalDate from, @Param("to") LocalDate to);

    // Gợi ý gom lượt đón: đơn công ty đón, đi trục từ hub này, ngày đón khách muốn là ngày đó
    @EntityGraph(attributePaths = {"departure", "departure.route"})
    @Query("""
            SELECT b FROM Booking b JOIN b.departure d
            WHERE b.status IN :statuses AND b.pickupMethod = com.example.racehorse_transport.entity.enums.TransferMethod.COMPANY
              AND d.route.origin.id = :hubId AND b.requestedPickupDate = :date
            ORDER BY b.code
            """)
    List<Booking> findForPickupGrouping(@Param("statuses") Collection<BookingStatus> statuses, @Param("hubId") Long hubId, @Param("date") LocalDate date);

    // Gợi ý gom lượt giao: đơn công ty giao, chuyến trục tới hub này xuất phát trong [from, to] (ngày tới nơi lọc ở service)
    @EntityGraph(attributePaths = {"departure", "departure.route"})
    @Query("""
            SELECT b FROM Booking b JOIN b.departure d
            WHERE b.status IN :statuses AND b.deliveryMethod = com.example.racehorse_transport.entity.enums.TransferMethod.COMPANY
              AND d.route.destination.id = :hubId AND d.departureDate BETWEEN :from AND :to
            ORDER BY b.code
            """)
    List<Booking> findForDeliveryGrouping(@Param("statuses") Collection<BookingStatus> statuses, @Param("hubId") Long hubId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);
}
