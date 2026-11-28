package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;

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

// Giấy tờ gốc nhận hoặc trả trong một lần giao nhận.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "handover_document")
public class HandoverDocument {

    @EmbeddedId
    private HandoverDocumentId id;

    @MapsId("handoverId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id")
    private Handover handover;

    @MapsId("documentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id")
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", length = 30, nullable = false)
    private HandoverDocumentDirection direction;
}
