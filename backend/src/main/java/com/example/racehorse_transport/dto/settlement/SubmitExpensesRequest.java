package com.example.racehorse_transport.dto.settlement;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitExpensesRequest {
    private String tripId;
    private String driverName;
    private List<ExpenseItemDto> expenses;
}
