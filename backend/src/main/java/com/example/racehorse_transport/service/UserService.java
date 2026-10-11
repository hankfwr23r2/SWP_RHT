package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    List<User> findAll();
    Optional<User> findById(Integer id);
    User save(User entity);
    void deleteById(Integer id);
}
