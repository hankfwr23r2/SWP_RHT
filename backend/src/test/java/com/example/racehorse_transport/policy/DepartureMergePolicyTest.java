package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.DepartureSchedule;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DepartureMergePolicyTest {

    private static final Instant NOW = Instant.parse("2026-12-01T00:00:00Z");
    private static final LocalDate SOURCE_DATE = LocalDate.parse("2026-12-04");
    private static final LocalDate HOLD_THROUGH = BookingSubmissionPolicy.priceHoldThrough(SOURCE_DATE, 30);
    private final Departure source = departure(1, SOURCE_DATE);
    private final Departure target = departure(2, SOURCE_DATE.plusWeeks(1));

    @Test
    void allowsOpenOrClosedFutureDeparturesWithEnoughSpaceThroughLastPriceHoldDay() {
        source.setBookedStallCount(2);
        target.setBookedStallCount(7);
        target.setDepartureDate(HOLD_THROUGH);
        target.setPlannedDepartureAt(HOLD_THROUGH.atStartOfDay().toInstant(ZoneOffset.UTC));

        assertThat(problem()).isEmpty();
        target.setStatus(DepartureStatus.CLOSED);
        assertThat(problem()).isEmpty();
        target.setBookedStallCount(8);
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("không đủ chỗ"));
    }

    @Test
    void requiresTwoDifferentScheduledDeparturesOnTheSameRoute() {
        assertThat(DepartureMergePolicy.reasonNotMergeable(source, source, HOLD_THROUGH, NOW))
                .hasValueSatisfying(reason -> assertThat(reason).contains("khác chuyến nguồn"));
        source.setDepartureSchedule(null);
        assertThat(DepartureMergePolicy.canMergeSource(source)).isFalse();
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("đột xuất"));
        source.setDepartureSchedule(new DepartureSchedule());
        target.setDepartureSchedule(null);
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("đột xuất"));
        target.setDepartureSchedule(new DepartureSchedule());
        target.getRoute().setId(2L);
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("cùng tuyến"));
    }

    @Test
    void rejectsTargetsOutsideInclusivePriceHoldWindowOrAtTheirEffectiveDepartureTime() {
        target.setDepartureDate(HOLD_THROUGH.plusDays(1));
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("hạn giữ giá"));
        target.setDepartureDate(SOURCE_DATE.minusDays(1));
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("hạn giữ giá"));
        target.setDepartureDate(SOURCE_DATE);
        target.setPlannedDepartureAt(NOW.minusSeconds(3600));
        target.setRevisedDepartureAt(NOW);
        assertThat(problem()).hasValueSatisfying(reason -> assertThat(reason).contains("qua giờ xuất phát"));
        target.setRevisedDepartureAt(NOW.plusSeconds(1));
        assertThat(problem()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = DepartureStatus.class, names = {"CANCELLED", "IN_PROGRESS", "COMPLETED"})
    void rejectsDeparturesThatRanOrWereCancelled(DepartureStatus status) {
        source.setStatus(status);
        assertThat(DepartureMergePolicy.canMergeSource(source)).isFalse();
        target.setStatus(status);
        assertThat(problem()).isPresent();
    }

    @Test
    void runCapacityUsesHeldStallsAndOriginalCapacityAsBounds() {
        DepartureDecisionPolicy.validateRunCapacity(2, 2, 9);
        DepartureDecisionPolicy.validateRunCapacity(9, 2, 9);
        assertThatThrownBy(() -> DepartureDecisionPolicy.validateRunCapacity(1, 2, 9)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> DepartureDecisionPolicy.validateRunCapacity(10, 2, 9)).isInstanceOf(BookingRuleException.class);
        assertThatThrownBy(() -> DepartureDecisionPolicy.validateRunCapacity(0, 0, 9)).isInstanceOf(BookingRuleException.class);
    }

    private java.util.Optional<String> problem() {
        return DepartureMergePolicy.reasonNotMergeable(source, target, HOLD_THROUGH, NOW);
    }

    private static Departure departure(long id, LocalDate date) {
        Route route = new Route();
        route.setId(1L);
        Departure departure = new Departure();
        departure.setId(id);
        departure.setRoute(route);
        departure.setDepartureSchedule(new DepartureSchedule());
        departure.setDepartureDate(date);
        departure.setPlannedDepartureAt(date.atStartOfDay().toInstant(ZoneOffset.UTC));
        departure.setStatus(DepartureStatus.OPEN);
        departure.setStallCapacity(9);
        return departure;
    }
}
