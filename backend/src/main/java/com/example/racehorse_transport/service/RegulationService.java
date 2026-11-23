package com.example.racehorse_transport.service;

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
import com.example.racehorse_transport.entity.enums.BookingType;
import java.time.LocalDate;
import java.util.List;

public interface RegulationService {

    RegulationPreviewResponseDto preview(RegulationPreviewRequestDto request);

    /** Resolves the rules and requirements shared by booking and document completeness. */
    RegulationPreviewResponseDto resolve(BookingType bookingType, String pickupCountryCode,
            String deliveryCountryCode, LocalDate requestedPickupDate, Long gateLocationId);

    List<CountryRuleResponseDto> listCountryRules();

    CountryRuleResponseDto getCountryRule(Long id);

    CountryRuleResponseDto createCountryRule(CountryRuleCreateRequestDto request);

    CountryRuleResponseDto updateCountryRule(Long id, CountryRuleUpdateRequestDto request);

    List<DocumentTypeResponseDto> listDocumentTypes();

    DocumentTypeResponseDto getDocumentType(Long id);

    DocumentTypeResponseDto createDocumentType(DocumentTypeCreateRequestDto request);

    DocumentTypeResponseDto updateDocumentType(Long id, DocumentTypeUpdateRequestDto request);

    List<DocumentRequirementResponseDto> listDocumentRequirements(Long ruleId);

    DocumentRequirementResponseDto createDocumentRequirement(Long ruleId, DocumentRequirementCreateRequestDto request);

    DocumentRequirementResponseDto updateDocumentRequirement(Long ruleId, Long id, DocumentRequirementUpdateRequestDto request);
}
