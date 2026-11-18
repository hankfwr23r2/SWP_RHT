package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.example.racehorse_transport.entity.Horse;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "HorseDTO")
public class HorseDto implements Serializable {
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
    private String currentLocationName;
    private String currentCountry;
    private Long locationUpdatedAt;
    private String locationSource;
    private String transitStatus;

    public void applyTo(Horse horse) {
        if (getHorseName() != null) horse.setHorseName(getHorseName().trim());
        if (getMicrochipID() != null) horse.setMicrochipID(getMicrochipID().trim().toUpperCase());
        if (getGender() != null) horse.setGender(getGender());
        if (getBreed() != null) horse.setBreed(getBreed());
        if (getColor() != null) horse.setColor(getColor());
        if (getAge() != null) horse.setAge(getAge());
        if (getWeight() != null) horse.setWeight(getWeight());
        if (getIdentificationMarks() != null) horse.setIdentificationMarks(getIdentificationMarks());
        if (getStatus() != null) horse.setStatus(getStatus());
    }
}
