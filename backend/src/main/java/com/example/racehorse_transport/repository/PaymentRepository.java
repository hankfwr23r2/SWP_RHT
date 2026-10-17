package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByBookingID_IdOrderByPaymentDateDesc(Integer bookingId);

    // Khóa dòng khi xác nhận VNPAY để IPN và Return URL đến cùng lúc không xử lý 2 lần
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByTransactionCode(String transactionCode);
}
