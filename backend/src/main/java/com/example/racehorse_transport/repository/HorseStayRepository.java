package com.example.racehorse_transport.repository;

import java.time.Instant;
import jakarta.persistence.LockModeType;

import com.example.racehorse_transport.entity.enums.HorseStayType;

import com.example.racehorse_transport.entity.HorseStay;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;

public interface HorseStayRepository extends JpaRepository<HorseStay, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM HorseStay s WHERE s.id = :stayId")
    Optional<HorseStay> findForUpdate(Long stayId);

    @Query("SELECT s.bookingHorse.booking.id FROM HorseStay s WHERE s.id = :stayId")
    Optional<Long> findBookingId(Long stayId);

    // Lưu trú đang diễn ra của một con (đã vào, chưa ra)
    Optional<HorseStay> findFirstByBookingHorse_IdAndActualStartAtIsNotNullAndActualEndAtIsNull(Long bookingHorseId);

    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking", "location"})
    List<HorseStay> findByLocation_IdAndActualStartAtIsNotNullAndActualEndAtIsNullOrderByActualStartAt(Long locationId);

    // Theo dõi hành trình: mọi lưu trú của các con trong một đơn
    @EntityGraph(attributePaths = {"location", "operatorParty"})
    List<HorseStay> findByLocation_IdAndActualStartAtIsNullOrderByPlannedStartAt(Long locationId);

    List<HorseStay> findByBookingHorse_IdInOrderById(Collection<Long> bookingHorseIds);

    @EntityGraph(attributePaths = {"location"})
    List<HorseStay> findByBookingHorse_Booking_Id(Long bookingId);

    // Gửi chuồng đang diễn ra đã quá hạn dự kiến
    @EntityGraph(attributePaths = {"bookingHorse", "bookingHorse.horse", "bookingHorse.booking", "bookingHorse.booking.customer", "location"})
    List<HorseStay> findByStayTypeAndActualStartAtIsNotNullAndActualEndAtIsNullAndPlannedEndAtBefore(HorseStayType stayType, Instant at);

    // Lịch hẹn chưa bắt đầu của một con (khách tự chở tới hub)
    long deleteByBookingHorse_Booking_IdAndStayTypeAndActualStartAtIsNull(Long bookingId, HorseStayType type);

    void deleteByBookingHorse_IdAndActualStartAtIsNull(Long bookingHorseId);
}
