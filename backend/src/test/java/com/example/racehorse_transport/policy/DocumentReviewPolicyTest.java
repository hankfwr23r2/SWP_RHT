package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import org.junit.jupiter.api.Test;

class DocumentReviewPolicyTest {
    @Test
    void onlyUploadedDocumentsCanBeReviewed() {
        assertThatCode(() -> DocumentReviewPolicy.requireUploaded(DocumentStatus.UPLOADED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DocumentReviewPolicy.requireUploaded(DocumentStatus.APPROVED))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }
}
