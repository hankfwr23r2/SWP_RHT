package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.PaymentTransaction;
import com.example.racehorse_transport.entity.enums.PaymentTransactionStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByGatewayCodeAndGatewayTransactionNumber(String code, String number);

    boolean existsByPayment_IdAndGatewayCodeAndStatus(Long paymentId, String code, PaymentTransactionStatus status);

    List<PaymentTransaction> findByPayment_Booking_IdAndPayment_PaymentTypeAndGatewayCodeAndStatus(Long bookingId,
            PaymentType type, String code, PaymentTransactionStatus status);

    @Modifying
    @Query(value = """
            INSERT INTO payment_transaction (payment_id, gateway_code, gateway_transaction_number,
                bank_code, response_code, amount, status, gateway_paid_at, raw_payload)
            VALUES (:paymentId, :gateway, :number, :bank, :response, :amount, :status, :paidAt, CAST(:payload AS jsonb))
            ON CONFLICT (gateway_code, gateway_transaction_number) DO UPDATE
            SET bank_code = EXCLUDED.bank_code, response_code = EXCLUDED.response_code, status = EXCLUDED.status,
                gateway_paid_at = EXCLUDED.gateway_paid_at, raw_payload = EXCLUDED.raw_payload
            WHERE ((payment_transaction.status = 'FAILED' AND EXCLUDED.status = 'SUCCESS')
                OR (payment_transaction.status = 'PENDING' AND EXCLUDED.status IN ('SUCCESS', 'FAILED')))
                AND payment_transaction.payment_id = EXCLUDED.payment_id
                AND payment_transaction.amount = EXCLUDED.amount
                AND payment_transaction.raw_payload ->> 'vnp_TxnRef' = EXCLUDED.raw_payload ->> 'vnp_TxnRef'
            """, nativeQuery = true)
    int insertResult(Long paymentId, String gateway, String number, String bank, String response,
                     BigDecimal amount, String status, Instant paidAt, String payload);
}
