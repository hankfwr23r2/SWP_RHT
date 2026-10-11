package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Customer;

import com.example.racehorse_transport.repository.CustomerRepository;
import com.example.racehorse_transport.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    @Autowired
    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    @Override
    public Optional<Customer> findById(Integer id) {
        return customerRepository.findById(id);
    }

    @Override
    public Customer save(Customer entity) {
        return customerRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        customerRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Core (Flow 1)
    // =========================================================================
    // TODO (Dev 1): CRUD Khách hàng.
    // - (Đã có sẵn CRUD cơ bản, bổ sung validate email, sđt, liên kết userId...)

}
