package com.example.racehorse_transport.dto.network;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

// Giao một tỉnh cho một hub. Tỉnh đã thuộc hub khác thì chuyển sang hub này (mỗi tỉnh thuộc đúng một hub).
@Getter
@Setter
@Schema(name = "HubServiceAreaRequest")
public class HubServiceAreaRequestDto {

    @NotNull(message = "Chọn hub phục vụ")
    private Long hubLocationId;
}
