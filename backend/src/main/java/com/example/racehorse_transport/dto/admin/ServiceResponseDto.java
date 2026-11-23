package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ServiceResponse")
public class ServiceResponseDto {
    private Integer id;
    private String serviceName;
    private String serviceCode;
    private String billingUnit;
    private String description;
    private Integer categoryId;
    private String categoryName;
    private BigDecimal price;
    private String status;
}
