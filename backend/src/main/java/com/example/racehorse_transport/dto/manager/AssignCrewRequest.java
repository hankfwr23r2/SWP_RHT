package com.example.racehorse_transport.dto.manager;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignCrewRequest {
    private String by;
    private List<CrewPickDto> picks;
}
