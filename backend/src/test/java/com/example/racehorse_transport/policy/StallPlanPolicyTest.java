package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.racehorse_transport.entity.enums.HorseGender;
import com.example.racehorse_transport.policy.StallPlanPolicy.StallHorse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StallPlanPolicyTest {

    private static StallHorse horse(long id, int board, int alight, int seats) {
        return new StallHorse(id, "Ngựa " + id, 1L, HorseGender.GELDING, board, alight, seats, null, false);
    }

    @Test
    void wideStallTakesAnyTwoAdjacentPositionsInsideOneSegment() {
        // Xe 3 chỗ: 1 con thường ở vị trí 2 thì khoang rộng không còn hai vị trí liền nhau
        StallHorse standing = new StallHorse(1L, "Sao Mai", 1L, HorseGender.MARE, 1, 3, 1, 2, true);
        assertThatThrownBy(() -> StallPlanPolicy.suggest(List.of(standing, horse(2, 1, 3, 2)), List.of(3)))
                .hasMessageContaining("Ngựa 2").hasMessageContaining("cần 2 vị trí liền nhau");
        // Con thường chưa lên xe thì được dồn để khoang rộng vừa
        Map<Long, Integer> plan = StallPlanPolicy.suggest(List.of(horse(1, 1, 3, 1), horse(2, 1, 3, 2)), List.of(3));
        StallPlanPolicy.requireValid(List.of(horse(1, 1, 3, 1), horse(2, 1, 3, 2)), plan, List.of(3));
        // Đoạn [2, 2]: khoang rộng không vắt qua hai đoạn
        assertThatThrownBy(() -> StallPlanPolicy.requireValid(List.of(horse(1, 1, 3, 2)), Map.of(1L, 2), List.of(2, 2)))
                .hasMessageContaining("khoang rộng");
    }

    @Test
    void horsesOnDifferentPartsOfTheRouteCanShareAPosition() {
        // Con A xuống ở điểm 2, con B lên ở điểm 2: không ở trên xe cùng lúc
        List<StallHorse> horses = List.of(horse(1, 1, 2, 1), horse(2, 2, 3, 1));
        assertThatCode(() -> StallPlanPolicy.requireValid(horses, Map.of(1L, 1, 2L, 1), List.of(1))).doesNotThrowAnyException();
        assertThatThrownBy(() -> StallPlanPolicy.requireValid(List.of(horse(1, 1, 3, 1), horse(2, 2, 3, 1)), Map.of(1L, 1, 2L, 1), List.of(2)))
                .hasMessageContaining("trùng con khác");
        assertThatThrownBy(() -> StallPlanPolicy.requireValid(List.of(horse(1, 1, 3, 2)), Map.of(1L, 2), List.of(2)))
                .hasMessageContaining("vượt số chỗ");
    }

    @Test
    void suggestionPutsEarlyBoardingAndLateAlightingHorsesTowardTheFront() {
        // Con lên ở điểm 1 xuống ở điểm 3 đứng trong; con lên ở điểm 2 đứng gần cửa
        Map<Long, Integer> plan = StallPlanPolicy.suggest(List.of(horse(2, 2, 3, 1), horse(1, 1, 3, 1)), List.of(4));
        assertThat(plan).containsEntry(1L, 1).containsEntry(2L, 2);
        // Con xuống trước đứng gần cửa
        Map<Long, Integer> unload = StallPlanPolicy.suggest(List.of(horse(1, 1, 2, 1), horse(2, 1, 3, 1)), List.of(4));
        assertThat(unload).containsEntry(2L, 1).containsEntry(1L, 2);
    }

    @Test
    void placeOneKeepsOthersWhereTheyAre() {
        StallHorse first = new StallHorse(1L, "Sao Mai", 1L, HorseGender.MARE, 1, 3, 1, 1, false);
        assertThat(StallPlanPolicy.placeOne(horse(2, 1, 3, 2), List.of(first), List.of(3))).contains(2);
        StallHorse middle = new StallHorse(1L, "Sao Mai", 1L, HorseGender.MARE, 1, 3, 1, 2, false);
        assertThat(StallPlanPolicy.placeOne(horse(2, 1, 3, 2), List.of(middle), List.of(3))).isEmpty();
    }

    @Test
    void preferencesOnlyWarn() {
        StallHorse stallion = new StallHorse(1L, "Gió Bấc", 1L, HorseGender.STALLION, 1, 3, 1, 2, false);
        StallHorse mare = new StallHorse(2L, "Sao Mai", 2L, HorseGender.MARE, 1, 2, 1, 1, false);
        StallHorse late = new StallHorse(3L, "Trăng Non", 3L, HorseGender.GELDING, 2, 3, 1, 3, false);
        List<String> warnings = StallPlanPolicy.warnings(List.of(stallion, mare, late), Map.of(1L, 2, 2L, 1, 3L, 3));
        assertThat(warnings).containsExactly("Sao Mai xuống trước nhưng bị Gió Bấc chắn phía cửa.");
        assertThat(StallPlanPolicy.warnings(List.of(stallion, late), Map.of(1L, 3, 3L, 1)))
                .contains("Trăng Non lên sau nhưng bị Gió Bấc chắn phía cửa.");
    }
}
