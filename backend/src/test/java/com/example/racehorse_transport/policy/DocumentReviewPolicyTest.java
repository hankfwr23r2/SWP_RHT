package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentReviewPolicyTest {
    @Test
    void onlyUploadedDocumentsCanBeReviewed() {
        assertThatCode(() -> DocumentReviewPolicy.requireUploaded(DocumentStatus.UPLOADED)).doesNotThrowAnyException();
        assertThatThrownBy(() -> DocumentReviewPolicy.requireUploaded(DocumentStatus.APPROVED))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @Test
    void approvedDocumentCanBeRevokedBeforePickup() {
        assertThatCode(() -> DocumentReviewPolicy.requireRevocationBeforePickup(BookingStatus.CONFIRMED, false))
                .doesNotThrowAnyException();
        assertThatCode(() -> DocumentReviewPolicy.requireApproved(DocumentStatus.APPROVED)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = DocumentStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void onlyApprovedDocumentsCanBeRevoked(DocumentStatus status) {
        assertThatThrownBy(() -> DocumentReviewPolicy.requireApproved(status))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(409));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CONFIRMED", "IN_OPERATION", "DELIVERED", "COMPLETED"})
    void receiptOrOperationBlocksRevocation(BookingStatus status) {
        assertThatThrownBy(() -> DocumentReviewPolicy.requireRevocationBeforePickup(status, status == BookingStatus.CONFIRMED))
                .isInstanceOfSatisfying(BookingRuleException.class, ex -> {
                    assertThat(ex.getStatusCode().value()).isEqualTo(409);
                    assertThat(ex.getReason()).isEqualTo("Giấy đã theo xe, xử lý qua sự cố giấy tờ.");
                });
    }
}
