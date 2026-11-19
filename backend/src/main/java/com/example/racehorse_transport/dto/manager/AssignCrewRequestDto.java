package com.example.racehorse_transport.dto.manager;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AssignCrewRequest")
public class AssignCrewRequestDto {
    private String by;
    private List<CrewPickDto> picks;
}
