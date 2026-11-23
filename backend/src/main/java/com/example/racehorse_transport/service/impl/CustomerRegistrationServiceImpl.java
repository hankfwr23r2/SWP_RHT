package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.auth.RegisterRequestDto;
import com.example.racehorse_transport.entity.AppUser;
import com.example.racehorse_transport.entity.TermsAcceptance;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.TermsKind;
import com.example.racehorse_transport.policy.TermsDocumentPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.CountryRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.RoleRepository;
import com.example.racehorse_transport.repository.TermsAcceptanceRepository;
import com.example.racehorse_transport.service.CustomerRegistrationService;
import com.example.racehorse_transport.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerRegistrationServiceImpl implements CustomerRegistrationService {

    // Nước mặc định của khách mới; khách sửa được trong hồ sơ (task A2)
    private static final String DEFAULT_COUNTRY = "VN";
    private static final String CUSTOMER_ROLE = "CUSTOMER";

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final CustomerRepository customers;
    private final CountryRepository countries;
    private final TermsAcceptanceRepository termsAcceptances;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerification;

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
        user.setRole(roles.getReferenceById(CUSTOMER_ROLE));
        user.setEmailVerified(false);
        user.setStatus(AppUserStatus.ACTIVE);
        user = users.save(user);

        customers.save(request.toCustomer(user, countries.getReferenceById(DEFAULT_COUNTRY)));

        TermsAcceptance terms = new TermsAcceptance();
        terms.setTermsType(TermsKind.SERVICE_TERMS);
        terms.setTermsVersion(TermsDocumentPolicy.TRANSPORT_TERMS_VERSION);
        terms.setUser(user);
        terms.setIpAddress(ipAddress);
        termsAcceptances.save(terms);

        // Chưa nhập OTP thì chưa đăng nhập được
        emailVerification.sendOtp(user.getEmail());
    }
}
