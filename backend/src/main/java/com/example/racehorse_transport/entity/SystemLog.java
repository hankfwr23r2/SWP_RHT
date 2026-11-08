package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "SYSTEM_LOG", indexes = {
        @Index(name = "idx_system_log_target_time", columnList = "TargetTable,CreatedAt,SystemLogID"),
        @Index(name = "idx_system_log_actor_time", columnList = "ActionBy_UserID,CreatedAt")
})
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

    // Nullable additions preserve legacy records. New audit entries use structured JSON snapshots.
    @Column(name = "Description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "ActorName", length = 255)
    private String actorName;

    @Column(name = "ActorEmail", length = 255)
    private String actorEmail;

    @Column(name = "ActorRole", length = 50)
    private String actorRole;

    @Column(name = "TargetKey", length = 255)
    private String targetKey;

    @Column(name = "TargetLabel", length = 255)
    private String targetLabel;

    @Column(name = "DataVersion")
    private Integer dataVersion;
}
