package com.example.racehorse_transport.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceResponse {
    private Integer id;
    private String serviceName;
    private Integer categoryId;
    private String categoryName;
    private BigDecimal price;
    private String status;
}
