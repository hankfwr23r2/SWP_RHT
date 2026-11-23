package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayloadDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class BookingHistory {

    private BookingHistory() {
    }

    public static void appendWithDefaultActor(BookingDetailsPayloadDto payload, String actor, String text) {
        appendHistory(payload, actor != null ? actor : "Hệ thống", text);
    }

    public static void appendHistory(BookingDetailsPayloadDto payload, String actor, String text) {
        if (payload.getHistory() == null) {
            payload.setHistory(new ArrayList<>());
        }
        Map<String, Object> entry = new HashMap<>();
        entry.put("time", System.currentTimeMillis());
        entry.put("actor", actor);
        entry.put("text", text);
        payload.getHistory().add(entry);
    }
}
