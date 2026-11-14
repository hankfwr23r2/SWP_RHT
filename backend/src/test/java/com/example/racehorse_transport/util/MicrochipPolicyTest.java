package com.example.racehorse_transport.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrochipPolicyTest {

    @Test
    void spacesAndDashesFromTypingOrScannersAreRemoved() {
        assertThat(MicrochipPolicy.normalize(" 981 1000-0123 4567 ")).isEqualTo("981100001234567");
        assertThat(MicrochipPolicy.normalize("9811000012345678")).isEqualTo("9811000012345678");
    }

    @Test
    void missingChipBecomesEmpty() {
        assertThat(MicrochipPolicy.normalize(null)).isEmpty();
        assertThat(MicrochipPolicy.normalize("   ")).isEmpty();
    }

    @Test
    void onlyFifteenDigitsIsAnIsoChip() {
        assertThat(MicrochipPolicy.isIso("981100001234567")).isTrue();
        assertThat(MicrochipPolicy.isIso("98110000123456")).isFalse();
        assertThat(MicrochipPolicy.isIso("9811000012345678")).isFalse();
        assertThat(MicrochipPolicy.isIso("98110000123456A")).isFalse();
        assertThat(MicrochipPolicy.isIso("")).isFalse();
    }

    @Test
    void aChipTypedWithSeparatorsPassesAfterNormalizing() {
        assertThat(MicrochipPolicy.isIso(MicrochipPolicy.normalize("981-100-001-234-567"))).isTrue();
    }
}
