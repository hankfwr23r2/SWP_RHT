package com.example.racehorse_transport.dto.booking;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingHorseDto {
    private String horseId;
    private String name;
    private String microchip;
    private String breed;
    private String sex;
    private String stall;
    private String feedPackage;
    private String waterPlan;
    private Boolean insuranceOpted;
    private String status; // ELIGIBLE, UNFIT
    private Map<String, Object> unfitInfo;
}

