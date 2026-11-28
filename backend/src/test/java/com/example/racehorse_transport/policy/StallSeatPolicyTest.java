package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StallSeatPolicyTest {

    @Test
    void countsStandardAndWideStallsInBothInputForms() {
        assertThat(StallSeatPolicy.total(List.of("STANDARD", "WIDE", "WIDE"))).isEqualTo(5);
        assertThat(StallSeatPolicy.total(Map.of("STANDARD", 1, "WIDE", 2))).isEqualTo(5);
        assertThat(StallSeatPolicy.total(Collections.nCopies(9, "WIDE"))).isEqualTo(18);
    }

    @Test
    void rejectsUnknownStalls() {
        assertThatThrownBy(() -> StallSeatPolicy.total(List.of("UNKNOWN"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StallSeatPolicy.total(Map.of("UNKNOWN", 1))).isInstanceOf(IllegalArgumentException.class);
    }
}
