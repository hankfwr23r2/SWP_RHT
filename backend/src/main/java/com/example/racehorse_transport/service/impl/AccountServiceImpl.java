package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.account.StaffAccountCreateRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountResponseDto;
import com.example.racehorse_transport.dto.account.StaffAccountStatusRequestDto;
import com.example.racehorse_transport.dto.account.StaffAccountUpdateRequestDto;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import com.example.racehorse_transport.entity.enums.EmploymentStatus;
import com.example.racehorse_transport.exception.AccountRuleException;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.policy.StaffAccountPolicy;
import com.example.racehorse_transport.repository.AppUserRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RoleRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.AccountService;
import com.example.racehorse_transport.service.AuditService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final Set<String> UNIQUE_FIELDS = Set.of(
            "uk_app_user_email", "uk_app_user_username", "uk_staff_staff_code");

    private final AppUserRepository users;
    private final StaffRepository staff;
    private final RoleRepository roles;
    private final LocationRepository locations;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    @Override
    @Transactional(readOnly = true)
    public List<StaffAccountResponseDto> staffAccounts() {
        return staff.findAllByOrderByStaffCodeAsc().stream().map(StaffAccountResponseDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StaffAccountResponseDto staffAccount(Long id) {
        return staff.findOneByUserId(id).map(StaffAccountResponseDto::from).orElseThrow(this::missingStaff);
    }

    @Override
    @Transactional
    public StaffAccountResponseDto createStaffAccount(StaffAccountCreateRequestDto request) {
        String roleCode = request.roleCode().strip().toUpperCase(Locale.ROOT);
        StaffAccountPolicy.requireStaffRole(roleCode);
        StaffAccountPolicy.requireValidLicense(roleCode, request.driverLicenseClass(), request.driverLicenseExpiryDate());
        StaffAccountPolicy.requirePasswordFitsBcrypt(request.password());
        var role = roles.findById(roleCode)
                .orElseThrow(() -> new AccountRuleException("Vai trò nhân viên không hợp lệ."));
        var hub = headquarters(request.homeHubLocationId());
        var user = request.toAccount();
        user.setRole(role);
        user.setStatus(AppUserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        var profile = request.toEntity(user, hub);
        profile.setEmploymentStatus(EmploymentStatus.ACTIVE);
        try {
            if (users.findByUsername(user.getUsername()).isPresent() || users.findByUsername(user.getEmail()).isPresent()
                    || users.findByEmailIgnoreCase(user.getEmail()).isPresent()
                    || staff.findByStaffCode(profile.getStaffCode()).isPresent()) {
                throw new AccountRuleException.DuplicateAccountException();
            }
            users.save(user);
            return StaffAccountResponseDto.from(staff.saveAndFlush(profile));
        } catch (DataIntegrityViolationException ex) {
            throw translateDuplicate(ex);
        }
    }

    @Override
    @Transactional
    public StaffAccountResponseDto updateStaffAccount(Long id, StaffAccountUpdateRequestDto request) {
        var profile = staff.findForUpdate(id).orElseThrow(this::missingStaff);
        StaffAccountPolicy.requireValidLicense(profile.getUser().getRole().getCode(),
                request.driverLicenseClass(), request.driverLicenseExpiryDate());
        request.applyTo(profile, headquarters(request.homeHubLocationId()));
        try {
            staff.flush();
        } catch (DataIntegrityViolationException ex) {
            throw translateDuplicate(ex);
        }
        return StaffAccountResponseDto.from(profile);
    }

    @Override
    @Transactional
    public StaffAccountResponseDto lockStaffAccount(Long id, String username, StaffAccountStatusRequestDto request) {
        return changeStatus(id, username, request, AppUserStatus.LOCKED, "LOCK_STAFF_ACCOUNT");
    }

    @Override
    @Transactional
    public StaffAccountResponseDto unlockStaffAccount(Long id, String username, StaffAccountStatusRequestDto request) {
        return changeStatus(id, username, request, AppUserStatus.ACTIVE, "UNLOCK_STAFF_ACCOUNT");
    }

    private StaffAccountResponseDto changeStatus(Long id, String username, StaffAccountStatusRequestDto request,
                                                 AppUserStatus next, String action) {
        var actor = users.findByUsername(username)
                .filter(user -> user.getStatus() == AppUserStatus.ACTIVE && "ADMIN".equals(user.getRole().getCode()))
                .orElseThrow(() -> new AccessDeniedException("Chỉ quản trị viên được khóa tài khoản."));
        var profile = staff.findForUpdate(id).orElseThrow(this::missingStaff);
        var user = profile.getUser();
        AppUserStatus previous = user.getStatus();
        StaffAccountPolicy.requireStatusTransition(previous, next, actor.getId().equals(user.getId()));
        user.setStatus(next);
        audit.record(actor.getId(), action, "app_user", user.getId(), Map.of("status", previous),
                Map.of("status", next, "reason", request.reason().strip()));
        return StaffAccountResponseDto.from(profile);
    }

    private Location headquarters(Long id) {
        Location hub = locations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa điểm."));
        StaffAccountPolicy.requireHeadquarters(hub.getLocationType(), hub.isHeadquarters());
        return hub;
    }

    private RuntimeException translateDuplicate(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null && UNIQUE_FIELDS.contains(violation.getConstraintName())) {
            return new AccountRuleException.DuplicateAccountException();
        }
        return ex;
    }

    private ResourceNotFoundException missingStaff() {
        return new ResourceNotFoundException("Không tìm thấy hồ sơ nhân viên.");
    }
}
