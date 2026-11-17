package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.dto.booking.BookingHorseDto;
import com.example.racehorse_transport.dto.coordinator.VehicleTripDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseJourneyLog;
import com.example.racehorse_transport.repository.HorseJourneyLogRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HorseDeliveryLocationServiceTest {
    private final HorseRepository horses = mock(HorseRepository.class);
    private final HorseJourneyLogRepository journeys = mock(HorseJourneyLogRepository.class);
    private final HorseDeliveryLocationService service = new HorseDeliveryLocationService(horses, journeys);
    private Booking booking;
    private BookingDetailsPayload payload;
    private Horse horse;
    private VehicleTripDto trip;

    @BeforeEach
    void setup() {
        booking = Booking.builder().id(42).bookingType("domestic")
                .pickupAddress("Trang trại A, Đà Nẵng").dropoffAddress("Trang trại B, Hà Nội").build();
        payload = new BookingDetailsPayload();
        payload.setDestCountry("VN");
        var reference = new BookingHorseDto();
        reference.setHorseId("H-012");
        payload.setHorses(List.of(reference));
        trip = new VehicleTripDto();
        trip.setTripId("TRP-042-1");
        trip.setHorseIds(List.of("H12"));
        trip.setRun(Map.of("startedAt", 100L));
        payload.setTrips(List.of(trip));
        horse = Horse.builder().id(12).transitStatus("IN_TRANSIT").completedTrips(0).build();
        when(horses.findById(12)).thenReturn(Optional.of(horse));
    }

    @Test
    void deliverySetsCompleteDestinationMetadataAndPassportEntry() {
        service.recordTripDelivery(booking, payload, trip, "driver", "signed.jpg", 200L);
        assertThat(horse.getCurrentLocationName()).isEqualTo("Trang trại B, Hà Nội");
        assertThat(horse.getCurrentCountry()).isEqualTo("VN");
        assertThat(horse.getTransitStatus()).isEqualTo("STABLE_IDLE");
        assertThat(horse.getLocationSource()).isEqualTo("SYSTEM_DELIVERY");
        assertThat(horse.getLocationUpdatedAt()).isEqualTo(200L);
        assertThat(horse.getCompletedTrips()).isEqualTo(1);
        var saved = ArgumentCaptor.forClass(HorseJourneyLog.class);
        verify(journeys).save(saved.capture());
        assertThat(saved.getValue().getHorse()).isSameAs(horse);
        assertThat(saved.getValue().getBookingId()).isEqualTo(42);
        assertThat(saved.getValue().getTripCode()).isEqualTo("TRP-042-1");
        assertThat(saved.getValue().getDepartAt()).isEqualTo(100L);
        assertThat(saved.getValue().getProofImageUrl()).isEqualTo("signed.jpg");
    }

    @Test
    void crossBorderDeliveryUsesDestinationCountryInsteadOfPreviousCountry() {
        booking.setBookingType("international");
        payload.setDestCountry("Việt Nam");
        horse.setCurrentCountry("KH");
        service.recordTripDelivery(booking, payload, trip, "driver", "signed.jpg", 200L);
        assertThat(horse.getCurrentCountry()).isEqualTo("VN");
        payload.setDestCountry("Campuchia");
        booking.setDropoffAddress("Trang trại C, Phnom Penh");
        service.recordTripDelivery(booking, payload, trip, "driver", "signed.jpg", 300L);
        assertThat(horse.getCurrentCountry()).isEqualTo("KH");
    }

    @Test
    void oldDomesticBookingCanResolveCountryWithoutGuessingFromAddress() {
        payload.setDestCountry(null);
        service.recordTripDelivery(booking, payload, trip, "driver", null, 200L);
        assertThat(horse.getCurrentCountry()).isEqualTo("VN");
    }

    @Test
    void unknownInternationalDestinationDoesNotSilentlyMarkHorseDelivered() {
        booking.setBookingType("international");
        payload.setDestCountry(null);
        assertThatThrownBy(() -> service.recordTripDelivery(booking, payload, trip, "driver", null, 200L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("quốc gia");
        verify(horses, never()).save(any());
        verify(journeys, never()).save(any());
    }

    @Test
    void deliveryOnlyUpdatesHorsesAssignedToThatVehicle() {
        var otherHorse = new BookingHorseDto();
        otherHorse.setHorseId("H13");
        payload.setHorses(List.of(payload.getHorses().getFirst(), otherHorse));
        var second = new VehicleTripDto();
        second.setHorseIds(List.of("H13"));
        payload.setTrips(List.of(trip, second));
        service.recordTripDelivery(booking, payload, trip, "driver", null, 200L);
        verify(horses).save(horse);
        verify(horses, never()).findById(13);
    }

    @Test
    void missingAssignmentInMultipleVehicleBookingFailsRatherThanReleasingAllHorses() {
        trip.setHorseIds(null);
        payload.setTrips(List.of(trip, new VehicleTripDto()));
        assertThatThrownBy(() -> service.recordTripDelivery(booking, payload, trip, "driver", null, 200L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("danh sách ngựa");
        verify(horses, never()).save(any());
    }

    @Test
    void oldSingleVehicleBookingCanUseItsBookingHorseList() {
        trip.setHorseIds(null);
        service.recordTripDelivery(booking, payload, trip, "driver", null, 200L);
        verify(horses).save(horse);
    }

    @Test
    void handoverCannotUpdateAHorseOutsideTheBooking() {
        trip.setHorseIds(List.of("H99"));
        assertThatThrownBy(() -> service.recordTripDelivery(booking, payload, trip, "driver", null, 200L))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("không thuộc đơn");
        verify(horses, never()).save(any());
    }

    @Test
    void repeatedHandoverPreservesALaterOwnerRelocationAndAvoidsDuplicateLogs() {
        horse.setCurrentLocationName("Trang trại mới, Đà Nẵng");
        horse.setLocationSource("OWNER_DECLARED");
        when(journeys.existsByHorse_IdAndBookingIdAndSource(12, 42, "SYSTEM_DELIVERY")).thenReturn(true);
        service.recordTripDelivery(booking, payload, trip, "driver", null, 200L);
        assertThat(horse.getCurrentLocationName()).isEqualTo("Trang trại mới, Đà Nẵng");
        assertThat(horse.getCompletedTrips()).isEqualTo(0);
        verify(horses, never()).save(any());
        verify(journeys, never()).save(any());
    }

    @Test
    void legacyBookingHandoverAlsoSetsCountryAndLocationMetadata() {
        service.recordBookingHandover(booking, payload, "driver", "proof.jpg", 200L);
        assertThat(horse.getCurrentCountry()).isEqualTo("VN");
        assertThat(horse.getLocationSource()).isEqualTo("SYSTEM_DELIVERY");
        verify(journeys).save(any());
    }
}
