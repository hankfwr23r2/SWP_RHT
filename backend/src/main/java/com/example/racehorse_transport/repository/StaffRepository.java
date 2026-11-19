package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StaffRepository extends JpaRepository<Staff, Integer> {
    long countByRoleIgnoreCase(String role);
    List<Staff> findByRoleIgnoreCase(String role);
    List<Staff> findByRoleIgnoreCaseAndEmploymentStatusIgnoreCase(String role, String employmentStatus);
    java.util.Optional<Staff> findByStaffCode(String staffCode);
    java.util.Optional<Staff> findByUser_Username(String username);

    // Mã nhân sự chưa ai dùng: giữ mã mong muốn nếu còn trống, nếu không thì tăng số cuối (ADMIN-01 -> ADMIN-02 ...)
    default String nextFreeStaffCode(String preferred) {
        if (findByStaffCode(preferred).isEmpty()) return preferred;
        int dash = preferred.lastIndexOf('-');
        String prefix = dash >= 0 ? preferred.substring(0, dash + 1) : preferred + "-";
        int width = dash >= 0 ? preferred.length() - dash - 1 : 2;
        for (int number = 1; ; number++) {
            String candidate = prefix + String.format("%0" + Math.max(width, 1) + "d", number);
            if (findByStaffCode(candidate).isEmpty()) return candidate;
        }
    }
}
