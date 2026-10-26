package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "SERVICE")
@Check(
        name = "CK_SERVICE_PRICE_STATUS",
        constraints = "price >= 0 AND status IN ('ACTIVE', 'INACTIVE')"
)
public class Service {

    @Id
    @Column(name = "ServiceID", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "CategoryID", nullable = false)
    private Category categoryID;

    @NotBlank
    @Size(max = 255)
    @Column(name = "ServiceName", nullable = false, length = 255)
    private String serviceName;

    // Hai cột này thêm sau khi bảng đã có dữ liệu: để cột cho phép rỗng thì Hibernate mới thêm được cột,
    // ReferenceDataSeeder điền mã và đơn vị tính cho các dòng cũ; dữ liệu mới được kiểm tra ở CreateServiceRequest.
    @Size(max = 100)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$")
    @Column(name = "service_code", length = 100, unique = true)
    private String serviceCode;

    @Pattern(regexp = "PER_TRIP|PER_HORSE|PER_HORSE_DAY")
    @Column(name = "billing_unit", length = 30)
    private String billingUnit;

    @Size(max = 1000)
    @Column(name = "description", length = 1000)
    private String description;

    @NotNull
    @DecimalMin(value = "0.00", inclusive = true)
    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "Status phải là ACTIVE hoặc INACTIVE")
    @Column(name = "status", nullable = false, length = 50)
    private String status;
}