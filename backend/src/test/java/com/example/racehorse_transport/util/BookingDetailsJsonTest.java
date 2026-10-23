package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Booking;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingDetailsJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Booking booking(String json) {
        Booking b = new Booking();
        b.setId(7);
        b.setDetailsJson(json);
        return b;
    }

    @Test
    void readsTheDetailsObject() {
        var payload = BookingDetailsJson.read(objectMapper, booking("{\"gate\":\"Mộc Bài\",\"history\":[]}"));

        assertThat(payload.getGate()).isEqualTo("Mộc Bài");
    }

    @Test
    void readsTheLegacyHorseArray() {
        var payload = BookingDetailsJson.read(objectMapper, booking("[{\"horseId\":\"H-001\",\"name\":\"Storm\"}]"));

        assertThat(payload.getHorses()).hasSize(1);
        assertThat(payload.getHorses().get(0).getName()).isEqualTo("Storm");
    }

    @Test
    void emptyDetailsGiveAnEmptyPayload() {
        assertThat(BookingDetailsJson.read(objectMapper, booking(null)).getHistory()).isNull();
        assertThat(BookingDetailsJson.read(objectMapper, booking("  ")).getHistory()).isNull();
    }

    @Test
    void unreadableDetailsStopTheUpdateInsteadOfStartingFromEmpty() {
        assertThatThrownBy(() -> BookingDetailsJson.read(objectMapper, booking("{\"history\": [")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể đọc chi tiết đơn 7");
    }
}
