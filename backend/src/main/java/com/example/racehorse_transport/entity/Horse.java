package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "HORSE")
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "HorseID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "HorseTypeID")
    private HorseType horseTypeID;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CustomerID")
    private Customer customerID;

    @Size(max = 255)
    @Nationalized
    @Column(name = "HorseName")
    private String horseName;

    @Size(max = 100)
    @Column(name = "MicrochipID", length = 100)
    private String microchipID;

    @Size(max = 20)
    @Column(name = "Gender", length = 20)
    private String gender;

    @Size(max = 50)
    @Nationalized
    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "Age")
    private Integer age;

    @Column(name = "Weight", precision = 10, scale = 2)
    private BigDecimal weight;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;


}