package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.UserRole;
import com.example.racehorse_transport.entity.UserRoleId;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findById_UserId(Long userId);
}
