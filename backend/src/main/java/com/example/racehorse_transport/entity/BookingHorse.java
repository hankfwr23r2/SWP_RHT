package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.BookingHorseStatus;

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

import java.time.Instant;

// Dòng đơn: mỗi con ngựa một dòng.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_horse")
public class BookingHorse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stall_type_code", nullable = false)
    private StallType stallType;

    @Column(name = "diet_note", columnDefinition = "text")
    private String dietNote;

    @Column(name = "feedings_per_day")
    private Short feedingsPerDay;

    @Column(name = "is_feed_supplied_by_owner", nullable = false)
    private Boolean feedSuppliedByOwner = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private BookingHorseStatus status;

    @Column(name = "pickup_missed_at")
    private Instant pickupMissedAt;

    @Column(name = "pickup_choice_due_at")
    private Instant pickupChoiceDueAt;

    @Column(name = "pickup_miss_reason", length = 500)
    private String pickupMissReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_miss_liability", length = 20)
    private com.example.racehorse_transport.entity.enums.PickupMissLiability pickupMissLiability;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_miss_choice", length = 24)
    private com.example.racehorse_transport.entity.enums.PickupMissChoice pickupMissChoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pickup_retry_departure_id")
    private Departure pickupRetryDeparture;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "added_at", nullable = false, insertable = false, updatable = false)
    private Instant addedAt;
}
