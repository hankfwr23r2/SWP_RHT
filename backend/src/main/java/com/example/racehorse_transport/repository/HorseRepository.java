package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Horse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HorseRepository extends JpaRepository<Horse, Integer> {
    java.util.List<Horse> findByCustomerID_Id(Integer customerId);
    boolean existsByMicrochipIDIgnoreCase(String microchipID);
    boolean existsByMicrochipIDIgnoreCaseAndIdNot(String microchipID, Integer id);
}
