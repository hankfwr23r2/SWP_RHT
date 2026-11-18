package com.example.racehorse_transport.dto.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CustomerProfileResponse")
public class CustomerProfileResponseDto {
    private Integer userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String address;
    private int totalHorses;
    private int totalBookings;
}
