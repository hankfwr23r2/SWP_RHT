package com.example.racehorse_transport.dto.booking;

import static org.assertj.core.api.Assertions.assertThat;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BookingReviewDtoTest {
    @Test
    void insuranceEvidenceConversionLeavesMoneyAndWorkflowFieldsForService() {
        HorseInsurance row = new HorseInsurance();
        row.setId(1L);
        row.setPremiumAmount(BigDecimal.ONE);
        row.setStatus(HorseInsuranceStatus.DRAFT);
        Instant expires = Instant.parse("2026-12-01T00:00:00Z");
        new InsuranceEvidenceRequestDto("Hãng A", BigDecimal.ONE, expires).applyTo(row);
        assertThat(row.getId()).isEqualTo(1L);
        assertThat(row.getPremiumAmount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(row.getStatus()).isEqualTo(HorseInsuranceStatus.DRAFT);
        assertThat(row.getConfirmedBy()).isNull();
        assertThat(row.getConfirmedAt()).isNull();
        assertThat(row.getQuoteExpiresAt()).isEqualTo(expires);
        assertThat(row.getInsurerName()).isEqualTo("Hãng A");
        assertThat(row.getCurrencyCode()).isEqualTo("VND");

        new InsuranceEvidenceRequestDto(null, BigDecimal.ONE, expires).applyTo(row);
        assertThat(row.getInsurerName()).isEqualTo("Hãng A");
    }

    @Test
    void revisionConversionPreservesReasonStatusAndMonetaryFields() {
        HorseInsurance row = new HorseInsurance();
        row.setId(1L);
        row.setRevisionReason("Bổ sung hợp đồng");
        row.setStatus(HorseInsuranceStatus.REVISION_REQUESTED);
        row.setPremiumAmount(BigDecimal.TEN);
        LocalDate date = LocalDate.of(2026, 12, 1);
        new PersonalInsuranceRevisionRequestDto(" Hãng A ", " HĐ01 ", date, date.plusMonths(1)).applyTo(row);
        assertThat(row.getId()).isEqualTo(1L);
        assertThat(row.getPolicyNumber()).isEqualTo("HĐ01");
        assertThat(row.getInsurerName()).isEqualTo("Hãng A");
        assertThat(row.getStatus()).isEqualTo(HorseInsuranceStatus.REVISION_REQUESTED);
        assertThat(row.getPremiumAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(row.getRevisionReason()).isEqualTo("Bổ sung hợp đồng");
    }

    @Test
    void uploadOnlyAppliesRequirementAndExpiryAndResponseShowsReview() {
        Document row = new Document();
        row.setId(1L);
        row.setStatus(DocumentStatus.UPLOADED);
        DocumentType type = new DocumentType();
        type.setCode("PASSPORT");
        row.setDocumentType(type);
        AppUser uploader = new AppUser();
        row.setUploadedBy(uploader);
        DocumentRequirement requirement = new DocumentRequirement();
        requirement.setId(3L);
        LocalDate expiry = LocalDate.of(2026, 12, 1);
        new DocumentUploadRequestDto(3L, null, expiry, null).applyTo(row, requirement);
        assertThat(row.getUploadedBy()).isSameAs(uploader);
        assertThat(row.getReviewedAt()).isNull();
        var result = BookingDocumentResponseDto.from(row);
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.requirementId()).isEqualTo(3L);
        assertThat(result.typeCode()).isEqualTo("PASSPORT");
        assertThat(result.status()).isEqualTo(DocumentStatus.UPLOADED);
        assertThat(result.expiryDate()).isEqualTo(expiry);
        assertThat(result.bookingHorseId()).isNull();
        assertThat(BookingDocumentResponseDto.from(row, 9L).bookingHorseId()).isEqualTo(9L);
    }

    @Test
    void revocationRequestOnlyAppliesTheReviewNote() {
        Document row = new Document();
        row.setId(1L);
        row.setStatus(DocumentStatus.APPROVED);
        Staff reviewer = new Staff();
        Instant reviewedAt = Instant.parse("2026-11-23T00:00:00Z");
        row.setReviewedBy(reviewer);
        row.setReviewedAt(reviewedAt);

        new DocumentRevocationRequestDto("  Giấy sai nội dung  ").applyTo(row);

        assertThat(row.getReviewNote()).isEqualTo("Giấy sai nội dung");
        assertThat(row.getId()).isEqualTo(1L);
        assertThat(row.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(row.getReviewedBy()).isSameAs(reviewer);
        assertThat(row.getReviewedAt()).isEqualTo(reviewedAt);
    }
}
