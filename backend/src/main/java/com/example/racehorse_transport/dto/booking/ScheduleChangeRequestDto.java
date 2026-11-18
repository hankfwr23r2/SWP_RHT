package com.example.racehorse_transport.dto.booking;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(name = "ScheduleChangeRequest")
public record ScheduleChangeRequestDto(@NotNull LocalDate departureDate) { }
