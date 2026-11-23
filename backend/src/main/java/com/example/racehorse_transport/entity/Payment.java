package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PAYMENT")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PaymentID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @Column(name = "Amount", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "PaymentDate")
    private Instant paymentDate;

    @Size(max = 50)
    @Column(name = "payment_type", length = 50)
    private String paymentType; // DEPOSIT, SETTLEMENT, REFUND

    @Size(max = 50)
    @Column(name = "payment_method", length = 50)
    private String paymentMethod; // BANK_TRANSFER, VNPAY, CASH, CARD

    @Size(max = 100)
    @Column(name = "transaction_code", length = 100)
    private String transactionCode;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status; // PENDING, COMPLETED, FAILED, NEEDS_REVIEW (đã trừ tiền nhưng đơn không còn hợp lệ)

    // Mã giao dịch phía VNPAY (vnp_TransactionNo) và ngân hàng khách dùng (vnp_BankCode)
    @Size(max = 50)
    @Column(name = "gateway_transaction_no", length = 50)
    private String gatewayTransactionNo;

    @Size(max = 20)
    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Size(max = 255)
    @Nationalized
    @Column(name = "note")
    private String note;
}