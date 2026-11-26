package com.example.racehorse_transport.dto.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddOnInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingDtoTest {
    @Test
    void bookingResponseIncludesTheCurrentSpecialist() {
        var booking = new Booking();
        assertThat(BookingResponseDto.from(booking).specialistId()).isNull();
        var assignee = new com.example.racehorse_transport.entity.Staff();
        assignee.setUserId(17L);
        assignee.setFullName("Mai");
        booking.setSpecialist(assignee);
        assertThat(BookingResponseDto.from(booking).specialistId()).isEqualTo(17L);
        assertThat(BookingResponseDto.from(booking).specialistName()).isEqualTo("Mai");
    }


    @Test
    void summaryShowsDeliveryProvinceAndCountryWhenLoaded() {
        var customer = new Customer();
        customer.setFullName("Khách");
        var booking = new Booking();
        booking.setCustomer(customer);
        assertThat(BookingSummaryResponseDto.from(booking, 1, null).destinationProvinceName()).isNull();
        var country = new Country();
        country.setName("Campuchia");
        var province = new Province();
        province.setName("Phnom Penh");
        var delivery = new BookingAddress();
        delivery.setProvince(province);
        delivery.setCountry(country);
        var summary = BookingSummaryResponseDto.from(booking, 2, delivery);
        assertThat(summary.destinationProvinceName()).isEqualTo("Phnom Penh");
        assertThat(summary.destinationCountryName()).isEqualTo("Campuchia");
        assertThat(summary.horseCount()).isEqualTo(2);
    }

    @Test
    void optionalNotesAcceptUpToTwoThousandCharacters() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (var field : List.of(
                    java.util.Map.entry(AddressInput.class, "accessNote"),
                    java.util.Map.entry(HorseInput.class, "dietNote"))) {
                assertThat(validator.validateValue(field.getKey(), field.getValue(), null)).isEmpty();
                assertThat(validator.validateValue(field.getKey(), field.getValue(), "a".repeat(2000))).isEmpty();
                assertThat(validator.validateValue(field.getKey(), field.getValue(), "a".repeat(2001))).hasSize(1);
            }
        }
    }

    @Test
    void bookingMappingLeavesFieldsOwnedByServiceUnset() throws Exception {
        BookingCreateRequestDto input = new ObjectMapper().readValue("""
                {"bookingType":"LOCAL","transportMode":"PRIVATE","id":99,"code":"fake","status":"COMPLETED","createdBy":99,
                 "createdAt":"2027-01-01T00:00:00Z","price":100,"emergencySpendingLimit":42}
                """, BookingCreateRequestDto.class);
        Customer owner = new Customer();
        Booking booking = input.toEntity(owner, null);
        assertThat(booking.getCustomer()).isSameAs(owner);
        assertThat(booking.getBookingType()).isEqualTo(BookingType.LOCAL);
        assertThat(BookingResponseDto.from(booking).transportMode()).isEqualTo(TransportMode.PRIVATE);
        assertThat(booking.getPickupMethod()).isEqualTo(TransferMethod.COMPANY);
        assertThat(booking.getDeliveryMethod()).isEqualTo(TransferMethod.COMPANY);
        assertThat(booking.getId()).isNull();
        assertThat(booking.getCode()).isNull();
        assertThat(booking.getStatus()).isNull();
        assertThat(booking.getCreatedBy()).isNull();
        assertThat(booking.getCreatedAt()).isNull();
        assertThat(booking.getEmergencySpendingLimit()).isNull();
    }

    @Test
    void mapsChosenTransferMethodAndDefaultsNull() throws Exception {
        BookingCreateRequestDto input = new ObjectMapper().readValue("""
                {"bookingType":"EXPORT","pickupMethod":"SELF","deliveryMethod":null}
                """, BookingCreateRequestDto.class);
        Booking booking = input.toEntity(new Customer(), null);
        var response = BookingDetailResponseDto.from(booking, List.of(), List.of(), List.of());
        assertThat(response.rated()).isFalse();
        assertThat(BookingDetailResponseDto.from(booking, List.of(), List.of(), List.of(), null, null, true).rated())
                .isTrue();
        assertThat(response.pickupMethod()).isEqualTo(TransferMethod.SELF);
        assertThat(response.deliveryMethod()).isEqualTo(TransferMethod.COMPANY);
        assertThat(response.intakeByName()).isNull();
        assertThat(BookingDetailResponseDto.from(booking, List.of(), List.of(), List.of(), null, "Lê An", true).intakeByName()).isEqualTo("Lê An");
    }

    @Test
    void childMappingsLeaveIdsStatusAndPriceUnset() throws Exception {
        Booking booking = new Booking();
        var party = new PartyInput(BookingPartyRole.PAYER, " Payer ", "0900000000", null, null, null, " Address ")
                .toEntity(booking);
        assertThat(party.getId()).isNull();
        assertThat(party.getFullName()).isEqualTo("Payer");
        Province province = new Province();
        Country country = new Country();
        province.setCountry(country);
        var address = new AddressInput(AddressRole.PICKUP, " Address ", "VN-79", "VN", BigDecimal.ONE,
                BigDecimal.TEN, "Contact", "0900000000", null, null).toEntity(booking, province, null);
        assertThat(address.getId()).isNull();
        assertThat(address.getCountry()).isSameAs(country);
        Horse horse = new Horse();
        var row = new HorseInput(1L, "STANDARD", "hay", (short) 2, true, null, null)
                .toEntity(booking, horse, new StallType());
        assertThat(row.getId()).isNull();
        assertThat(row.getStatus()).isNull();
        assertThat(row.getAddedAt()).isNull();
        assertThat(row.getHorse()).isSameAs(horse);
        var selection = new AddOnInput(1L, 2).toEntity(row, new BillableService());
        assertThat(selection.getId()).isNull();
        assertThat(selection.getQuantity()).isEqualTo(2);
        var arranged = new ObjectMapper().readValue("""
                {"coverageOption":"ARRANGED","id":99,"status":"CONFIRMED","coverageAmount":200,
                 "currencyCode":"VND","premiumAmount":100,"policyNumber":"ignore","insurerPartyId":99}
                """, InsuranceDeclarationRequestDto.class);
        var insurance = arranged.toEntity(row, null, null);
        assertThat(insurance.getBookingHorse()).isSameAs(row);
        assertThat(insurance.getId()).isNull();
        assertThat(insurance.getStatus()).isNull();
        assertThat(insurance.getConfirmedBy()).isNull();
        assertThat(insurance.getPolicyNumber()).isNull();
        assertThat(insurance.getCoverageAmount()).isNull();
        assertThat(insurance.getCurrencyCode()).isNull();
        assertThat(insurance.getPremiumAmount()).isNull();
    }

    @Test
    void detailMapsPartyAddressHorseAddOnAndInsuranceSnapshots() {
        Booking booking = new Booking();
        booking.setId(3L);
        var party = new PartyInput(BookingPartyRole.PAYER, "Payer", "0900000000", null, null, null, "Address")
                .toEntity(booking);
        party.setId(new BookingPartyId(3L, BookingPartyRole.PAYER));
        Province province = new Province();
        province.setCode("VN-79");
        Country country = new Country();
        country.setCode("VN");
        province.setCountry(country);
        var address = new AddressInput(AddressRole.PICKUP, "Address", "VN-79", "VN", BigDecimal.ONE,
                BigDecimal.TEN, "Contact", "0900000000", null, null).toEntity(booking, province, null);

        Horse horse = new Horse();
        horse.setId(6L);
        StallType stall = new StallType();
        stall.setCode("STANDARD");
        var row = new HorseInput(6L, "STANDARD", "hay", (short) 2, true, null, null).toEntity(booking, horse, stall);
        BillableService service = new BillableService();
        service.setId(8L);
        var addOn = new AddOnInput(8L, 2).toEntity(row, service);
        var insurance = new InsuranceDeclarationRequestDto(InsuranceCoverageOption.ARRANGED, null, null, null, null, null)
                .toEntity(row, null, null);
        insurance.setStatus(HorseInsuranceStatus.DRAFT);

        var horseDetail = BookingDetailResponseDto.HorseDetail.from(row, List.of(addOn), insurance);
        var detail = BookingDetailResponseDto.from(booking, List.of(party),
                List.of(address), List.of(horseDetail));
        assertThat(detail.parties()).singleElement().satisfies(value -> {
            assertThat(value.bookingRole()).isEqualTo(BookingPartyRole.PAYER);
            assertThat(value.fullName()).isEqualTo("Payer");
        });
        assertThat(detail.addresses()).singleElement().satisfies(value -> {
            assertThat(value.provinceCode()).isEqualTo("VN-79");
            assertThat(value.countryCode()).isEqualTo("VN");
        });
        assertThat(detail.horses()).singleElement().satisfies(value -> {
            assertThat(value.horseId()).isEqualTo(6L);
            assertThat(value.stallTypeCode()).isEqualTo("STANDARD");
            assertThat(value.addOns()).containsExactly(new BookingDetailResponseDto.AddOn(8L, null, 2));
            assertThat(value.insurance().coverageOption()).isEqualTo(InsuranceCoverageOption.ARRANGED);
            assertThat(value.insurance().status()).isEqualTo(HorseInsuranceStatus.DRAFT);
        });
    }

    @Test
    void responseMapsHeader() {
        Booking booking = new Booking();
        booking.setId(3L);
        booking.setCode("BK-TEST");
        booking.setRequestedPickupDate(LocalDate.of(2027, 1, 11));
        booking.setCreatedAt(Instant.parse("2027-01-01T00:00:00Z"));
        booking.setStatus(BookingStatus.PENDING_INTAKE);
        assertThat(BookingResponseDto.from(booking)).satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(3L);
            assertThat(dto.code()).isEqualTo("BK-TEST");
            assertThat(dto.status()).isEqualTo(BookingStatus.PENDING_INTAKE);
            assertThat(dto.departureId()).isNull();
        });
    }
}
