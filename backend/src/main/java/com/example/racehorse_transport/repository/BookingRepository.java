package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Booking;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByCode(String code);

    // SELECT ... FOR UPDATE: hai thao tác đổi trạng thái cùng một đơn chạy lần lượt
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") Long id);

    List<Booking> findByDeparture_Id(Long departureId);
}
