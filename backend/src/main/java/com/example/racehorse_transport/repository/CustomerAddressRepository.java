package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.CustomerAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    @EntityGraph(attributePaths = {"country", "province"})
    List<CustomerAddress> findByCustomer_UserIdOrderById(Long customerId);

    Optional<CustomerAddress> findByIdAndCustomer_UserId(Long id, Long customerId);

    Optional<CustomerAddress> findByCustomer_UserIdAndDefaultAddressTrue(Long customerId);
}
