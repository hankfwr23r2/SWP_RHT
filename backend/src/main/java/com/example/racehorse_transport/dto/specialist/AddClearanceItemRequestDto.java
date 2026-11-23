package com.example.racehorse_transport.dto.specialist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AddClearanceItemRequest")
public class AddClearanceItemRequestDto {
    private String type;
    private String label;
    private String note;
}
