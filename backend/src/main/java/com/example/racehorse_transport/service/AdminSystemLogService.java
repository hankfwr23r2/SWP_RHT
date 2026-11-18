package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.admin.SystemLogResponseDto;
import java.time.Instant;
import java.util.List;

public interface AdminSystemLogService {

    public record LogPage(List<SystemLogResponseDto> content, long totalElements, int totalPages, int number, int size) {}

    LogPage search(String target, String action, String actor, String key, Instant from, Instant to, int page, int size);

    LogPage search(String target, String action, String actor, String key, Instant from, Instant to, int page, int size, String role);

    SystemLogResponseDto detail(Integer id);
}
