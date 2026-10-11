package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "HORSE_DOCUMENT")
public class HorseDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DocumentID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HorseID")
    private Horse horseID;

    @Size(max = 100)
    @Column(name = "documentType", length = 100)
    private String documentType;

    @Size(max = 500)
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "expiryDate")
    private LocalDate expiryDate;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "UploadDate")
    private Instant uploadDate;


}