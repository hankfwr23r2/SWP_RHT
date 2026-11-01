package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDetailsPayload;
import com.example.racehorse_transport.entity.*;
import com.example.racehorse_transport.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CaptureServiceTest {
    final CaptureAssetRepository assets = mock(CaptureAssetRepository.class);
    final BookingRepository bookings = mock(BookingRepository.class);
    final StaffRepository staff = mock(StaffRepository.class);
    final StorageService storage = mock(StorageService.class);
    final CaptureService service = new CaptureService(assets, bookings, staff, storage, new ObjectMapper());
    final String url = "https://example.com/photo.jpg";
    @BeforeEach void setup() {
        Booking booking = Booking.builder().id(1).status("in_transit").detailsJson("""
            {"trips":[{"tripId":"T1","driverId":"DR-01","escortId":"ES-01"}]}
            """).build();
        Staff driver = new Staff(); driver.setId(10); driver.setRole("DRIVER"); driver.setStaffCode("DR-01");
        when(bookings.findById(1)).thenReturn(Optional.of(booking));
        when(staff.findByUser_Username("driver")).thenReturn(Optional.of(driver));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        return new MockMultipartFile("file", "camera.jpg", "image/jpeg", bytes.toByteArray());
    }
    CaptureAsset asset() {
        return CaptureAsset.builder().id(1L).url(url).bookingId(1).tripId("T1").purpose("ARRIVE_PICKUP").target("pickup").capturedBy("driver")
            .capturedAt(Instant.now().minusSeconds(10)).recordedAt(Instant.now()).build();
    }
    @Test void savesDeviceTimeSeparatelyAndUsesAuthenticatedActor() throws Exception {
        long shot = Instant.now().minusSeconds(10).toEpochMilli();
        when(storage.uploadFile(any(), eq("captures"))).thenReturn(url);
        when(assets.save(any())).thenAnswer(call -> { CaptureAsset a = call.getArgument(0); a.setId(1L); return a; });
        var result = service.upload(image(), 1, "T1", "ARRIVE_PICKUP", "pickup", shot, "driver", "DRIVER");
        assertThat(result.capturedAt()).isEqualTo(shot);
        assertThat(result.recordedAt()).isGreaterThan(result.capturedAt());
        assertThat(result.capturedBy()).isEqualTo("driver");
    }
    @Test void rejectsWrongTripRoleAndFakeImageBeforeStorage() throws Exception {
        assertThatThrownBy(() -> service.upload(image(), 1, "T2", "ARRIVE_PICKUP", "pickup", System.currentTimeMillis(), "driver", "DRIVER")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.upload(image(), 1, "T1", "SUBMIT_WELFARE", "rest-1", System.currentTimeMillis(), "driver", "DRIVER")).isInstanceOf(AccessDeniedException.class);
        var fake = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());
        assertThatThrownBy(() -> service.upload(fake, 1, "T1", "ARRIVE_PICKUP", "pickup", System.currentTimeMillis(), "driver", "DRIVER")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(storage);
    }
    @Test void rejectsImplausibleClockAndStorageFailureNeverCreatesEvidence() throws Exception {
        assertThatThrownBy(() -> service.upload(image(), 1, "T1", "ARRIVE_PICKUP", "pickup", Instant.now().plusSeconds(900).toEpochMilli(), "driver", "DRIVER")).isInstanceOf(IllegalArgumentException.class);
        when(storage.uploadFile(any(), any())).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(() -> service.upload(image(), 1, "T1", "ARRIVE_PICKUP", "pickup", System.currentTimeMillis(), "driver", "DRIVER")).hasMessageContaining("offline");
        verify(assets, never()).save(any());
    }
    @Test void rejectsUnstoredPhotoAndCrossTripCrossActorCrossCheckpointReuse() {
        var payload = new BookingDetailsPayload();
        when(assets.findByUrl(url)).thenReturn(Optional.of(asset()));
        assertThatThrownBy(() -> service.attach(payload, 1, "T2", "ARRIVE_PICKUP", "pickup", url, "driver")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.attach(payload, 1, "T1", "ARRIVE_PICKUP", "other", url, "driver")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.attach(payload, 1, "T1", "ARRIVE_PICKUP", "pickup", url, "escort")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.attach(payload, 1, "T1", "ARRIVE_PICKUP", "pickup", "capture_123.jpg", "driver")).isInstanceOf(IllegalArgumentException.class);
        assertThat(payload.getPhotoEvidence()).isNull();
        service.attach(payload, 1, "T1", "ARRIVE_PICKUP", "pickup", url, "driver");
        assertThat(payload.getPhotoEvidence()).containsKey(url);
    }
    @Test void cameraMetadataNeverLeaksInternalIncidentPhotosToCustomer() {
        var payload = new BookingDetailsPayload();
        var sos = asset(); sos.setPurpose("SOS");
        payload.setPhotoEvidence(Map.of(url, com.example.racehorse_transport.dto.file.CaptureEvidenceDto.of(sos)));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("customer", null, "ROLE_CUSTOMER"));
        assertThat(CaptureService.visibleEvidence(payload)).isEmpty();
        payload.setSettlement(Map.of("items", List.of(Map.of("photo", url))));
        assertThat(CaptureService.visibleEvidence(payload)).containsKey(url);
    }
}
