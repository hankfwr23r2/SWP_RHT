package com.example.racehorse_transport.dto.config;

// Con số quy định vận chuyển hiện cho khách, quản trị viên đặt ở system_config.
// pickupWindowEndHour: giờ muộn nhất ngựa về tới hub trong ngày đón. maxTransferDriveMinutes: giới hạn lái một chiều đón, giao (#31)
public record TransportRulesResponseDto(
        int pickupWindowStartHour, int pickupWindowEndHour, int deliveryWindowStartHour, int deliveryWindowEndHour,
        int hubCheckinHours, int handoverFreeWaitMinutes, int balanceWaitMinutes, int departureDecisionHours,
        int maxTransferDriveMinutes) {}
