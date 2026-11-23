package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.customer.CustomerProfileResponseDto;
import com.example.racehorse_transport.dto.customer.UpdateCustomerProfileRequestDto;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.exception.ResourceNotFoundException;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.UserRepository;
import com.example.racehorse_transport.service.CustomerProfileService;
import java.util.Optional;
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
    public CustomerProfileResponseDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản: " + username));

        Customer customer = customerRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản chưa có thông tin khách hàng!"));

        int totalHorses = horseRepository.findByCustomerID_Id(customer.getId()).size();
        int totalBookings = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(customer.getId()).size();

        return CustomerProfileResponseDto.builder()
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
    public void updateLanguage(String username, String language) {
        if (!"vi".equals(language) && !"en".equals(language)) {
            throw new IllegalArgumentException("Ngôn ngữ chỉ nhận vi hoặc en.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản."));
        user.setPreferredLanguage(language);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public CustomerProfileResponseDto updateProfile(String username, UpdateCustomerProfileRequestDto request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin tài khoản: " + username));

        Customer customer = customerRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản chưa có thông tin khách hàng!"));

        request.applyTo(customer);

        Customer saved = customerRepository.save(customer);

        int totalHorses = horseRepository.findByCustomerID_Id(saved.getId()).size();
        int totalBookings = bookingRepository.findByCustomerID_IdOrderByBookingDateDesc(saved.getId()).size();

        return CustomerProfileResponseDto.builder()
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

    @Override
    public Optional<Customer> findById(Integer userId) {
        return customerRepository.findById(userId);
    }

    @Override
    public boolean existsById(Integer userId) {
        return customerRepository.existsById(userId);
    }

    @Override
    public Customer getOrCreate(User user, String name, String phone) {
        return findById(user.getId()).orElseGet(() -> create(user, name, phone, null));
    }

    @Override
    public Customer create(User user, String name, String phone, String address) {
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName(name);
        customer.setPhone(phone);
        customer.setAddress(address);
        return customerRepository.save(customer);
    }
}
