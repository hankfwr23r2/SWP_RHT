package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingPartyRole;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingCreateRequestDto(
        @NotNull BookingType bookingType,
        @Positive Long departureId,
        @NotNull LocalDate requestedPickupDate,
        @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal emergencySpendingLimit,
        @NotNull @Size(min = 4, max = 4) List<@NotNull @Valid PartyInput> parties,
        @NotNull @Size(min = 2) List<@NotNull @Valid AddressInput> addresses,
        @NotEmpty List<@NotNull @Valid HorseInput> horses,
        @NotNull Boolean acceptedServiceTerms,
        TransferMethod pickupMethod,
        TransferMethod deliveryMethod) {

    public BookingCreateRequestDto {
        pickupMethod = pickupMethod == null ? TransferMethod.COMPANY : pickupMethod;
        deliveryMethod = deliveryMethod == null ? TransferMethod.COMPANY : deliveryMethod;
    }

    public BookingCreateRequestDto(BookingType bookingType, Long departureId, LocalDate requestedPickupDate,
            BigDecimal emergencySpendingLimit, List<PartyInput> parties, List<AddressInput> addresses,
            List<HorseInput> horses, Boolean acceptedServiceTerms) {
        this(bookingType, departureId, requestedPickupDate, emergencySpendingLimit, parties, addresses, horses, acceptedServiceTerms, TransferMethod.COMPANY, TransferMethod.COMPANY);
    }

    public Booking toEntity(Customer customer, Departure departure) {
        Booking booking = new Booking();
        booking.setCustomer(customer);
        booking.setDeparture(departure);
        booking.setBookingType(bookingType);
        booking.setRequestedPickupDate(requestedPickupDate);
        booking.setPickupMethod(pickupMethod);
        booking.setDeliveryMethod(deliveryMethod);
        return booking;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PartyInput(
            @NotNull BookingPartyRole bookingRole,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Size(max = 20) String phone,
            @Size(max = 30) String identityDocumentType,
            @Size(max = 50) String identityDocumentNumber,
            @Pattern(regexp = "[A-Z]{2}") String identityDocumentCountryCode,
            @NotBlank @Size(max = 500) String address) {

        public BookingParty toEntity(Booking booking) {
            BookingParty party = new BookingParty();
            party.setBooking(booking);
            party.setFullName(fullName.trim());
            party.setPhone(phone.trim());
            party.setIdentityDocumentType(identityDocumentType);
            party.setIdentityDocumentNumber(identityDocumentNumber);
            party.setIdentityDocumentCountryCode(identityDocumentCountryCode);
            party.setAddress(address.trim());
            return party;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddressInput(
            @NotNull AddressRole addressType,
            @NotBlank @Size(max = 500) String addressLine,
            @NotBlank @Size(max = 10) String provinceCode,
            @NotNull @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6) BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6) BigDecimal longitude,
            @NotBlank @Size(max = 200) String contactName,
            @NotBlank @Size(max = 20) String contactPhone,
            @Size(max = 2000) String accessNote,
            @Positive Long sourceCustomerAddressId) {

        public BookingAddress toEntity(Booking booking, Province province, CustomerAddress source) {
            BookingAddress address = new BookingAddress();
            address.setBooking(booking);
            address.setAddressType(addressType);
            address.setAddressLine(addressLine.trim());
            address.setProvince(province);
            address.setCountry(province.getCountry());
            address.setLatitude(latitude);
            address.setLongitude(longitude);
            address.setContactName(contactName.trim());
            address.setContactPhone(contactPhone.trim());
            address.setAccessNote(accessNote);
            address.setSourceAddress(source);
            return address;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HorseInput(
            @NotNull @Positive Long horseId,
            @NotBlank @Size(max = 30) String stallTypeCode,
            @Size(max = 2000) String dietNote,
            @Min(1) @Max(10) Short feedingsPerDay,
            Boolean feedSuppliedByOwner,
            List<@NotNull @Valid AddOnInput> addOns,
            @NotNull @Valid InsuranceDeclarationRequestDto insurance,
            @PositiveOrZero Integer deliveryPlaceIndex) {

        public HorseInput(Long horseId, String stallTypeCode, String dietNote, Short feedingsPerDay,
                          Boolean feedSuppliedByOwner, List<AddOnInput> addOns, InsuranceDeclarationRequestDto insurance) {
            this(horseId, stallTypeCode, dietNote, feedingsPerDay, feedSuppliedByOwner, addOns, insurance, null);
        }

        public int selectedDeliveryPlace() {
            return deliveryPlaceIndex == null ? 0 : deliveryPlaceIndex;
        }

        public BookingHorse toEntity(Booking booking, Horse horse, StallType stallType) {
            BookingHorse row = new BookingHorse();
            row.setBooking(booking);
            row.setHorse(horse);
            row.setStallType(stallType);
            row.setDietNote(dietNote);
            row.setFeedingsPerDay(feedingsPerDay);
            row.setFeedSuppliedByOwner(Boolean.TRUE.equals(feedSuppliedByOwner));
            return row;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AddOnInput(@NotNull @Positive Long serviceId, @NotNull @Positive Integer quantity) {

        public BookingHorseAddOn toEntity(BookingHorse horse, BillableService service) {
            BookingHorseAddOn addOn = new BookingHorseAddOn();
            addOn.setBookingHorse(horse);
            addOn.setBillableService(service);
            addOn.setQuantity(quantity);
            return addOn;
        }
    }
}
