package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.User;

import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Optional<User> findById(Integer id) {
        return userRepository.findById(id);
    }

    @Override
    public User save(User entity) {
        return userRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        userRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Core, Auth (Flow 1)
    // =========================================================================
    // TODO (Dev 1): Đăng nhập/Phân quyền (JWT).
    // - Viết hàm login(username, password)
    // - Validate user, generate JWT token
    // - Phân quyền (Role: Manager, Staff, Driver, Escort, Customer)

}
