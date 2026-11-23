package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.DepartureStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeparturePoliciesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23); // thứ Hai
    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    @Test
    void generatesMatchingWeekdaysAfterToday() {
        // Thứ Hai và thứ Năm trong 2 tuần tới, không gồm hôm nay
        List<LocalDate> dates = DepartureGenerationPolicy.datesToGenerate(TODAY, 2, LocalDate.of(2026, 1, 1), null, Set.of((short) 1, (short) 4));
        assertThat(dates).containsExactly(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 3), LocalDate.of(2026, 12, 7));
    }

    @Test
    void generationRespectsScheduleValidity() {
        List<LocalDate> dates = DepartureGenerationPolicy.datesToGenerate(TODAY, 4, LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 8), Set.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7));
        assertThat(dates).first().isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(dates).last().isEqualTo(LocalDate.of(2026, 12, 8));
        assertThat(DepartureGenerationPolicy.datesToGenerate(TODAY, 0, TODAY, null, Set.of((short) 1))).isEmpty();
        assertThat(DepartureGenerationPolicy.datesToGenerate(TODAY, 2, TODAY, null, Set.of())).isEmpty();
    }

    @Test
    void computesDepartureAndCutoffInLocalTime() {
        Instant planned = DepartureGenerationPolicy.plannedDepartAt(LocalDate.of(2026, 12, 7), LocalTime.of(6, 0), ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(planned).isEqualTo(Instant.parse("2026-12-06T23:00:00Z"));
        assertThat(DepartureGenerationPolicy.bookingCutoffAt(planned, 72)).isEqualTo(Instant.parse("2026-12-03T23:00:00Z"));
    }

    @Test
    void reservesOnlyOpenDepartureBeforeCutoffWithEnoughStalls() {
        Instant cutoff = NOW.plusSeconds(3600);
        assertThat(DepartureCapacityPolicy.reasonNotReservable(DepartureStatus.OPEN, cutoff, NOW, 9, 7, 2)).isEmpty();
        assertThat(DepartureCapacityPolicy.reasonNotReservable(DepartureStatus.OPEN, cutoff, NOW, 9, 8, 2)).get()
                .asString().contains("còn 1 khoang");
        assertThat(DepartureCapacityPolicy.reasonNotReservable(DepartureStatus.CLOSED, cutoff, NOW, 9, 0, 1)).isPresent();
        assertThat(DepartureCapacityPolicy.reasonNotReservable(DepartureStatus.OPEN, NOW, NOW, 9, 0, 1)).get()
                .asString().contains("giờ chốt");
        assertThat(DepartureCapacityPolicy.reasonNotReservable(DepartureStatus.OPEN, cutoff, NOW, 9, 0, 0)).isPresent();
    }

    @Test
    void releaseNeverGoesBelowZeroSilently() {
        assertThat(DepartureCapacityPolicy.bookedAfterRelease(3, 2)).isEqualTo(1);
        assertThatThrownBy(() -> DepartureCapacityPolicy.bookedAfterRelease(1, 2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> DepartureCapacityPolicy.bookedAfterRelease(1, 0)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closesOpenDeparturePastCutoffAndCancelsOnlyNotStarted() {
        assertThat(DepartureCapacityPolicy.shouldClose(DepartureStatus.OPEN, NOW, NOW)).isTrue();
        assertThat(DepartureCapacityPolicy.shouldClose(DepartureStatus.OPEN, NOW.plusSeconds(1), NOW)).isFalse();
        assertThat(DepartureCapacityPolicy.shouldClose(DepartureStatus.CANCELLED, NOW, NOW)).isFalse();
        assertThat(DepartureCapacityPolicy.canCancel(DepartureStatus.CLOSED)).isTrue();
        assertThat(DepartureCapacityPolicy.canCancel(DepartureStatus.IN_PROGRESS)).isFalse();
        assertThat(DepartureCapacityPolicy.available(9, 11)).isZero();
    }
}
