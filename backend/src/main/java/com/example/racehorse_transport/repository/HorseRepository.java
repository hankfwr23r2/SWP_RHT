package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.Horse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorseRepository extends JpaRepository<Horse, Long> {

    Optional<Horse> findByMicrochip(String microchip);

    @EntityGraph(attributePaths = "horseType")
    List<Horse> findByOwner_User_UsernameOrderByNameAscIdAsc(String username);

    @EntityGraph(attributePaths = "horseType")
    Optional<Horse> findByIdAndOwner_User_Username(Long id, String username);
}
