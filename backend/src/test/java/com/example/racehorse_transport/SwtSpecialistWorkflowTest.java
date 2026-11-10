package com.example.racehorse_transport;

import com.example.racehorse_transport.dto.booking.HorseInsuranceDto;
import com.example.racehorse_transport.dto.specialist.ApproveMedicalRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.LocationRepository;
import com.example.racehorse_transport.repository.RegulationRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.impl.SpecialistServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class SwtSpecialistWorkflowTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final StaffRepository staffRepository = mock(StaffRepository.class);
    private final RegulationRepository regulationRepository = mock(RegulationRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SpecialistServiceImpl specialistService;

    @BeforeEach
    void setUp() {
        specialistService = new SpecialistServiceImpl(
                bookingRepository,
                staffRepository,
                regulationRepository,
                locationRepository,
                objectMapper,
                mock(com.example.racehorse_transport.service.StaffNotifier.class)
        );

        Staff specialistA = new Staff();
        specialistA.setRole("SPECIALIST");
        specialistA.setStaffCode("SP-01");
        when(staffRepository.findByUser_Username("specialistA")).thenReturn(Optional.of(specialistA));

        Staff specialistB = new Staff();
        specialistB.setRole("SPECIALIST");
        specialistB.setStaffCode("SP-02");
        when(staffRepository.findByUser_Username("specialistB")).thenReturn(Optional.of(specialistB));
    }

    @Test
    @DisplayName("QU-022: Chuyên viên chỉ được duyệt hồ sơ khi đơn ở bước thẩm định (under_review)")
    void testApproveMedicalOnlyWhenUnderReview() {
        Booking booking = new Booking();
        booking.setId(201);
        booking.setStatus("confirmed"); // Đã qua bước thẩm định
        booking.setDetailsJson("""
            {
              "intake": {
                "specialist": {
                  "id": "SP-01"
                }
              }
            }
            """);

        when(bookingRepository.findById(201)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(201)).thenReturn(Optional.of(booking));

        ApproveMedicalRequest request = new ApproveMedicalRequest();
        request.setNote("Đủ giấy tờ");

        assertThatThrownBy(() -> specialistService.approveMedical("BK-201", request, "specialistA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Chỉ duyệt hồ sơ ngựa khi đơn đang ở bước thẩm định.");
    }

    @Test
    @DisplayName("QU-023: Nhập báo giá bảo hiểm phí phải lớn hơn 0")
    void testInsurancePremiumMustBeGreaterThanZero() {
        Booking booking = new Booking();
        booking.setId(202);
        booking.setStatus("under_review");
        booking.setDetailsJson("""
            {
              "intake": {
                "specialist": {
                  "id": "SP-01"
                }
              },
              "horses": [
                {
                  "horseId": "H1",
                  "name": "Ngựa Thần Mã",
                  "insurance": {
                    "option": "assistance"
                  }
                }
              ]
            }
            """);

        when(bookingRepository.findById(202)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(202)).thenReturn(Optional.of(booking));

        // Nhập phí = 0
        HorseInsuranceDto dto = HorseInsuranceDto.builder()
                .option("assistance")
                .provider("Bảo Việt")
                .premium(BigDecimal.ZERO) // Sai: phí phải > 0
                .reviewed(true)
                .build();

        assertThatThrownBy(() -> specialistService.updateInsurance("BK-202", "H1", dto, "specialistA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nhập đơn vị cung cấp và phí bảo hiểm lớn hơn 0.");
    }

    @Test
    @DisplayName("QU-025: Không sửa hợp đồng bảo hiểm đã được xác nhận có hiệu lực")
    void testCannotEditConfirmedInsuranceContract() {
        Booking booking = new Booking();
        booking.setId(203);
        booking.setStatus("waybill_issued"); // Đã cọc và phát hành vận đơn
        booking.setDetailsJson("""
            {
              "intake": {
                "specialist": {
                  "id": "SP-01"
                }
              },
              "horses": [
                {
                  "horseId": "H1",
                  "name": "Bạch Mã",
                  "insurance": {
                    "option": "assistance",
                    "provider": "Bảo Việt",
                    "premium": 1500000,
                    "policyNumber": "HD-BH-001",
                    "arranged": true
                  }
                }
              ]
            }
            """);

        when(bookingRepository.findById(203)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(203)).thenReturn(Optional.of(booking));

        // Thử thay đổi số hợp đồng hoặc ngày khi arranged đã true
        HorseInsuranceDto dto = HorseInsuranceDto.builder()
                .option("assistance")
                .policyNumber("HD-BH-NEW")
                .arranged(true)
                .build();

        assertThatThrownBy(() -> specialistService.updateInsurance("BK-203", "H1", dto, "specialistA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hợp đồng bảo hiểm đã được xác nhận có hiệu lực, không thể chỉnh sửa tại bước này.");
    }

    @Test
    @DisplayName("QU-026: Khóa bảo hiểm khi chưa đặt cọc (ở trạng thái awaiting_payment)")
    void testLockInsuranceBeforeDeposit() {
        Booking booking = new Booking();
        booking.setId(204);
        booking.setStatus("awaiting_payment"); // Đang chờ khách đặt cọc
        booking.setDetailsJson("""
            {
              "intake": {
                "specialist": {
                  "id": "SP-01"
                }
              },
              "horses": [
                {
                  "horseId": "H1",
                  "name": "Bạch Mã",
                  "insurance": {
                    "option": "assistance",
                    "provider": "Bảo Việt",
                    "premium": 1500000,
                    "reviewed": true
                  }
                }
              ]
            }
            """);

        when(bookingRepository.findById(204)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByIdForUpdate(204)).thenReturn(Optional.of(booking));

        HorseInsuranceDto dto = HorseInsuranceDto.builder()
                .option("assistance")
                .policyNumber("HD-BH-001")
                .arranged(true)
                .build();

        assertThatThrownBy(() -> specialistService.updateInsurance("BK-204", "H1", dto, "specialistA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không thể sửa bảo hiểm ở trạng thái hiện tại.");
    }

    @Test
    @DisplayName("QU-033: Chuyên viên phân quyền: Chuyên viên B không được thao tác đơn của Chuyên viên A")
    void testSpecialistRoleAssignmentAuthorization() {
        Booking booking = new Booking();
        booking.setId(205);
        booking.setStatus("under_review");
        // Đơn được phân công cho SP-01 (specialistA)
        booking.setDetailsJson("""
            {
              "intake": {
                "specialist": {
                  "id": "SP-01"
                }
              }
            }
            """);

        when(bookingRepository.findById(205)).thenReturn(Optional.of(booking));

        // specialistB (SP-02) cố mở đơn của SP-01
        assertThatThrownBy(() -> specialistService.getBookingDetail("BK-205", "specialistB"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Đơn hàng chưa được phân công cho tài khoản này.");
    }
}
