package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.entity.enums.VehicleStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.IncidentPolicy;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.IncidentRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.TripReadinessService;
import com.example.racehorse_transport.service.VehicleService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripReadinessServiceImpl implements TripReadinessService {
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final HorseInsuranceRepository insurances;
    private final DocumentCompletenessService documents;
    private final VehicleService vehicles;
    private final IncidentRepository incidents;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireReadyToStart(VehicleTrip trip) {
        if (vehicles.lockVehicle(trip.getVehicle().getId()).getStatus() != VehicleStatus.ACTIVE) {
            throw new DispatchRuleException("vehicleId", "Xe đang bảo dưỡng hoặc đã ngừng sử dụng. Liên hệ điều phối để đổi xe.", true);
        }
        Map<Long, String> bookings = new LinkedHashMap<>();
        for (TripStop stop : stops.findByVehicleTrip_IdOrderBySequenceNumber(trip.getId())) {
            if (stop.getStopType() != TripStopType.PICKUP) continue;
            for (HorseLeg leg : legs.findByBoardTripStop_IdAndStatusIn(stop.getId(), Set.of(HorseLegStatus.PLANNED))) {
                var horse = leg.getBookingHorse();
                if (horse.getPickupMissedAt() != null && horse.getPickupMissChoice() == null) {
                    throw new DispatchRuleException("pickup", "Còn ngựa chưa chọn cách xử lý đón hụt.", true);
                }
                var booking = horse.getBooking();
                bookings.put(booking.getId(), booking.getCode());
                boolean insured = insurances.findByBookingHorse_Id(leg.getBookingHorse().getId())
                        .map(i -> i.getStatus() == HorseInsuranceStatus.CONFIRMED).orElse(false);
                if (!insured) {
                    throw new DispatchRuleException("insurance", "Bảo hiểm của " + leg.getBookingHorse().getHorse().getName()
                            + " (đơn " + booking.getCode() + ") chưa được xác nhận.", true);
                }
            }
        }
        bookings.forEach((id, code) -> {
            if (!documents.isComplete(id)) {
                throw new DispatchRuleException("documents", "Đơn " + code + " còn thiếu giấy tờ bắt buộc.", true);
            }
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
