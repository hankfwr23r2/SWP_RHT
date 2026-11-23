package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "HorseJourneyLogDTO")
public class HorseJourneyLogDto implements Serializable {
    private Long id;
    private Integer horseId;
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
