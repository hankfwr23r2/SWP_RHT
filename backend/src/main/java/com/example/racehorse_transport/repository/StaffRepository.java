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
}
