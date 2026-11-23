package com.example.racehorse_transport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

// Khóa của handover_document.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class HandoverDocumentId implements Serializable {

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;
}
