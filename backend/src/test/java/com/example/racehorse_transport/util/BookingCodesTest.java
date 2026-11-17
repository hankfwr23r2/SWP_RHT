package com.example.racehorse_transport.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookingCodesTest {

    @Test
    void newCodeIsBkPlusEightUnambiguousCharacters() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(BookingCodes.newCode()).matches("^BK-[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$");
        }
    }

    @Test
    void newCodeDoesNotRepeatAcrossManyCalls() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(BookingCodes.newCode());
        }
        assertThat(codes).hasSize(10_000);
    }
}
