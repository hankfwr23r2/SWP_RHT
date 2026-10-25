package com.example.racehorse_transport.dto.booking;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingServiceSelectionDto {
    private Integer id;
    private String serviceCode;
    private String serviceName;
    private Integer categoryId;
    private String categoryName;
    private BigDecimal price;
    private String billingUnit;
    private String description;
}
