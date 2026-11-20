package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.dto.field.TripUpdateRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.repository.*;
import com.example.racehorse_transport.service.BookingQueryService;
import com.example.racehorse_transport.service.CaptureService;
import com.example.racehorse_transport.service.OperationalLogService;
import com.example.racehorse_transport.service.StaffNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Luồng một đơn nhiều xe: mỗi xe một tài xế và một hộ tống, mỗi người chỉ làm đúng phần việc của mình.
class MultiVehicleTripFlowTest {

    private final BookingRepository bookings = mock(BookingRepository.class);
    private final HorseRepository horses = mock(HorseRepository.class);
    private final StaffRepository staff = mock(StaffRepository.class);
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final com.example.racehorse_transport.service.BillMailService mail = mock(com.example.racehorse_transport.service.BillMailService.class);
    private TripProgressServiceImpl service;

    @BeforeEach
    void setUp() {
        var delivery = new DeliveryServiceImpl(bookings, vehicles, mock(BookingVehicleRepository.class),
                mock(BookingAssignmentRepository.class), mock(IncidentRepository.class), mock(PaymentRepository.class),
                mock(OperationalLogService.class), new HorseDeliveryLocationServiceImpl(horses, mock(HorseJourneyLogRepository.class)),
                staff, mock(StaffNotificationService.class), new BookingVisibilityServiceImpl());
        service = new TripProgressServiceImpl(mock(CaptureService.class), bookings, horses, mock(QuotationRepository.class),
                mock(NotificationRepository.class), mock(LogRepository.class), vehicles, staff, delivery,
                mock(IncidentRepository.class), mock(StaffNotificationService.class), mock(OperationalLogService.class),
                mock(BookingQueryService.class), mail);
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(horses.findById(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation ->
                Optional.of(com.example.racehorse_transport.entity.Horse.builder().id(invocation.getArgument(0)).build()));
        person("driver01", "DRIVER", "DR-01", 5);
        person("driver02", "DRIVER", "DR-02", 6);
        person("escort01", "ESCORT", "ES-01", 8);
    }

    @Test
    void lastLoadedVehicleDeliveredClosesTheBookingWhenTheOtherVehicleLostAllItsHorsesAtPickup() {
        // Xe 2 bị loại con ngựa duy nhất ở điểm đón nên không chạy; xe 1 chở ngựa còn lại và giao xong
        Booking booking = booking(1, "BK-AAAA0001", "in_transit", """
            {"horses":[{"horseId":"H1","name":"Storm","microchip":"985000000000001"},
                       {"horseId":"H2","name":"Wind","microchip":"985000000000002","status":"UNFIT"}],
             "trips":[
               {"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"],"departedAt":10,
                "run":{"startedAt":100,"checkpoints":[{"id":"pickup","type":"pickup","arrivedAt":90,"doneAt":100},
                       {"id":"delivery","type":"delivery","arrivedAt":200,"photo":"a.jpg","handoverPhoto":"h.jpg","returnedOriginals":true}],
                       "welfare":[{"checkpointId":"delivery"}]}},
               {"tripId":"T2","vehicleId":"VH-002","driverId":"DR-02","escortId":"ES-02","horseIds":[],"departedAt":10,
                "run":{"checkpoints":[{"id":"pickup","type":"pickup","arrivedAt":90,"photo":"p.jpg"}]}}]}
            """);

        service.updateTripProgress("BK-AAAA0001", action("T1", "COMPLETE_DELIVERY", Map.of()), "driver01", "DRIVER");

        assertThat(booking.getStatus()).isEqualTo("delivered_pending_settlement");
    }

    @Test
    void vehicleWhoseOnlyHorseIsExcludedAtPickupHeadsBackToHqWhileTheOtherVehicleCarriesOn() {
        person("escort02", "ESCORT", "ES-02", 9);
        Booking booking = booking(1, "BK-AAAA0001", "en_route_to_pickup", """
            {"horses":[{"horseId":"H1","name":"Storm"},{"horseId":"H2","name":"Wind"}],
             "trips":[
               {"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"],"departedAt":10},
               {"tripId":"T2","vehicleId":"VH-002","driverId":"DR-02","escortId":"ES-02","horseIds":["H2"],"departedAt":10,
                "run":{"checkpoints":[{"id":"pickup","type":"pickup","arrivedAt":90,"photo":"p.jpg"}]}}]}
            """);

        service.updateTripProgress("BK-AAAA0001", action("T2", "EXCLUDE_UNFIT_HORSE", Map.of("horseId", "H2", "reason", "Sốt")), "escort02", "ESCORT");

        assertThat(booking.getStatus()).isEqualTo("en_route_to_pickup");
        assertThat(booking.getDetailsJson()).contains("\"homeReturn\":{").contains("không còn ngựa để chở, xe về trụ sở");
    }

    @Test
    void customerGetsOneReminderMailToPayTheBalanceOnceTheCrewHasCollectedTheHorses() {
        Booking booking = booking(1, "BK-AAAA0001", "en_route_to_pickup", """
            {"horses":[{"horseId":"H1","name":"Storm","microchip":"985000000000001"}],
             "quote":{"total":10000000,"deposit":3000000,"balance":7000000},"payment":{"depositPaid":true},
             "trips":[{"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"],"departedAt":10,
               "run":{"checkpoints":[{"id":"pickup","type":"pickup","arrivedAt":90,"photo":"p.jpg","chips":["985000000000001"],
                 "originals":["Hộ chiếu ngựa bản gốc (FEI / National Passport)","Sổ tiêm phòng","Phiếu xét nghiệm EIA/EVA, bản gốc kèm 02 bản sao công chứng"]}]}}]}
            """);

        service.updateTripProgress("BK-AAAA0001", action("T1", "UPLOAD_HANDOVER", Map.of("checkpointId", "pickup", "photo", "h.jpg")), "driver01", "DRIVER");
        service.updateTripProgress("BK-AAAA0001", action("T1", "UPLOAD_HANDOVER", Map.of("checkpointId", "pickup", "photo", "h2.jpg")), "driver01", "DRIVER");

        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(1))
                .balanceDue(org.mockito.ArgumentMatchers.same(booking), org.mockito.ArgumentMatchers.argThat(a -> a.compareTo(new java.math.BigDecimal("7000000")) == 0));
    }

