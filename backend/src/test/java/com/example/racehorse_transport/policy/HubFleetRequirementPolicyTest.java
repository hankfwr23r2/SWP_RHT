package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.policy.HubFleetRequirementPolicy.Requirement;
import com.example.racehorse_transport.policy.HubFleetRequirementPolicy.Rules;
import com.example.racehorse_transport.policy.HubFleetRequirementPolicy.WeeklyRun;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class HubFleetRequirementPolicyTest {
    private static final Rules RULES = new Rules(12, 20, 120, 1, 1, 50, 6, 2);
    private static final long HCM = 1L;
    private static final long NT = 2L;
    private static final long DN = 3L;
    private static final long VINH = 4L;
    private static final long HN = 5L;
    private static final long PNH = 6L;
    private static final long VTE = 7L;
    private static final long SVK = 8L;

    @Test
    void matchesTheFleetRegulationForAllTenSamplePairs() {
        var runs = Stream.of(
                pair(HCM, NT, 6, 1, 5, 478, 497),
                pair(NT, DN, 3, 4, 5, 699, 694),
                pair(HCM, DN, 6, 1, 5, 1747, 1761),
                pair(DN, VINH, 6, 1, 5, 616, 616),
                pair(VINH, HN, 3, 4, 5, 399, 397),
                pair(DN, HN, 5, 1, 5, 1585, 1583),
                pair(HCM, HN, 3, 1, 5, 3902, 3914),
                pair(HCM, PNH, 6, 1, 6, 620, 617),
                pair(DN, SVK, 5, 1, 6, 1412, 1414),
                pair(VINH, VTE, 6, 1, 6, 715, 695)
        ).flatMap(List::stream).toList();

        assertThat(HubFleetRequirementPolicy.calculate(List.of(HCM, NT, DN, VINH, HN, PNH, VTE, SVK), runs, RULES))
                .isEqualTo(Map.of(
                        HCM, new Requirement(5, 1, 3, 1, 1, 11),
                        NT, new Requirement(1, 0, 1, 1, 1, 4),
                        DN, new Requirement(3, 1, 2, 1, 1, 8),
                        VINH, new Requirement(2, 1, 1, 1, 1, 5),
                        HN, new Requirement(0, 0, 2, 1, 1, 4),
                        PNH, new Requirement(0, 0, 1, 1, 1, 3),
                        VTE, new Requirement(0, 0, 1, 1, 1, 3),
                        SVK, new Requirement(0, 0, 1, 1, 1, 3)));
    }

    @Test
    void departuresOnDifferentDaysCanOverlapWithoutIncreasingTheBranchPeak() {
        var runs = List.of(run(HCM, NT, true, 120, 1, 5, 1), run(HCM, NT, true, 120, 3, 5, 1),
                run(NT, HCM, false, 120, 4, 5, 1));

        assertThat(requirement(runs)).isEqualTo(new Requirement(2, 0, 1, 1, 1, 5));
    }

    @Test
    void withoutAReturnScheduleAssumesTheSameDurationBackAndCoversFullWeeks() {
        // Hai lượt 6,5 ngày và hai lần nghỉ 12 giờ làm thành đúng hai tuần.
        assertThat(requirement(List.of(run(HCM, NT, true, 9360, 6, 5, 1))))
                .isEqualTo(new Requirement(2, 0, 1, 1, 1, 5));
        assertThat(requirement(List.of(run(HCM, NT, true, 9361, 6, 5, 1))).linehaul()).isEqualTo(3);
    }

    @Test
    void multipleWeeklyRunsOfOnePairDoNotCreateSpareLinehaulDemand() {
        var runs = List.of(run(HCM, NT, true, 120, 1, 5, 1), run(HCM, NT, true, 120, 3, 5, 1),
                run(NT, HCM, false, 120, 4, 5, 1));
        assertThat(requirement(runs).spare()).isZero();
        assertThat(requirement(Stream.concat(runs.stream(), pair(HCM, DN, 6, 1, 5, 120, 120).stream()).toList()).spare())
                .isEqualTo(1);
    }

    @Test
    void weightsEachOccupiedIntervalByItsOwnBaseVehicleCount() {
        var runs = List.of(run(HCM, HN, true, 3902, 3, 5, 2), run(HN, HCM, false, 3914, 1, 5, 1));
        assertThat(requirement(runs)).isEqualTo(new Requirement(4, 0, 2, 1, 1, 9));

        var differentCounts = List.of(run(HCM, NT, true, 120, 1, 5, 2), run(HCM, NT, true, 120, 3, 5, 1),
                run(NT, HCM, false, 120, 4, 5, 1));
        assertThat(requirement(differentCounts).linehaul()).isEqualTo(3);
    }

    @Test
    void vehicleCanReturnExactlyWhenItsRestEndsAndDepartAgainWhenItIsReady() {
        var runs = List.of(run(HCM, NT, true, 120, 1, 5, 1), run(NT, HCM, false, 120, 1, 19, 1),
                run(HCM, NT, true, 120, 2, 9, 1), run(NT, HCM, false, 120, 2, 23, 1));
        assertThat(requirement(runs).linehaul()).isEqualTo(1);
    }

    @Test
    void hubWithoutRunsStillNeedsSmallBranchVehiclesSpareAndCrew() {
        assertThat(requirement(List.of())).isEqualTo(new Requirement(0, 0, 0, 1, 1, 2));
    }

    private static Requirement requirement(List<WeeklyRun> runs) {
        return HubFleetRequirementPolicy.calculate(List.of(HCM), runs, RULES).get(HCM);
    }

    private static List<WeeklyRun> pair(long from, long to, int outDay, int returnDay, int hour, int outMinutes, int returnMinutes) {
        return List.of(run(from, to, true, outMinutes, outDay, hour, 1), run(to, from, false, returnMinutes, returnDay, hour, 1));
    }

    private static WeeklyRun run(long from, long to, boolean outbound, int minutes, int day, int hour, int count) {
        return new WeeklyRun(from, to, outbound, minutes, day, LocalTime.of(hour, 0), count, 9);
    }
}
