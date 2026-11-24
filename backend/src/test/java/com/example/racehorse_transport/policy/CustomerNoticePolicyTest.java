package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.entity.enums.HorseLegStatus;
import com.example.racehorse_transport.entity.enums.HorseLegType;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.ExpectedTimes;
import com.example.racehorse_transport.policy.CustomerNoticePolicy.HourWindow;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CustomerNoticePolicyTest {

    // 08:25 ngày 05/03 giờ Việt Nam
    private static final Instant AT = Instant.parse("2031-03-05T01:25:00Z");

    @Test
    void windowRoundsDownToTheHourInVietnamTime() {
        assertThat(CustomerNoticePolicy.window(AT))
                .isEqualTo(new HourWindow(Instant.parse("2031-03-05T01:00:00Z"), Instant.parse("2031-03-05T02:00:00Z")));
        assertThat(CustomerNoticePolicy.text(CustomerNoticePolicy.window(AT))).isEqualTo("08:00–09:00 ngày 05/03");
        // Nửa đêm: ngày theo giờ bắt đầu
        assertThat(CustomerNoticePolicy.text(CustomerNoticePolicy.window(Instant.parse("2031-03-05T16:10:00Z")))).isEqualTo("23:00–00:00 ngày 05/03");
    }

    @Test
    void expectedTimesFollowTheEndWhereTheTruckMeetsTheCustomer() {
        Instant end = AT.plusSeconds(7200);
        assertThat(CustomerNoticePolicy.expectedTimes(leg(HorseLegType.PICKUP, HorseLegStatus.PLANNED, AT, end))).isEqualTo(new ExpectedTimes(AT, null));
        assertThat(CustomerNoticePolicy.expectedTimes(leg(HorseLegType.DELIVERY, HorseLegStatus.PLANNED, AT, end))).isEqualTo(new ExpectedTimes(null, end));
        assertThat(CustomerNoticePolicy.expectedTimes(leg(HorseLegType.DIRECT, HorseLegStatus.PLANNED, AT, end))).isEqualTo(new ExpectedTimes(AT, end));
    }

    @ParameterizedTest
    @EnumSource(value = HorseLegType.class, names = {"PICKUP", "DELIVERY", "DIRECT"}, mode = EnumSource.Mode.EXCLUDE)
    void otherLegsTellNothing(HorseLegType type) {
        ExpectedTimes times = CustomerNoticePolicy.expectedTimes(leg(type, HorseLegStatus.PLANNED, AT, AT.plusSeconds(60)));
        assertThat(times.isEmpty()).isTrue();
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", null, times, 30)).isEmpty();
    }

    @Test
    void firstNoticeGivesTheWindowOfEachEnd() {
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", null, new ExpectedTimes(AT, AT.plusSeconds(7200)), 30)).get().satisfies(notice -> {
            assertThat(notice.title()).isEqualTo("Giờ xe dự kiến");
            assertThat(notice.message()).isEqualTo("Đơn BK-1: xe dự kiến đến đón ngựa 08:00–09:00 ngày 05/03, giao ngựa 10:00–11:00 ngày 05/03.");
        });
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", null, new ExpectedTimes(null, AT), 30)).get()
                .satisfies(notice -> assertThat(notice.message()).isEqualTo("Đơn BK-1: xe dự kiến giao ngựa 08:00–09:00 ngày 05/03."));
    }

    @Test
    void tellsAgainOnlyPastTheThreshold() {
        ExpectedTimes told = new ExpectedTimes(AT, null);
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", told, new ExpectedTimes(AT.plusSeconds(30 * 60), null), 30)).isEmpty();
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", told, new ExpectedTimes(AT.minusSeconds(31 * 60), null), 30)).get()
                .satisfies(notice -> assertThat(notice.title()).isEqualTo("Giờ dự kiến thay đổi"));
        // Chạy thẳng: chỉ đầu giao dời cũng báo
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", new ExpectedTimes(AT, AT.plusSeconds(3600)),
                new ExpectedTimes(AT, AT.plusSeconds(2 * 3600)), 30)).isPresent();
    }

    @Test
    void toldTimeIgnoresShiftsUnderTheThresholdSoTheyDoNotAddUp() {
        HorseLeg first = leg(HorseLegType.PICKUP, HorseLegStatus.CANCELLED, AT, AT.plusSeconds(7200));
        HorseLeg silent = leg(HorseLegType.PICKUP, HorseLegStatus.CANCELLED, AT.plusSeconds(25 * 60), AT.plusSeconds(7200));
        assertThat(CustomerNoticePolicy.lastTold(List.of(), 30)).isNull();
        assertThat(CustomerNoticePolicy.lastTold(List.of(first, silent), 30)).isEqualTo(new ExpectedTimes(AT, null));
        // 50 phút so với giờ đã báo, dù chỉ 25 phút so với chặng hủy gần nhất
        assertThat(CustomerNoticePolicy.expectedNotice("BK-1", CustomerNoticePolicy.lastTold(List.of(first, silent), 30),
                new ExpectedTimes(AT.plusSeconds(50 * 60), null), 30)).isPresent();
        // Giao không thành rồi giao lại: tính như báo lần đầu
        HorseLeg failed = leg(HorseLegType.PICKUP, HorseLegStatus.FAILED, AT, AT.plusSeconds(7200));
        assertThat(CustomerNoticePolicy.lastTold(List.of(first, failed), 30)).isNull();
    }

    @Test
    void appointmentsAndOrderNoticesNameTheOrder() {
        assertThat(CustomerNoticePolicy.hubDropOff("BK-1", "Hub TP.HCM", AT).message()).isEqualTo("Đơn BK-1: đưa ngựa tới Hub TP.HCM lúc 08:25 ngày 05/03.");
        assertThat(CustomerNoticePolicy.hubCollection("BK-1", "Hub Đà Nẵng", AT).message()).isEqualTo("Đơn BK-1: nhận ngựa tại Hub Đà Nẵng lúc 08:25 ngày 05/03.");
        assertThat(CustomerNoticePolicy.orderReceived("BK-1").title()).isEqualTo("Đã nhận đơn");
        assertThat(CustomerNoticePolicy.orderWaitingIntake("BK-1").message()).contains("BK-1");
    }

    private static HorseLeg leg(HorseLegType type, HorseLegStatus status, Instant boardArrival, Instant end) {
        TripStop board = new TripStop();
        board.setPlannedArrivalAt(boardArrival);
        HorseLeg leg = new HorseLeg();
        leg.setLegType(type);
        leg.setStatus(status);
        leg.setBoardTripStop(board);
        // Xe rời điểm lên sau khi dừng nhận ngựa
        leg.setPlannedStartAt(boardArrival.plusSeconds(1800));
        leg.setPlannedEndAt(end);
        return leg;
    }
}
