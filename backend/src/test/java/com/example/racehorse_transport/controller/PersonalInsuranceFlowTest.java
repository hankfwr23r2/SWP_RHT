package com.example.racehorse_transport.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.AddressInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.HorseInput;
import com.example.racehorse_transport.dto.booking.BookingCreateRequestDto.PartyInput;
import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.dto.booking.InsuranceEvidenceRequestDto;
import com.example.racehorse_transport.dto.booking.PersonalInsuranceRevisionRequestDto;
import com.example.racehorse_transport.entity.enums.AddressRole;
import com.example.racehorse_transport.entity.enums.BookingPartyRole;
import com.example.racehorse_transport.entity.enums.BookingType;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.policy.BookingSubmissionPolicy;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.multipart.MultipartFile;

@PostgresIntegrationTest
@AutoConfigureMockMvc
class PersonalInsuranceFlowTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @Autowired private Clock clock;
    @MockitoBean private CloudinaryClient cloudinary;

    @Test
    void personalPoliciesRequireTheirOwnFilesAndOnlyTheAssignedSpecialistCanReviewThem() throws Exception {
        var customer = data.customer(data.unique("f2-customer"));
        var assigned = data.staff(data.unique("f2-assigned"), "SPECIALIST");
        var other = data.staff(data.unique("f2-other"), "SPECIALIST");
        long customerId = customer.getUserId();
        String ownerToken = token(customer.getUser().getUsername(), "CUSTOMER");
        String assignedToken = token(assigned.getUser().getUsername(), "SPECIALIST");
        String otherToken = token(other.getUser().getUsername(), "SPECIALIST");
        LocalDate pickup = BookingSubmissionPolicy.businessDate(clock.instant()).plusDays(10);
        String insurerName = data.unique("f2-insurer");
        var personal = new InsuranceDeclarationRequestDto(InsuranceCoverageOption.PERSONAL,
                "OLD-01", pickup, pickup.plusDays(5), insurerName, null);
        var arranged = new InsuranceDeclarationRequestDto(InsuranceCoverageOption.ARRANGED, null, null, null, null, null);
        long horseWithRevision = horse(customerId);
        long horseWithoutFile = horse(customerId);
        var request = booking(pickup, List.of(line(horseWithRevision, personal), line(horse(customerId), arranged),
                line(horseWithoutFile, personal)));
        var firstFile = pdf("insuranceFiles", "original.pdf");
        var lastFile = pdf("insuranceFiles", "second.pdf");
        when(cloudinary.uploadFile(any(), eq("documents/insurance"))).thenAnswer(invocation -> {
            MultipartFile file = invocation.getArgument(0);
            return "https://example.test/" + data.unique("f2-file") + "/" + file.getOriginalFilename();
        });

        mvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/bookings").file(requestPart(request)).file(firstFile)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking WHERE customer_id = ?", Long.class, customerId))
                .isZero();

        var created = mvc.perform(multipart("/bookings").file(requestPart(request)).file(firstFile).file(lastFile)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isCreated());
        long bookingId = ((Number) JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
        mvc.perform(post("/bookings/{id}/intake", bookingId).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(java.util.Map.of("specialistId", assigned.getUserId())))
                        .header(HttpHeaders.AUTHORIZATION, token("manager.dev", "MANAGER")))
                .andExpect(status().isOk());
        long firstInsurance = insuranceId(bookingId, horseWithRevision);
        long lastInsurance = insuranceId(bookingId, horseWithoutFile);
        long firstLine = bookingHorseId(firstInsurance);
        long lastLine = bookingHorseId(lastInsurance);
        files(bookingId, assignedToken)
                .andExpect(jsonPath(policyNames(firstLine), contains("original.pdf")))
                .andExpect(jsonPath(policyNames(lastLine), contains("second.pdf")));

        var evidence = new InsuranceEvidenceRequestDto(insurerName, BigDecimal.valueOf(1_000_000), null, "VND", null);
        insuranceAction(bookingId, lastInsurance, "record-evidence", assignedToken, evidence).andExpect(status().isOk());
        // Mô phỏng bảo hiểm khai trước khi hệ thống bắt buộc tệp.
        jdbc.update("DELETE FROM document WHERE horse_insurance_id = ?", lastInsurance);
        insuranceAction(bookingId, lastInsurance, "confirm", assignedToken, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chưa có tệp hợp đồng bảo hiểm tự mua."));
        insuranceAction(bookingId, firstInsurance, "confirm", otherToken, null).andExpect(status().isForbidden());
        insuranceAction(bookingId, firstInsurance, "request-revision", otherToken, java.util.Map.of("reason", "Đổi hợp đồng"))
                .andExpect(status().isForbidden());
        insuranceAction(bookingId, firstInsurance, "request-revision", assignedToken, java.util.Map.of("reason", "Đổi hợp đồng"))
                .andExpect(status().isOk());

        var revision = new PersonalInsuranceRevisionRequestDto("Hãng bảo hiểm mới", "NEW-02", pickup, pickup.plusDays(8));
        var reviseUrl = "/bookings/{id}/insurances/{insuranceId}/revise";
        mvc.perform(multipart(reviseUrl, bookingId, firstInsurance).file(requestPart(revision))
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.file").exists());
        var wrongFormat = new MockMultipartFile("file", "wrong.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[] {1});
        mvc.perform(multipart(reviseUrl, bookingId, firstInsurance).file(requestPart(revision)).file(wrongFormat)
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.file").exists());
        mvc.perform(get("/bookings/{id}", bookingId).header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[?(@.id == " + firstLine + ")].insurance.status", contains("REVISION_REQUESTED")))
                .andExpect(jsonPath("$.data.horses[?(@.id == " + firstLine + ")].insurance.policyNumber", contains("OLD-01")))
                .andExpect(jsonPath("$.data.horses[?(@.id == " + firstLine + ")].insurance.insurerName", contains(insurerName)));

        mvc.perform(multipart(reviseUrl, bookingId, firstInsurance).file(requestPart(revision)).file(pdf("file", "updated.pdf"))
                        .header(HttpHeaders.AUTHORIZATION, ownerToken))
                .andExpect(status().isOk());
        files(bookingId, assignedToken).andExpect(jsonPath(policyNames(firstLine), contains("updated.pdf")));
        var revisedEvidence = new InsuranceEvidenceRequestDto(
                "Hãng bảo hiểm mới", BigDecimal.valueOf(1_000_000), null, "VND", null);
        insuranceAction(bookingId, firstInsurance, "record-evidence", assignedToken, revisedEvidence).andExpect(status().isOk());
        insuranceAction(bookingId, firstInsurance, "confirm", assignedToken, null).andExpect(status().isOk());
        mvc.perform(get("/bookings/{id}/review-detail", bookingId).header(HttpHeaders.AUTHORIZATION, assignedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.horses[?(@.id == " + firstLine + ")].insurance.status", contains("CONFIRMED")))
                .andExpect(jsonPath("$.data.horses[?(@.id == " + firstLine + ")].insurance.insurerName", contains("Hãng bảo hiểm mới")));
    }

    private ResultActions insuranceAction(long bookingId, long insuranceId, String action, String token, Object body)
            throws Exception {
        var request = post("/bookings/{id}/insurances/{insuranceId}/{action}", bookingId, insuranceId, action)
                .header(HttpHeaders.AUTHORIZATION, token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        return mvc.perform(request);
    }

    private ResultActions files(long bookingId, String token) throws Exception {
        return mvc.perform(get("/bookings/{id}/documents", bookingId).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());
    }

    private String policyNames(long bookingHorseId) {
        return "$.data[?(@.typeCode == 'INSURANCE_POLICY' && @.bookingHorseId == " + bookingHorseId + ")].fileName";
    }

    private long horse(long customerId) {
        return jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                VALUES (?, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE') RETURNING id
                """, Long.class, customerId, data.unique("f2-horse"));
    }

    private long insuranceId(long bookingId, long horseId) {
        return jdbc.queryForObject("""
                SELECT i.id FROM horse_insurance i JOIN booking_horse h ON h.id = i.booking_horse_id
                WHERE h.booking_id = ? AND h.horse_id = ?
                """, Long.class, bookingId, horseId);
    }

    private long bookingHorseId(long insuranceId) {
        return jdbc.queryForObject("SELECT booking_horse_id FROM horse_insurance WHERE id = ?", Long.class, insuranceId);
    }

    private BookingCreateRequestDto booking(LocalDate pickup, List<HorseInput> horses) {
        var parties = Arrays.stream(BookingPartyRole.values())
                .map(role -> new PartyInput(role, role.name(), "0901234567", null, null, null, "Địa chỉ thử"))
                .toList();
        return new BookingCreateRequestDto(BookingType.LOCAL, null, pickup, null, parties,
                List.of(address(AddressRole.PICKUP), address(AddressRole.DELIVERY)), horses, true);
    }

    private static AddressInput address(AddressRole role) {
        return new AddressInput(role, "Địa chỉ thử", "VN-79", "VN", new BigDecimal("10.8"), new BigDecimal("106.8"),
                "Người liên hệ", "0901234567", null, null);
    }

    private static HorseInput line(long horse, InsuranceDeclarationRequestDto insurance) {
        return new HorseInput(horse, "STANDARD", null, null, false, List.of(), insurance);
    }

    private MockMultipartFile requestPart(Object request) throws Exception {
        return new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(request));
    }

    private static MockMultipartFile pdf(String part, String name) {
        return new MockMultipartFile(part, name, MediaType.APPLICATION_PDF_VALUE, new byte[] {'%', 'P', 'D', 'F', '-', '1', '.', '7'});
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
