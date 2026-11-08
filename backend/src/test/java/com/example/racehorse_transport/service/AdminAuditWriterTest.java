package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.SystemLog;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.repository.SystemLogRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.example.racehorse_transport.service.AdminAuditWriter.values;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAuditWriterTest {
    private final SystemLogRepository logs = mock(SystemLogRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AdminAuditWriter writer = new AdminAuditWriter(logs, users, staff, mapper);

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private void signedIn() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        User actor = new User(); actor.setId(7); actor.setUsername("Admin"); actor.setEmail("admin@example.com");
        Staff admin = new Staff(); admin.setRole("ADMIN");
        when(users.findByUsernameOrEmail("admin@example.com", "admin@example.com")).thenReturn(Optional.of(actor));
        when(staff.findById(7)).thenReturn(Optional.of(admin));
    }

    @Test
    void preservesSnapshotsSeparatelyFromDescriptionAndCapturesAuthenticatedActor() throws Exception {
        signedIn();
        writer.record("UPDATE_CONFIG", "SYSTEM_CONFIG", "TRIP_OPEN_FEE", "Phí mở chuyến", "Đổi giá",
                values("value", "3000000"), values("value", "3500000"));
        var captor = ArgumentCaptor.forClass(SystemLog.class);
        verify(logs).save(captor.capture());
        var row = captor.getValue();
        assertThat(row.getActorName()).isEqualTo("Admin");
        assertThat(row.getActorRole()).isEqualTo("ADMIN");
        assertThat(row.getActionbyUserid().getId()).isEqualTo(7);
        assertThat(row.getTargetKey()).isEqualTo("TRIP_OPEN_FEE");
        assertThat(row.getTargetRecordID()).isNull();
        assertThat(row.getDescription()).isEqualTo("Đổi giá");
        assertThat(mapper.readTree(row.getOldData()).get("value").asText()).isEqualTo("3000000");
        assertThat(mapper.readTree(row.getNewData()).get("value").asText()).isEqualTo("3500000");
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getDataVersion()).isEqualTo(1);
    }

    @Test
    void rejectsCredentialsEvenInsideNestedSnapshots() {
        signedIn();
        assertThatThrownBy(() -> writer.record("UPDATE_ROUTE", "ROUTE", 1, "Route", "Update", null,
                values("stops", List.of(values("passwordHash", "secret"))))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(logs);
    }

    @Test
    void missingActorCannotProduceMisleadingSystemEntry() {
        assertThatThrownBy(() -> writer.record("RESET_PASSWORD", "USER", 1, "Account", "Reset", null, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(logs);
    }

    @Test
    void skipsUnchangedValuesButRecordsPasswordResetWithoutSecrets() {
        writer.record("UPDATE_CONFIG", "SYSTEM_CONFIG", "X", "X", "Same", values("value", "1"), values("value", "1"));
        verifyNoInteractions(logs, users, staff);
        signedIn();
        writer.record("RESET_PASSWORD", "USER", 1, "Account", "Reset", null, null);
        var captor = ArgumentCaptor.forClass(SystemLog.class); verify(logs).save(captor.capture());
        assertThat(captor.getValue().getOldData()).isNull();
        assertThat(captor.getValue().getNewData()).isNull();
    }

    @Test
    void auditFailurePropagatesToRollBackAdministrativeTransaction() {
        signedIn();
        when(logs.save(any(SystemLog.class))).thenThrow(new IllegalStateException("db unavailable"));
        assertThatThrownBy(() -> writer.record("RESET_PASSWORD", "USER", 1, "Account", "Reset", null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
