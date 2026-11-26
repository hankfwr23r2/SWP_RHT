package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.PlannedTripRequestDto;
import com.example.racehorse_transport.dto.dispatch.PlannedTripResponseDto;
import com.example.racehorse_transport.dto.dispatch.VehicleTripResponseDto;
import com.example.racehorse_transport.dto.route.GeoPointDto;
import com.example.racehorse_transport.entity.enums.TripStopType;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Rules;
import com.example.racehorse_transport.policy.TripSchedulePolicy.Window;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Phần đọc và ghi DB của TripScheduleService, tách khỏi lúc gọi dịch vụ bản đồ
public interface TripScheduleDataService {

    TripInput load(PlannedTripRequestDto request);

    List<PlaceInput> activeRestStops();

    VehicleTripResponseDto save(PlannedTripRequestDto request, List<HorseInput> horses, PlannedTripResponseDto plan, String username);

    // Con ngựa lên ở điểm board, xuống ở điểm alight
    record HorseInput(Long bookingHorseId, int board, int alight, String horseName) {
        public HorseInput(Long bookingHorseId, int board, int alight) {
            this(bookingHorseId, board, alight, String.valueOf(bookingHorseId));
        }

        HorseInput shiftForInsertion(int at) {
            return new HorseInput(bookingHorseId, board < at ? board : board + 1,
                    alight < at ? alight : alight + 1, horseName);
        }
    }

    record PlaceInput(Long locationId, Long bookingAddressId, TripStopType stopType, String name, String bookingCode, GeoPointDto point) {}

    // hubDeadline: hạn có mặt ở hub (lượt đón). readyAt: lúc xe trục tới hub (lượt giao)
    record TripInput(List<PlaceInput> places, List<HorseInput> horses, LocalDate pickupDate, Instant hubDeadline, Instant readyAt,
                     Rules rules, Window pickupWindow, Window deliveryWindow, int weightTons, int topSpeedKmh, boolean multiPlaceDelivery) {

        public TripInput insertRest(int at, PlaceInput rest) {
            var revised = new java.util.ArrayList<>(places);
            revised.add(at, rest);
            return new TripInput(List.copyOf(revised), horses.stream().map(horse -> horse.shiftForInsertion(at)).toList(),
                    pickupDate, hubDeadline, readyAt, rules, pickupWindow, deliveryWindow, weightTons, topSpeedKmh, multiPlaceDelivery);
        }

        public List<Integer> horsesAtStops() {
            return java.util.stream.IntStream.range(0, places.size())
                    .mapToObj(i -> (int) horses.stream().filter(h -> h.board() == i || h.alight() == i).count()).toList();
        }
    }
}
