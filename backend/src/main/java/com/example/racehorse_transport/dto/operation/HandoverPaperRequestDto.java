package com.example.racehorse_transport.dto.operation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// Đánh dấu đã nhận (lúc nhận ngựa) hoặc đã trả (lúc giao ngựa) một giấy gốc
public record HandoverPaperRequestDto(@NotNull @Positive Long documentId) {}
