package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponse;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequest;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.service.CustomerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final HorseRepository horseRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản: " + username));

        Customer customer = customerRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản chưa có thông tin khách hàng!"));

        int totalHorses = horseRepository.findByCustomerID_Id(customer.getId()).size();
        int totalBookings = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(customer.getId()).size();

        return CustomerProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(customer.getFullName())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .totalHorses(totalHorses)
                .totalBookings(totalBookings)
                .build();
    }

    @Override
    @Transactional
    public CustomerProfileResponse updateProfile(String username, UpdateCustomerProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản: " + username));

        Customer customer = customerRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản chưa có thông tin khách hàng!"));

        customer.setFullName(request.getFullName().trim());
        customer.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        customer.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);

        Customer saved = customerRepository.save(customer);

        int totalHorses = horseRepository.findByCustomerID_Id(saved.getId()).size();
        int totalBookings = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(saved.getId()).size();

        return CustomerProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(saved.getFullName())
                .phone(saved.getPhone())
                .address(saved.getAddress())
                .totalHorses(totalHorses)
                .totalBookings(totalBookings)
                .build();
    }
}
