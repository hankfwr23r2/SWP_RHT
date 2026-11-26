package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.HealthCheckTaskPolicy;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
import com.example.racehorse_transport.repository.HealthCheckTaskRepository;
import com.example.racehorse_transport.repository.HorseInsuranceRepository;
import com.example.racehorse_transport.service.DocumentCompletenessService;
import com.example.racehorse_transport.service.HandoverReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HandoverReadinessServiceImpl implements HandoverReadinessService {
    private final HorseInsuranceRepository insurances;
    private final DocumentCompletenessService documents;
    private final HealthCheckTaskRepository healthChecks;

    @Override
    public void requireConfirmedInsurance(BookingHorse horse) {
        if (!isInsuranceConfirmed(horse)) {
            throw new DispatchRuleException("insurance", "Bảo hiểm của " + horse.getHorse().getName()
                    + " (đơn " + horse.getBooking().getCode() + ") chưa được xác nhận.", true);
        }
    }

    @Override
    public void requireReadyToReceiveAtHub(BookingHorse horse) {
        if (!isInsuranceConfirmed(horse)) {
            throw new DispatchRuleException("insurance", "Bảo hiểm của " + horse.getHorse().getName()
                    + " chưa được xác nhận.", true);
        }
        Booking booking = horse.getBooking();
        if (!documents.isComplete(booking.getId())) {
            throw new DispatchRuleException("documents", "Đơn " + booking.getCode()
                    + " còn thiếu giấy tờ bắt buộc. Không nhận ngựa tại hub.", true);
        }
    }

    @Override
    public void requireDocumentsForPickup(Booking booking) {
        if (!documents.isComplete(booking.getId())) {
            throw new DispatchRuleException("documents", "Đơn " + booking.getCode() + " còn thiếu giấy tờ bắt buộc.", true);
        }
    }

    @Override
    public void requireDocumentsAtBorder(Booking booking, boolean fromAgent) {
        TripExecutionPolicy.requireBorderDocuments(documents.isComplete(booking.getId()), booking.getCode(), fromAgent);
    }

    @Override
    public void requireHubExitCheck(HorseLeg leg) {
        var trip = leg.getVehicleTrip();
        HealthCheckTaskPolicy.hubExitType(trip.getTripType())
                .ifPresent(type -> requireCheck(leg.getBookingHorse(), type, trip.getId(), null));
    }

    @Override
    public void requireHubCollectionCheck(HorseStay stay) {
        requireCheck(stay.getBookingHorse(), HealthCheckType.BEFORE_DELIVERY, null, stay.getId());
    }

    private void requireCheck(BookingHorse horse, HealthCheckType type, Long tripId, Long stayId) {
        if (!healthChecks.hasCompletedCheck(horse.getId(), type, tripId, stayId)) {
            String message = type == HealthCheckType.BEFORE_DEPARTURE ? "Chưa khám trước khi chạy: " : "Chưa khám trước khi giao: ";
            throw new DispatchRuleException("healthCheck", message + horse.getHorse().getName(), true);
        }
    }

    private boolean isInsuranceConfirmed(BookingHorse horse) {
        return insurances.findByBookingHorse_Id(horse.getId())
                .map(insurance -> insurance.getStatus() == HorseInsuranceStatus.CONFIRMED).orElse(false);
    }
}
