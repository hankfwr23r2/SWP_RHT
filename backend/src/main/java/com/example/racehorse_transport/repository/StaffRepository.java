package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.AppUserStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    @Query("""
            select s from Staff s join fetch s.user u join fetch u.role r where r.code = :role
              and u.status = com.example.racehorse_transport.entity.enums.AppUserStatus.ACTIVE
              and s.employmentStatus = com.example.racehorse_transport.entity.enums.EmploymentStatus.ACTIVE
            order by s.fullName, s.userId
            """)
    List<Staff> findActiveForRole(String role);

    Optional<Staff> findByUser_Username(String username);

    Optional<Staff> findByStaffCode(String staffCode);

    @Query("select s.userId from Staff s where s.user.role.code = :role and s.user.status = :status order by s.userId")
    List<Long> findUserIdsByRoleAndStatus(String role, AppUserStatus status);

    @EntityGraph(attributePaths = {"user", "user.role", "homeHubLocation"})
    List<Staff> findAllByOrderByStaffCodeAsc();

    @EntityGraph(attributePaths = {"user", "user.role", "homeHubLocation"})
    Optional<Staff> findOneByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Staff s where s.userId = :id")
    Optional<Staff> findForUpdate(@Param("id") Long id);
}
