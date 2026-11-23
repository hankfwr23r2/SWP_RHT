package com.example.racehorse_transport.dto.departure;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "DepartureCancelRequest")
public class DepartureCancelRequestDto {

    // Ghi vào lịch sử trạng thái của các đơn trên chuyến
    @NotBlank(message = "Nhập lý do hủy chuyến")
    @Size(max = 1000, message = "Lý do tối đa 1000 ký tự")
    private String reason;
}
