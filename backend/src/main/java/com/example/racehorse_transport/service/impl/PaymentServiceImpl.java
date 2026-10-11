package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Payment;

import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    @Autowired
    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    @Override
    public Optional<Payment> findById(Integer id) {
        return paymentRepository.findById(id);
    }

    @Override
    public Payment save(Payment entity) {
        return paymentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        paymentRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 2: Tài chính (Flow 2, 4, 6)
    // =========================================================================
    // TODO (Dev 2): Thanh toán (Cọc, Thanh toán cuối, Phí phạt).
    // - Viết hàm processPayment(Integer bookingId, BigDecimal amount, String paymentType)
    // - Cập nhật trạng thái Payment (Đã cọc, Đã thanh toán đủ...)

}
