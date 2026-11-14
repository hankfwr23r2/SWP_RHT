package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.HorseFeedingDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HorseFeedingPolicyTest {

    @Test
    void trimsTextAndKeepsTheDeclaration() {
        HorseFeedingDto result = HorseFeedingPolicy.normalize(HorseFeedingDto.builder()
                .usualTimes("  07:00, 12:00, 18:00 ").ration(" 8 kg cỏ/ngày ").ownFeed(true).build());

        assertThat(result.getUsualTimes()).isEqualTo("07:00, 12:00, 18:00");
        assertThat(result.getRation()).isEqualTo("8 kg cỏ/ngày");
        assertThat(result.getOwnFeed()).isTrue();
    }

    @Test
    void emptyDeclarationIsNotStored() {
        assertThat(HorseFeedingPolicy.normalize(null)).isNull();
        assertThat(HorseFeedingPolicy.normalize(HorseFeedingDto.builder().usualTimes("  ").ration("").build())).isNull();
        assertThat(HorseFeedingPolicy.normalize(HorseFeedingDto.builder().ownFeed(false).build())).isNull();
    }

    @Test
    void ownFeedAloneIsADeclaration() {
        HorseFeedingDto result = HorseFeedingPolicy.normalize(HorseFeedingDto.builder().ownFeed(true).build());

        assertThat(result.getOwnFeed()).isTrue();
        assertThat(result.getUsualTimes()).isNull();
        assertThat(result.getRation()).isNull();
    }

    @Test
    void rejectsTextLongerThanTheLimits() {
        String longTimes = "x".repeat(HorseFeedingPolicy.MAX_USUAL_TIMES_LENGTH + 1);
        String longRation = "x".repeat(HorseFeedingPolicy.MAX_RATION_LENGTH + 1);

        assertThatThrownBy(() -> HorseFeedingPolicy.normalize(HorseFeedingDto.builder().usualTimes(longTimes).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Giờ ăn quen");
        assertThatThrownBy(() -> HorseFeedingPolicy.normalize(HorseFeedingDto.builder().ration(longRation).build()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Khẩu phần");
        assertThat(HorseFeedingPolicy.normalize(HorseFeedingDto.builder()
                .ration("x".repeat(HorseFeedingPolicy.MAX_RATION_LENGTH)).build())).isNotNull();
    }
}
