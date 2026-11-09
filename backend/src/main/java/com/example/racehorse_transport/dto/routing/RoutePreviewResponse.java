package com.example.racehorse_transport.dto.routing;

import java.util.List;

public record RoutePreviewResponse(
        String source,
        double km,
        double driveHours,
        List<Leg> legs,
        List<double[]> path,
        List<Event> schedule,
        long arrivalAt,
        Long borderArrivalAt,
        int crewDays,
        List<String> warnings) {

    public record Leg(String from, String to, double km, double driveHours) {}

    // km: quãng đường lái (chỉ với DRIVE); lat/lng: vị trí của sự kiện dừng (null với DRIVE)
    public record Event(String type, String place, long startAt, long endAt, double km, Double lat, Double lng) {}
}
