package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "BOOKING_ASSIGNMENT")
public class BookingAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "AssignmentID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StaffID")
    private Staff staffID;

    @Size(max = 100)
    @Column(name = "AssignRole", length = 100)
    private String assignRole;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Size(max = 255)
    @Nationalized
    @Column(name = "note")
    private String note;
}