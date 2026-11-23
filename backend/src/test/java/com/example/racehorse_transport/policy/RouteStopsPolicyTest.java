package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.entity.enums.RouteStopType;
import com.example.racehorse_transport.policy.RouteStopsPolicy.Stop;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteStopsPolicyTest {

    private static final long HUB = 1L;
    private static final long REST = 2L;
    private static final long GATE = 3L;

    private static Stop hub(int seq, int offset, int dwell) {
        return new Stop(seq, HUB, LocationType.HUB, RouteStopType.HUB, offset, dwell);
    }

    private static Stop rest(int seq, int offset, int dwell) {
        return new Stop(seq, REST, LocationType.REST_STOP, RouteStopType.REST, offset, dwell);
    }

    private static Stop gate(int seq, int offset, int dwell) {
        return new Stop(seq, GATE, LocationType.BORDER_GATE, RouteStopType.BORDER, offset, dwell);
    }

    @Test
    void acceptsOutboundRouteFromHubToGate() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(hub(1, 0, 30), rest(2, 70, 20), gate(3, 120, 120)));
        assertThat(errors).isEmpty();
    }

    @Test
    void acceptsInboundRouteFromGateToHubInAnyInputOrder() {
        List<String> errors = RouteStopsPolicy.violations(GATE, LocationType.BORDER_GATE, HUB, LocationType.HUB,
                List.of(hub(3, 120, 0), gate(1, 0, 60), rest(2, 70, 20)));
        assertThat(errors).isEmpty();
    }

    @Test
    void rejectsOriginThatIsNeitherHubNorGate() {
        List<String> errors = RouteStopsPolicy.violations(REST, LocationType.REST_STOP, HUB, LocationType.HUB,
                List.of(rest(1, 0, 10), hub(2, 60, 0)));
        assertThat(errors).anyMatch(e -> e.contains("Điểm đầu tuyến"));
    }

    @Test
    void rejectsSameOriginAndDestination() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, HUB, LocationType.HUB,
                List.of(hub(1, 0, 10), hub(2, 60, 0)));
        assertThat(errors).anyMatch(e -> e.contains("khác nhau"));
    }

    @Test
    void requiresAtLeastTwoStops() {
        assertThat(RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE, List.of(hub(1, 0, 0))))
                .anyMatch(e -> e.contains("ít nhất 2"));
    }

    @Test
    void requiresContiguousSequence() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(hub(1, 0, 30), gate(3, 120, 0)));
        assertThat(errors).anyMatch(e -> e.contains("liên tục"));
    }

    @Test
    void requiresFirstAndLastStopToMatchRouteEnds() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(rest(1, 0, 10), hub(2, 60, 0)));
        assertThat(errors).anyMatch(e -> e.contains("đầu tiên phải là điểm đầu"));
        assertThat(errors).anyMatch(e -> e.contains("cuối cùng phải là điểm cuối"));
    }

    @Test
    void requiresFirstStopAtMinuteZero() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(hub(1, 15, 0), gate(2, 60, 0)));
        assertThat(errors).anyMatch(e -> e.contains("0 phút"));
    }

    @Test
    void rejectsArrivingBeforeLeavingThePreviousStop() {
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(hub(1, 0, 30), rest(2, 20, 10), gate(3, 120, 0)));
        assertThat(errors).anyMatch(e -> e.contains("Điểm dừng 2 phải tới sau"));
    }

    @Test
    void rejectsBorderStopAtLocationThatIsNotAGate() {
        Stop borderAtRestStop = new Stop(2, REST, LocationType.REST_STOP, RouteStopType.BORDER, 60, 10);
        List<String> errors = RouteStopsPolicy.violations(HUB, LocationType.HUB, GATE, LocationType.BORDER_GATE,
                List.of(hub(1, 0, 30), borderAtRestStop, gate(3, 120, 0)));
        assertThat(errors).anyMatch(e -> e.contains("BORDER phải đặt tại địa điểm loại BORDER_GATE"));
    }
}
