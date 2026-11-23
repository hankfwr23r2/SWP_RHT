package com.example.racehorse_transport.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateSystemConfigRequest")
public class UpdateSystemConfigRequestDto {

    @NotBlank(message = "Giá trị cấu hình không được để trống")
    @Size(max = 500, message = "Giá trị cấu hình không được vượt quá 500 ký tự")
    private String configValue;
}
