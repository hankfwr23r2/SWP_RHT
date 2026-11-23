package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.config.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigUpdateRequestDto;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.SystemConfigPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemConfigRepository;
import com.example.racehorse_transport.service.AuditService;
import com.example.racehorse_transport.service.SystemConfigService;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository repository;
    private final AppUserRepository users;
    private final StaffRepository staff;
    private final AuditService audit;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public int getInt(String key) {
        String value = repository.findById(key)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số hệ thống " + key))
                .getConfigValue();
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Tham số hệ thống " + key + " phải là số nguyên", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemConfigResponseDto> configurations() {
        return repository.findAllByOrderByConfigKeyAsc().stream().map(SystemConfigResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SystemConfigResponseDto configuration(String key) {
        return repository.findOneByConfigKey(key).map(SystemConfigResponseDto::from).orElseThrow(this::unknownConfig);
    }

    @Override
    @Transactional
    public SystemConfigResponseDto updateConfiguration(String key, String username, SystemConfigUpdateRequestDto request) {
        Staff actor = adminStaff(username);
        var config = repository.findForUpdate(key).orElseThrow(this::unknownConfig);
        int value = request.integerValue();
        SystemConfigPolicy.requireValidUpdate(config.getUnit(), value, config.getMinValue(), config.getMaxValue());
        var before = SystemConfigResponseDto.from(config);
        if (Integer.toString(value).equals(config.getConfigValue())) {
            return before;
        }
        request.applyTo(config);
        config.setUpdatedBy(actor);
        config.setUpdatedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
        var after = SystemConfigResponseDto.from(config);
        audit.record(actor.getUserId(), "UPDATE_SYSTEM_CONFIG", "system_config", config.getAuditTargetId(), before, after);
        return after;
    }

    private Staff adminStaff(String username) {
        var user = users.findByUsername(username)
                .filter(candidate -> candidate.getStatus() == AppUserStatus.ACTIVE
                        && "ADMIN".equals(candidate.getRole().getCode()))
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được sửa tham số."));
        return staff.findById(user.getId()).filter(profile -> profile.getEmploymentStatus() != EmploymentStatus.TERMINATED)
                .orElseThrow(() -> new AccessDeniedException("Tài khoản quản trị không còn hoạt động."));
    }

    private ResourceNotFoundException unknownConfig() {
        return new ResourceNotFoundException("Không tìm thấy tham số kỹ thuật.");
    }
}
