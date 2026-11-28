package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.config.ContactResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigResponseDto;
import com.example.racehorse_transport.dto.config.SystemConfigUpdateRequestDto;
import com.example.racehorse_transport.dto.config.SystemTextConfigUpdateRequestDto;
import com.example.racehorse_transport.dto.config.TransportRulesResponseDto;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.SystemConfig;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.SystemConfigPolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
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
    private final com.example.racehorse_transport.config.DemoProperties demo;

    @Override
    @Transactional(readOnly = true)
    public int getInt(String key) {
        if (demo.skipTimeRules()) {
            var override = com.example.racehorse_transport.policy.DemoTimeRulePolicy.override(key);
            if (override.isPresent()) return override.getAsInt();
        }
        return storedInt(key);
    }

    @Override
    public boolean timeRulesSkipped() {
        return demo.skipTimeRules();
    }

    // Giá trị lưu trong DB, không qua chế độ demo (dùng cho trang quy định hiện cho khách)
    private int storedInt(String key) {
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
        return saved(config, actor, before);
    }

    @Override
    @Transactional
    public SystemConfigResponseDto updateText(String key, String username, SystemTextConfigUpdateRequestDto request) {
        Staff actor = adminStaff(username);
        var config = repository.findForUpdate(key).orElseThrow(this::unknownConfig);
        SystemConfigPolicy.requireValidText(key, config.getUnit(), request.textValue());
        var before = SystemConfigResponseDto.from(config);
        if (request.textValue().equals(config.getConfigValue())) {
            return before;
        }
        request.applyTo(config);
        return saved(config, actor, before);
    }

    @Override
    @Transactional(readOnly = true)
    public ContactResponseDto contact() {
        return new ContactResponseDto(text(SystemConfigPolicy.HOTLINE), text(SystemConfigPolicy.OFFICE_ADDRESS));
    }

    @Override
    @Transactional(readOnly = true)
    public TransportRulesResponseDto transportRules() {
        Rules rules = new Rules(storedInt("MAX_CONTINUOUS_HOURS"), storedInt("MIN_REST_MINUTES"), storedInt("MAX_DRIVE_HOURS_PER_DAY"), 0, 0);
        return new TransportRulesResponseDto(
                storedInt("PICKUP_WINDOW_START_HOUR"), storedInt("PICKUP_WINDOW_END_HOUR"),
                storedInt("DELIVERY_WINDOW_START_HOUR"), storedInt("DELIVERY_WINDOW_END_HOUR"),
                storedInt("HUB_CHECKIN_HOURS"), storedInt("HANDOVER_FREE_WAIT_MINUTES"), storedInt("BALANCE_WAIT_MINUTES"),
                storedInt("DEPARTURE_DECISION_HOURS"),
                Math.toIntExact(TripSchedulePolicy.rideLimitSeconds(false, rules) / 60),
                storedInt("STABLING_DAILY_FEE"));
    }

    private String text(String key) {
        return repository.findById(key).map(SystemConfig::getConfigValue)
                .orElseThrow(() -> new IllegalStateException("Thiếu tham số hệ thống " + key));
    }

    private SystemConfigResponseDto saved(SystemConfig config, Staff actor, SystemConfigResponseDto before) {
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
