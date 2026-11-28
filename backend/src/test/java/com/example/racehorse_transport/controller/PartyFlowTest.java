package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class PartyFlowTest {

    private static final String PARTY = """
            {"partyType":"ORGANIZATION","displayName":" A2-Partner ","countryCode":"vn","status":"INACTIVE","id":99999}
            """;
    private static final String ROLE = """
            {"roleType":"INSURER","validFrom":"2026-11-23"}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;

    @Test
    void coordinatorManagesPartyAndRolePeriod() throws Exception {
        String token = staffToken("COORDINATOR");
        long id = createParty(token);
        mvc.perform(post("/parties/{id}/roles", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(ROLE))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.roleType").value("INSURER"));
        mvc.perform(put("/parties/{id}/roles/INSURER", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"validFrom":"2026-11-23","validTo":"2026-12-01"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.validTo").value("2026-12-01"));
        mvc.perform(put("/parties/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(PARTY.replace("A2-Partner", "Updated Partner")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("Updated Partner"));
        mvc.perform(get("/parties/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.roles[0].validTo").value("2026-12-01"));
        mvc.perform(get("/parties").header(HttpHeaders.AUTHORIZATION, staffToken("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotManagePartners() throws Exception {
        String customer = testData.unique("party.customer");
        testData.customer(customer);
        String token = "Bearer " + jwtUtils.generateToken(customer, List.of("CUSTOMER"));
        mvc.perform(get("/parties").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        mvc.perform(post("/parties").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(PARTY))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnknownCountryAndInvalidOrDuplicateRole() throws Exception {
        String token = staffToken("ADMIN");
        long id = createParty(token);
        mvc.perform(post("/parties").header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON)
                        .content(PARTY.replace("\"vn\"", "\"ZZ\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/parties/{id}/roles", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"roleType":"INSURER","validFrom":"2026-11-23","validTo":"2026-11-22"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/parties/{id}/roles", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(ROLE)).andExpect(status().isCreated());
        mvc.perform(post("/parties/{id}/roles", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(ROLE)).andExpect(status().isConflict());
    }

    private long createParty(String token) throws Exception {
        String response = mvc.perform(post("/parties").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(PARTY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.countryCode").value("VN"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private String staffToken(String role) {
        String username = testData.unique("party.staff");
        testData.staff(username, role);
        return "Bearer " + jwtUtils.generateToken(username, List.of(role));
    }
}
