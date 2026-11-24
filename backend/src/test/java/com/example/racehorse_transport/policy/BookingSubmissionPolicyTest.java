package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingPartyRole;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class BookingSubmissionPolicyTest {

    private static final Instant NOW = Instant.parse("2027-01-10T18:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2027, 1, 11);

    @Test
    void usesVietnamDateAndRequiresTermsAndDistinctHorses() {
        assertThatCode(() -> BookingSubmissionPolicy.validate(request(TODAY, true, List.of(horse(1L))), NOW))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validate(request(TODAY.minusDays(1), true,
                List.of(horse(1L))), NOW)).isInstanceOf(BookingRuleException.class).hasMessageContaining("quá khứ");
        assertThatThrownBy(() -> BookingSubmissionPolicy.validate(request(TODAY, false, List.of(horse(1L))), NOW))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("điều khoản");
        assertThatThrownBy(() -> BookingSubmissionPolicy.validate(request(TODAY, true, List.of(horse(1L), horse(1L))), NOW))
                .isInstanceOf(BookingRuleException.class).hasMessageContaining("trùng");
    }

    @Test
    void departureIsRequiredOnlyForLinehaul() {
        assertThatCode(() -> BookingSubmissionPolicy.validateDepartureSelection(HubResolutionPolicy.Routing.DIRECT, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateDepartureSelection(HubResolutionPolicy.Routing.DIRECT, 1L))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateDepartureSelection(HubResolutionPolicy.Routing.LINEHAUL, null))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void acceptsPickupOnDepartureDayButRejectsLaterPickup() {
        assertThatCode(() -> BookingSubmissionPolicy.validatePickupDate(BookingType.IMPORT, TODAY, TODAY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validatePickupDate(BookingType.IMPORT, TODAY.plusDays(1), TODAY))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void linehaulPickupMustBeAtLeastOneCalendarDayBeforeDeparture() {
        for (BookingType type : List.of(BookingType.INTERPROVINCE, BookingType.EXPORT)) {
            assertThatCode(() -> BookingSubmissionPolicy.validatePickupDate(type, TODAY.minusDays(1), TODAY))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> BookingSubmissionPolicy.validatePickupDate(type, TODAY, TODAY))
                    .isInstanceOf(BookingRuleException.class);
        }
    }

    @Test
    void onlyDomesticLinehaulEndsAllowSelfTransfer() {
        var linehaul = HubResolutionPolicy.Routing.LINEHAUL;
        var company = TransferMethod.COMPANY;
        var self = TransferMethod.SELF;
        assertThatCode(() -> BookingSubmissionPolicy.validateTransfer(BookingType.INTERPROVINCE, linehaul, self, self))
                .doesNotThrowAnyException();
        assertThatCode(() -> BookingSubmissionPolicy.validateTransfer(BookingType.EXPORT, linehaul, self, company))
                .doesNotThrowAnyException();
        assertThatCode(() -> BookingSubmissionPolicy.validateTransfer(BookingType.IMPORT, linehaul, company, self))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateTransfer(BookingType.IMPORT, linehaul, self, company))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateTransfer(BookingType.EXPORT, linehaul, company, self))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateTransfer(BookingType.INTERPROVINCE,
                HubResolutionPolicy.Routing.DIRECT, self, company)).isInstanceOf(BookingRuleException.class);
    }

    private static BookingCreateRequestDto request(LocalDate date, boolean accepted, List<HorseInput> horses) {
        return new BookingCreateRequestDto(BookingType.LOCAL, null, date, null,
                Arrays.stream(BookingPartyRole.values())
                        .map(role -> new PartyInput(role, "Test", "0900000000", null, null, null, "Test")).toList(),
                Arrays.stream(AddressRole.values())
                        .map(role -> new AddressInput(role, "Test", "VN-79", "VN", null, null, "Test", "0900000000", null, null))
                        .toList(),
                horses, accepted);
    }

    private static HorseInput horse(Long id) {
        return new HorseInput(id, "STANDARD", null, null, false, List.of(),
                new InsuranceDeclarationRequestDto(InsuranceCoverageOption.ARRANGED, null, null, null, null, null));
    }

    @Test
    void priceHoldIncludesTheLastDateMeasuredFromTheMissedDeparture() {
        LocalDate missed = LocalDate.of(2027, 1, 17);
        BookingSubmissionPolicy.validatePriceHold(missed, missed.plusDays(30), 30);
        assertThatThrownBy(() -> BookingSubmissionPolicy.validatePriceHold(missed, missed.plusDays(31), 30))
                .isInstanceOf(BookingRuleException.class);
    }
}
