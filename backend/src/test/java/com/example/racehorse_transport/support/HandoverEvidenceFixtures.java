package com.example.racehorse_transport.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.racehorse_transport.security.JwtUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public final class HandoverEvidenceFixtures {
    private HandoverEvidenceFixtures() {}

    public static MockHttpServletRequestBuilder complete(MockMvc mvc, JdbcTemplate jdbc, JwtUtils jwt, String path) throws Exception {
        var request = post(path);
        if (!path.matches("/handovers/[0-9]+/complete")) return request;
        long handover = Long.parseLong(path.split("/")[2]);
        if (!Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM document d JOIN document_type t ON t.id = d.document_type_id
                               WHERE d.handover_id = ? AND t.code = 'HANDOVER_RECORD')
                """, Boolean.class, handover))) return request;

        jdbc.update("""
                INSERT INTO document (document_type_id, file_url, file_name, uploaded_by, status, handover_id, evidence_horse_leg_id)
                SELECT t.id, 'https://example.test/horse.jpg', 'horse.jpg', h.handled_by, 'UPLOADED', h.id, hh.horse_leg_id
                FROM handover h JOIN handover_horse hh ON hh.handover_id = h.id
                CROSS JOIN document_type t
                WHERE h.id = ? AND hh.is_microchip_matched AND t.code = 'HANDOVER_PHOTO'
                  AND NOT EXISTS (SELECT 1 FROM document d WHERE d.handover_id = h.id AND d.evidence_horse_leg_id = hh.horse_leg_id)
                """, handover);
        jdbc.update("""
                INSERT INTO handover_document (handover_id, document_id, direction)
                SELECT DISTINCT h.id, d.id, 'NOT_RECEIVED'
                FROM handover h
                JOIN handover_horse hh ON hh.handover_id = h.id AND hh.is_microchip_matched
                JOIN horse_leg l ON l.id = hh.horse_leg_id
                JOIN booking_horse bh ON bh.id = l.booking_horse_id
                JOIN document d ON (d.booking_id = bh.booking_id
                    OR d.booking_horse_id IN (SELECT x.id FROM booking_horse x WHERE x.booking_id = bh.booking_id AND x.status = 'ACTIVE')
                    OR d.horse_id IN (SELECT x.horse_id FROM booking_horse x WHERE x.booking_id = bh.booking_id AND x.status = 'ACTIVE'))
                WHERE h.id = ? AND (h.handover_type = 'PICKUP' OR h.handover_type = 'AGENT' AND h.from_party_id IS NOT NULL)
                  AND d.status = 'APPROVED' AND d.document_requirement_id IS NOT NULL
                ON CONFLICT DO NOTHING
                """, handover);
        Map<String, Map<String, String>> recipients = new LinkedHashMap<>();
        for (var row : jdbc.queryForList("""
                SELECT DISTINCT b.id, b.code, u.username
                FROM handover h JOIN handover_horse hh ON hh.handover_id = h.id AND hh.is_microchip_matched
                JOIN horse_leg l ON l.id = hh.horse_leg_id JOIN booking_horse bh ON bh.id = l.booking_horse_id
                JOIN booking b ON b.id = bh.booking_id JOIN app_user u ON u.id = b.customer_id
                WHERE h.id = ? AND h.handover_type = 'DELIVERY'
                """, handover)) {
            confirm(mvc, jwt, ((Number) row.get("id")).longValue(), row.get("username").toString());
            recipients.put(row.get("code").toString(), Map.of("recipientName", "Người nhận thử nghiệm"));
        }
        return recipients.isEmpty() ? request : request.contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("recipients", recipients)));
    }

    public static void confirm(MockMvc mvc, JwtUtils jwt, long booking, String username) throws Exception {
        mvc.perform(post("/bookings/{id}/confirm-collection", booking).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + jwt.generateToken(username, List.of("CUSTOMER")))).andExpect(status().isOk());
    }
}