    @Test
    void driverBackAtHqCanDepartForTheNextBookingWhileAColleagueOfTheSameBookingIsStillOnTheRoad() {
        // Đơn A hai xe: xe của driver01 đã giao và xác nhận về trụ sở, xe của driver02 còn đang chạy
        Booking first = booking(1, "BK-AAAA0001", "in_transit", """
            {"trips":[
               {"tripId":"A1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"],"departedAt":10,
                "homeReturn":{"backAt":500}},
               {"tripId":"A2","vehicleId":"VH-002","driverId":"DR-02","escortId":"ES-02","horseIds":["H2"],"departedAt":10}]}
            """);
        Booking next = booking(2, "BK-BBBB0002", "ready_for_pickup", """
            {"trips":[{"tripId":"B1","vehicleId":"VH-003","driverId":"DR-01","escortId":"ES-03","horseIds":["H3"]}]}
            """);
        when(bookings.findAll()).thenReturn(List.of(first, next));

        service.updateTripProgress("BK-BBBB0002", action("B1", "DEPART_TO_PICKUP", Map.of()), "driver01", "DRIVER");

        assertThat(next.getStatus()).isEqualTo("en_route_to_pickup");
    }

    @Test
    void escortAcknowledgesOnlyAsEscortEvenIfTheRequestSaysDriver() {
        Booking booking = booking(1, "BK-AAAA0001", "clearance_in_progress", """
            {"trips":[{"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"]}]}
            """);

        service.updateTripProgress("BK-AAAA0001", action("T1", "ACKNOWLEDGE", Map.of("who", "driver")), "escort01", "ESCORT");

        assertThat(booking.getDetailsJson()).contains("\"escort\":").doesNotContain("\"driver\":");
    }

    @Test
    void escortCannotStartTheJourneyOfTheDriver() {
        booking(1, "BK-AAAA0001", "en_route_to_pickup", """
            {"trips":[{"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"]}]}
            """);

        assertThatThrownBy(() -> service.updateTripProgress("BK-AAAA0001", action("T1", "START_JOURNEY", Map.of()), "escort01", "ESCORT"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void driverCannotWriteTheEscortHealthLogThatGatesLeavingARestStop() {
        booking(1, "BK-AAAA0001", "in_transit", """
            {"route":{"legs":[],"rests":[{"afterLeg":1,"name":"Trạm nghỉ A","minutes":30}]},
             "trips":[{"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1"],"departedAt":10,
              "run":{"startedAt":100,"checkpoints":[{"id":"pickup","type":"pickup","arrivedAt":90,"doneAt":100},
                     {"id":"rest-1","type":"rest","arrivedAt":300,"photo":"r.jpg"},{"id":"delivery","type":"delivery"}]}}]}
            """);

        assertThatThrownBy(() -> service.updateTripProgress("BK-AAAA0001",
                action("T1", "SUBMIT_WELFARE", Map.of("checkpointId", "rest-1", "photo", "w.jpg")), "driver01", "DRIVER"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void horseCannotBeExcludedAsUnfitAfterTheJourneyStarted() {
        booking(1, "BK-AAAA0001", "in_transit", """
            {"horses":[{"horseId":"H1","name":"Storm"},{"horseId":"H2","name":"Wind"}],
             "trips":[{"tripId":"T1","vehicleId":"VH-001","driverId":"DR-01","escortId":"ES-01","horseIds":["H1","H2"],"departedAt":10,
              "run":{"startedAt":100,"checkpoints":[{"id":"pickup","type":"pickup","arrivedAt":90,"doneAt":100}]}}]}
            """);

        assertThatThrownBy(() -> service.updateTripProgress("BK-AAAA0001",
                action("T1", "EXCLUDE_UNFIT_HORSE", Map.of("horseId", "H2", "reason", "Sốt")), "escort01", "ESCORT"))
                .isInstanceOf(IllegalStateException.class);
    }

    private Booking booking(int id, String code, String status, String details) {
        Booking booking = Booking.builder().id(id).code(code).status(status).bookingType("domestic")
                .pickupAddress("Điểm đón").dropoffAddress("Điểm giao").detailsJson(details).build();
        when(bookings.findIdByCode(code)).thenReturn(Optional.of(id));
        when(bookings.findByIdForUpdate(id)).thenReturn(Optional.of(booking));
        when(bookings.findAll()).thenReturn(List.of(booking));
        return booking;
    }

    private void person(String username, String role, String code, int id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        Staff s = new Staff();
        s.setId(id);
        s.setRole(role);
        s.setStaffCode(code);
        s.setUser(user);
        when(staff.findByUser_Username(username)).thenReturn(Optional.of(s));
    }

    private static TripUpdateRequestDto action(String tripId, String action, Map<String, Object> payload) {
        TripUpdateRequestDto request = new TripUpdateRequestDto();
        request.setTripId(tripId);
        request.setAction(action);
        request.setPayload(new java.util.HashMap<>(payload));
        return request;
    }
}
