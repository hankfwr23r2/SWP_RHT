package com.example.racehorse_transport.service;

import com.example.racehorse_transport.PostgresIntegrationTest;
import com.example.racehorse_transport.dto.account.StaffAccountCreateRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountUpdateRequestDto;
import com.example.racehorse_transport.exception.AccountRuleException;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@PostgresIntegrationTest
@Transactional
class AccountServiceTest {

    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private LocationRepository locations;
    @Autowired private PasswordEncoder encoder;

    @Test
    void createsLinkedAccountWithEncodedPasswordAndUpdatesProfile() {
        var date = LocalDate.of(2026, 11, 23);
        long hub = locations.findByCode("HUB-HCM").orElseThrow().getId();
        var request = new StaffAccountCreateRequestDto("a2-driver-test", "a2-driver-test@example.com", "test-password",
                "DRIVER", "STAFF-TEST", "Driver Test", "0123", date, hub, "C", date.plusYears(1));
        var created = accounts.createStaffAccount(request);

        assertThat(encoder.matches(request.password(), users.findById(created.userId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(accounts.staffAccount(created.userId())).isEqualTo(created);
        var updated = accounts.updateStaffAccount(created.userId(), new StaffAccountUpdateRequestDto(
                "STAFF-TEST", "Driver Updated", "0123", date, hub, null, null));
        assertThat(updated.username()).isEqualTo(created.username());
        assertThat(updated.fullName()).isEqualTo("Driver Updated");
        assertThat(accounts.staffAccounts()).contains(updated);
        assertThatThrownBy(() -> accounts.createStaffAccount(request))
                .isInstanceOf(AccountRuleException.DuplicateAccountException.class);
    }

    @Test
    void rejectsHubSatelliteWithoutCreatingLogin() {
        long hub = locations.findByCode("HUB-DN").orElseThrow().getId();
        var request = new StaffAccountCreateRequestDto("a2-no-login", "a2-no-login@example.com", "test-password",
                "ESCORT", "NO-LOGIN", "Escort Test", "0123", LocalDate.of(2026, 11, 23), hub, null, null);
        assertThatThrownBy(() -> accounts.createStaffAccount(request)).isInstanceOf(AccountRuleException.class);
        assertThat(users.findByUsername(request.username())).isEmpty();
    }
}
