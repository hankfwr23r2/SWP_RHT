package com.example.racehorse_transport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateHorseLocationRequest")
public class UpdateHorseLocationRequestDto {
    private String locationName;
    private String country;
    private String notes;
}
