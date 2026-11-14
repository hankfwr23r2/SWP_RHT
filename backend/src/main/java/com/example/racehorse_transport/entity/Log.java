package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "LOG")
public class Log {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LogID", nullable = false)
    private Integer id;

    @Column(name = "actor_username", length = 255)
    private String actorUsername;
    @Column(name = "target_table", length = 100)
    private String targetTable;
    @Column(name = "target_record_id")
    private Integer targetRecordId;
    @Column(name = "source_system_log_id", unique = true)
    private Integer sourceSystemLogId;
    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
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

    @Column(name = "Note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "createdAt")
    private Instant createdAt;

}