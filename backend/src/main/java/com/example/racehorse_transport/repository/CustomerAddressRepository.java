package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.CustomerAddress;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomer_UserIdOrderById(Long customerId);
}
