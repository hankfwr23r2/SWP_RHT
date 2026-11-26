package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.repository.DocumentTypeRepository;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class RegulationControllerTest {

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DocumentTypeRepository types;

    @Test
    void specialistCanReadButOnlyAdminCanWrite() throws Exception {
        String specialist = staffToken("SPECIALIST");
        String admin = staffToken("ADMIN");
        String payload = """
                {"code":"A4_TEST_TYPE","name":"Test certificate","ownerType":"HORSE"}
                """;
        mvc.perform(get("/document-types").header(HttpHeaders.AUTHORIZATION, specialist))
                .andExpect(status().isOk());
        mvc.perform(post("/document-types").header(HttpHeaders.AUTHORIZATION, specialist)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mvc.perform(post("/document-types").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.ownerType").value("HORSE"));
        mvc.perform(post("/country-rules/preview").header(HttpHeaders.AUTHORIZATION, admin)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bookingType":"LOCAL","pickupCountryCode":"VN","deliveryCountryCode":"VN",
                                 "requestedPickupDate":"2026-12-01"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsOverlappingEffectivePeriod() throws Exception {
        mvc.perform(post("/country-rules").header(HttpHeaders.AUTHORIZATION, staffToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"countryCode":"KH","direction":"EXPORT","permitLeadDays":14,
                                 "certificateLeadDays":7,"validFrom":"2026-01-01"}
                                """))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.validFrom").isString());
    }

    @Test
    void authenticatedCustomerCanPreviewButCannotReadTheAdminCatalog() throws Exception {
        String username = data.unique("regulation-customer");
        data.customer(username);
        String token = "Bearer " + jwt.generateToken(username, List.of("CUSTOMER"));
        mvc.perform(get("/country-rules").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/country-rules/preview").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bookingType":"EXPORT","pickupCountryCode":"VN","deliveryCountryCode":"KH",
                                 "requestedPickupDate":"2026-12-01"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rule.countryCode").value("KH"))
                .andExpect(jsonPath("$.data.leadDays").value(21))
                .andExpect(jsonPath("$.data.requirements[0].ownerType").isString());
    }

    @Test
    void mocBaiRequirementsReplaceGeneralRequirementsWithTheSameDocumentType() throws Exception {
        String username = data.unique("regulation-export");
        data.customer(username);
        String token = "Bearer " + jwt.generateToken(username, List.of("CUSTOMER"));
        Long gate = jdbc.queryForObject("SELECT id FROM location WHERE code = 'GATE-MOCBAI'", Long.class);

        mvc.perform(post("/country-rules/preview").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"bookingType":"EXPORT","pickupCountryCode":"VN","deliveryCountryCode":"KH",
                                 "requestedPickupDate":"2026-12-01","gateLocationId":%d}
                                """.formatted(gate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirementsDependOnGate").value(false))
                .andExpect(jsonPath("$.data.requirements.length()").value(7))
                .andExpect(jsonPath("$.data.requirements[?(@.gateLocationId == " + gate + ")].documentTypeCode")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("CUSTOMS_DECLARATION", "IMPORT_PERMIT", "QUARANTINE_CLEARANCE")));
    }

    @Test
    void rejectsDuplicateRequirementWithNullGate() throws Exception {
        Long ruleId = jdbc.queryForObject("""
                SELECT id FROM country_rule WHERE country_code = 'VN' AND direction = 'INTERPROVINCE'
                """, Long.class);
        Long typeId = types.findByCode("HEALTH_CERT").orElseThrow().getId();
        mvc.perform(post("/country-rules/{ruleId}/document-requirements", ruleId)
                        .header(HttpHeaders.AUTHORIZATION, staffToken("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"documentTypeId":%d,"submitterType":"COMPANY","mandatory":true}
                                """.formatted(typeId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.documentTypeId").isString());
    }

    private String staffToken(String role) {
        String username = data.unique("regulation-a4");
        data.staff(username, role);
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
