package com.example.racehorse_transport.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class GeocodeUnavailableException extends ResponseStatusException {
    public GeocodeUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Location search is temporarily unavailable.");
    }
}
