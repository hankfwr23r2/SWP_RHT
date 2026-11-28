package com.example.racehorse_transport.dto.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.HealthCheckTaskStatus;
import com.example.racehorse_transport.entity.enums.HealthCheckType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HealthCheckTaskDtoTest {
    @Test
    void requestCopiesInputsAndLeavesLifecycleFieldsEmpty() {
        BookingHorse horse = new BookingHorse();
        HorseStay stay = new HorseStay();
        Staff escort = new Staff();
        Instant due = Instant.parse("2026-11-29T12:00:00Z");
        var request = new HealthCheckTaskCreateRequestDto(1L, null, 2L, 3L, HealthCheckType.ROUTINE, due, "Check intake");
        var task = request.toEntity(horse, null, stay, escort);

        assertThat(task.getBookingHorse()).isSameAs(horse);
        assertThat(task.getHorseStay()).isSameAs(stay);
        assertThat(task.getAssignedTo()).isSameAs(escort);
        assertThat(task.getDueAt()).isEqualTo(due);
        assertThat(task.getNote()).isEqualTo("Check intake");
        assertThat(task.getCheckType()).isEqualTo(HealthCheckType.ROUTINE);
        assertThat(task.getId()).isNull();
        assertThat(task.getAssignedBy()).isNull();
        assertThat(task.getAssignedAt()).isNull();
        assertThat(task.getStatus()).isNull();
        assertThat(task.getAcceptedAt()).isNull();
        assertThat(task.getDoneAt()).isNull();
        assertThat(task.getHealthLog()).isNull();
        assertThat(task.getCancelReason()).isNull();
    }

    @Test
    void responsesExposeNamesAndUseServiceFlags() {
        Staff escort = new Staff();
        escort.setUserId(3L);
        escort.setFullName("Escort");
        var horse = new Horse();
        horse.setName("Horse");
        var line = new BookingHorse();
        line.setHorse(horse);
        var location = new Location();
        location.setName("Hub");
        var stay = new HorseStay();
        stay.setLocation(location);
        var task = new HealthCheckTaskCreateRequestDto(1L, null, 2L, 3L, HealthCheckType.ROUTINE,
                Instant.EPOCH, null).toEntity(line, null, stay, escort);
        task.setStatus(HealthCheckTaskStatus.ASSIGNED);

        assertThat(HealthCheckAssigneeResponseDto.from(escort)).isEqualTo(new HealthCheckAssigneeResponseDto(3L, "Escort"));
        var response = HealthCheckTaskResponseDto.from(task, true, false, 2L);
        assertThat(response.horseName()).isEqualTo("Horse");
        assertThat(response.place()).isEqualTo("Hub");
        assertThat(response.assigneeName()).isEqualTo("Escort");
        assertThat(response.overdue()).isTrue();
        assertThat(response.canCancel()).isFalse();
        assertThat(response.recordingHorseStayId()).isEqualTo(2L);
    }
}
