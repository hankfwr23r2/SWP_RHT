package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.HandoverReadinessService;
import com.example.racehorse_transport.service.PaymentReadinessService;
import com.example.racehorse_transport.service.TripReadinessService;
import com.example.racehorse_transport.service.VehicleService;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripReadinessServiceImpl implements TripReadinessService {
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final HandoverReadinessService handoverReadiness;
    private final PaymentReadinessService paymentReadiness;
    private final VehicleService vehicles;
    private final IncidentRepository incidents;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireReadyToStart(VehicleTrip trip) {
        if (vehicles.lockVehicle(trip.getVehicle().getId()).getStatus() != VehicleStatus.ACTIVE) {
            throw new DispatchRuleException("vehicleId", "Xe đang bảo dưỡng hoặc đã ngừng sử dụng. Liên hệ điều phối để đổi xe.", true);
        }
        Map<Long, Booking> bookings = new TreeMap<>();
        for (TripStop stop : stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId())) {
            if (stop.getStopType() != TripStopType.PICKUP) continue;
            for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.PLANNED))) {
                var horse = leg.getBookingHorse();
                if (horse.getPickupMissedAt() != null && horse.getPickupMissChoice() == null) {
                    throw new DispatchRuleException("pickup", "Còn ngựa chưa chọn cách xử lý đón hụt.", true);
                }
                var booking = horse.getBooking();
                bookings.put(booking.getId(), booking);
                handoverReadiness.requireConfirmedInsurance(horse);
            }
        }
        bookings.forEach((id, booking) -> {
            paymentReadiness.lockBooking(id);
            handoverReadiness.requireDocumentsForPickup(booking);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void requireTripUnblocked(Long tripId) {
        if (incidents.findForTripBySeverity(tripId, IncidentSeverity.HIGH).stream()
                .anyMatch(incident -> IncidentPolicy.blocksTrip(incident.getSeverity(), incident.getStatus(), incident.getTripUnlockedAt()))) {
            throw new DispatchRuleException("tripStopId",
                    "Lượt xe đang có sự cố nặng chưa có phương án được duyệt. Chờ quản lý duyệt hoặc điều phối mở khóa.", true);
        }
    }
}
