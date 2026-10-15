package com.example.racehorse_transport.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HorseDTO implements Serializable {
    private Integer id;
    private Integer horseTypeID;
    private Integer customerID;
    private String horseName;
    private String microchipID;
    private String gender;
    private String breed;
    private String color;
    private Integer age;
    private BigDecimal weight;
    private String identificationMarks;
    private Integer completedTrips;
    private Instant createdAt;
    private String status;
}
