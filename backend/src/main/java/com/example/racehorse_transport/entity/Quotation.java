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
@Table(name = "QUOTATION")
public class Quotation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookingid", nullable = false)
    private Booking booking;

    @Column(name = "subtotal", precision = 18, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "adjustment_amount", precision = 18, scale = 2)
    private BigDecimal adjustmentAmount;

    @Size(max = 255)
    @Nationalized
    @Column(name = "adjustment_note")
    private String adjustmentNote;

    @Column(name = "total_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "deposit_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal depositAmount; // 30%

    @Column(name = "balance_amount", precision = 18, scale = 2, nullable = false)
    private BigDecimal balanceAmount; // 70%

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sent_by_userid")
    private User sentByUser;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status; // PENDING, ACCEPTED, REJECTED, EXPIRED

    @Nationalized
    @Lob
    @Column(name = "quote_lines_json")
    private String quoteLinesJson;
}
