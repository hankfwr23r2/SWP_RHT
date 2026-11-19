package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(name = "HorseHealthLogDTO")
public class HorseHealthLogDto implements Serializable {
    private Integer id;
    private Integer horseID;
    private String bookingCode;
    private Integer reportBy;
    private String healthStatus;
    private BigDecimal tempature;
    private BigDecimal weight;
    private String symptoms;
}
