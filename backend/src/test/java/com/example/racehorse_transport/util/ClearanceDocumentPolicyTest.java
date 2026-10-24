package com.example.racehorse_transport.util;

import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Regulation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClearanceDocumentPolicyTest {

    @Test
    void domesticBookingExcludesInternationalTypesAndNonVietnamRegulations() {
        Regulation healthCertificate = regulation("health_cert", null, "Việt Nam");
        Regulation powerOfAttorney = regulation("poa", null, "Việt Nam");
        Regulation mislabeledAsGeneral = regulation("import_permit", null, "Campuchia");
        Regulation borderRule = regulation("doc_ata", location(16), "Campuchia");
        Regulation countryRuleWithUnknownType = regulation("country_rule", null, "Lào");

        assertThat(List.of(healthCertificate, powerOfAttorney, mislabeledAsGeneral, borderRule, countryRuleWithUnknownType)
                .stream()
                .filter(regulation -> ClearanceDocumentPolicy.isApplicable(regulation, false)))
                .containsExactly(healthCertificate, powerOfAttorney);
    }

    @Test
    void internationalBookingKeepsApplicableInternationalRegulations() {
        Regulation importPermit = regulation("import_permit", location(16), "Campuchia");

        assertThat(ClearanceDocumentPolicy.isApplicable(importPermit, true)).isTrue();
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

    private Regulation regulation(String type, Location location, String country) {
        Regulation regulation = new Regulation();
        regulation.setDocumentType(type);
        regulation.setLocationID(location);
        regulation.setCountry(country);
        return regulation;
    }

    private Location location(Integer id) {
        Location location = new Location();
        location.setId(id);
        return location;
    }
}
