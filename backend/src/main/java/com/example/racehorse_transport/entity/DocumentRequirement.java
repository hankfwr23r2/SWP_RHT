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
@Table(name = "DOCUMENT_REQUIREMENT")
public class DocumentRequirement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "documentGroup", nullable = false, length = 20)
    private String documentGroup;

    @Column(name = "documentType", nullable = false, length = 50)
    private String documentType;

    @Column(name = "documentName", nullable = false, length = 255)
    private String documentName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "tripType", nullable = false, length = 20)
    private String tripType;

    @Column(name = "country", nullable = false, length = 10)
    private String country;

    @Column(name = "required", nullable = false)
    private boolean required;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "updatedBy", length = 100)
    private String updatedBy;

    @Column(name = "updatedAt")
    private Instant updatedAt;
}
