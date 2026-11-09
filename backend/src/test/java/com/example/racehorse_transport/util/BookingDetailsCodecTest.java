package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.BookingHorse;
import com.example.racehorse_transport.entity.ServiceBooking;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BookingDetailsCodecTest {
    private final ObjectMapper json = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    void normalizesRealSelectionsWithoutChangingWorkflowOrFrozenInsuranceSnapshots() throws Exception {
        Booking booking = new Booking();
        booking.setId(9);
        booking.setDetailsJson(details());

        assertThat(booking.isDetailsNormalized()).isTrue();
        assertThat(booking.getBookingHorses()).hasSize(1);
        assertThat(booking.getServiceBookings()).hasSize(2);
        JsonNode stored = json.readTree(booking.getStoredDetailsJson());
        assertThat(stored.has("horses")).isFalse();
        assertThat(stored.has("originCountry")).isFalse();
        assertThat(stored.path("quote")).isEqualTo(json.readTree(details()).path("quote"));
        JsonNode reconstructed = json.readTree(booking.getDetailsJson());
        assertThat(reconstructed.path("horses").path(0).path("selectedServices").path("feed").path("price").decimalValue())
                .isEqualByComparingTo("0");
        assertThat(reconstructed.path("horses").path(0).path("insurance").path("documentNames").path("policy.pdf").asText())
                .isEqualTo("Original policy.pdf");
        assertThat(reconstructed.path("settlement").path("total").decimalValue()).isEqualByComparingTo("9007199254740993.25");
        assertThat(reconstructed.path("unknownWorkflow").path("retained").asBoolean()).isTrue();
    }

    @Test
    void updatingWorkflowRetainsManagedHorseServicePolicyAndDocumentIdentities() throws Exception {
        Booking booking = new Booking(); booking.setId(9); booking.setDetailsJson(details());
        BookingHorse horse = booking.getBookingHorses().getFirst();
        ServiceBooking service = booking.getServiceBookings().getFirst();
        var policy = horse.getInsurancePolicy();
        var document = policy.getInsuranceDocuments().getFirst();
        JsonNode update = json.readTree(booking.getDetailsJson());
        ((com.fasterxml.jackson.databind.node.ObjectNode) update).put("gate", "Updated gate");

        booking.setDetailsJson(update.toString());

        assertThat(booking.getBookingHorses().getFirst()).isSameAs(horse);
        assertThat(booking.getServiceBookings()).contains(service);
        assertThat(horse.getInsurancePolicy()).isSameAs(policy);
        assertThat(policy.getInsuranceDocuments()).contains(document);
        assertThat(booking.getGateName()).isEqualTo("Updated gate");
    }

    @Test
    void unsupportedLegacyHorseFieldsAndIncompleteReferencesRemainIntact() {
        for (String legacy : new String[] {
                "[{\"horseId\":\"H-001\",\"name\":\"Legacy\"}]",
                "{\"horses\":[{\"horseId\":\"unknown\",\"extra\":true}]}",
                "{\"horses\":[{\"horseId\":\"H-001\",\"selectedServices\":{}}]}",
                "{\"history\":["
        }) {
            Booking booking = new Booking(); booking.setDetailsJson(legacy);
            assertThat(booking.isDetailsNormalized()).isFalse();
            assertThat(booking.getStoredDetailsJson()).isEqualTo(legacy);
            assertThat(booking.getDetailsJson()).isEqualTo(legacy);
            assertThat(booking.getBookingHorses()).isEmpty();
        }
    }

    @Test
    void rejectedCanonicalUpdateLeavesAllPreviouslyStoredDataUnchanged() {
        Booking booking = new Booking(); booking.setId(9); booking.setDetailsJson(details());
        String before = booking.getDetailsJson();
        BookingHorse horse = booking.getBookingHorses().getFirst();

        assertThatThrownBy(() -> booking.setDetailsJson(details().replace("\"horseId\":\"H-001\"", "\"horseId\":\"missing\"")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(booking.getDetailsJson()).isEqualTo(before);
        assertThat(booking.getBookingHorses().getFirst()).isSameAs(horse);
    }

    @Test
    void canonicalEmptyHorseListRemovesChildrenAndRemainsDistinctFromLegacy() throws Exception {
        Booking booking = new Booking(); booking.setDetailsJson(details());
        booking.setDetailsJson("{\"horses\":[],\"history\":[]}");

        assertThat(booking.isDetailsNormalized()).isTrue();
        assertThat(booking.getHorsesPresent()).isTrue();
        assertThat(booking.getBookingHorses()).isEmpty();
        assertThat(booking.getServiceBookings()).isEmpty();
        assertThat(json.readTree(booking.getDetailsJson()).path("horses").isEmpty()).isTrue();
    }

    @Test
    void duplicateHorseOrServiceIdentifiersNeverPartiallyNormalize() {
        Booking booking = new Booking();
        String invalid = details().replace("\"id\":3", "\"id\":1");
        booking.setDetailsJson(invalid);
        assertThat(booking.isDetailsNormalized()).isFalse();
        assertThat(booking.getBookingHorses()).isEmpty();
        assertThat(booking.getServiceBookings()).isEmpty();
        assertThat(booking.getDetailsJson()).isEqualTo(invalid);
    }

    private static String details() {
        return """
                {"originCountry":"VN","destCountry":"KH","gate":"Moc Bai",
                 "horses":[{"horseId":"H-001","name":"Snapshot name","microchip":"123",
                   "selectedServiceCodes":{"stall":"STALL_STANDARD","feed":"FEED_BASIC"},
                   "selectedServices":{
                     "stall":{"id":1,"serviceCode":"STALL_STANDARD","serviceName":"Original stall","price":1000.25,"billingUnit":"PER_HORSE"},
                     "feed":{"id":3,"serviceCode":"FEED_BASIC","serviceName":"Free feed","price":0,"billingUnit":"PER_HORSE_DAY"}},
                   "insurance":{"option":"personal","provider":"Original insurer","policyNumber":"P-1",
                      "validFrom":"2026-01-01","validUntil":"2026-12-31","premium":123.45,
                      "documents":["policy.pdf"],"policyDocuments":[],"documentNames":{"policy.pdf":"Original policy.pdf"}}}],
                 "quote":{"insuranceSnapshot":[{"horseId":"H-001","insurance":{"provider":"Frozen insurer"}}]},
                 "settlement":{"total":9007199254740993.25},"unknownWorkflow":{"retained":true}}
                """;
    }
}
