package com.example.racehorse_transport.dto.field;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "HandoverCompleteRequest")
public class HandoverCompleteRequestDto {

    @NotBlank(message = "Tên người nhận không được để trống")
    private String recipientName;

    private String recipientPhone;

    private String recipientSignature; // Chữ ký số điện tử hoặc ghi chú xác nhận

    private String proofImageUrl; // Ảnh chụp Biên bản bàn giao ký tay

    private String driverUsername;

    private String note;
}
