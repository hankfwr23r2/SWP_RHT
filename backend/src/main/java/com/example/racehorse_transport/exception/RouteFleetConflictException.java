package com.example.racehorse_transport.exception;

public class RouteFleetConflictException extends IllegalStateException {
    public RouteFleetConflictException() {
        super("Cặp tuyến chỉ có một chiều đi.");
    }
}
