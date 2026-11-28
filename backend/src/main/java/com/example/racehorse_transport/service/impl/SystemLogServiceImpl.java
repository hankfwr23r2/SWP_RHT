package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.audit.SystemLogPageResponseDto;
import com.example.racehorse_transport.dto.audit.SystemLogResponseDto;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.SystemLogQueryPolicy;
import com.example.racehorse_transport.repository.AuditLogRepository;
import com.example.racehorse_transport.service.SystemLogService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private final AuditLogRepository logs;

    @Override
    @Transactional(readOnly = true)
    public SystemLogPageResponseDto search(Instant from, Instant to, String target, String action, String role, String actor, String key, int page, int size) {
        SystemLogQueryPolicy.requireValid(from, to, page, size);
        var result = logs.findAll(
                AuditLogRepository.matching(from, to, SystemLogQueryPolicy.blankToNull(target), SystemLogQueryPolicy.blankToNull(action),
                        SystemLogQueryPolicy.blankToNull(role), SystemLogQueryPolicy.targetId(key), SystemLogQueryPolicy.blankToNull(actor)),
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new SystemLogPageResponseDto(result.getContent().stream().map(SystemLogResponseDto::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Override
    @Transactional(readOnly = true)
    public SystemLogResponseDto get(Long id) {
        return logs.findById(id).map(SystemLogResponseDto::from)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhật ký " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> options() {
        return logs.findTargetActions().stream().collect(Collectors.groupingBy(row -> (String) row[0], LinkedHashMap::new,
                Collectors.mapping(row -> (String) row[1], Collectors.toList())));
    }
}
