package com.example.racehorse_transport.dto.specialist;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddClearanceItemRequest {
    private String type;
    private String label;
    private String note;
}
