package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.DocumentRequirementRequestDto;
import com.example.racehorse_transport.entity.DocumentRequirement;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.DocumentRequirementRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AdminAuditService;
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
    private AdminAuditService audit;
    @Mock
    private StaffRepository staffRepository;
    @Mock
    private LocationRepository locationRepository;

    private DocumentRequirementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DocumentRequirementServiceImpl(repository, staffRepository, locationRepository, audit);
    }

    @Test
    void createWritesActorAndNewValueToAuditLog() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any(DocumentRequirement.class))).thenAnswer(invocation -> {
            DocumentRequirement saved = invocation.getArgument(0);
            saved.setId(12);
            return saved;
        });
        when(staffRepository.findByUser_Username("admin")).thenReturn(Optional.of(admin()));
        DocumentRequirementRequestDto request = request("CLEARANCE", "poa", "ALL", "ALL");

        var response = service.create(request, "admin");

        assertThat(response.getUpdatedBy()).isEqualTo("admin");
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
        DocumentRequirementRequestDto request = request("CLEARANCE", "poa", "ALL", "ALL");
        request.setDocumentName("Tên mới");
        when(staffRepository.findByUser_Username("admin")).thenReturn(Optional.of(admin()));

        var response = service.update(4, request, "admin");

        assertThat(response.getUpdatedBy()).isEqualTo("admin");

        verify(audit).record(org.mockito.ArgumentMatchers.eq("UPDATE_DOCUMENT_REQUIREMENT"), any(), any(), any(), any(), org.mockito.ArgumentMatchers.argThat(before -> "Tên cũ".equals(before.get("documentName"))), org.mockito.ArgumentMatchers.argThat(after -> "Tên mới".equals(after.get("documentName"))));
    }

    @Test
    void customsDocumentCannotBeConfiguredForDomesticTrips() {
        DocumentRequirementRequestDto request = request("CLEARANCE", "customs_declaration", "DOMESTIC", "VN");

        assertThatThrownBy(() -> service.create(request, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chỉ áp dụng cho chuyến quốc tế");
    }

    @Test
    void createRejectsActorWithoutStaffRecord() {
        when(repository.findAll()).thenReturn(List.of());
        when(staffRepository.findByUser_Username("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("CLEARANCE", "poa", "ALL", "ALL"), "ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không tìm thấy nhân viên");
    }

    @Test
    void gateRequirementIsStoredWithBorderGateAndReturnsItsName() {
        when(repository.findAll()).thenReturn(List.of());
        when(repository.save(any(DocumentRequirement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(staffRepository.findByUser_Username("admin")).thenReturn(Optional.of(admin()));
        when(locationRepository.findById(16)).thenReturn(Optional.of(gate(16, "BorderGate", "KH")));
        DocumentRequirementRequestDto request = request("CLEARANCE", "customs_declaration", "INTERNATIONAL", "KH");
        request.setLocationId(16);

        var response = service.create(request, "admin");

        assertThat(response.getLocationId()).isEqualTo(16);
        assertThat(response.getLocationName()).isEqualTo("Cửa khẩu 16");
    }

    @Test
    void gateRequirementRejectsNonGateWrongCountryAndDomesticScope() {
        when(locationRepository.findById(5)).thenReturn(Optional.of(gate(5, "Warehouse", null)));
        when(locationRepository.findById(24)).thenReturn(Optional.of(gate(24, "BorderGate", "LA")));
        DocumentRequirementRequestDto warehouse = request("CLEARANCE", "customs_declaration", "INTERNATIONAL", "KH");
        warehouse.setLocationId(5);
        DocumentRequirementRequestDto wrongCountry = request("CLEARANCE", "customs_declaration", "INTERNATIONAL", "KH");
        wrongCountry.setLocationId(24);
        DocumentRequirementRequestDto horseDoc = request("HORSE", "passport", "ALL", "ALL");
        horseDoc.setLocationId(24);

        assertThatThrownBy(() -> service.create(warehouse, "admin")).hasMessageContaining("không phải cửa khẩu");
        assertThatThrownBy(() -> service.create(wrongCountry, "admin")).hasMessageContaining("không dẫn tới quốc gia");
        assertThatThrownBy(() -> service.create(horseDoc, "admin")).hasMessageContaining("Chỉ giấy tờ thông quan");
    }

    @Test
    void sameTypeMayExistOncePerGate() {
        DocumentRequirement general = new DocumentRequirement();
        general.setId(1);
        general.setDocumentGroup("CLEARANCE");
        general.setDocumentType("customs_declaration");
        general.setTripType("INTERNATIONAL");
        general.setCountry("KH");
        DocumentRequirement atGate = new DocumentRequirement();
        atGate.setId(2);
        atGate.setDocumentGroup("CLEARANCE");
        atGate.setDocumentType("customs_declaration");
        atGate.setTripType("INTERNATIONAL");
        atGate.setCountry("KH");
        atGate.setLocation(gate(16, "BorderGate", "KH"));
        when(repository.findAll()).thenReturn(List.of(general, atGate));
        when(locationRepository.findById(16)).thenReturn(Optional.of(gate(16, "BorderGate", "KH")));
        DocumentRequirementRequestDto duplicate = request("CLEARANCE", "customs_declaration", "INTERNATIONAL", "KH");
        duplicate.setLocationId(16);

        assertThatThrownBy(() -> service.create(duplicate, "admin")).hasMessageContaining("đã tồn tại");
    }

    private Location gate(int id, String type, String destinationCountry) {
        Location location = new Location();
        location.setId(id);
        location.setLocationType(type);
        location.setLocationName("Cửa khẩu " + id);
        location.setDestinationCountry(destinationCountry);
        return location;
    }

    private Staff admin() {
        User user = new User();
        user.setId(1);
        user.setUsername("admin");
        Staff staff = new Staff();
        staff.setId(1);
        staff.setUser(user);
        staff.setRole("ADMIN");
        return staff;
    }

    private DocumentRequirementRequestDto request(String group, String type, String tripType, String country) {
        DocumentRequirementRequestDto request = new DocumentRequirementRequestDto();
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
