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
public class HorseHealthLogDTO implements Serializable {
    private Integer id;
    private Integer horseID;
    private Integer bookingID;
    private Integer reportBy;
    private String healthStatus;
    private BigDecimal tempature;
    private BigDecimal weight;
    private String symptoms;
}
