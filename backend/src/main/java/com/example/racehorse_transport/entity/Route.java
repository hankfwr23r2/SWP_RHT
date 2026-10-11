package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "ROUTE")
public class Route {
    @Id
    @Column(name = "RouteID", nullable = false)
    private Integer id;

    @Size(max = 255)
    @Nationalized
    @Column(name = "RouteName")
    private String routeName;

    @Nationalized
    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "estimatedTime")
    private Integer estimatedTime;

    @Column(name = "distance", precision = 10, scale = 2)
    private BigDecimal distance;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;


}