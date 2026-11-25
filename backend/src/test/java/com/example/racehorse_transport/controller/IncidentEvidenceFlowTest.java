package com.example.racehorse_transport.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.client.CloudinaryClient;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.security.JwtUtils;
import com.example.racehorse_transport.support.TestData;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// B4: tổ xe gửi biên lai kèm chi phí và ảnh hiện trường kèm sự cố; quản lý xem lại khi duyệt
@PostgresIntegrationTest
@AutoConfigureMockMvc
@Transactional
class IncidentEvidenceFlowTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestData testData;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private EntityManager entityManager;
    @MockitoBean private CloudinaryClient cloudinary;

    private String driver;
    private String manager;
    private long incident;
    private long toll;
    private long vet;

    @BeforeEach
    void setUp() {
        Staff driverStaff = testData.staff(testData.unique("evidence.driver"), "DRIVER");
        driver = token(driverStaff, "DRIVER");
        manager = token(testData.staff(testData.unique("evidence.manager"), "MANAGER"), "MANAGER");
        long booking = jdbc.queryForObject("SELECT id FROM booking WHERE code = 'DEV-DIRECT-001'", Long.class);
        incident = jdbc.queryForObject("""
                INSERT INTO incident (booking_id, incident_type, severity, cause_type, title, description, reported_by, status)
                VALUES (?, 'HEALTH', 'HIGH', 'HORSE_CONDITION', 'Ngựa sốt', 'Sốt 39,5 độ', ?, 'REPORTED') RETURNING id""",
                Long.class, booking, driverStaff.getUserId());
        toll = jdbc.queryForObject("""
                INSERT INTO cost_item (category, description, amount, currency_code, payer_type, booking_id, incurred_at, recorded_by)
                VALUES ('TOLL', 'Phí cao tốc', 300000, 'VND', 'COMPANY', ?, now(), ?) RETURNING id""", Long.class, booking, driverStaff.getUserId());
        vet = jdbc.queryForObject("""
                INSERT INTO cost_item (category, description, amount, currency_code, payer_type, booking_id, incident_id, incurred_at, recorded_by)
                VALUES ('VET', 'Khám thú y', 1500000, 'VND', 'CUSTOMER', ?, ?, now(), ?) RETURNING id""", Long.class, booking, incident, driverStaff.getUserId());
        Mockito.when(cloudinary.uploadFile(ArgumentMatchers.any(), ArgumentMatchers.anyString()))
                .thenAnswer(call -> "https://cdn.example/evidence-" + System.nanoTime() + ".jpg");
    }

    @Test
    void crewAttachesReceiptsToPendingCostsAndManagersSeeThem() throws Exception {
        // Khoản cầu đường là biên lai cầu đường, khoản khác là hóa đơn chi phí
        upload("/cost-items/{id}/receipts", toll, driver).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].documentTypeCode").value("TOLL_RECEIPT"))
                .andExpect(jsonPath("$.data[0].uploadedByName").isNotEmpty());
        upload("/cost-items/{id}/receipts", vet, driver).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentTypeCode").value("EXPENSE_INVOICE"));
        mvc.perform(get("/cost-items/{id}/receipts", toll).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileUrl").value(org.hamcrest.Matchers.startsWith("https://cdn.example/evidence-")));
        // Quản lý duyệt rồi thì không thêm biên lai nữa; quản lý không tải biên lai
        entityManager.flush();
        jdbc.update("UPDATE cost_item SET approved_by = recorded_by, approved_at = now() WHERE id = ?", toll);
        entityManager.clear();
        upload("/cost-items/{id}/receipts", toll, driver).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chi phí đã được duyệt, không thêm biên lai."));
        upload("/cost-items/{id}/receipts", vet, manager).andExpect(status().isForbidden());
    }

    @Test
    void crewAttachesScenePhotosWithTimeAndPlaceUntilTheIncidentCloses() throws Exception {
        mvc.perform(multipart("/incidents/{id}/photos", incident).file(photo()).param("latitude", "10.95").param("longitude", "106.82")
                        .header(HttpHeaders.AUTHORIZATION, driver)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].documentTypeCode").value("INCIDENT_PHOTO"))
                .andExpect(jsonPath("$.data[0].capturedAt").isNotEmpty())
                .andExpect(jsonPath("$.data[0].latitude").value(10.95));
        // Không có vị trí vẫn gửi được; chỉ có vĩ độ thì không
        upload("/incidents/{id}/photos", incident, driver).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(multipart("/incidents/{id}/photos", incident).file(photo()).param("latitude", "10.95").header(HttpHeaders.AUTHORIZATION, driver))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/incidents/{id}/photos", incident).header(HttpHeaders.AUTHORIZATION, manager)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        entityManager.flush();
        jdbc.update("UPDATE incident SET status = 'CLOSED', resolved_at = now() WHERE id = ?", incident);
        entityManager.clear();
        upload("/incidents/{id}/photos", incident, driver).andExpect(status().isConflict());
    }

    private ResultActions upload(String path, long id, String token) throws Exception {
        return mvc.perform(multipart(path, id).file(photo()).header(HttpHeaders.AUTHORIZATION, token));
    }

    private static MockMultipartFile photo() {
        return new MockMultipartFile("file", "anh.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    private String token(Staff staff, String role) {
        return "Bearer " + jwtUtils.generateToken(staff.getUser().getUsername(), List.of(role));
    }
}
