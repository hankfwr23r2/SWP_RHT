package com.example.racehorse_transport.dto.specialist;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResubmitItemDto {
    private String horseId;
    private String doc; // "passport", "vaccine", "lab", hoặc "insurance" cho bảo hiểm chuyến đi
    private String reason;
}
