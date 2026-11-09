package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.entity.BookingHorseInsurance;
import com.example.racehorse_transport.entity.InsuranceDocument;
import com.example.racehorse_transport.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsuranceDetailsMapperTest {
    @Test
    void roundTripsDraftNullsAndExplicitlyEmptyCollectionsWithoutInventingConfirmation() {
        var entity = new BookingHorseInsurance();
        var draft = HorseInsuranceDto.builder().option("assistance").provider("Draft provider").build();
        InsuranceDetailsMapper.update(entity, draft);

        assertThat(InsuranceDetailsMapper.toDto(entity)).usingRecursiveComparison().isEqualTo(draft);
        assertThat(entity.getInsuranceDocuments()).isEmpty();
        assertThat(entity.getReviewed()).isNull();
        assertThat(entity.getArranged()).isNull();

        var empty = HorseInsuranceDto.builder().option("none").documents(List.of())
                .policyDocuments(List.of()).documentNames(Map.of()).reviewed(false).arranged(false).build();
        InsuranceDetailsMapper.update(entity, empty);

        assertThat(InsuranceDetailsMapper.toDto(entity)).usingRecursiveComparison().isEqualTo(empty);
        assertThat(InsuranceDetailsMapper.toDto(null)).isNull();
    }

    @Test
    void preservesPolicyDatesExactMoneyDocumentGroupsAndNamesIncludingUnlinkedLegacyNames() {
        String quote = "https://example.test/quote.pdf";
        String policy = "https://example.test/policy.pdf";
        String legacy = "https://example.test/old.png";
        Map<String, String> names = new LinkedHashMap<>();
        names.put(quote, "Báo giá gốc.pdf");
        names.put(policy, null);
        names.put(legacy, "Giấy cũ.png");
        var dto = HorseInsuranceDto.builder().option("assistance").provider("Provider")
                .policyNumber("POLICY-9").premium(new BigDecimal("1250000.00"))
                .validFrom("2026-11-01").validUntil("2027-11-01").quoteValidUntil("2026-10-30")
                .note("Original terms").reviewed(true).arranged(true)
                .documents(List.of(quote, quote)).policyDocuments(List.of(policy, quote)).documentNames(names).build();
        var entity = InsuranceDetailsMapper.update(new BookingHorseInsurance(), dto);

        assertThat(entity.getValidFrom()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(entity.getPremium()).isEqualTo(new BigDecimal("1250000.00"));
        assertThat(entity.getInsuranceDocuments()).hasSize(5)
                .allSatisfy(document -> assertThat(document.getInsurance()).isSameAs(entity));
        assertThat(InsuranceDetailsMapper.toDto(entity)).usingRecursiveComparison().isEqualTo(dto);
        assertThat(dto.getDocuments()).containsExactly(quote, quote);
        assertThat(dto.getDocumentNames()).containsEntry(policy, null).containsEntry(legacy, "Giấy cũ.png");
    }

    @Test
    void retainsDocumentIdsAcrossRetriesEditsAndReorderingAndRemovesOnlyUnreferencedRows() {
        String first = "https://example.test/first.pdf";
        String second = "https://example.test/second.pdf";
        var original = HorseInsuranceDto.builder().option("personal").documents(List.of(first, second))
                .documentNames(Map.of(first, "First.pdf", second, "Second.pdf")).build();
        var entity = InsuranceDetailsMapper.update(new BookingHorseInsurance(), original);
        var firstRow = entity.getInsuranceDocuments().get(0);
        var secondRow = entity.getInsuranceDocuments().get(1);
        firstRow.setId(10L);
        secondRow.setId(11L);
        List<InsuranceDocument> managedList = entity.getInsuranceDocuments();

        var reordered = HorseInsuranceDto.builder().option("personal").documents(List.of(second, first))
                .documentNames(Map.of(first, "Renamed.pdf", second, "Second.pdf")).build();
        InsuranceDetailsMapper.update(entity, reordered);

        assertThat(entity.getInsuranceDocuments()).isSameAs(managedList).containsExactly(firstRow, secondRow);
        assertThat(firstRow.getId()).isEqualTo(10L);
        assertThat(secondRow.getId()).isEqualTo(11L);
        assertThat(firstRow.getOriginalName()).isEqualTo("Renamed.pdf");
        assertThat(InsuranceDetailsMapper.toDto(entity).getDocuments()).containsExactly(second, first);

        InsuranceDetailsMapper.update(entity, reordered);
        assertThat(entity.getInsuranceDocuments()).containsExactly(firstRow, secondRow);

        InsuranceDetailsMapper.update(entity, HorseInsuranceDto.builder().option("personal").documents(List.of(second)).build());
        assertThat(entity.getInsuranceDocuments()).containsExactly(secondRow);
        assertThat(secondRow.getId()).isEqualTo(11L);
        assertThat(InsuranceDetailsMapper.toDto(entity).getDocumentNames()).isNull();
    }

    @Test
    void readingProducesDetachedCollectionsAndMappingPreservesExistingAuditMetadata() {
        var entity = new BookingHorseInsurance();
        var reviewer = new User();
        reviewer.setId(7);
        Instant reviewedAt = Instant.parse("2026-11-01T00:00:00Z");
        entity.setReviewedBy(reviewer);
        entity.setReviewedAt(reviewedAt);
        entity.setArrangedBy(reviewer);
        entity.setArrangedAt(reviewedAt);
        entity.setCreatedAt(reviewedAt);
        entity.setUpdatedAt(reviewedAt);
        entity.setVersion(4L);
        var dto = HorseInsuranceDto.builder().option("personal").documents(List.of("https://example.test/doc.pdf"))
                .documentNames(Map.of("https://example.test/doc.pdf", "Original.pdf")).build();
        InsuranceDetailsMapper.update(entity, dto);

        var detached = InsuranceDetailsMapper.toDto(entity);
        detached.getDocuments().clear();
        detached.getDocumentNames().clear();

        assertThat(entity.getInsuranceDocuments()).hasSize(1);
        assertThat(entity.getInsuranceDocuments().getFirst().getOriginalName()).isEqualTo("Original.pdf");
        assertThat(entity.getReviewedBy()).isSameAs(reviewer);
        assertThat(entity.getReviewedAt()).isEqualTo(reviewedAt);
        assertThat(entity.getArrangedBy()).isSameAs(reviewer);
        assertThat(entity.getArrangedAt()).isEqualTo(reviewedAt);
        assertThat(entity.getCreatedAt()).isEqualTo(reviewedAt);
        assertThat(entity.getUpdatedAt()).isEqualTo(reviewedAt);
        assertThat(entity.getVersion()).isEqualTo(4L);
    }

    @Test
    void invalidDatesAreRejectedBeforeMutatingAnExistingPolicy() {
        var entity = InsuranceDetailsMapper.update(new BookingHorseInsurance(),
                HorseInsuranceDto.builder().option("personal").provider("Original").validFrom("2026-01-01").build());
        var invalid = HorseInsuranceDto.builder().option("assistance").provider("Changed").validFrom("bad-date").build();

        assertThatThrownBy(() -> InsuranceDetailsMapper.update(entity, invalid)).isInstanceOf(DateTimeParseException.class);
        assertThat(entity.getOption()).isEqualTo("personal");
        assertThat(entity.getProvider()).isEqualTo("Original");
        assertThat(entity.getValidFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
    }
}
