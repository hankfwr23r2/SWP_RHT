package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

@Getter
@Setter
@Entity
@Table(name = "HORSE_TYPE")
public class HorseType {
    @Id
    @Column(name = "HorseTypeID", nullable = false)
    private Integer id;

    @Size(max = 255)
    @Nationalized
    @Column(name = "typeName")
    private String typeName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "insured_value", precision = 18, scale = 2)
    private java.math.BigDecimal insuredValue;
}