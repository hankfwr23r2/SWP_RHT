package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.Booking;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.example.racehorse_transport.validation.ContactPatterns;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartyContactDto {
    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Tên liên hệ phải từ 2 đến 100 ký tự")
    @Pattern(regexp = ContactPatterns.CONTACT_NAME, message = "Tên liên hệ chỉ được chứa chữ, số và dấu câu thông dụng")
    private String name;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = ContactPatterns.REGIONAL_PHONE, message = "Số điện thoại phải thuộc Việt Nam, Campuchia hoặc Lào")
    private String phone;

    @NotBlank(message = "CCCD / MST / Hộ chiếu không được để trống")
    @Size(max = 50, message = "CCCD / MST / Hộ chiếu tối đa 50 ký tự")
    private String idNumber; // CCCD / MST / Hộ chiếu

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
