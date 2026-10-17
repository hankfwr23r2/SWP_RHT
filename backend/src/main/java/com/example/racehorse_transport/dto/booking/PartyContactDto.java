package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyContactDto {
    @NotBlank(message = "Họ tên không được để trống")
    private String name;

    @NotBlank(message = "Số điện thoại không được để trống")
    private String phone;

    @Size(max = 50, message = "CCCD / MST / Hộ chiếu tối đa 50 ký tự")
    private String idNumber; // CCCD / MST / Hộ chiếu (không bắt buộc)

    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address; // không bắt buộc

    public static PartyContactDto consignorOf(Booking b) {
        return PartyContactDto.builder()
                .name(b.getConsignorName())
                .phone(b.getConsignorPhone())
                .idNumber(b.getConsignorIdNumber())
                .address(b.getConsignorAddress())
                .build();
    }

    public static PartyContactDto consigneeOf(Booking b) {
        return PartyContactDto.builder()
                .name(b.getConsigneeName())
                .phone(b.getConsigneePhone())
                .idNumber(b.getConsigneeIdNumber())
                .address(b.getConsigneeAddress())
                .build();
    }
}
