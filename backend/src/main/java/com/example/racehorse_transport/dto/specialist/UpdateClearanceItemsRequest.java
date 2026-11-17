package com.example.racehorse_transport.dto.specialist;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateClearanceItemsRequest {
    @NotEmpty @Size(max = 30)
    private List<@NotNull @Valid UpdateClearanceItemRequest> items;
}
