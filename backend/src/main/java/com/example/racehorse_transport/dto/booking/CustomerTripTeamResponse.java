package com.example.racehorse_transport.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

// Xe và kíp xe của một chuyến, phần khách được xem (biển số, tài xế, hộ tống và số điện thoại liên hệ)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTripTeamResponse {
    private String tripId;
    private List<String> horseNames;
    private TeamVehicle vehicle;
    private TeamMember driver;
    private TeamMember escort;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamVehicle {
        private String plate;
        private String kind;
        private Integer stalls;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamMember {
        private String name;
        private String phone;
    }
}
