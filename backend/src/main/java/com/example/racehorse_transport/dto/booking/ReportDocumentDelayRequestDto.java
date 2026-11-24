package com.example.racehorse_transport.dto.booking;

import com.example.racehorse_transport.entity.enums.ScheduleChangeCause;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportDocumentDelayRequestDto(@NotNull ScheduleChangeCause cause,
                                            @NotBlank @Size(max = 500) String reason) {}
