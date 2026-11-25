package com.example.racehorse_transport.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import com.example.racehorse_transport.entity.IncidentPlan;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentPlanRepository extends JpaRepository<IncidentPlan, Long> {
    @Query("select p.incident.id from IncidentPlan p where p.id = :id")
    Optional<Long> findIncidentId(Long id);


    @EntityGraph(attributePaths = {"handlerParty", "proposedBy", "decidedBy"})
    List<IncidentPlan> findByIncident_IdOrderByVersion(Long incidentId);

    @EntityGraph(attributePaths = {"handlerParty"})
    Optional<IncidentPlan> findFirstByIncident_IdOrderByVersionDesc(Long incidentId);

    @EntityGraph(attributePaths = {"handlerParty", "proposedBy", "decidedBy"})
    List<IncidentPlan> findByIncident_IdInOrderByIncident_IdAscVersionAsc(Collection<Long> incidentIds);
}
