package com.example.racehorse_transport.dto.admin;

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
public class UpdateSystemConfigRequest {

    @NotBlank(message = "Giá trị cấu hình không được để trống")
    @Size(max = 500, message = "Giá trị cấu hình không được vượt quá 500 ký tự")
    private String configValue;
}
