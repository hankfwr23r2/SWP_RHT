package com.example.racehorse_transport.dto.horse;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseType;
import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.entity.enums.HorseStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class HorseDtoTest {

    private static HorseType type() {
        HorseType type = new HorseType();
        type.setId(3L);
        type.setName("Arabian");
        return type;
    }

    @Test
    void createLeavesOwnerChipStatusAndMoneyToService() {
        HorseType type = type();
        Horse horse = new HorseCreateRequestDto("985000000000001", " Sao Mai ", " ", HorseGender.MARE, (short) 2019, "Nâu", null,
                null, null, 3L, null, new BigDecimal("480.0"), BigDecimal.TEN, "VND").toEntity(type);

        assertThat(horse.getName()).isEqualTo("Sao Mai");
        assertThat(horse.getPassportNumber()).isNull();
        assertThat(horse.getHorseType()).isSameAs(type);
        assertThat(horse.getWeightKg()).isEqualByComparingTo("480");
        assertThat(horse.getId()).isNull();
        assertThat(horse.getOwner()).isNull();
        assertThat(horse.getMicrochip()).isNull();
        assertThat(horse.getStatus()).isNull();
        assertThat(horse.getDeclaredValue()).isNull();
    }

    @Test
    void updateKeepsIdentityChipAndStatus() {
        Customer owner = new Customer();
        Horse horse = new Horse();
        horse.setId(9L);
        horse.setOwner(owner);
        horse.setMicrochip("985000000000001");
        horse.setStatus(HorseStatus.RETIRED);

        new HorseUpdateRequestDto("Tên mới", "VN-1", HorseGender.STALLION, null, null, null, null, null, 3L, null, null,
                BigDecimal.TEN, "VND").applyTo(horse, type());

        assertThat(horse.getName()).isEqualTo("Tên mới");
        assertThat(horse.getId()).isEqualTo(9L);
        assertThat(horse.getOwner()).isSameAs(owner);
        assertThat(horse.getMicrochip()).isEqualTo("985000000000001");
        assertThat(horse.getStatus()).isEqualTo(HorseStatus.RETIRED);
        assertThat(horse.getDeclaredValue()).isNull();
    }

    @Test
    void responsesCarryHorseType() {
        Horse horse = new Horse();
        horse.setId(9L);
        horse.setHorseType(type());

        assertThat(HorseResponseDto.from(horse).id()).isEqualTo(9L);
        assertThat(HorseResponseDto.from(horse).horseTypeName()).isEqualTo("Arabian");
        assertThat(HorseTypeResponseDto.from(type()).id()).isEqualTo(3L);
    }
}
