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
        row.setCoverageAmount(BigDecimal.TEN);
        row.setPremiumAmount(BigDecimal.ONE);
        row.setStatus(HorseInsuranceStatus.DRAFT);
        Instant expires = Instant.parse("2026-12-01T00:00:00Z");
        Party insurer = new Party();
        new InsuranceEvidenceRequestDto(3L, BigDecimal.ONE, BigDecimal.TEN, "VND", expires).applyTo(row, insurer);
        assertThat(row.getId()).isEqualTo(1L);
        assertThat(row.getCoverageAmount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(row.getPremiumAmount()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(row.getStatus()).isEqualTo(HorseInsuranceStatus.DRAFT);
        assertThat(row.getConfirmedBy()).isNull();
        assertThat(row.getConfirmedAt()).isNull();
        assertThat(row.getQuoteExpiresAt()).isEqualTo(expires);
        assertThat(row.getInsurerParty()).isSameAs(insurer);
    }

    @Test
    void revisionConversionPreservesReasonStatusAndMonetaryFields() {
        HorseInsurance row = new HorseInsurance();
        row.setId(1L);
        row.setRevisionReason("Bổ sung hợp đồng");
        row.setStatus(HorseInsuranceStatus.REVISION_REQUESTED);
        row.setCoverageAmount(BigDecimal.TEN);
        Party insurer = new Party();
        LocalDate date = LocalDate.of(2026, 12, 1);
        new PersonalInsuranceRevisionRequestDto(3L, " HĐ01 ", date, date.plusMonths(1)).applyTo(row, insurer);
        assertThat(row.getId()).isEqualTo(1L);
        assertThat(row.getPolicyNumber()).isEqualTo("HĐ01");
        assertThat(row.getInsurerParty()).isSameAs(insurer);
        assertThat(row.getStatus()).isEqualTo(HorseInsuranceStatus.REVISION_REQUESTED);
        assertThat(row.getCoverageAmount()).isEqualByComparingTo(BigDecimal.TEN);
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
    }
}
