package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.admin.SystemConfigResponse;
import com.example.racehorse_transport.dto.admin.UpdateSystemConfigRequest;
import com.example.racehorse_transport.entity.SystemConfig;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.SystemConfigRepository;
import com.example.racehorse_transport.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private record Definition(
            String key,
            String name,
            String value,
            String unit,
            String group,
            String description,
            BigDecimal minimum,
            BigDecimal maximum) {}

    private static final List<Definition> DEFINITIONS = List.of(
            new Definition("MAX_CONTINUOUS_HOURS", "Thời gian di chuyển liên tục tối đa", "4", "giờ", "WELFARE",
                    "Giới hạn thời gian ngựa di chuyển liên tục trước khi cần nghỉ.", new BigDecimal("0.25"), BigDecimal.valueOf(12)),
            new Definition("MIN_REST_MINUTES", "Thời gian nghỉ tối thiểu", "30", "phút", "WELFARE",
                    "Thời gian nghỉ tối thiểu tại mỗi trạm dừng.", BigDecimal.valueOf(15), BigDecimal.valueOf(240)),
            new Definition("VITAL_CHECK_INTERVAL_HOURS", "Tần suất đo sinh hiệu", "2", "giờ", "WELFARE",
                    "Khoảng thời gian tối đa giữa các lần ghi nhận sinh hiệu của ngựa.", BigDecimal.ONE, BigDecimal.valueOf(24)),
            new Definition("DEPOSIT_RATE", "Tỷ lệ đặt cọc", "0.30", "tỷ lệ", "PAYMENT",
                    "Tỷ lệ đặt cọc trên tổng báo giá để phát hành vận đơn.", new BigDecimal("0.01"), new BigDecimal("0.99")),
            new Definition("QUOTE_VALID_HOURS", "Thời hạn hiệu lực báo giá", "24", "giờ", "PAYMENT",
                    "Số giờ khách có thể thanh toán đặt cọc sau khi báo giá được phát hành.", BigDecimal.ONE, BigDecimal.valueOf(168)),
            new Definition("DEMURRAGE_PER_HOUR", "Phí lưu xe chờ khách", "400000", "VNĐ/giờ", "PAYMENT",
                    "Phí lưu xe mỗi giờ khi bàn giao bị chậm do phía khách hàng.", BigDecimal.ZERO, BigDecimal.valueOf(100_000_000)),
            new Definition("MIN_LEAD_DAYS", "Số ngày đặt trước tối thiểu", "20", "ngày", "OPERATIONS",
                    "Số ngày tối thiểu giữa ngày tạo đơn và ngày khởi hành.", BigDecimal.ZERO, BigDecimal.valueOf(365))
    );

    private static final Set<String> LEGACY_BORDER_KEYS = Set.of("BORDER_OPEN_MINUTES", "BORDER_CLOSE_MINUTES");

    private static final Map<String, Definition> DEFINITIONS_BY_KEY = DEFINITIONS.stream()
            .collect(LinkedHashMap::new, (map, definition) -> map.put(definition.key(), definition), LinkedHashMap::putAll);

    private final SystemConfigRepository repository;

    @Override
    @Transactional
    public List<SystemConfigResponse> getAllConfigs() {
        ensureDefaults();
        return repository.findAll(Sort.by(Sort.Order.asc("configGroup"), Sort.Order.asc("configKey")))
                .stream()
                .filter(config -> !LEGACY_BORDER_KEYS.contains(config.getConfigKey()))
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public Map<String, List<SystemConfigResponse>> getAllConfigsGrouped() {
        Map<String, List<SystemConfigResponse>> grouped = new LinkedHashMap<>();
        getAllConfigs().forEach(config ->
                grouped.computeIfAbsent(config.getConfigGroup(), ignored -> new java.util.ArrayList<>()).add(config));
        return grouped;
    }

    @Override
    @Transactional
    public SystemConfigResponse getConfig(String key) {
        ensureDefaults();
        requireDefinition(key);
        return repository.findById(key)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình: " + key));
    }

    @Override
    @Transactional
    public SystemConfigResponse updateConfig(String key, UpdateSystemConfigRequest request, String updatedBy) {
        if (request == null || request.getConfigValue() == null || request.getConfigValue().isBlank()) {
            throw new IllegalArgumentException("Giá trị cấu hình không được để trống.");
        }
        ensureDefaults();
        Definition definition = requireDefinition(key);
        String value = request.getConfigValue().trim();
        BigDecimal numericValue;
        try {
            numericValue = new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Giá trị cấu hình phải là một số hợp lệ.");
        }
        if (numericValue.compareTo(definition.minimum()) < 0 || numericValue.compareTo(definition.maximum()) > 0) {
            throw new IllegalArgumentException("Giá trị " + definition.name() + " phải nằm trong khoảng "
                    + definition.minimum().toPlainString() + " đến " + definition.maximum().toPlainString() + ".");
        }
        if (isIntegerRule(key) && numericValue.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Giá trị " + definition.name() + " phải là số nguyên.");
        }
        SystemConfig config = repository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cấu hình: " + key));
        config.setConfigValue(value);
        config.setUpdatedBy(updatedBy);
        config.setUpdatedAt(Instant.now());
        return toResponse(repository.save(config));
    }

    private void ensureDefaults() {
        List<String> keys = DEFINITIONS.stream().map(Definition::key).toList();
        List<SystemConfig> existing = repository.findAllById(keys);
        java.util.Set<String> existingKeys = existing.stream().map(SystemConfig::getConfigKey)
                .collect(java.util.stream.Collectors.toSet());
        List<SystemConfig> missing = DEFINITIONS.stream()
                .filter(definition -> !existingKeys.contains(definition.key()))
                .map(this::toEntity)
                .toList();
        if (!missing.isEmpty()) {
            repository.saveAll(missing);
        }
    }

    private Definition requireDefinition(String key) {
        Definition definition = DEFINITIONS_BY_KEY.get(key);
        if (definition == null) {
            throw new ResourceNotFoundException("Không tìm thấy cấu hình: " + key);
        }
        return definition;
    }

    private boolean isIntegerRule(String key) {
        return !"DEPOSIT_RATE".equals(key) && !"MAX_CONTINUOUS_HOURS".equals(key);
    }

    private SystemConfig toEntity(Definition definition) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(definition.key());
        config.setConfigName(definition.name());
        config.setConfigValue(definition.value());
        config.setUnit(definition.unit());
        config.setConfigGroup(definition.group());
        config.setDescription(definition.description());
        return config;
    }

    private SystemConfigResponse toResponse(SystemConfig config) {
        return SystemConfigResponse.builder()
                .configKey(config.getConfigKey())
                .configName(config.getConfigName())
                .configValue(config.getConfigValue())
                .unit(config.getUnit())
                .configGroup(config.getConfigGroup())
                .description(config.getDescription())
                .updatedBy(config.getUpdatedBy())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
