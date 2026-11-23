package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "HORSE_DOCUMENT")
public class HorseDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "documentid", nullable = false)
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

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "UploadDate")
    private Instant uploadDate;

    /** Ghi chu cua chuyen vien khi duyet / tu choi */
    @Size(max = 1000)
    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    /** Ten chuyen vien da duyet / tu choi */
    @Size(max = 200)
    @Column(name = "reviewer_name", length = 200)
    private String reviewerName;

    /** Thoi diem chuyen vien duyet / tu choi */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}