package com.example.racehorse_transport.dto.settlement;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SubmitExpensesRequest")
public class SubmitExpensesRequestDto {
    private String tripId;
    private String driverName;
    private List<ExpenseItemDto> expenses;
}
