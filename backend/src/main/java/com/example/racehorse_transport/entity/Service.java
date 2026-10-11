package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.Nationalized;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "SERVICE")
@Check(
        constraints = "price >= 0"
)


public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ServiceID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CategoryID", nullable = false)
    private Category categoryID;

    @NotBlank
    @Size(max = 255)
    @Nationalized
    @Column(name = "ServiceName", nullable = false, length = 255)
    private String serviceName;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @NotBlank
    @Size(max = 50)
    @Column(name = "status", nullable = false, length = 50)
    @Check(
            constraints = "status IN ('ACTIVE', 'INACTIVE')"
    )
    private String status;


}