package com.example.racehorse_transport.dto.booking;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ScheduleChangeRequest(@NotNull LocalDate departureDate) { }
