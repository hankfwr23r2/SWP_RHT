package com.example.racehorse_transport.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteDeliveryRequest {
    @NotBlank(message = "Tên người nhận không được để trống")
    private String recipientName;

    private String recipientSignature;
    private String note;
}