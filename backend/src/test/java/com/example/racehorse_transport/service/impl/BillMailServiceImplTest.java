package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.User;
import com.example.racehorse_transport.service.MailDeliveryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillMailServiceImplTest {

    @Test
    void balanceDueMailTellsTheCustomerHowMuchIsLeftAndLinksToTheOrder() {
        MailDeliveryService sender = mock(MailDeliveryService.class);
        when(sender.isConfigured()).thenReturn(true);
        var service = new BillMailServiceImpl(sender, "http://localhost:5173/payment/return");
        User user = new User();
        user.setEmail("khach@example.com");
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName("Khách");
        Booking booking = Booking.builder().id(1).code("BK-AAAA0001").customerID(customer).build();

        service.balanceDue(booking, new BigDecimal("7000000"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sender).sendHtml(eq("khach@example.com"), subject.capture(), html.capture(), text.capture());
        assertThat(subject.getValue()).isEqualTo("Thanh toán 70% còn lại · đơn BK-AAAA0001");
        assertThat(text.getValue()).contains("7,000,000 ₫").contains("Kíp xe đã nhận ngựa").contains("/orders/BK-AAAA0001");
        assertThat(html.getValue()).contains("7,000,000 ₫");
    }
}
