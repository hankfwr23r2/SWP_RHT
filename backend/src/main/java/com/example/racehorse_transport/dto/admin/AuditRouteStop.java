package com.example.racehorse_transport.dto.admin;

/** Scalar projection: taking an audit snapshot must not manage entities replaced by bulk delete. */
public record AuditRouteStop(Integer locationId, String name, Integer stopOrder, Double latitude, Double longitude) {}
