package com.example.racehorse_transport.dto.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.CostItem;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.IncidentPlan;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.VehicleTrip;
import com.example.racehorse_transport.entity.enums.CostCategory;
import com.example.racehorse_transport.entity.enums.CostPayerType;
import com.example.racehorse_transport.entity.enums.IncidentCauseType;
import com.example.racehorse_transport.entity.enums.IncidentPlanStatus;
import com.example.racehorse_transport.entity.enums.IncidentResolutionType;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.example.racehorse_transport.entity.enums.IncidentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentDtoTest {

    @Test
    void createsIncidentPlanAndCostWithoutServerFields() {
        VehicleTrip trip = new VehicleTrip();
        Incident incident = new IncidentReportRequestDto(1L, null, IncidentType.DELAY, IncidentCauseType.FORCE_MAJEURE, " Tắc đường ", " Lũ ",
                new BigDecimal("11.2"), new BigDecimal("106.5")).toEntity(trip, null, null);
        assertThat(incident.getId()).isNull();
        assertThat(incident.getStatus()).isNull();
        assertThat(incident.getReportedBy()).isNull();
        assertThat(incident.getVehicleTrip()).isSameAs(trip);
        assertThat(incident.getTitle()).isEqualTo("Tắc đường");
        assertThat(incident.getDescription()).isEqualTo("Lũ");

        Party vet = new Party();
        IncidentPlan plan = new IncidentPlanRequestDto(IncidentResolutionType.VET_TREATMENT, " Khám ", new BigDecimal("1000000"), 3L).toEntity(incident, vet);
        assertThat(plan.getId()).isNull();
        assertThat(plan.getVersion()).isNull();
        assertThat(plan.getStatus()).isNull();
        assertThat(plan.getProposedBy()).isNull();
        assertThat(plan.getDecidedBy()).isNull();
        assertThat(plan.getHandlerParty()).isSameAs(vet);
        assertThat(plan.getInstructions()).isEqualTo("Khám");

        var request = new CostItemRequestDto(CostCategory.VET, " Thuốc ", new BigDecimal("500000"), null, 1L, null, null, Instant.parse("2031-03-05T10:00:00Z"));
        CostItem cost = request.toEntity(incident, trip, null);
        assertThat(cost.getId()).isNull();
        assertThat(cost.getPayerType()).isNull();
        assertThat(cost.getRecordedBy()).isNull();
        assertThat(cost.getApprovedBy()).isNull();
        assertThat(cost.getApprovedAt()).isNull();
        assertThat(cost.getExtraChargePayment()).isNull();
        assertThat(cost.getCurrencyCode()).isEqualTo("VND");
        assertThat(cost.getCustomerConsulted()).isFalse();
        assertThat(cost.getDescription()).isEqualTo("Thuốc");
    }

    @Test
    void describesIncidentWithPlansAndCost() {
        Staff staff = new Staff();
        staff.setFullName("Điều phối Một");
        Booking booking = new Booking();
        booking.setCode("BK-ABCDEFGH");
        Incident incident = new Incident();
        incident.setId(5L);
        incident.setIncidentType(IncidentType.HEALTH);
        incident.setCauseType(IncidentCauseType.HORSE_CONDITION);
        incident.setStatus(IncidentStatus.PLAN_PENDING);
        incident.setBooking(booking);
        incident.setReportedBy(staff);
        Party vet = new Party();
        vet.setId(3L);
        vet.setDisplayName("Phòng khám thú y");
        IncidentPlan plan = new IncidentPlan();
        plan.setId(7L);
        plan.setVersion(2);
        plan.setStatus(IncidentPlanStatus.REJECTED);
        plan.setHandlerParty(vet);
        plan.setProposedBy(staff);
        plan.setDecidedBy(staff);
        plan.setRejectionReason("Đắt");

        var response = IncidentResponseDto.from(incident, true, List.of(IncidentResponseDto.PlanDto.from(plan)), null);
        assertThat(response.requiresFit()).isTrue();
        assertThat(response.bookingCode()).isEqualTo("BK-ABCDEFGH");
        assertThat(response.vehicleTripId()).isNull();
        assertThat(response.horseName()).isNull();
        assertThat(response.plans()).singleElement().satisfies(p -> {
            assertThat(p.handlerPartyName()).isEqualTo("Phòng khám thú y");
            assertThat(p.rejectionReason()).isEqualTo("Đắt");
        });

        CostItem cost = new CostItem();
        cost.setId(9L);
        cost.setIncident(incident);
        cost.setBooking(booking);
        cost.setPayerType(CostPayerType.CUSTOMER);
        cost.setRecordedBy(staff);
        var costDto = CostItemResponseDto.from(cost);
        assertThat(costDto.incidentId()).isEqualTo(5L);
        assertThat(costDto.bookingCode()).isEqualTo("BK-ABCDEFGH");
        assertThat(costDto.approvedByName()).isNull();
        assertThat(costDto.extraChargePaymentId()).isNull();
    }
}
