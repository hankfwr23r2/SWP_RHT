package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.QuarantineResult;

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

import java.time.Instant;

// Thời gian ngựa ở một nơi, không trên xe: tập kết ở hub, cách ly, nghỉ qua đêm, gửi chuồng.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horse_stay")
public class HorseStay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_horse_id", nullable = false)
    private BookingHorse bookingHorse;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_type", length = 30, nullable = false)
    private HorseStayType stayType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    // Đơn vị vận hành nơi đó, ví dụ trại cách ly đối tác
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "operator_party_id")
    private Party operatorParty;

    @Column(name = "planned_start_at", nullable = false)
    private Instant plannedStartAt;

    @Column(name = "planned_end_at", nullable = false)
    private Instant plannedEndAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Column(name = "balance_hold_at")
    private Instant balanceHoldAt;

    // snapshot: đơn giá mỗi 24 giờ lúc bắt đầu giữ, đổi cấu hình không ảnh hưởng
    @Column(name = "stabling_unit_price", precision = 15, scale = 2)
    private java.math.BigDecimal stablingUnitPrice;

    // Chỉ dùng cho QUARANTINE (CHECK ở DB)
    @Enumerated(EnumType.STRING)
    @Column(name = "quarantine_result", length = 30)
    private QuarantineResult quarantineResult;
}
