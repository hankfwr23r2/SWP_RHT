package com.example.racehorse_transport.policy;

import static org.assertj.core.api.Assertions.*;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DocumentReviewPolicyTest {
    @Test
    void uploadedOrValidApprovedPaperBlocksANewVersion() {
        LocalDate today = LocalDate.of(2026, 12, 1);
        Document document = new Document();
        document.setStatus(DocumentStatus.UPLOADED);
        assertThat(DocumentReviewPolicy.blocksReplacement(document, 2L, today)).isTrue();
        DocumentRequirement source = new DocumentRequirement();
        source.setId(1L);
        document.setDocumentRequirement(source);
        assertThat(DocumentReviewPolicy.blocksReplacement(document, null, today)).isTrue();
        assertThat(DocumentReviewPolicy.blocksReplacement(document, 1L, today)).isTrue();
        assertThat(DocumentReviewPolicy.blocksReplacement(document, 2L, today)).isFalse();

        document.setStatus(DocumentStatus.APPROVED);
        document.setExpiryDate(today);
        assertThat(DocumentReviewPolicy.blocksReplacement(document, 2L, today)).isTrue();
        assertThat(DocumentReviewPolicy.statusOn(document, today)).isEqualTo(DocumentStatus.APPROVED);
        assertThat(DocumentReviewPolicy.blocksReplacement(document, null, today.plusDays(1))).isFalse();
        assertThat(DocumentReviewPolicy.statusOn(document, today.plusDays(1))).isEqualTo(DocumentStatus.EXPIRED);

        document.setStatus(DocumentStatus.REJECTED);
        assertThat(DocumentReviewPolicy.blocksReplacement(document, null, today)).isFalse();
        assertThat(DocumentReviewPolicy.statusOn(document, today)).isEqualTo(DocumentStatus.REJECTED);
    }

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
