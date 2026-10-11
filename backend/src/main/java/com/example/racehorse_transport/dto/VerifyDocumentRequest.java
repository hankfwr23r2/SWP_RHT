package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDocumentRequest {
    private Integer routeId;

    @NotEmpty(message = "Danh sách ID giấy tờ không được để trống")
    private List<Integer> documentIds;
}
