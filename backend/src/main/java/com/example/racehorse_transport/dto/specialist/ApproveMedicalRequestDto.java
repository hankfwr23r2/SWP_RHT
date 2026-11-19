package com.example.racehorse_transport.dto.specialist;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApproveMedicalRequest")
public class ApproveMedicalRequestDto {
    private String specialistName;
    private String conclusion; // "passed"
    private String note;
}
