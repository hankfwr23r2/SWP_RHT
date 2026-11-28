package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Party;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartyRepository extends JpaRepository<Party, Long> {

    @EntityGraph(attributePaths = "country")
    List<Party> findAllByOrderByDisplayNameAscIdAsc();

    @EntityGraph(attributePaths = "country")
    Optional<Party> findOneById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Party p WHERE p.id = :id")
    Optional<Party> findForUpdate(@Param("id") Long id);
}
