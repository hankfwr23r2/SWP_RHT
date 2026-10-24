package com.example.racehorse_transport.dto;

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
public class CreateRestStopRequest {

    @NotBlank(message = "Tên trạm dừng không được để trống")
    @Size(max = 255, message = "Tên trạm dừng không được vượt quá 255 ký tự")
    private String locationName;

    @NotBlank(message = "Địa chỉ không được để trống")
    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @NotBlank(message = "Thành phố không được để trống")
    @Size(max = 100, message = "Thành phố không được vượt quá 100 ký tự")
    private String city;

    @NotBlank(message = "Tỉnh/thành không được để trống")
    @Size(max = 100, message = "Tỉnh/thành không được vượt quá 100 ký tự")
    private String province;

    @NotBlank(message = "Quốc gia không được để trống")
    @Size(max = 100, message = "Quốc gia không được vượt quá 100 ký tự")
    private String country;
}
