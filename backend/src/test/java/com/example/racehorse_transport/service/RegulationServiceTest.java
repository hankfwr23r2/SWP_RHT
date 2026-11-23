package com.example.racehorse_transport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.racehorse_transport.dto.regulation.*;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.entity.enums.*;
import com.example.racehorse_transport.exception.RegulationRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.impl.RegulationServiceImpl;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class RegulationServiceTest {

    private static final LocalDate PICKUP = LocalDate.of(2026, 12, 1);

    @Mock private CountryRuleRepository rules;
    @Mock private DocumentTypeRepository types;
    @Mock private DocumentRequirementRepository requirements;
    @Mock private CountryRepository countries;
    @Mock private LocationRepository locations;
    @Mock private DepartureRepository departures;
    private RegulationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC);
        service = new RegulationServiceImpl(rules, types, requirements, countries, locations, departures, clock);
    }

    @Test
    void localResolvesWithoutAnyRepositoryLookup() {
        var result = service.resolve(BookingType.LOCAL, "VN", "VN", PICKUP, null);
        assertThat(result.rule()).isNull();
        assertThat(result.leadDays()).isZero();
        assertThat(result.requirements()).isEmpty();
        verifyNoInteractions(rules, requirements, countries, locations, departures, types);
    }

    @Test
    void previewSelectsByPickupDateAndChecksTheDepartureCutoff() {
        CountryRule expired = rule("KH", RuleDirection.EXPORT);
        expired.setValidFrom(PICKUP.minusDays(10));
        expired.setValidTo(PICKUP.minusDays(1));
        CountryRule effective = rule("KH", RuleDirection.EXPORT);
        effective.setId(2L);
        when(rules.findByCountry_CodeAndDirection("KH", RuleDirection.EXPORT)).thenReturn(List.of(expired, effective));
        DocumentRequirement specific = new DocumentRequirement();
        Location gate = new Location();
        gate.setId(5L);
        specific.setGateLocation(gate);
        specific.setDocumentType(type());
        DocumentRequirement general = new DocumentRequirement();
        general.setRule(effective);
        general.setDocumentType(type());
        general.setId(3L);
        when(requirements.findByRule_Id(2L)).thenReturn(List.of(specific, general));

        Departure departure = new Departure();
        departure.setBookingCutoffAt(Instant.parse("2026-12-29T00:00:00Z"));
        when(departures.findById(10L)).thenReturn(Optional.of(departure));

        var result = service.preview(new RegulationPreviewRequestDto(BookingType.EXPORT, "VN", "KH", PICKUP, null, 10L));
        assertThat(result.rule().id()).isEqualTo(2L);
        assertThat(result.requirements()).extracting(DocumentRequirementResponseDto::id).containsExactly(3L);
        assertThat(result.requirementsDependOnGate()).isTrue();
        assertThat(result.leadDays()).isEqualTo(28);
        assertThat(result.bookingCutoffAt()).isEqualTo(departure.getBookingCutoffAt());
        assertThat(result.feasible()).isTrue();
    }

    @Test
    void reportsMissingConfigurationForNonLocalOrder() {
        CountryRule future = rule("VN", RuleDirection.INTERPROVINCE);
        future.setValidFrom(PICKUP.plusDays(1));
        when(rules.findByCountry_CodeAndDirection("VN", RuleDirection.INTERPROVINCE)).thenReturn(List.of(future));

        assertThatThrownBy(() -> service.resolve(BookingType.INTERPROVINCE, "VN", "VN", PICKUP, null))
                .isInstanceOfSatisfying(RegulationRuleException.class, ex -> assertThat(ex.isConflict()).isTrue());
        verifyNoInteractions(requirements);
    }

    @Test
    void previewRejectsUnknownDeparture() {
        assertThatThrownBy(() -> service.preview(
                new RegulationPreviewRequestDto(BookingType.LOCAL, "VN", "VN", PICKUP, null, 10L)))
                .isInstanceOfSatisfying(RegulationRuleException.class, ex -> assertThat(ex.getField()).isEqualTo("departureId"));
    }

    @Test
    void readsCreatesAndUpdatesCountryRules() {
        CountryRule entity = rule("KH", RuleDirection.EXPORT);
        when(rules.findAll(Sort.by("id"))).thenReturn(List.of(entity));
        when(rules.findById(1L)).thenReturn(Optional.of(entity));
        when(countries.findById("KH")).thenReturn(Optional.of(entity.getCountry()));
        when(rules.saveAndFlush(any(CountryRule.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.listCountryRules()).containsExactly(service.getCountryRule(1L));
        assertThat(service.createCountryRule(new CountryRuleCreateRequestDto("KH", RuleDirection.EXPORT,
                14, 7, 7, PICKUP, null)).countryCode()).isEqualTo("KH");
        assertThat(service.updateCountryRule(1L, new CountryRuleUpdateRequestDto("KH", RuleDirection.EXPORT,
                1, 2, 3, PICKUP, PICKUP)).validTo()).isEqualTo(PICKUP);
    }

    @Test
    void rejectsInvalidRuleAndMissingRuleIds() {
        assertThatThrownBy(() -> service.createCountryRule(new CountryRuleCreateRequestDto("KH", RuleDirection.EXPORT,
                14, 7, 7, PICKUP, PICKUP.minusDays(1)))).isInstanceOf(RegulationRuleException.class);
        assertThatThrownBy(() -> service.getCountryRule(1L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateCountryRule(1L, new CountryRuleUpdateRequestDto("KH",
                RuleDirection.EXPORT, 0, 0, 0, PICKUP, null))).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.listDocumentRequirements(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void translatesExclusionViolationEvenWhenHibernateOmitsTheConstraintName() {
        when(countries.findById("KH")).thenReturn(Optional.of(rule("KH", RuleDirection.EXPORT).getCountry()));
        var conflict = new ConstraintViolationException("overlap", new SQLException("overlap", "23P01"), null);
        when(rules.saveAndFlush(any(CountryRule.class))).thenThrow(new DataIntegrityViolationException("overlap", conflict));
        var request = new CountryRuleCreateRequestDto("KH", RuleDirection.EXPORT, 0, 0, 0, PICKUP, null);

        assertThatThrownBy(() -> service.createCountryRule(request))
                .isInstanceOfSatisfying(RegulationRuleException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("validFrom");
                    assertThat(ex.isConflict()).isTrue();
                });
    }

    @Test
    void readsCreatesAndUpdatesDocumentTypes() {
        DocumentType type = type();
        when(types.findAll(Sort.by("id"))).thenReturn(List.of(type));
        when(types.findById(4L)).thenReturn(Optional.of(type));
        when(types.saveAndFlush(any(DocumentType.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.listDocumentTypes()).containsExactly(service.getDocumentType(4L));
        assertThat(service.createDocumentType(new DocumentTypeCreateRequestDto("PERMIT", "Permit",
                DocumentSubject.BOOKING)).code()).isEqualTo("PERMIT");
        assertThat(service.updateDocumentType(4L, new DocumentTypeUpdateRequestDto("CERT", "Updated certificate",
                DocumentSubject.HORSE)).name()).isEqualTo("Updated certificate");
    }

    @Test
    void rejectsMissingDocumentTypeIds() {
        assertThatThrownBy(() -> service.getDocumentType(4L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateDocumentType(4L, new DocumentTypeUpdateRequestDto("CERT", "Certificate",
                DocumentSubject.HORSE))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createsListsAndUpdatesRequirementsWithinTheirRule() {
        CountryRule rule = rule("KH", RuleDirection.EXPORT);
        DocumentType type = type();
        DocumentRequirement entity = new DocumentRequirementCreateRequestDto(4L, null, DocumentSubmitter.COMPANY,
                true).toEntity(rule, type, null);
        entity.setId(3L);
        when(rules.findById(1L)).thenReturn(Optional.of(rule));
        when(types.findById(4L)).thenReturn(Optional.of(type));
        when(requirements.findByRule_Id(1L)).thenReturn(List.of(entity));
        when(requirements.findByIdAndRule_Id(3L, 1L)).thenReturn(Optional.of(entity));
        when(requirements.saveAndFlush(any(DocumentRequirement.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(service.createDocumentRequirement(1L,
                new DocumentRequirementCreateRequestDto(4L, null, DocumentSubmitter.COMPANY, true)).mandatory()).isTrue();
        assertThat(service.listDocumentRequirements(1L)).extracting(DocumentRequirementResponseDto::id).containsExactly(3L);
        assertThat(service.updateDocumentRequirement(1L, 3L,
                new DocumentRequirementUpdateRequestDto(4L, null, DocumentSubmitter.CUSTOMER, false)).mandatory()).isFalse();
        assertThatThrownBy(() -> service.updateDocumentRequirement(2L, 3L,
                new DocumentRequirementUpdateRequestDto(4L, null, DocumentSubmitter.CUSTOMER, false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsHubAsRequirementGate() {
        Location hub = new Location();
        hub.setLocationType(LocationType.HUB);
        when(rules.findById(1L)).thenReturn(Optional.of(rule("KH", RuleDirection.EXPORT)));
        when(types.findById(4L)).thenReturn(Optional.of(type()));
        when(locations.findById(5L)).thenReturn(Optional.of(hub));

        assertThatThrownBy(() -> service.createDocumentRequirement(1L,
                new DocumentRequirementCreateRequestDto(4L, 5L, DocumentSubmitter.COMPANY, true)))
                .isInstanceOfSatisfying(RegulationRuleException.class, ex -> assertThat(ex.getField()).isEqualTo("gateLocationId"));
        verifyNoInteractions(requirements);
    }

    private CountryRule rule(String code, RuleDirection direction) {
        Country country = new Country();
        country.setCode(code);
        CountryRule rule = new CountryRuleCreateRequestDto(code, direction, 14, 7, 7, PICKUP, null).toEntity(country);
        rule.setId(1L);
        return rule;
    }

    private DocumentType type() {
        DocumentType type = new DocumentTypeCreateRequestDto("CERT", "Certificate", DocumentSubject.HORSE).toEntity();
        type.setId(4L);
        return type;
    }
}
