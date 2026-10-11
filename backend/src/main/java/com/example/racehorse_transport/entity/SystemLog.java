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
@Table(name = "SYSTEM_LOG")
public class SystemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SystemLogID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ActionBy_UserID")
    private User actionbyUserid;

    @Size(max = 100)
    @Column(name = "ActionType", length = 100)
    private String actionType;

    @Size(max = 100)
    @Column(name = "TargetTable", length = 100)
    private String targetTable;

    @Column(name = "TargetRecordID")
    private Integer targetRecordID;

    @Column(name = "OldData", columnDefinition = "TEXT")
    private String oldData;

    @Column(name = "NewData", columnDefinition = "TEXT")
    private String newData;

    @Column(name = "CreatedAt")
    private Instant createdAt;
}