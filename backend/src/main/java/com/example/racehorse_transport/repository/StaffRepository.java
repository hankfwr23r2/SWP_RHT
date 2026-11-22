package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    Optional<Staff> findByStaffCode(String staffCode);
}
