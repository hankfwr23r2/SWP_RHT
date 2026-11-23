package com.example.racehorse_transport.dto.specialist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(name = "UpdateClearanceItemsRequest")
public class UpdateClearanceItemsRequestDto {
    @NotEmpty @Size(max = 30)
    private List<@NotNull @Valid UpdateClearanceItemRequestDto> items;
}
