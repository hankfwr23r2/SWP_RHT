package com.example.racehorse_transport.controller;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class CustomerFlowTest {

    private static final String ADDRESS = """
            {"label":"Home","ward":"Phường Bến Nghé","addressLine":"Test address","provinceCode":"VN-79","countryCode":"VN","defaultAddress":true}
            """;

    @Autowired private MockMvc mvc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;

    @Test
    void customerUpdatesOwnProfileAndManagesAddress() throws Exception {
        String token = customerToken();
        mvc.perform(get("/customers/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mvc.perform(put("/customers/me").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerType":"PERSON","fullName":"Customer Updated","countryCode":"VN","userId":99999}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fullName").value("Customer Updated"));
        long id = createAddress(token);
        mvc.perform(put("/customers/me/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(ADDRESS.replace("Home", "Farm")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.label").value("Farm"));
        mvc.perform(get("/customers/me/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].defaultAddress").value(true));
        mvc.perform(delete("/customers/me/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
        mvc.perform(get("/customers/me/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void wardIsRequiredButStreetIsOptional() throws Exception {
        String token = customerToken();
        mvc.perform(post("/customers/me/addresses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Trại","addressLine":"12 Đường Đua","provinceCode":"VN-79","countryCode":"VN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.ward").exists());
        mvc.perform(post("/customers/me/addresses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"Trại","ward":"Phường Hiệp An","provinceCode":"VN-79","countryCode":"VN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.addressLine").doesNotExist());
    }

    @Test
    void forbidsStaffAndHidesAddressesBelongingToAnotherCustomer() throws Exception {
        long id = createAddress(customerToken());
        String other = customerToken();
        mvc.perform(delete("/customers/me/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, other))
                .andExpect(status().isNotFound());
        mvc.perform(put("/customers/me/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, other)
                        .contentType(MediaType.APPLICATION_JSON).content(ADDRESS)).andExpect(status().isNotFound());
        String username = data.unique("profile-staff-a2");
        data.staff(username, "ADMIN");
        mvc.perform(get("/customers/me").header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwt.generateToken(username, List.of("ADMIN"), 0)))
                .andExpect(status().isForbidden());
    }

    private long createAddress(String token) throws Exception {
        String response = mvc.perform(post("/customers/me/addresses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(ADDRESS))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.id")).longValue();
    }

    private String customerToken() {
        String username = data.unique("customer-a2");
        data.customer(username);
        return "Bearer " + jwt.generateToken(username, List.of("CUSTOMER"), 0);
    }
}
