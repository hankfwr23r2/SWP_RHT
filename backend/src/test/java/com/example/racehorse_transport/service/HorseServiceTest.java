package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.horse.HorseCreateRequestDto;
import com.example.racehorse_transport.dto.horse.HorseUpdateRequestDto;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import com.example.racehorse_transport.exception.HorseRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class HorseServiceTest {

    @Autowired private HorseService horses;
    @Autowired private TestData data;

    @Test
    void customerCreatesAndUpdatesOwnHorse() {
        String owner = customer();
        String chip = chip();
        var created = horses.createHorse(owner, create(chip.substring(0, 3) + "-" + chip.substring(3), BigDecimal.TEN, "VND"));
        assertThat(created.microchip()).isEqualTo(chip);
        assertThat(created.status()).isEqualTo(HorseStatus.ACTIVE);
        assertThat(created.createdAt()).isNotNull();

        var updated = horses.updateHorse(owner, created.id(), new HorseUpdateRequestDto("Đổi tên", null, HorseGender.MARE,
                null, null, null, null, null, typeId(), null, null, null, null));
        assertThat(updated.name()).isEqualTo("Đổi tên");
        assertThat(updated.microchip()).isEqualTo(chip);
        assertThat(updated.declaredValue()).isNull();
        assertThat(updated.updatedAt()).isNotNull();
        assertThat(horses.horses(owner)).singleElement().satisfies(h -> assertThat(h.name()).isEqualTo("Đổi tên"));
    }

    @Test
    void otherCustomersCannotSeeOrEditTheHorse() {
        Long id = horses.createHorse(customer(), create(chip(), null, null)).id();
        String other = customer();
        assertThat(horses.horses(other)).isEmpty();
        assertThatThrownBy(() -> horses.horse(other, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> horses.updateHorse(other, id, new HorseUpdateRequestDto("X", null, HorseGender.MARE,
                null, null, null, null, null, typeId(), null, null, null, null))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsWrongChipAndHalfDeclaredValue() {
        String owner = customer();
        assertThatThrownBy(() -> horses.createHorse(owner, create("123", null, null))).isInstanceOf(HorseRuleException.class);
        assertThatThrownBy(() -> horses.createHorse(owner, create(chip(), BigDecimal.TEN, null))).isInstanceOf(HorseRuleException.class);
        assertThat(horses.horses(owner)).isEmpty();
    }

    // Câu vi phạm ràng buộc DB luôn là câu cuối của transaction
    @Test
    void duplicateChipIsRejected() {
        String chip = chip();
        horses.createHorse(customer(), create(chip, null, null));
        String other = customer();
        assertThatThrownBy(() -> horses.createHorse(other, create(chip, null, null)))
                .isInstanceOf(HorseRuleException.DuplicateHorseException.class);
    }

    private HorseCreateRequestDto create(String microchip, BigDecimal value, String currency) {
        return new HorseCreateRequestDto(microchip, "Sao Mai", null, HorseGender.MARE, (short) 2019, null, null, null, null,
                typeId(), null, null, value, currency);
    }

    private Long typeId() {
        return horses.horseTypes().get(0).id();
    }

    private String customer() {
        String username = data.unique("horse-a5");
        data.customer(username);
        return username;
    }

    private String chip() {
        return "%015d".formatted(900_000_000_000_000L + System.nanoTime() % 1_000_000_000_000L);
    }
}
