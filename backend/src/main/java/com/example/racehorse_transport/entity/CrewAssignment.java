package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.CrewAssignmentStatus;
import com.example.racehorse_transport.entity.enums.CrewRole;

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

// Phân công tổ lái cho một lượt chạy.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "crew_assignment")
public class CrewAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_trip_id", nullable = false)
    private VehicleTrip vehicleTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private Staff staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "crew_role", length = 30, nullable = false)
    private CrewRole crewRole;

    // ACKNOWLEDGED = nhân viên đã xác nhận nhận việc
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private CrewAssignmentStatus status;

    // DB đặt giá trị mặc định now() lúc thêm dòng
    @Generated
    @Column(name = "assigned_at", nullable = false, insertable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
