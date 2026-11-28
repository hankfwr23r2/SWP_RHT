package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BookingAddressRepository extends JpaRepository<BookingAddress, Long> {

    List<BookingAddress> findByBooking_Id(Long bookingId);

    @Query("""
            SELECT a FROM BookingAddress a JOIN FETCH a.province JOIN FETCH a.country
            LEFT JOIN FETCH a.sourceAddress WHERE a.booking.id = :bookingId ORDER BY a.addressType, a.id
            """)
    List<BookingAddress> findAddressesForDetail(Long bookingId);

    // Nơi giao của nhiều đơn cho danh sách; đơn nhiều nơi giao lấy nơi lập trước
    @Query("""
            SELECT a FROM BookingAddress a JOIN FETCH a.province JOIN FETCH a.country
            WHERE a.booking.id IN :bookingIds AND a.addressType = com.example.racehorse_transport.entity.enums.AddressRole.DELIVERY
            ORDER BY a.id
            """)
    List<BookingAddress> findDeliveriesOfBookings(List<Long> bookingIds);
}
