package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.regulation.CountryRuleCreateRequestDto;
import com.example.racehorse_transport.dto.regulation.CountryRuleUpdateRequestDto;
import com.example.racehorse_transport.dto.regulation.CountryRuleResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeCreateRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeUpdateRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentTypeResponseDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementCreateRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementUpdateRequestDto;
import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewRequestDto;
import com.example.racehorse_transport.dto.regulation.RegulationPreviewResponseDto;
import com.example.racehorse_transport.entity.Country;
import com.example.racehorse_transport.entity.CountryRule;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.DocumentType;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.RuleDirection;
import com.example.racehorse_transport.exception.RegulationRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.RegulationPolicy;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.CountryRuleRepository;
import com.example.racehorse_transport.repository.DepartureRepository;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.DocumentTypeRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RouteStopRepository;
import com.example.racehorse_transport.policy.DepartureRoutePolicy;
import com.example.racehorse_transport.entity.Route;
import com.example.racehorse_transport.service.RegulationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegulationServiceImpl implements RegulationService {

    private final CountryRuleRepository rules;
    private final DocumentTypeRepository types;
    private final DocumentRequirementRepository requirements;
    private final CountryRepository countries;
    private final LocationRepository locations;
    private final DepartureRepository departures;
    private final RouteStopRepository routeStops;
    private final Clock clock;
    private final com.example.racehorse_transport.service.SystemConfigService configs;

    @Override
    public RegulationPreviewResponseDto preview(RegulationPreviewRequestDto request) {
        RegulationPreviewResponseDto resolved = resolve(request.bookingType(), request.pickupCountryCode(),
                request.deliveryCountryCode(), request.requestedPickupDate(), request.gateLocationId());
        if (request.departureId() == null) {
            if (request.bookingType() == BookingType.INTERPROVINCE) {
                return new RegulationPreviewResponseDto(resolved.rule(), resolved.leadDays(), resolved.requirements(),
                        resolved.requirementsDependOnGate(), null, RegulationPolicy.feasible(resolved.leadDays(),
                                com.example.racehorse_transport.policy.BookingSubmissionPolicy.businessDate(clock.instant()),
                                request.requestedPickupDate()));
            }
            return resolved;
        }
        Instant cutoff = departures.findById(request.departureId())
                .orElseThrow(() -> new RegulationRuleException("departureId", "Chuyến không tồn tại."))
                .getBookingCutoffAt();
        return new RegulationPreviewResponseDto(resolved.rule(), resolved.leadDays(), resolved.requirements(),
                resolved.requirementsDependOnGate(), cutoff,
                RegulationPolicy.feasible(resolved.leadDays(), clock.instant(), cutoff));
    }

    @Override
    @Transactional(readOnly = true)
    public Long gateOf(Route route, BookingType bookingType) {
        return route == null ? null : DepartureRoutePolicy.gateId(bookingType, routeStops.findBorderLocationIds(route.getId()));
    }

    @Override
    public RegulationPreviewResponseDto resolve(BookingType bookingType, String pickupCountryCode,
            String deliveryCountryCode, LocalDate requestedPickupDate, Long gateLocationId) {
        if (bookingType == BookingType.LOCAL) {
            return new RegulationPreviewResponseDto(null, 0, List.of(), false, null, null);
        }

        gate(gateLocationId);
        String countryCode = RegulationPolicy.partnerCountry(bookingType, pickupCountryCode, deliveryCountryCode);
        RuleDirection direction = RuleDirection.valueOf(bookingType.name());
        CountryRule rule = rules.findByCountry_CodeAndDirection(countryCode, direction).stream()
                .filter(candidate -> RegulationPolicy.effectiveOn(candidate, requestedPickupDate))
                .findFirst()
                .orElseThrow(() -> new RegulationRuleException("requestedPickupDate",
                        "Chưa cấu hình quy định có hiệu lực cho quốc gia và chiều vận chuyển này.", true, null));
        List<DocumentRequirement> configured = requirements.findByRule_Id(rule.getId());
        // Demo bỏ kiểm tra giờ: không cần thời gian làm giấy trước chuyến
        boolean skip = configs.timeRulesSkipped();
        CountryRuleResponseDto ruleDto = CountryRuleResponseDto.from(rule);
        if (skip) ruleDto = new CountryRuleResponseDto(ruleDto.id(), ruleDto.countryCode(), ruleDto.direction(), 0, 0,
                ruleDto.validFrom(), ruleDto.validTo());
        return new RegulationPreviewResponseDto(ruleDto, skip ? 0 : RegulationPolicy.leadDays(rule),
                DocumentRequirementResponseDto.fromList(RegulationPolicy.requirementsForGate(configured, gateLocationId)),
                gateLocationId == null && configured.stream().anyMatch(item -> item.getGateLocation() != null),
                null, null);
    }

    @Override
    public List<CountryRuleResponseDto> listCountryRules() {
        return CountryRuleResponseDto.fromList(rules.findAll(Sort.by("id")));
    }

    @Override
    public CountryRuleResponseDto getCountryRule(Long id) {
        return CountryRuleResponseDto.from(rule(id));
    }

    @Override
    @Transactional
    public CountryRuleResponseDto createCountryRule(CountryRuleCreateRequestDto request) {
        RegulationPolicy.validateRule(request.countryCode(), request.direction(), request.validFrom(), request.validTo());
        CountryRule entity = request.toEntity(country(request.countryCode()));
        return CountryRuleResponseDto.from(write(() -> rules.saveAndFlush(entity)));
    }

    @Override
    @Transactional
    public CountryRuleResponseDto updateCountryRule(Long id, CountryRuleUpdateRequestDto request) {
        CountryRule entity = rule(id);
        RegulationPolicy.validateRule(request.countryCode(), request.direction(), request.validFrom(), request.validTo());
        request.applyTo(entity, country(request.countryCode()));
        return CountryRuleResponseDto.from(write(() -> rules.saveAndFlush(entity)));
    }

    @Override
    public List<DocumentTypeResponseDto> listDocumentTypes() {
        return DocumentTypeResponseDto.fromList(types.findAll(Sort.by("id")));
    }

    @Override
    public DocumentTypeResponseDto getDocumentType(Long id) {
        return DocumentTypeResponseDto.from(types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loại giấy tờ không tồn tại.")));
    }

    @Override
    @Transactional
    public DocumentTypeResponseDto createDocumentType(DocumentTypeCreateRequestDto request) {
        return DocumentTypeResponseDto.from(write(() -> types.saveAndFlush(request.toEntity())));
    }

    @Override
    @Transactional
    public DocumentTypeResponseDto updateDocumentType(Long id, DocumentTypeUpdateRequestDto request) {
        DocumentType entity = types.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Loại giấy tờ không tồn tại."));
        request.applyTo(entity);
        return DocumentTypeResponseDto.from(write(() -> types.saveAndFlush(entity)));
    }

    @Override
    public List<DocumentRequirementResponseDto> listDocumentRequirements(Long ruleId) {
        rule(ruleId);
        return DocumentRequirementResponseDto.fromList(requirements.findByRule_Id(ruleId));
    }

    @Override
    @Transactional
    public DocumentRequirementResponseDto createDocumentRequirement(Long ruleId, DocumentRequirementCreateRequestDto request) {
        DocumentRequirement entity = request.toEntity(rule(ruleId), documentType(request.documentTypeId()),
                gate(request.gateLocationId()));
        return DocumentRequirementResponseDto.from(write(() -> requirements.saveAndFlush(entity)));
    }

    @Override
    @Transactional
    public DocumentRequirementResponseDto updateDocumentRequirement(Long ruleId, Long id,
            DocumentRequirementUpdateRequestDto request) {
        DocumentRequirement entity = requirements.findByIdAndRule_Id(id, ruleId)
                .orElseThrow(() -> new ResourceNotFoundException("Yêu cầu giấy tờ không tồn tại trong quy định này."));
        request.applyTo(entity, documentType(request.documentTypeId()), gate(request.gateLocationId()));
        return DocumentRequirementResponseDto.from(write(() -> requirements.saveAndFlush(entity)));
    }

    private CountryRule rule(Long id) {
        return rules.findById(id).orElseThrow(() -> new ResourceNotFoundException("Quy định không tồn tại."));
    }

    private Country country(String code) {
        return countries.findById(code)
                .orElseThrow(() -> new RegulationRuleException("countryCode", "Quốc gia không tồn tại."));
    }

    private DocumentType documentType(Long id) {
        return types.findById(id)
                .orElseThrow(() -> new RegulationRuleException("documentTypeId", "Loại giấy tờ không tồn tại."));
    }

    private Location gate(Long id) {
        if (id == null) {
            return null;
        }
        Location location = locations.findById(id)
                .orElseThrow(() -> new RegulationRuleException("gateLocationId", "Cửa khẩu không tồn tại."));
        RegulationPolicy.validateGate(location);
        return location;
    }

    private <T> T write(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataIntegrityViolationException ex) {
            if (!(ex.getCause() instanceof ConstraintViolationException violation)) {
                throw ex;
            }
            // PostgreSQL uses 23P01 for an exclusion violation. Hibernate may omit its constraint name.
            if ("23P01".equals(violation.getSQLState())) {
                throw new RegulationRuleException("validFrom",
                        "Thời hạn hiệu lực trùng quy định cùng nước và chiều vận chuyển.", true, ex);
            }
            throw switch (violation.getConstraintName()) {
                case "uk_document_type_code" -> new RegulationRuleException("code", "Mã loại giấy tờ đã tồn tại.", true, ex);
                case "uk_document_requirement" -> new RegulationRuleException("documentTypeId",
                        "Loại giấy tờ đã được yêu cầu trong quy định với cùng phạm vi cửa khẩu.", true, ex);
                case null, default -> ex;
            };
        }
    }
}
