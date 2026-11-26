package com.example.racehorse_transport.dto.audit;

import java.util.List;

// Một trang nhật ký (number tính từ 0)
public record SystemLogPageResponseDto(List<SystemLogResponseDto> content, long totalElements, int totalPages, int number, int size) {
}
