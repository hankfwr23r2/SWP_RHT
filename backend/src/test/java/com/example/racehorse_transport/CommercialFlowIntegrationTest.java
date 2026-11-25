package com.example.racehorse_transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.policy.QuotationPolicy;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.service.PricingRouteService;
import com.example.racehorse_transport.support.TestData;
import com.example.racehorse_transport.util.VnpayUtil;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;

// Đoạn thương mại của FullFlowIntegrationTest, đi qua API như người dùng: đặt đơn, tiếp nhận kèm chuyên viên,
// duyệt giấy và bảo hiểm, báo giá, cọc. Lớp riêng vì lập báo giá gọi bản đồ ngoài transaction.
@PostgresIntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"vnpay.tmn-code=FLOWTEST", "vnpay.hash-secret=flow-test-signature"})
class CommercialFlowIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData data;
    @Autowired private JwtUtils jwt;
    @MockitoBean private CloudinaryClient cloudinary;
    @MockitoBean private PricingRouteService maps;

    private String customer;
    private String manager;
    private String specialist;
    private String otherSpecialist;
    private Staff specialistStaff;
    private long horse;

    @BeforeEach
    void setUp() {
        String username = data.unique("flow.customer");
        data.customer(username);
        customer = token(username, "CUSTOMER");
        manager = token(data.staff(data.unique("flow.manager"), "MANAGER").getUser().getUsername(), "MANAGER");
        specialistStaff = data.staff(data.unique("flow.specialist"), "SPECIALIST");
        specialist = token(specialistStaff.getUser().getUsername(), "SPECIALIST");
        otherSpecialist = token(data.staff(data.unique("flow.other"), "SPECIALIST").getUser().getUsername(), "SPECIALIST");
        horse = jdbc.queryForObject("""
                INSERT INTO horse (owner_customer_id, name, sex, horse_type_id, status)
                SELECT u.id, ?, 'MARE', (SELECT min(id) FROM horse_type), 'ACTIVE' FROM app_user u WHERE u.username = ? RETURNING id
                """, Long.class, data.unique("Ngựa"), username);
        when(cloudinary.uploadFile(any(), anyString())).thenAnswer(call -> "https://example.test/" + java.util.UUID.randomUUID() + ".pdf");
        when(maps.route(any(), any(), anyInt(), anyInt())).thenReturn(new PricingRouteService.RoadEstimate(new BigDecimal("45"), 3600));
    }

    @Test
    void customerOrderGoesFromSubmissionToAPaidDeposit() throws Exception {
        long booking = submit();
        assertThat(bookingStatus(booking)).isEqualTo("PENDING_INTAKE");

        // Chuyên viên không tự tiếp nhận; quản lý phải chọn người phụ trách
        String assign = "{\"specialistId\":%d}".formatted(specialistStaff.getUserId());
        act(specialist, "/bookings/" + booking + "/intake", assign).andExpect(status().isForbidden());
        act(manager, "/bookings/" + booking + "/intake", "{}").andExpect(status().isBadRequest());
        act(manager, "/bookings/" + booking + "/intake", assign).andExpect(status().isOk());
        assertThat(bookingStatus(booking)).isEqualTo("UNDER_REVIEW");

        submitAndApproveCustomerDocuments(booking);
        confirmInsurance(booking);
        act(otherSpecialist, "/bookings/" + booking + "/start-quoting", "{}").andExpect(status().isForbidden());
        act(specialist, "/bookings/" + booking + "/start-quoting", "{}").andExpect(status().isOk());

        long quotation = id(act(manager, "/bookings/" + booking + "/quotations", "{}"));
        act(manager, "/quotations/" + quotation + "/send", "{}").andExpect(status().isOk());
        assertThat(bookingStatus(booking)).isEqualTo("AWAITING_DEPOSIT");
        act(customer, "/quotations/" + quotation + "/accept", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        payDeposit(booking);
        assertThat(bookingStatus(booking)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT waybill_number FROM booking WHERE id = ?", String.class, booking)).isNotBlank();
        assertThat(jdbc.queryForList("SELECT to_status FROM booking_status_history WHERE booking_id = ? ORDER BY id", String.class, booking))
                .containsExactly("PENDING_INTAKE", "UNDER_REVIEW", "QUOTING", "AWAITING_DEPOSIT", "CONFIRMED");
    }

    // Đồng Nai đi TP.HCM: cùng hub nên chạy thẳng, không có chuyến trục
    private long submit() throws Exception {
        String party = "{\"bookingRole\":\"%s\",\"fullName\":\"Người thử\",\"phone\":\"0901234567\",\"address\":\"Đồng Nai\"}";
        String parties = String.join(",", List.of("SHIPPER", "CONSIGNEE", "PAYER", "EMERGENCY_CONTACT").stream().map(party::formatted).toList());
        String address = "{\"addressType\":\"%s\",\"addressLine\":\"Điểm thử\",\"provinceCode\":\"%s\",\"countryCode\":\"VN\",\"latitude\":%s,"
                + "\"longitude\":%s,\"contactName\":\"Liên hệ\",\"contactPhone\":\"0901234567\"}";
        LocalDate pickup = LocalDate.now(QuotationPolicy.BUSINESS_ZONE).plusDays(20);
        return id(act(customer, "/bookings", """
                {"bookingType":"INTERPROVINCE","requestedPickupDate":"%s","acceptedServiceTerms":true,"parties":[%s],"addresses":[%s,%s],
                 "horses":[{"horseId":%d,"stallTypeCode":"STANDARD","insurance":{"coverageOption":"NONE","acceptedWaiver":true}}]}
                """.formatted(pickup, parties, address.formatted("PICKUP", "VN-75", "10.78", "106.95"),
                address.formatted("DELIVERY", "VN-79", "10.80", "106.74"), horse)));
    }

    private void submitAndApproveCustomerDocuments(long booking) throws Exception {
        String checklist = read(customer, "/bookings/" + booking + "/documents/my-checklist");
        List<Map<String, Object>> items = JsonPath.read(checklist,
                "$.data.items[?(@.submitterType == 'CUSTOMER' && @.requirement.mandatory == true)]");
        assertThat(items).isNotEmpty();
        for (var item : items) {
            var upload = multipart("/bookings/{id}/documents/upload", booking)
                    .file(new MockMultipartFile("file", "giay.pdf", "application/pdf", "%PDF-1.4".getBytes()));
            upload.param("requirementId", ((Map<?, ?>) item.get("requirement")).get("id").toString());
            upload.param("expiryDate", LocalDate.now(QuotationPolicy.BUSINESS_ZONE).plusYears(1).toString());
            if (item.get("bookingHorseId") != null) {
                upload.param("bookingHorseId", item.get("bookingHorseId").toString());
            }
            upload.header(HttpHeaders.AUTHORIZATION, customer);
            long document = id(mvc.perform(upload));
            act(otherSpecialist, "/bookings/" + booking + "/documents/" + document + "/approve", "{}").andExpect(status().isForbidden());
            act(specialist, "/bookings/" + booking + "/documents/" + document + "/approve", "{}").andExpect(status().isOk());
        }
    }

    private void confirmInsurance(long booking) throws Exception {
        for (Long insurance : jdbc.queryForList("""
                SELECT i.id FROM horse_insurance i JOIN booking_horse h ON h.id = i.booking_horse_id WHERE h.booking_id = ?
                """, Long.class, booking)) {
            act(specialist, "/bookings/" + booking + "/insurances/" + insurance + "/confirm", "{}").andExpect(status().isOk());
        }
    }

    private void payDeposit(long booking) throws Exception {
        String payments = read(customer, "/bookings/" + booking + "/payments");
        long payment = ((Number) JsonPath.read(payments, "$.data[0].id")).longValue();
        BigDecimal amount = new BigDecimal(JsonPath.read(payments, "$.data[0].amount").toString());
        String url = JsonPath.read(act(customer, "/payments/" + payment + "/checkout", "{}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.data.paymentUrl");
        var params = new HashMap<String, String>();
        params.put("vnp_TxnRef", UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("vnp_TxnRef"));
        params.put("vnp_TmnCode", "FLOWTEST");
        params.put("vnp_Amount", amount.multiply(BigDecimal.valueOf(100)).toBigIntegerExact().toString());
        params.put("vnp_PayDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(QuotationPolicy.BUSINESS_ZONE).format(Instant.now()));
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", String.valueOf(System.nanoTime() % 1_000_000_000));
        params.put("vnp_SecureHash", VnpayUtil.sign(params, "flow-test-signature"));
        var ipn = get("/payments/vnpay/ipn");
        params.forEach(ipn::param);
        mvc.perform(ipn).andExpect(status().isOk()).andExpect(jsonPath("$.RspCode").value("00"));
    }

    private ResultActions act(String token, String path, String body) throws Exception {
        return mvc.perform(post(path).header(HttpHeaders.AUTHORIZATION, token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String read(String token, String path) throws Exception {
        return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String bookingStatus(long booking) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE id = ?", String.class, booking);
    }

    private static long id(ResultActions result) throws Exception {
        var response = result.andReturn().getResponse();
        assertThat(response.getStatus()).as(response.getContentAsString()).isBetween(200, 201);
        return ((Number) JsonPath.read(response.getContentAsString(), "$.data.id")).longValue();
    }

    private String token(String username, String role) {
        return "Bearer " + jwt.generateToken(username, List.of(role));
    }
}
