package com.example.racehorse_transport.dto;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorseJourneyLogDTO implements Serializable {
    private Long id;
    private Integer horseId;
    private Integer bookingId;
    private String tripCode;
    private String fromLocation;
    private String toLocation;
    private Long departAt;
    private Long arrivedAt;
    private String driverName;
    private String proofImageUrl;
    private String notes;
    private String source;
    private Long createdAt;
}
