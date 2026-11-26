package com.example.racehorse_transport.dto.horse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public record HorseDocumentUploadRequestDto(
        @NotBlank(message = "Chọn loại giấy tờ.") @Size(max = 30, message = "Mã loại giấy tờ không hợp lệ.")
        String documentTypeCode,
        @NotNull(message = "Chọn tệp giấy tờ.") MultipartFile file) {
}
