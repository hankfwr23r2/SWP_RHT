package com.example.racehorse_transport;

import com.example.racehorse_transport.controller.ManagerBookingController;
import com.example.racehorse_transport.dto.manager.RejectBookingRequest;
import com.example.racehorse_transport.dto.manager.SendBackRequest;
import com.example.racehorse_transport.dto.payment.ReconcileInsuranceRequest;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BookingService;
import com.example.racehorse_transport.service.PaymentService;
import com.example.racehorse_transport.service.impl.PaymentServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class SwtManagerWorkflowTest {

    private final BookingService bookingService = mock(BookingService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final QuotationRepository quotationRepository = mock(QuotationRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("QU-003: Quản lý từ chối đơn hàng bắt buộc phải có lý do")
    void testManagerRejectRequiresReason() {
        ManagerBookingController controller = new ManagerBookingController(bookingService, paymentService);
        TestingAuthenticationToken auth = new TestingAuthenticationToken("manager01", null, "ROLE_MANAGER");

        RejectBookingRequest request = RejectBookingRequest.builder()
                .reason("Xe bận lịch đột xuất không thể phục vụ")
                .build();

        controller.rejectBooking("BK-100", request, auth);
        verify(bookingService).rejectBooking(eq("BK-100"), any(RejectBookingRequest.class));
    }

    @Test
    @DisplayName("QU-007: Trả lại đơn cho điều phối bắt buộc nhập lý do")
    void testManagerSendBackRequiresReason() {
        ManagerBookingController controller = new ManagerBookingController(bookingService, paymentService);

        SendBackRequest request = SendBackRequest.builder()
                .to("coordinator")
                .reason("Tuyến đường qua đèo cần chọn xe gầm cao hơn")
                .by("manager01")
                .build();

        controller.sendBack("BK-101", request);
        verify(bookingService).sendBack(eq("BK-101"), eq(request));
    }

    @Test
    @DisplayName("QU-011: Đối soát bảo hiểm của đơn hủy tuân thủ công thức hoàn tiền")
    void testInsuranceReconciliationFormula() {
        PaymentServiceImpl paymentServiceImpl = new PaymentServiceImpl(
                paymentRepository,
                bookingRepository,
                quotationRepository,
                objectMapper,
                mock(com.example.racehorse_transport.service.StaffNotifier.class)
        );

        Booking booking = new Booking();
        booking.setId(99);
        booking.setStatus("cancelled");
        booking.setDetailsJson("""
            {
              "quote": {
                "lines": [
                  {
                    "label": "Thu hộ bảo hiểm bên thứ ba",
                    "amount": 5000000
                  }
                ]
              },
              "payment": {
                "insuranceReconciliationPending": true,
                "insuranceCollected": 5000000
              }
            }
            """);

        when(bookingRepository.findByIdForUpdate(99)).thenReturn(Optional.of(booking));

        // Nhập sai công thức (Thu hộ 5tr - Chuyển đối tác 4tr + Đối tác hoàn 1tr = Khách nhận 2tr, nhưng nhập 3tr)
        ReconcileInsuranceRequest wrongReq = new ReconcileInsuranceRequest(
                new BigDecimal("4000000"),
                new BigDecimal("1000000"),
                new BigDecimal("3000000"), // Sai công thức
                "REF-ERR",
                "Đối soát thử nghiệm"
        );

        assertThatThrownBy(() -> paymentServiceImpl.reconcileInsurance("BK-99", wrongReq, "manager01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tiền hoàn khách phải bằng số thu hộ trừ số chuyển đối tác cộng số đối tác hoàn");
    }

    @Test
    @DisplayName("QU-011: Chặn đối soát bảo hiểm lần 2 khi không còn cờ pending")
    void testInsuranceReconciliationBlockDoubleReconciliation() {
        PaymentServiceImpl paymentServiceImpl = new PaymentServiceImpl(
                paymentRepository,
                bookingRepository,
                quotationRepository,
                objectMapper,
                mock(com.example.racehorse_transport.service.StaffNotifier.class)
        );

        Booking booking = new Booking();
        booking.setId(98);
        booking.setStatus("cancelled");
        // Đã đối soát xong, cờ insuranceReconciliationPending = false
        booking.setDetailsJson("""
            {
              "payment": {
                "insuranceReconciliationPending": false
              }
            }
            """);

        when(bookingRepository.findByIdForUpdate(98)).thenReturn(Optional.of(booking));

        ReconcileInsuranceRequest req = new ReconcileInsuranceRequest(
                new BigDecimal("1000000"),
                new BigDecimal("500000"),
                new BigDecimal("500000"),
                "REF-ALREADY",
                "Đối soát lần hai"
        );

        assertThatThrownBy(() -> paymentServiceImpl.reconcileInsurance("BK-98", req, "manager01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Đơn không có khoản bảo hiểm chờ đối soát hoặc đã đối soát");
    }
}
