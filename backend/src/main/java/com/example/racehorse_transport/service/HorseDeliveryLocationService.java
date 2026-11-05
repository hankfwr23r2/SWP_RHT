package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseJourneyLog;
import com.example.racehorse_transport.repository.HorseJourneyLogRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.util.DocumentRequirementPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Updates the horse's physical destination, independently of the vehicle's return to HQ. */
@Service
@RequiredArgsConstructor
public class HorseDeliveryLocationService {
    private final HorseRepository horses;
    private final HorseJourneyLogRepository journeys;

    @Transactional
    public void recordTripDelivery(Booking booking, BookingDetailsPayload payload, VehicleTripDto trip,
            String actor, String proofImageUrl, long arrivedAt) {
        List<String> assigned = trip.getHorseIds();
        if (assigned == null || assigned.isEmpty()) {
            if (payload.getTrips() != null && payload.getTrips().size() > 1) {
                throw new IllegalStateException("Chuyến xe chưa có danh sách ngựa bàn giao.");
            }
            // Older single-vehicle bookings may not have an explicit assignment.
            assigned = bookingHorseIds(payload);
        }
        Long departedAt = trip.getRun() != null && trip.getRun().get("startedAt") instanceof Number time
                ? time.longValue() : null;
        recordDelivery(booking, payload, assigned, trip.getTripId(), actor, proofImageUrl, departedAt, arrivedAt);
    }

    @Transactional
    public void recordBookingHandover(Booking booking, BookingDetailsPayload payload,
            String actor, String proofImageUrl, long arrivedAt) {
        recordDelivery(booking, payload, bookingHorseIds(payload), "BK-" + booking.getId(),
                actor, proofImageUrl, null, arrivedAt);
    }

    private void recordDelivery(Booking booking, BookingDetailsPayload payload, Collection<String> deliveredIds,
            String tripCode, String actor, String proofImageUrl, Long departedAt, long arrivedAt) {
        if (deliveredIds.isEmpty()) return;
        String destination = booking.getDropoffAddress();
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("Đơn hàng chưa có địa chỉ bàn giao ngựa.");
        }
        String country = DocumentRequirementPolicy.normalizeCountry(payload.getDestCountry());
        if ("ALL".equals(country) && "domestic".equalsIgnoreCase(booking.getBookingType())) country = "VN";
        if (!Set.of("VN", "KH", "LA").contains(country)) {
            throw new IllegalStateException("Đơn hàng chưa xác định quốc gia điểm giao ngựa.");
        }

        Set<Integer> allowed = new LinkedHashSet<>();
        for (String id : bookingHorseIds(payload)) allowed.add(horseId(id));
        Set<Integer> delivered = new LinkedHashSet<>();
        for (String id : deliveredIds) delivered.add(horseId(id));
        if (!allowed.containsAll(delivered)) {
            throw new IllegalStateException("Danh sách ngựa bàn giao không thuộc đơn hàng.");
        }

        for (Integer id : delivered) {
            // Retrying an old handover must not overwrite a later owner relocation.
            if (journeys.existsByHorse_IdAndBookingIdAndSource(id, booking.getId(), "SYSTEM_DELIVERY")) continue;
            Horse horse = horses.findById(id)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy ngựa bàn giao: " + id));
            horse.setCurrentLocationName(destination.trim());
            horse.setCurrentCountry(country);
            horse.setTransitStatus("STABLE_IDLE");
            horse.setLocationSource("SYSTEM_DELIVERY");
            horse.setLocationUpdatedAt(arrivedAt);
            horses.save(horse);
            journeys.save(HorseJourneyLog.builder()
                    .horse(horse).bookingId(booking.getId()).tripCode(tripCode)
                    .fromLocation(booking.getPickupAddress()).toLocation(destination.trim())
                    .departAt(departedAt).arrivedAt(arrivedAt).createdAt(arrivedAt)
                    .driverName(actor).proofImageUrl(proofImageUrl)
                    .notes("Hoàn tất vận chuyển và bàn giao ngựa")
                    .source("SYSTEM_DELIVERY").build());
        }
    }

    private List<String> bookingHorseIds(BookingDetailsPayload payload) {
        return payload.getHorses() == null ? List.of()
                : payload.getHorses().stream().map(BookingHorseDto::getHorseId).toList();
    }

    private Integer horseId(String reference) {
        if (reference == null || !reference.matches("(?i)(?:(?:H|HORSE)-?)?[0-9]+")) {
            throw new IllegalArgumentException("Mã ngựa bàn giao không hợp lệ: " + reference);
        }
        return Integer.valueOf(reference.replaceAll("\\D", ""));
    }
}
