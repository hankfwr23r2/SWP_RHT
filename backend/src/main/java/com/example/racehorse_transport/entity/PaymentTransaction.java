package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.PaymentTransactionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

// Một giao dịch với cổng thanh toán. UK (gateway_code, gateway_transaction_number) chống ghi trùng khi cổng gửi lại callback.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_transaction")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "gateway_code", length = 30, nullable = false)
    private String gatewayCode;

    @Column(name = "gateway_transaction_number", length = 100, nullable = false)
    private String gatewayTransactionNumber;

    @Column(name = "bank_code", length = 30)
    private String bankCode;

    @Column(name = "response_code", length = 10, nullable = false)
    private String responseCode;

    // Số tiền cổng báo về, dùng để đối chiếu
    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private PaymentTransactionStatus status;

    @Column(name = "gateway_paid_at")
    private Instant gatewayPaidAt;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    // Phản hồi thô của cổng. Một trong hai ngoại lệ được phép lưu JSON (AGENTS.md mục 4)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private String rawPayload;
}
