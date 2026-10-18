
package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "REGULATION")
public class Regulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RegulationID", nullable = false)
    private Integer id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "StaffID", nullable = false)
    private Staff staffID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LocationID")
    private Location locationID;

    @Size(max = 50)
    @Column(name = "document_type", length = 50)
    private String documentType;

    @Size(max = 100)
    @Nationalized
    @Column(name = "country", length = 100)
    private String country;

    @NotBlank
    @Size(max = 255)
    @Nationalized
    @Column(name = "RegulationName", nullable = false, length = 255)
    private String regulationName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @NotNull
    @Pattern(regexp = "ACTIVE|INACTIVE")
    @Column(name = "status", nullable = false, length = 8)
    private String status;
}