package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Horse;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface HorseRepository extends JpaRepository<Horse, Long> {

    Optional<Horse> findByMicrochip(String microchip);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT h FROM Horse h
            WHERE h.owner.userId = :customerId AND h.id IN :ids
            ORDER BY h.id
            """)
    List<Horse> findOwnedForUpdate(Long customerId, Collection<Long> ids);

    @EntityGraph(attributePaths = "horseType")
    List<Horse> findByOwner_User_UsernameOrderByNameAscIdAsc(String username);

    @EntityGraph(attributePaths = "horseType")
    Optional<Horse> findByIdAndOwner_User_Username(Long id, String username);
}
