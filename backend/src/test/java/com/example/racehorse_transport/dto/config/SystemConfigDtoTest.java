package com.example.racehorse_transport.dto.config;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.SystemConfig;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SystemConfigDtoTest {

    @Test
    void editsOnlyValueAndMapsResponse() {
        var actor = new Staff();
        actor.setUserId(8L);
        var config = new SystemConfig();
        config.setAuditTargetId(7L);
        config.setConfigKey("OTP_MAX_ATTEMPTS");
        config.setUnit("COUNT");
        config.setConfigGroup("AUTH");
        config.setUpdatedBy(actor);
        var updatedAt = Instant.parse("2026-11-23T08:00:00Z");
        config.setUpdatedAt(updatedAt);

        new SystemConfigUpdateRequestDto(BigDecimal.valueOf(6)).applyTo(config);

        assertThat(config.getAuditTargetId()).isEqualTo(7L);
        assertThat(config.getUpdatedBy()).isSameAs(actor);
        assertThat(SystemConfigResponseDto.from(config)).isEqualTo(new SystemConfigResponseDto(
                "OTP_MAX_ATTEMPTS", "6", "COUNT", "AUTH", null, 8L, updatedAt));
    }
}
