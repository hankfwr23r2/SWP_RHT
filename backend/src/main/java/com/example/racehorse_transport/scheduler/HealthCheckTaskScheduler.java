package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.HealthCheckTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HealthCheckTaskScheduler {
    private final HealthCheckTaskService tasks;

    @Scheduled(fixedDelayString = "${health-check-task.reminder-delay-ms:60000}")
    public void notifyOverdue() {
        tasks.notifyOverdue();
    }
}
