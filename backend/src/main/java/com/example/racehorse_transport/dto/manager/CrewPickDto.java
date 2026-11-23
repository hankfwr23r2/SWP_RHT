package com.example.racehorse_transport.dto.manager;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrewPickDto {
    private String tripId;
    private String driverId;
    private String escortId;
}
