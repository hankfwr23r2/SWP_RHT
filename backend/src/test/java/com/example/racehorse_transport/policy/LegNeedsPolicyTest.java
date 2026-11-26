package com.example.racehorse_transport.policy;

import static com.example.racehorse_transport.entity.enums.HorseLegType.DELIVERY;
import static com.example.racehorse_transport.entity.enums.HorseLegType.DIRECT;
import static com.example.racehorse_transport.entity.enums.HorseLegType.LINEHAUL;
import static com.example.racehorse_transport.entity.enums.HorseLegType.PICKUP;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.BookingHorseStatus;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.TransferMethod;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegNeedsPolicyTest {

    private static final TransferMethod COMPANY = TransferMethod.COMPANY;
    private static final TransferMethod SELF = TransferMethod.SELF;

    @Test
    void directBookingNeedsOneLeg() {
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.ACTIVE, BookingType.LOCAL, false, COMPANY, COMPANY, List.of())).containsExactly(DIRECT);
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.ACTIVE, BookingType.LOCAL, false, COMPANY, COMPANY, List.of(DIRECT))).isEmpty();
    }

    @Test
    void linehaulBookingSkipsTheSidesTheCustomerHandles() {
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.ACTIVE, BookingType.INTERPROVINCE, true, COMPANY, COMPANY, List.of(PICKUP)))
                .containsExactly(LINEHAUL, DELIVERY);
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.ACTIVE, BookingType.INTERPROVINCE, true, SELF, SELF, List.of())).containsExactly(LINEHAUL);
    }

    @Test
    void foreignHubSideFollowsTheChosenMethodLikeDomestic() {
        // Hub nước ngoài là của công ty: tự chở ở đầu nước ngoài thì không có chặng xe
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.ACTIVE, BookingType.EXPORT, true, SELF, SELF, List.of())).containsExactly(LINEHAUL);
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.ACTIVE, BookingType.IMPORT, true, COMPANY, COMPANY, List.of()))
                .containsExactly(PICKUP, LINEHAUL, DELIVERY);
    }

    @Test
    void excludedHorseNeedsNothing() {
        assertThat(LegNeedsPolicy.missing(BookingHorseStatus.EXCLUDED, BookingType.LOCAL, false, COMPANY, COMPANY, List.of())).isEmpty();
    }
}
