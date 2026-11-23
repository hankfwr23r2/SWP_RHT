package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(name = "ResubmitDocumentsRequest")
public class ResubmitDocumentsRequestDto {
    // Khai báo bảo hiểm mới cho các ngựa được yêu cầu bổ sung bảo hiểm.
    @Valid private List<InsuranceResubmissionDto> insurance;
}
