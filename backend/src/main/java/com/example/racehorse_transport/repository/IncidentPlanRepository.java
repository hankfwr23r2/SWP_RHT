package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.IncidentPlan;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentPlanRepository extends JpaRepository<IncidentPlan, Long> {

    @EntityGraph(attributePaths = {"handlerParty", "proposedBy", "decidedBy"})
    List<IncidentPlan> findByIncident_IdOrderByVersion(Long incidentId);

    @EntityGraph(attributePaths = {"handlerParty", "proposedBy", "decidedBy"})
    List<IncidentPlan> findByIncident_IdInOrderByIncident_IdAscVersionAsc(Collection<Long> incidentIds);
}
