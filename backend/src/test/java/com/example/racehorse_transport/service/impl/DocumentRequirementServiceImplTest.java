package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.DocumentRequirementRequest;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.service.AdminAuditWriter;

import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentRequirementServiceImplTest {
    @Mock
    private DocumentRequirementRepository repository;
    @Mock
    private AdminAuditWriter audit;

    private DocumentRequirementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentRequirementServiceImpl(repository, audit);
    }

    @Test
    void createWritesActorAndNewValueToAuditLog() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any(DocumentRequirement.class))).thenAnswer(invocation -> {
            DocumentRequirement saved = invocation.getArgument(0);
            saved.setId(12);
            return saved;
        });
        DocumentRequirementRequest request = request("CLEARANCE", "poa", "ALL", "ALL");

        service.create(request, "admin");

        verify(audit).record(org.mockito.ArgumentMatchers.eq("CREATE_DOCUMENT_REQUIREMENT"), any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.argThat(after -> "poa".equals(after.get("documentType"))));
    }

    @Test
    void updateAuditsChangedOldAndNewValues() {
        DocumentRequirement existing = new DocumentRequirement();
        existing.setId(4);
        existing.setDocumentGroup("CLEARANCE");
        existing.setDocumentType("poa");
        existing.setDocumentName("Tên cũ");
        existing.setTripType("ALL");
        existing.setCountry("ALL");
        existing.setRequired(true);
        existing.setStatus("ACTIVE");
        when(repository.findById(4)).thenReturn(Optional.of(existing));
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any(DocumentRequirement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DocumentRequirementRequest request = request("CLEARANCE", "poa", "ALL", "ALL");
        request.setDocumentName("Tên mới");

        service.update(4, request, "admin");

        verify(audit).record(org.mockito.ArgumentMatchers.eq("UPDATE_DOCUMENT_REQUIREMENT"), any(), any(), any(), any(), org.mockito.ArgumentMatchers.argThat(before -> "Tên cũ".equals(before.get("documentName"))), org.mockito.ArgumentMatchers.argThat(after -> "Tên mới".equals(after.get("documentName"))));
    }

    @Test
    void customsDocumentCannotBeConfiguredForDomesticTrips() {
        DocumentRequirementRequest request = request("CLEARANCE", "customs_declaration", "DOMESTIC", "VN");

        assertThatThrownBy(() -> service.create(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chỉ áp dụng cho chuyến quốc tế");
    }

    private DocumentRequirementRequest request(String group, String type, String tripType, String country) {
        DocumentRequirementRequest request = new DocumentRequirementRequest();
        request.setDocumentGroup(group);
        request.setDocumentType(type);
        request.setDocumentName("Giấy tờ " + type);
        request.setTripType(tripType);
        request.setCountry(country);
        request.setRequired(true);
        request.setStatus("ACTIVE");
        return request;
    }
}
