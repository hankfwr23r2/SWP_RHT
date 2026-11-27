package com.example.racehorse_transport.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckControllerTest {

    @Test
    void healthReportsUp() {
        var body = new HealthCheckController().checkHealth().getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData()).containsEntry("status", "UP");
    }
}
