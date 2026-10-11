package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "INCIDENT")
public class Incident {
    @Id
    @Column(name = "IncidentID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StaffID")
    private Staff staffID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LogID")
    private Log logID;

    @Size(max = 255)
    @Nationalized
    @Column(name = "IncidentName")
    private String incidentName;

    @Size(max = 100)
    @Column(name = "IncidentType", length = 100)
    private String incidentType;

    @Nationalized
    @Lob
    @Column(name = "description")
    private String description;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;


}