package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Customer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @EntityGraph(attributePaths = {"user", "country", "identityDocumentCountry"})
    Optional<Customer> findByUser_Username(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.userId = :id")
    Optional<Customer> findForUpdate(@Param("id") Long id);
}
