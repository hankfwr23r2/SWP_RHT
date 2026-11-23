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
}
