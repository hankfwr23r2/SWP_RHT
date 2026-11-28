package com.example.racehorse_transport.dto.dispatch;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

// Lưu sơ đồ khoang cả xe. currentPosition: vị trí điều phối đang nhìn, để phát hiện người khác vừa đổi (409)
public record StallPlanApplyRequestDto(@NotEmpty List<@Valid StallAssignmentDto> horses) {

    public record StallAssignmentDto(@NotNull @Positive Long horseLegId, @Positive Integer currentPosition, @NotNull @Positive Integer stallPosition) {}
}
