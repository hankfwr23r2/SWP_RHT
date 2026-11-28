package com.example.racehorse_transport.dto.tracking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

// Tra cứu công khai: tối đa 5 mã đơn cùng 4 số cuối điện thoại người gửi
public record PublicTrackingRequestDto(
        @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 20) String> codes,
        @NotBlank @Pattern(regexp = "\\d{4}", message = "Nhập đúng 4 số cuối điện thoại.") String phoneLast4) {
}
