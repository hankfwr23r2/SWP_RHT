package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;
import static com.example.racehorse_transport.entity.enums.BookingStatus.*;
import com.example.racehorse_transport.exception.BookingRuleException;
import org.junit.jupiter.api.Test;

class BookingReviewPolicyTest {
    @Test
    void validatesStateAndRejectionReason() {
        assertThatCode(() -> BookingReviewPolicy.requireStatus(UNDER_REVIEW, UNDER_REVIEW, NEEDS_RESUBMISSION))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BookingReviewPolicy.requireStatus(QUOTING, UNDER_REVIEW))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
        assertThat(BookingReviewPolicy.requireReason("  Thiếu giấy  ")).isEqualTo("Thiếu giấy");
        assertThatThrownBy(() -> BookingReviewPolicy.requireReason(" ")).isInstanceOf(BookingRuleException.class);
    }
}
