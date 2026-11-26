package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
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
    void localKeepsTheGivenDateAndRejectsLaterPickup() {
        assertThat(BookingSubmissionPolicy.latestPickupDate(BookingType.LOCAL, TODAY)).isEqualTo(TODAY);
        assertThatCode(() -> BookingSubmissionPolicy.validatePickupDate(BookingType.LOCAL, TODAY, TODAY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validatePickupDate(BookingType.LOCAL, TODAY.plusDays(1), TODAY))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void linehaulPickupMustBeAtLeastOneCalendarDayBeforeDeparture() {
        for (BookingType type : List.of(BookingType.INTERPROVINCE, BookingType.EXPORT, BookingType.IMPORT)) {
            assertThat(BookingSubmissionPolicy.latestPickupDate(type, TODAY)).isEqualTo(TODAY.minusDays(1));
            assertThatCode(() -> BookingSubmissionPolicy.validatePickupDate(type, TODAY.minusDays(1), TODAY))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> BookingSubmissionPolicy.validatePickupDate(type, TODAY, TODAY))
                    .isInstanceOf(BookingRuleException.class);
        }
    }

    @Test
    void linehaulAllowsSelfTransferAtBothEnds() {
        for (BookingType type : List.of(BookingType.INTERPROVINCE, BookingType.EXPORT, BookingType.IMPORT)) {
            assertThatCode(() -> BookingSubmissionPolicy.validateTransfer(type, HubResolutionPolicy.Routing.LINEHAUL,
                    TransferMethod.SELF, TransferMethod.SELF)).doesNotThrowAnyException();
        }
    }

    @Test
    void directRequiresCompanyTransferAtBothEnds() {
        var direct = HubResolutionPolicy.Routing.DIRECT;
        var company = TransferMethod.COMPANY;
        var self = TransferMethod.SELF;
        assertThatCode(() -> BookingSubmissionPolicy.validateTransfer(BookingType.LOCAL, direct, company, company))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateTransfer(BookingType.INTERPROVINCE, direct, self, company))
                .isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateTransfer(BookingType.LOCAL, direct, company, self))
                .isInstanceOf(BookingRuleException.class);
    }

    @Test
    void routeMustBeActiveAndMatchThePickupAndDeliveryHubs() {
        var pickup = new Location();
        pickup.setId(1L);
        var delivery = new Location();
        delivery.setId(2L);
        var route = new Route();
        route.setOrigin(pickup);
        route.setDestination(delivery);
        route.setStatus(ActiveStatus.ACTIVE);
        assertThatCode(() -> BookingSubmissionPolicy.validateRoute(route, BookingType.EXPORT, 1L, 2L))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateRoute(route, BookingType.EXPORT, 2L, 1L))
                .isInstanceOf(BookingRuleException.class);
        route.setStatus(ActiveStatus.INACTIVE);
        assertThatThrownBy(() -> BookingSubmissionPolicy.validateRoute(route, BookingType.EXPORT, 1L, 2L))
                .isInstanceOf(BookingRuleException.class);
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
