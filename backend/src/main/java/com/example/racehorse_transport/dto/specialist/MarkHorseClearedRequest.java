package com.example.racehorse_transport.dto.specialist;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkHorseClearedRequest {
    private String horseId;
    private boolean cleared;
}
