package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.Size;

// Lý do rời trạm nghỉ trước khi nghỉ đủ (quy tắc #34); bỏ trống khi rời đúng giờ
public record StopDepartRequestDto(@Size(max = 500) String earlyReason) {}
