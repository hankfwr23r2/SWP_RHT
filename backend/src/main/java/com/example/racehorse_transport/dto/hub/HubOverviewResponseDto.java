package com.example.racehorse_transport.dto.hub;

import com.example.racehorse_transport.entity.Departure;
import com.example.racehorse_transport.entity.HorseStay;
import com.example.racehorse_transport.entity.Incident;
import com.example.racehorse_transport.entity.Location;
import com.example.racehorse_transport.entity.Vehicle;
import com.example.racehorse_transport.entity.enums.DepartureStatus;
import com.example.racehorse_transport.entity.enums.HorseStayType;
import com.example.racehorse_transport.entity.enums.IncidentSeverity;
import com.example.racehorse_transport.entity.enums.IncidentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record HubOverviewResponseDto(Long id, String code, String name, String provinceName,
        String countryCode, String countryName, boolean headquarters, int horseCount, List<HubHorseResponseDto> horses,
        HubStallCountsResponseDto stalls, HubFleetResponseDto fleet, List<HubStaffRoleResponseDto> staff,
        List<HubDepartureResponseDto> arrivals, List<HubDepartureResponseDto> departures,
        int openIncidentCount, List<HubIncidentResponseDto> incidents,
        List<HubFleetRequirementResponseDto> fleetRequirement) {

    public static HubOverviewResponseDto from(Location hub, int horseCount, List<HubHorseResponseDto> horses,
            HubStallCountsResponseDto stalls, HubFleetResponseDto fleet, List<HubStaffRoleResponseDto> staff,
            List<HubDepartureResponseDto> arrivals, List<HubDepartureResponseDto> departures,
            int openIncidentCount, List<HubIncidentResponseDto> incidents,
            List<HubFleetRequirementResponseDto> fleetRequirement) {
        return new HubOverviewResponseDto(hub.getId(), hub.getCode(), hub.getName(),
                hub.getProvince() == null ? null : hub.getProvince().getName(), hub.getCountry().getCode(),
                hub.getCountry().getName(), hub.isHeadquarters(), horseCount, horses, stalls, fleet, staff,
                arrivals, departures, openIncidentCount, incidents, fleetRequirement);
    }

    public record HubHorseResponseDto(Long id, String horseName, String bookingCode, HorseStayType stayType,
            Instant actualStartAt) {
        public static HubHorseResponseDto from(HorseStay stay) {
            var line = stay.getBookingHorse();
            return new HubHorseResponseDto(stay.getId(), line.getHorse().getName(), line.getBooking().getCode(),
                    stay.getStayType(), stay.getActualStartAt());
        }
    }

    public record HubStallCountsResponseDto(long total, long available) {}

    public record HubVehicleResponseDto(Long id, String licensePlate, String tripCode) {
        public static HubVehicleResponseDto from(Vehicle vehicle, String tripCode) {
            return new HubVehicleResponseDto(vehicle.getId(), vehicle.getLicensePlate(), tripCode);
        }
    }

    public record HubVehicleGroupResponseDto(int count, List<HubVehicleResponseDto> vehicles) {}

    public record HubFleetResponseDto(HubVehicleGroupResponseDto idle, HubVehicleGroupResponseDto running,
            HubVehicleGroupResponseDto returning, HubVehicleGroupResponseDto maintenance) {}

    public record HubFleetRequirementResponseDto(Item item, long required, long actual,
            @JsonInclude(JsonInclude.Include.NON_NULL) HubBranchBreakdownResponseDto breakdown) {
        public enum Item { LINEHAUL, SPARE, BRANCH, DRIVER, ESCORT }
    }

    public record HubBranchBreakdownResponseDto(long sixSeat, long twoSeat, long spare) {}

    public record HubStaffRoleResponseDto(String roleCode, long count) {}

    public record HubDepartureResponseDto(Long id, String routeCode, String originName, String destinationName,
            Instant departureAt, DepartureStatus status) {
        public static HubDepartureResponseDto from(Departure departure, Instant departureAt) {
            var route = departure.getRoute();
            return new HubDepartureResponseDto(departure.getId(), route.getCode(), route.getOrigin().getName(),
                    route.getDestination().getName(), departureAt, departure.getStatus());
        }
    }

    public record HubIncidentResponseDto(Long id, String title, IncidentSeverity severity, IncidentStatus status,
            Instant reportedAt) {
        public static HubIncidentResponseDto from(Incident incident) {
            return new HubIncidentResponseDto(incident.getId(), incident.getTitle(), incident.getSeverity(),
                    incident.getStatus(), incident.getReportedAt());
        }
    }
}
