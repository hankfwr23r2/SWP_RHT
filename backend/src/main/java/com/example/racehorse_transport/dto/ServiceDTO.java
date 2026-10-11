package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDTO implements Serializable {
    private Integer id;
    private Integer categoryID;
    private String serviceName;
    private BigDecimal price;
    private String status;
}
