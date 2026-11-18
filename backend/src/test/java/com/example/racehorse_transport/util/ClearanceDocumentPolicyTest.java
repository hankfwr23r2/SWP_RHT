package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.dto.booking.DocumentRequirementSnapshotDto;
import com.example.racehorse_transport.entity.DocumentRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClearanceDocumentPolicyTest {

    @Test
    void snapshotKeepsAgreedNamesAndAddsGateInfoOnlyForTheBookingGate() {
        Location mocBai = gate(16, "Mộc Bài", "KH");
        Location tinhBien = gate(18, "Tịnh Biên", "KH");
        List<DocumentRequirement> active = List.of(
                requirement("poa", "Giấy ủy quyền", null, "ALL", "ALL"),
                requirement("customs_declaration", "Tờ khai hải quan", null, "INTERNATIONAL", "KH"),
                requirement("customs_declaration", "Tờ khai Mộc Bài", mocBai, "INTERNATIONAL", "KH"),
                requirement("customs_declaration", "Tờ khai Tịnh Biên", tinhBien, "INTERNATIONAL", "KH"));
        List<DocumentRequirementSnapshotDto> snapshot = List.of(
                snapshot("CLEARANCE", "customs_declaration", "Tờ khai hải quan"),
                snapshot("CLEARANCE", "poa", "Giấy ủy quyền"),
                snapshot("HORSE", "passport", "Hộ chiếu"));

        List<Map<String, Object>> items = ClearanceDocumentPolicy.buildItems(active, true, mocBai, "KH", snapshot);

        assertThat(items).extracting(item -> item.get("type")).containsExactly("customs_declaration", "poa");
        assertThat(items.get(0)).containsEntry("label", "Tờ khai hải quan").containsEntry("gateName", "Mộc Bài")
                .containsEntry("gateLocationId", 16);
        assertThat(items.get(1)).doesNotContainKey("gateName");
    }

    @Test
    void unknownGateListsEveryGateOfTheDestinationCountry() {
        List<DocumentRequirement> active = List.of(
                requirement("import_permit", "Giấy phép Mộc Bài", gate(16, "Mộc Bài", "KH"), "INTERNATIONAL", "KH"),
                requirement("import_permit", "Giấy phép Lao Bảo", gate(24, "Lao Bảo", "LA"), "INTERNATIONAL", "LA"));

        List<Map<String, Object>> items = ClearanceDocumentPolicy.buildItems(active, true, null, "KH",
                List.of(snapshot("CLEARANCE", "import_permit", "Giấy phép nhập khẩu")));

        assertThat(items).extracting(item -> item.get("gateName")).containsExactly("Mộc Bài");
    }

    @Test
    void legacyDomesticBookingWithoutSnapshotSkipsInternationalAndGateRows() {
        List<DocumentRequirement> active = List.of(
                requirement("health_cert", "Kiểm dịch", null, "ALL", "ALL"),
                requirement("poa", "Ủy quyền", null, "ALL", "ALL"),
                requirement("import_permit", "Giấy phép", null, "INTERNATIONAL", "KH"),
                requirement("customs_declaration", "Tờ khai Mộc Bài", gate(16, "Mộc Bài", "KH"), "INTERNATIONAL", "KH"));

        List<Map<String, Object>> items = ClearanceDocumentPolicy.buildItems(active, false, null, null, null);

        assertThat(items).extracting(item -> item.get("type")).containsExactly("health_cert", "poa");
    }

    @Test
    void legacyInternationalBookingWithoutSnapshotUsesGateRowNames() {
        Location mocBai = gate(16, "Mộc Bài", "KH");
        List<DocumentRequirement> active = List.of(
                requirement("customs_declaration", "Tờ khai hải quan", null, "INTERNATIONAL", "KH"),
                requirement("customs_declaration", "Tờ khai Mộc Bài", mocBai, "INTERNATIONAL", "KH"),
                requirement("quarantine_cert", "Kiểm dịch Mộc Bài", mocBai, "INTERNATIONAL", "KH"));

        List<Map<String, Object>> items = ClearanceDocumentPolicy.buildItems(active, true, mocBai, "KH", null);

        assertThat(items).extracting(item -> item.get("label")).containsExactly("Tờ khai Mộc Bài", "Kiểm dịch Mộc Bài");
    }

    @Test
    void domesticBookingFiltersLegacyInternationalClearanceItems() {
        Map<String, Object> domesticItem = Map.of("type", "health_cert", "status", "done");
        Map<String, Object> customsItem = Map.of("type", "customs_declaration", "status", "todo");
        Map<String, Object> borderItem = Map.of("type", "legacy_border_doc", "gateLocationId", 16);
        Map<String, Object> legacyClearance = Map.of(
                "items", List.of(domesticItem, customsItem, borderItem),
                "horsesCleared", List.of("Horse-1"));

        Object filtered = ClearanceDocumentPolicy.forBooking(legacyClearance, false);

        assertThat(filtered).isInstanceOf(Map.class);
        Map<?, ?> clearance = (Map<?, ?>) filtered;
        assertThat(clearance.get("horsesCleared")).isEqualTo(List.of("Horse-1"));
        assertThat(clearance.get("items")).isEqualTo(List.of(domesticItem));
    }

    @Test
    void internationalBookingPreservesAllClearanceItems() {
        Map<String, Object> internationalItem = Map.of("type", "import_permit");
        Map<String, Object> clearance = Map.of("items", List.of(internationalItem));

        assertThat(ClearanceDocumentPolicy.forBooking(clearance, true)).isSameAs(clearance);
    }

    private DocumentRequirement requirement(String type, String name, Location gate, String tripType, String country) {
        DocumentRequirement requirement = new DocumentRequirement();
        requirement.setDocumentGroup("CLEARANCE");
        requirement.setDocumentType(type);
        requirement.setDocumentName(name);
        requirement.setLocation(gate);
        requirement.setTripType(tripType);
        requirement.setCountry(country);
        requirement.setRequired(true);
        requirement.setStatus("ACTIVE");
        return requirement;
    }

    private DocumentRequirementSnapshotDto snapshot(String group, String type, String name) {
        return DocumentRequirementSnapshotDto.builder().documentGroup(group).documentType(type).documentName(name).build();
    }

    private Location gate(Integer id, String name, String destinationCountry) {
        Location location = new Location();
        location.setId(id);
        location.setLocationName(name);
        location.setLocationType("BorderGate");
        location.setDestinationCountry(destinationCountry);
        return location;
    }
}
