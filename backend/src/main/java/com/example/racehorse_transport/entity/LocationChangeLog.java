package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "LOCATION_CHANGE_LOG")
public class LocationChangeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "LocationID", nullable = false)
    private Integer locationId;

    @Column(name = "fieldName", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "oldValue", length = 1000)
    private String oldValue;

    @Column(name = "newValue", length = 1000)
    private String newValue;

    @Column(name = "changedBy", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changedAt", nullable = false)
    private Instant changedAt;
}
