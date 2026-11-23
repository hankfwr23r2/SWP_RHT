package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.auth.RegisterRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.PartyRole;
import com.example.racehorse_transport.entity.PartyRoleId;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.UserRole;
import com.example.racehorse_transport.entity.UserRoleId;
import com.example.racehorse_transport.entity.enums.ActiveStatus;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.PartyKind;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import com.example.racehorse_transport.entity.enums.TermsKind;
import com.example.racehorse_transport.policy.TermsDocumentPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.PartyRepository;
import com.example.racehorse_transport.repository.PartyRoleRepository;
import com.example.racehorse_transport.repository.RoleRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.repository.UserRoleRepository;
import com.example.racehorse_transport.service.CustomerRegistrationService;
import com.example.racehorse_transport.service.EmailVerificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerRegistrationServiceImpl implements CustomerRegistrationService {

    // Ngày nghiệp vụ tính theo giờ Việt Nam
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    // Nước mặc định của khách mới; khách sửa được trong hồ sơ (task A2)
    private static final String DEFAULT_COUNTRY = "VN";
    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PartyRepository parties;
    private final PartyRoleRepository partyRoles;
    private final CountryRepository countries;
    private final TermsAcceptanceRepository termsAcceptances;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerification;
    private final Clock clock;

    @Override
    @Transactional
    public void register(RegisterRequestDto request, String ipAddress) {
        if (!Boolean.TRUE.equals(request.getAcceptedTerms())) {
            throw new IllegalArgumentException("Bạn cần đồng ý Điều khoản vận chuyển để đăng ký.");
        }
        AppUser user = request.toEntity(passwordEncoder.encode(request.getPassword()));
        // Trùng tài khoản: trả 409 để form gắn lỗi vào ô Email
        if (users.findByUsername(user.getUsername()).isPresent() || users.findByEmailIgnoreCase(user.getEmail()).isPresent()) {
            throw new IllegalStateException("Email này đã có tài khoản. Hãy đăng nhập hoặc dùng email khác.");
        }
        user.setEmailVerified(false);
        user.setStatus(AppUserStatus.ACTIVE);
        user = users.save(user);

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), CUSTOMER_ROLE));
        userRole.setUser(user);
        userRole.setRole(roles.getReferenceById(CUSTOMER_ROLE));
        userRoles.save(userRole);

        Party party = new Party();
        party.setKind(PartyKind.PERSON);
        party.setDisplayName(request.getFullName().trim());
        party.setPhone(request.getPhone().trim());
        party.setEmail(user.getEmail());
        party.setAddress(request.getAddress() == null || request.getAddress().isBlank() ? null : request.getAddress().trim());
        party.setCountry(countries.getReferenceById(DEFAULT_COUNTRY));
        party.setUser(user);
        party.setStatus(ActiveStatus.ACTIVE);
        party = parties.save(party);

        PartyRole partyRole = new PartyRole();
        partyRole.setId(new PartyRoleId(party.getId(), PartyRoleType.CUSTOMER));
        partyRole.setParty(party);
        partyRole.setValidFrom(LocalDate.ofInstant(clock.instant(), BUSINESS_ZONE));
        partyRoles.save(partyRole);

        TermsAcceptance terms = new TermsAcceptance();
        terms.setTermsKind(TermsKind.SERVICE_TERMS);
        terms.setTermsVersion(TermsDocumentPolicy.TRANSPORT_TERMS_VERSION);
        terms.setUser(user);
        terms.setIpAddress(ipAddress);
        termsAcceptances.save(terms);

        // Chưa nhập OTP thì chưa đăng nhập được
        emailVerification.sendOtp(user.getEmail());
    }
}
