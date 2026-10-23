package com.example.racehorse_transport.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckControllerTest {

    @Test
    void healthReportsTheCommitTheServiceWasBuiltFrom() {
        var body = new HealthCheckController("bd2a310").checkHealth().getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData()).containsEntry("status", "UP").containsEntry("commit", "bd2a310");
    }
}
