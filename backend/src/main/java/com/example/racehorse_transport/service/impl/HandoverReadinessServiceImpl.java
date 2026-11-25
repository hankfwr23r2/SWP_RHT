package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.exception.DispatchRuleException;
import com.example.racehorse_transport.policy.TripExecutionPolicy;
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
    public void requireDocumentsAtBorder(Booking booking, boolean fromAgent) {
        TripExecutionPolicy.requireBorderDocuments(documents.isComplete(booking.getId()), booking.getCode(), fromAgent);
    }

    private boolean isInsuranceConfirmed(BookingHorse horse) {
        return insurances.findByBookingHorse_Id(horse.getId())
                .map(insurance -> insurance.getStatus() == HorseInsuranceStatus.CONFIRMED).orElse(false);
    }
}
