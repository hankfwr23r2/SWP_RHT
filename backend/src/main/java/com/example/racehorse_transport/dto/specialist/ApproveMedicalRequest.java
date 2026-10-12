package com.example.racehorse_transport.dto.specialist;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveMedicalRequest {
    private String specialistName;
    private String conclusion; // "passed"
    private String note;
}
