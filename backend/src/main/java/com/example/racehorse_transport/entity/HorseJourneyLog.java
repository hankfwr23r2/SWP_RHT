package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "HORSE_JOURNEY_LOG")
public class HorseJourneyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @Column(name = "booking_id")
    private Integer bookingId;

    @Size(max = 50)
    @Column(name = "trip_code", length = 50)
    private String tripCode;

    @Size(max = 255)
    @Nationalized
    @Column(name = "from_location")
    private String fromLocation;

    @Size(max = 255)
    @Nationalized
    @Column(name = "to_location")
    private String toLocation;

    @Column(name = "depart_at")
    private Long departAt;

    @Column(name = "arrived_at")
    private Long arrivedAt;

    @Size(max = 100)
    @Nationalized
    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "proof_image_url", columnDefinition = "TEXT")
    private String proofImageUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Size(max = 50)
    @Column(name = "source", length = 50)
    private String source; // SYSTEM_DELIVERY, OWNER_RELOCATION

    @Column(name = "created_at")
    private Long createdAt;
}
