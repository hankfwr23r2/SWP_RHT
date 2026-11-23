package com.example.racehorse_transport.entity;

import com.example.racehorse_transport.entity.enums.DocumentSubject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Loại giấy tờ.
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_type")
public class DocumentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", length = 30, nullable = false)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    // Giấy gắn với đối tượng nào; mỗi giá trị ứng với một cột chủ sở hữu trong document
    @Enumerated(EnumType.STRING)
    @Column(name = "subject", length = 30, nullable = false)
    private DocumentSubject subject;
}
