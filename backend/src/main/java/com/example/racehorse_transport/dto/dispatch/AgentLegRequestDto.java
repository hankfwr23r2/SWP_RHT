package com.example.racehorse_transport.dto.dispatch;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

// Chặng ở nước ngoài do đại lý chở (GĐ7.4). Đơn xuất: nối sau chặng xe tới cửa khẩu, đi tới địa chỉ giao.
// Đơn nhập: chặng đầu, từ địa chỉ đón tới cửa khẩu gateLocationId
public record AgentLegRequestDto(
        @NotNull @Positive Long agentPartyId,
        @Positive Long gateLocationId,
        @NotNull Instant plannedStartAt,
        @NotNull Instant plannedEndAt) {}
