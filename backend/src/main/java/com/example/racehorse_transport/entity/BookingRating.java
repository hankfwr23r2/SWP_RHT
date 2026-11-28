package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.Generated;

import java.time.Instant;

// Khách đánh giá đơn (1–0..1).
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "booking_rating")
public class BookingRating {

    @Id
    @Column(name = "booking_id")
    private Long bookingId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Column(name = "score", nullable = false)
    private Short score;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "rated_at", nullable = false, insertable = false, updatable = false)
    private Instant ratedAt;
}
