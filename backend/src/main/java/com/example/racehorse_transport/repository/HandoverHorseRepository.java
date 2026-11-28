package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.enums.HandoverStatus;

import com.example.racehorse_transport.entity.HandoverHorse;
import com.example.racehorse_transport.entity.HandoverHorseId;
import com.example.racehorse_transport.entity.enums.HandoverType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HandoverHorseRepository extends JpaRepository<HandoverHorse, HandoverHorseId> {

    Optional<HandoverHorse> findFirstByHorseLeg_BookingHorse_IdAndHandover_HandoverTypeAndHandover_StatusAndMicrochipMatchedTrueOrderByHandover_HandedAtDescHandover_IdDesc(
            Long bookingHorseId, HandoverType type, HandoverStatus status);

    List<HandoverHorse> findById_HandoverId(Long handoverId);

    @Query("""
            SELECT hh FROM HandoverHorse hh JOIN FETCH hh.horseLeg JOIN FETCH hh.handover h
            WHERE h.tripStop.id = :stopId AND h.status = :status AND h.handoverType IN :types
                AND hh.microchipMatched = true
            ORDER BY h.handedAt, h.id
            """)
    List<HandoverHorse> findMatchedAtStop(Long stopId, HandoverStatus status, Collection<HandoverType> types);

    // Con giao thất bại đã được giao vào hub ở một lần giao nhận khác (đã xong)
    boolean existsByHorseLeg_IdAndHandover_HandoverTypeAndHandover_StatusAndHandover_IdNot(Long horseLegId, HandoverType handoverType,
                                                                                          HandoverStatus status, Long handoverId);

    // Lần quét đúng chip của một chặng ở lần giao nhận loại cho trước (vd. chip đã quét lúc nhận ngựa)
    Optional<HandoverHorse> findFirstByHorseLeg_IdAndHandover_HandoverTypeAndMicrochipMatchedTrue(Long horseLegId, HandoverType handoverType);

    // Theo dõi hành trình: mọi lần giao nhận của các con trong một đơn
    @Query("select hh from HandoverHorse hh join fetch hh.handover h join fetch hh.horseLeg l where l.bookingHorse.id in :bookingHorseIds order by h.id")
    List<HandoverHorse> findByBookingHorseIds(Collection<Long> bookingHorseIds);
}
