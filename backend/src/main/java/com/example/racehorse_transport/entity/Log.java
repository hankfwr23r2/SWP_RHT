package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "LOG")
public class Log {
    @Id
    @Column(name = "LogID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "BookingID")
    private Booking bookingID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "StaffID")
    private Staff staffID;

    @Size(max = 100)
    @Column(name = "logType", length = 100)
    private String logType;

    @Size(max = 50)
    @Column(name = "Status", length = 50)
    private String status;

    @Nationalized
    @Lob
    @Column(name = "Note")
    private String note;

    @Column(name = "createdAt")
    private Instant createdAt;


}