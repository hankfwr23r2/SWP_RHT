package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.repository.HorseLegRepository;
import com.example.racehorse_transport.repository.TripStopRepository;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.TripPickupReadinessService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TripPickupReadinessServiceImpl implements TripPickupReadinessService {
    private final TripStopRepository stops;
    private final HorseLegRepository legs;
    private final HorseInsuranceRepository insurances;
    private final DocumentCompletenessService documents;

    @Override
    @Transactional(readOnly = true)
    public void requireReady(Long tripId) {
        Map<Long, String> bookings = new LinkedHashMap<>();
        for (TripStop stop : stops.findByVehicleTrip_IdOrderBySequenceNumber(tripId)) {
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
}
