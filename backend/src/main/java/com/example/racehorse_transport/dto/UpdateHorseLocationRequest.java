package com.example.racehorse_transport.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateHorseLocationRequest {
    private String locationName;
    private String country;
    private String reason;
    private String notes;
}
