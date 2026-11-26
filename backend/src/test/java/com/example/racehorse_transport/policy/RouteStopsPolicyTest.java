package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import com.example.racehorse_transport.policy.RouteStopsPolicy.Stop;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteStopsPolicyTest {

    private static final RouteStopsPolicy.Limits LIMITS = new RouteStopsPolicy.Limits(4, 30, 10, 10);

    private static final long HUB = 1L;
    private static final long REST = 2L;
    private static final long GATE = 3L;
    private static final long OTHER_HUB = 4L;

    private static Stop hub(int sequenceNumber, int offset, int dwell) {
        return new Stop(sequenceNumber, HUB, LocationType.HUB, RouteStopType.HUB, offset, dwell);
    }

    private static Stop otherHub(int sequenceNumber, int offset, int dwell) {
        return new Stop(sequenceNumber, OTHER_HUB, LocationType.HUB, RouteStopType.HUB, offset, dwell);
    }

    private static Stop rest(int sequenceNumber, int offset, int dwell) {
        return new Stop(sequenceNumber, REST, LocationType.REST_STOP, RouteStopType.REST, offset, dwell);
    }

    private static Stop gate(int sequenceNumber, int offset, int dwell) {
        return new Stop(sequenceNumber, GATE, LocationType.BORDER_GATE, RouteStopType.BORDER, offset, dwell);
    }

    @Test
    void rejectsBorderGateAsDestination() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(hub(1, 0, 30), rest(2, 70, 30), gate(3, 120, 120)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("Điểm cuối tuyến"));
    }

    @Test
    void rejectsBorderGateAsOrigin() {
        List<String> errors = RouteStopsPolicy.violations(GATE, LocationType.BORDER_GATE, HUB, LocationType.HUB,
                List.of(hub(3, 120, 0), gate(1, 0, 60), rest(2, 70, 30)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("Điểm đầu tuyến"));
    }

    @Test
    void acceptsBorderGateBetweenHubsInAnyInputOrder() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB,
                List.of(otherHub(3, 180, 0), hub(1, 0, 30), gate(2, 90, 60)), LIMITS);
        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsRestStopAsOrigin() {
        List<String> errors = RouteStopsPolicy.violations(REST, LocationType.REST_STOP, HUB, LocationType.HUB,
                List.of(rest(1, 0, 10), hub(2, 60, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("Điểm đầu tuyến"));
    }

    @Test
    void rejectsSameOriginAndDestination() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, HUB, LocationType.HUB,
                List.of(hub(1, 0, 10), hub(2, 60, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("khác nhau"));
    }

    @Test
    void requiresAtLeastTwoStops() {
        assertThat(RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB, List.of(hub(1, 0, 0)), LIMITS))
                .anyMatch(e -> e.contains("ít nhất 2"));
    }

    @Test
    void requiresContiguousSequence() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB,
                List.of(hub(1, 0, 30), otherHub(3, 120, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("liên tục"));
    }

    @Test
    void requiresFirstAndLastStopToMatchRouteEnds() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB,
                List.of(rest(1, 0, 10), hub(2, 60, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("đầu tiên phải là điểm đầu"));
        assertThat(errors).anyMatch(e -> e.contains("cuối cùng phải là điểm cuối"));
    }

    @Test
    void requiresFirstStopAtMinuteZero() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB,
                List.of(hub(1, 15, 0), otherHub(2, 60, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("0 phút"));
    }

    @Test
    void rejectsArrivingBeforeLeavingThePreviousStop() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB,
                List.of(hub(1, 0, 30), rest(2, 20, 10), otherHub(3, 120, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("Điểm dừng 2 phải tới sau"));
    }

    @Test
    void rejectsBorderStopAtLocationThatIsNotAGate() {
        Stop borderAtRestStop = new Stop(2, REST, LocationType.REST_STOP, RouteStopType.BORDER, 60, 10);
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB,
                List.of(hub(1, 0, 30), borderAtRestStop, otherHub(3, 120, 0)), LIMITS);
        assertThat(errors).anyMatch(e -> e.contains("BORDER phải đặt tại địa điểm loại BORDER_GATE"));
    }

    @Test
    void rejectsOverlongDrivingAndShortRest() {
        var stops = List.of(hub(1, 0, 10), rest(2, 251, 29), otherHub(3, 300, 0));
        assertThat(RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB, stops, LIMITS))
                .anyMatch(error -> error.contains("chạy liên tục"))
                .anyMatch(error -> error.contains("ít nhất 30 phút"));
    }

    @Test
    void checksCumulativeDrivingAndResetsAfterOvernightRest() {
        var excessive = List.of(hub(1, 0, 0), rest(2, 240, 30), rest(3, 510, 30), otherHub(4, 661, 0));
        assertThat(RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB, excessive, LIMITS))
                .singleElement().asString().contains("giữa hai lần nghỉ đêm");
        var overnight = List.of(hub(1, 0, 0), rest(2, 240, 30), rest(3, 510, 600), otherHub(4, 1231, 0));
        assertThat(RouteStopsPolicy.violations(HUB, LocationType.HUB, OTHER_HUB, LocationType.HUB, overnight, LIMITS))
                .isEmpty();
    }
}
