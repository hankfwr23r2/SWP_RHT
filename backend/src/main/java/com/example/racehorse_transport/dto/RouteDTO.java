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
public class RouteDTO implements Serializable {
    private Integer id;
    private String routeName;
    private String description;
    private Integer estimatedTime;
    private BigDecimal distance;
    private String status;
}
