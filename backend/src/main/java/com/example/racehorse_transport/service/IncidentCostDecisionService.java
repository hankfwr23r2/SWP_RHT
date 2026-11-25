package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.incident.PlanApproveRequestDto;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.enums.PartyRoleType;
import java.util.List;

public interface IncidentCostDecisionService {
    void requireCustomerOwnership(Long bookingId, String username);

    void confirmCause(IncidentPlan plan, PlanApproveRequestDto request, Staff manager);
    void assignPayer(CostItem cost, IncidentCauseType cause);
    List<PartyRoleType> validRoles(Long partyId);
}
