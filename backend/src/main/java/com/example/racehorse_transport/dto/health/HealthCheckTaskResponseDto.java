package com.example.racehorse_transport.dto.health;

import com.example.racehorse_transport.entity.HealthCheckTask;
import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import java.time.Instant;

public record HealthCheckTaskResponseDto(
        Long id, Long bookingHorseId, String horseName, Long vehicleTripId, Long horseStayId, String place,
        Long assignedTo, String assigneeName, HealthCheckType checkType, HealthCheckTaskStatus status,
        Instant dueAt, boolean overdue, String note, Instant acceptedAt, Instant doneAt,
        Long healthLogId, String cancelReason, boolean canCancel, Long recordingHorseStayId) {
    public static HealthCheckTaskResponseDto from(HealthCheckTask task, boolean overdue, boolean canCancel, Long recordingHorseStayId) {
        var trip = task.getVehicleTrip();
        var stay = task.getHorseStay();
        return new HealthCheckTaskResponseDto(task.getId(), task.getBookingHorse().getId(),
                task.getBookingHorse().getHorse().getName(), trip == null ? null : trip.getId(),
                stay == null ? null : stay.getId(), trip == null ? stay.getLocation().getName() : trip.getCode(),
                task.getAssignedTo().getUserId(), task.getAssignedTo().getFullName(), task.getCheckType(), task.getStatus(),
                task.getDueAt(), overdue, task.getNote(), task.getAcceptedAt(), task.getDoneAt(),
                task.getHealthLog() == null ? null : task.getHealthLog().getId(), task.getCancelReason(), canCancel, recordingHorseStayId);
    }
}
