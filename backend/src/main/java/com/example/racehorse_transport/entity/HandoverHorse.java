package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.MicrochipEntryMethod;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Ngựa nào được giao trong một lần giao nhận, kèm kết quả kiểm tra chip.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "handover_horse")
public class HandoverHorse {

    @Column(name = "recipient_name", length = 120)
    private String recipientName;

    @EmbeddedId
    private HandoverHorseId id;

    @MapsId("handoverId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id")
    private Handover handover;

    @MapsId("horseLegId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horse_leg_id")
    private HorseLeg horseLeg;

    @Column(name = "scanned_microchip", length = 30)
    private String scannedMicrochip;

    @Enumerated(EnumType.STRING)
    @Column(name = "microchip_entry_method", nullable = false, length = 10)
    private MicrochipEntryMethod microchipEntryMethod = MicrochipEntryMethod.SCAN;

    @Column(name = "manual_entry_reason", length = 500)
    private String manualEntryReason;

    // So với horse.microchip
    @Column(name = "is_microchip_matched", nullable = false)
    private Boolean microchipMatched;

    @Column(name = "condition_note", columnDefinition = "text")
    private String conditionNote;
}
