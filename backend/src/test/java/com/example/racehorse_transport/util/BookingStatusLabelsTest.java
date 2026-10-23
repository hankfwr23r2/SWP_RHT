package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.BookingResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStatusLabelsTest {

    @Test
    void deliveryStatusesHaveLabelsForStaffAndCustomers() {
        for (String status : new String[] { "arrived", "handover_timeout", "returning_to_hq" }) {
            assertThat(BookingStatusLabels.staffLabel(status)).isNotEqualTo(status);
            assertThat(BookingStatusLabels.customerLabel(status)).isNotEqualTo(status);
        }
    }

    @Test
    void bookingResponseCarriesTheLabelsOfItsStatus() {
        BookingResponse response = BookingResponse.builder().status("waybill_issued").build();

        assertThat(response.getStatusLabel()).isEqualTo("Đã có Vận đơn, chờ kiểm dịch viên tiếp nhận");
        assertThat(response.getCustomerStatusLabel()).isEqualTo("Đã có vận đơn");
    }

    @Test
    void unknownStatusFallsBackToItsCode() {
        assertThat(BookingStatusLabels.staffLabel("new_status")).isEqualTo("new_status");
        assertThat(BookingStatusLabels.customerLabel(null)).isEqualTo("");
    }
}
