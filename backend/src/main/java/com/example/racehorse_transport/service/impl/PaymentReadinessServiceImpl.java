package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.policy.PaymentPolicy;
import com.example.racehorse_transport.policy.SettlementPolicy;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.PaymentReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Fallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Fallback
@RequiredArgsConstructor
public class PaymentReadinessServiceImpl implements PaymentReadinessService {
    private final QuotationRepository quotations;
    private final PaymentRepository payments;
    private final BookingRepository bookings;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockBooking(Long bookingId) {
        bookings.findForUpdate(bookingId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn."));
    }

    // Chỉ tính báo giá đã chấp nhận; chi phí phát sinh thu ở bước quyết toán (GĐ9)
    @Override
    @Transactional(readOnly = true)
    public boolean isPaidInFull(Long bookingId) {
        return quotations.findByBooking_IdAndStatus(bookingId, QuotationStatus.ACCEPTED)
                .map(q -> SettlementPolicy.isPaidInFull(PaymentPolicy.outstanding(q,
                        payments.findByBooking_IdOrderById(bookingId))))
                .orElse(false);
    }
}
