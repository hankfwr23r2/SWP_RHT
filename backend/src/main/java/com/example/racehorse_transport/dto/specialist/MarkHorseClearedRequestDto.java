package com.example.racehorse_transport.dto.specialist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MarkHorseClearedRequest")
public class MarkHorseClearedRequestDto {
    private String horseId;
    private boolean cleared;
}
