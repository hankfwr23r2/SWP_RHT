package com.example.racehorse_transport.dto.routing;

import java.util.List;

public record RoutePreviewResponse(
        String source,
        double km,
        double driveHours,
        List<Leg> legs,
        List<double[]> path,
        Toll toll,
        List<Event> schedule,
        long arrivalAt,
        Long borderArrivalAt,
        int crewDays,
        List<String> warnings) {

    public record Leg(String from, String to, double km, double driveHours) {}

    // amountVnd null: chưa lấy được phí; source null: tuyến không đi qua Việt Nam
    public record Toll(Long amountVnd, String source) {}

    public record Event(String type, String place, long startAt, long endAt) {}
}
