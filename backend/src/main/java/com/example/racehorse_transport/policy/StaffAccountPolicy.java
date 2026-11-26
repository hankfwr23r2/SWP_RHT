package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.LocationType;
import com.example.racehorse_transport.exception.AccountRuleException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

public final class StaffAccountPolicy {

    private StaffAccountPolicy() {}

    public static void requireStaffRole(String roleCode) {
        if ("CUSTOMER".equals(roleCode)) {
            throw new AccountRuleException("Chọn vai trò nhân viên.");
        }
    }

    public static void requireHeadquarters(LocationType type, boolean headquarters) {
        if (type != LocationType.HUB || !headquarters) {
            throw new AccountRuleException("Hub của nhân viên phải là trụ sở.");
        }
    }

    public static void requireValidLicense(String roleCode, String licenseClass, LocalDate expiryDate) {
        if (licenseClass == null && expiryDate == null) {
            return;
        }
        if (!"DRIVER".equals(roleCode) || licenseClass == null || licenseClass.isBlank() || expiryDate == null) {
            throw new AccountRuleException("Chỉ tài xế được khai bằng lái và phải nhập đủ hạng bằng, ngày hết hạn.");
        }
    }

    public static void requirePasswordFitsBcrypt(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AccountRuleException("Mật khẩu vượt giới hạn 72 byte.");
        }
    }
}
