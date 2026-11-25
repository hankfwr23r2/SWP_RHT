package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.BookingAddress;
import com.example.racehorse_transport.entity.CustomerAddress;
import jakarta.validation.constraints.*;

public record DeliveryChangeRequestDto(
        @NotNull @Positive Long deliveryAddressId,
        @NotNull @Positive Long customerAddressId,
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Size(max = 20) @Pattern(regexp = "[+0-9() .-]+") String contactPhone) {

    public void applyTo(BookingAddress address, CustomerAddress source) {
        address.setSourceAddress(source);
        address.setAddressLine(source.getAddressLine());
        address.setProvince(source.getProvince());
        address.setCountry(source.getCountry());
        address.setLatitude(source.getLatitude());
        address.setLongitude(source.getLongitude());
        address.setContactName(contactName.trim());
        address.setContactPhone(contactPhone.trim());
        address.setAccessNote(null);
        address.setLocation(null);
    }
}
