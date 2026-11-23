package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Staff;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByStaffCode(String staffCode);

    @EntityGraph(attributePaths = {"user", "user.role", "homeHubLocation"})
    List<Staff> findAllByOrderByStaffCodeAsc();

    @EntityGraph(attributePaths = {"user", "user.role", "homeHubLocation"})
    Optional<Staff> findOneByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Staff s where s.userId = :id")
    Optional<Staff> findForUpdate(@Param("id") Long id);
}
