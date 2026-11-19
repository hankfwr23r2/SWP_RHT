package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import com.example.racehorse_transport.entity.Booking;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

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
    void readsLegacyCaptureEvidenceContainingBookingId() throws Exception {
        String json = """
                {"photoEvidence":{"capture-url":{"id":4,"url":"capture-url","tripId":"TRP-019-1",
                "purpose":"ARRIVE_PICKUP","target":"pickup","capturedBy":"driver01",
                "capturedAt":1,"recordedAt":2,"bookingId":19}}}
                """;

        BookingDetailsPayloadDto payload = objectMapper.readValue(json, BookingDetailsPayloadDto.class);

        assertThat(payload.getPhotoEvidence().get("capture-url").id()).isEqualTo(4L);
    }

    @Test
    void readsUntypedDecimalAmountsWithoutLosingPrecision() {
        var payload = BookingDetailsJson.read(objectMapper,
                booking("{\"settlement\":{\"total\":9007199254740993.25}}"));

        Object total = ((Map<?, ?>) payload.getSettlement()).get("total");
        assertThat(total).isEqualTo(new BigDecimal("9007199254740993.25"));
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

    @Test
    void readsPhotoEvidenceWithExtraProperties() {
        String json = "{\"photoEvidence\":{\"http://example.com/img.jpg\":{\"id\":115,\"bookingId\":57,\"url\":\"http://example.com/img.jpg\"}}}";
        var payload = BookingDetailsJson.read(objectMapper, booking(json));
        assertThat(payload.getPhotoEvidence()).containsKey("http://example.com/img.jpg");
        assertThat(payload.getPhotoEvidence().get("http://example.com/img.jpg").id()).isEqualTo(115L);
    }
}
