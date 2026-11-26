package com.example.racehorse_transport.scheduler;

import com.example.racehorse_transport.service.HealthCheckTaskService;
import com.example.racehorse_transport.service.HubExitHealthCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HealthCheckTaskScheduler {
    private final HealthCheckTaskService tasks;
    private final HubExitHealthCheckService hubExitChecks;

    @Scheduled(fixedDelayString = "${health-check-task.reminder-delay-ms:60000}")
    public void run() {
        tasks.notifyOverdue();
        hubExitChecks.scheduleBoardingChecks();
        hubExitChecks.scheduleCollectionChecks();
    }
}
