package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.manager.QuoteLineDto;
import com.example.racehorse_transport.dto.settlement.ExpenseItemDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Customer;
import com.example.racehorse_transport.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Mail gửi khách ở các mốc tiền: báo giá, đã nhận cọc / 70% / quyết toán, phát hành bảng quyết toán.
// Mail HTML kèm bản chữ thuần, có link mở đơn trên web; mail lỗi không làm hỏng việc đang làm.
class BillMailServiceTest {

    record Sent(String to, String subject, String html, String text) {}

    // Bộ gửi giả: ghi lại thư thay vì gửi thật
    static class RecordingSender extends OtpMailSender {
        final List<Sent> sent = new ArrayList<>();
        boolean configured = true;
        boolean fail = false;

        RecordingSender() {
            super(null, "", "", "smtp.test", "noreply@test");
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public void sendHtml(String to, String subject, String html, String text) {
            if (fail) throw new MailSendException("SMTP không phản hồi");
            sent.add(new Sent(to, subject, html, text));
        }
    }

    private final RecordingSender sender = new RecordingSender();
    private final BillMailService mail = new BillMailService(sender, "http://localhost:5173/payment/vnpay-return");

    private static Booking booking(String name, String email) {
        return booking(name, email, null);
    }

    private static Booking booking(String name, String email, String language) {
        User user = new User();
        user.setPreferredLanguage(language);
        user.setUsername("khach");
        user.setEmail(email);
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setFullName(name);
        return Booking.builder().id(33).customerID(customer).build();
    }

    private static QuoteLineDto line(String label, long amount) {
        QuoteLineDto l = new QuoteLineDto();
        l.setLabel(label);
        l.setAmount(BigDecimal.valueOf(amount));
        return l;
    }

    @Test
    void scheduleMailExplainsTheChoicesAndLinksToTheExistingOrderInBothLanguages() {
        Booking vi = booking("Khách", "customer@example.com", "vi");
        vi.setDepartureDate(Instant.parse("2026-11-29T17:00:00Z"));
        mail.scheduleChangeRequired(vi);
        Sent sent = sender.sent.getFirst();
        assertThat(sent.text()).contains("30/11/2026", "chọn ngày mới", "hủy yêu cầu chưa thanh toán", "/orders/BK-033");
        Booking en = booking("Customer", "customer@example.com", "en");
        en.setDepartureDate(vi.getDepartureDate());
        mail.scheduleChangeRequired(en);
        assertThat(sender.sent.getLast().text()).contains("choose a new date", "cancel the unpaid request", "ask for support");
    }

    @Test
    void quoteMailListsTheLinesTotalsDeadlineAndLinkToTheOrder() {
        mail.quoteIssued(booking("Customer Test", "khach@example.com"),
                List.of(line("Cước dịch vụ vận chuyển ngựa trọn gói", 14_420_000), line("Khoang tiêu chuẩn", 1_000_000)),
                new BigDecimal("15420000"), new BigDecimal("4626000"), new BigDecimal("10794000"),
                Instant.parse("2026-11-06T13:54:00Z"));

        assertThat(sender.sent).hasSize(1);
        Sent sent = sender.sent.get(0);
        assertThat(sent.to()).isEqualTo("khach@example.com");
        assertThat(sent.subject()).contains("Báo giá").contains("BK-033");
        assertThat(sent.html()).contains("Cước dịch vụ vận chuyển ngựa trọn gói").contains("14,420,000 ₫")
                .contains("15,420,000 ₫").contains("4,626,000 ₫").contains("10,794,000 ₫")
                .contains("06/11/2026 20:54")
                .contains("http://localhost:5173/orders/BK-033");
        assertThat(sent.text()).contains("15,420,000 ₫").contains("http://localhost:5173/orders/BK-033");
    }

    // Khách chọn EN trên giao diện thì nhận mail tiếng Anh
    @Test
    void customerWhoChoseEnglishGetsTheQuoteInEnglish() {
        mail.quoteIssued(booking("Customer Test", "khach@example.com", "en"), List.of(line("Transport", 14_420_000)),
                new BigDecimal("15420000"), new BigDecimal("4626000"), new BigDecimal("10794000"), Instant.parse("2026-11-06T13:54:00Z"));

        Sent sent = sender.sent.get(0);
        assertThat(sent.subject()).isEqualTo("Quotation for order BK-033");
        assertThat(sent.html()).contains("Hello Customer Test").contains("Deposit (30%)").contains("15,420,000 VND")
                .contains("06/11/2026 20:54").contains("View order BK-033").doesNotContain("Báo giá").doesNotContain("₫");
        assertThat(sent.text()).contains("Total: 15,420,000 VND");
    }

    @Test
    void englishReceiptAndSettlementUseEnglishLabels() {
        Booking english = booking("Customer Test", "khach@example.com", "en");
        mail.paymentReceived(english, BillMailService.Payment.DEPOSIT, new BigDecimal("5256000"), "15727222", Instant.parse("2026-11-05T13:56:52Z"), "VD-2026-0033");
        mail.settlementIssued(english, List.of(), BigDecimal.ZERO, Instant.parse("2026-11-07T06:43:00Z"));

        assertThat(sender.sent.get(0).subject()).isEqualTo("Payment received: Deposit (30%) · order BK-033");
        assertThat(sender.sent.get(0).html()).contains("Waybill: VD-2026-0033").contains("VNPAY transaction");
        assertThat(sender.sent.get(1).html()).contains("no additional charges");
    }

    @Test
    void paymentReceiptShowsWhatWasPaidTheAmountAndTheVnpayCode() {
        mail.paymentReceived(booking("Customer Test", "khach@example.com"), BillMailService.Payment.DEPOSIT, new BigDecimal("5256000"),
                "15727222", Instant.parse("2026-11-05T13:56:52Z"), "VD-2026-0033");

        Sent sent = sender.sent.get(0);
        assertThat(sent.subject()).contains("Đã nhận").contains("Đặt cọc 30%").contains("BK-033");
        assertThat(sent.html()).contains("5,256,000 ₫").contains("15727222").contains("05/11/2026 20:56").contains("VD-2026-0033");
    }

    @Test
    void settlementMailListsTheCustomerItemsTotalAndDueDate() {
        ExpenseItemDto vet = ExpenseItemDto.builder().description("Thú y Mộc Bài hạ sốt").amount(new BigDecimal("600000")).payer("customer").build();
        ExpenseItemDto stable = ExpenseItemDto.builder().description("Thuê chuồng tạm 1 đêm").amount(new BigDecimal("1500000")).payer("customer").build();

        mail.settlementIssued(booking("Customer Test", "khach@example.com"), List.of(vet, stable), new BigDecimal("2100000"),
                Instant.parse("2026-11-07T06:43:00Z"));

        Sent sent = sender.sent.get(0);
        assertThat(sent.subject()).contains("quyết toán").contains("BK-033");
        assertThat(sent.html()).contains("Thú y Mộc Bài hạ sốt").contains("600,000 ₫").contains("Thuê chuồng tạm 1 đêm")
                .contains("2,100,000 ₫").contains("07/11/2026 13:43");
    }

    @Test
    void zeroSettlementSaysThereIsNothingMoreToPay() {
        mail.settlementIssued(booking("Customer Test", "khach@example.com"), List.of(), BigDecimal.ZERO, Instant.parse("2026-11-07T06:43:00Z"));

        assertThat(sender.sent.get(0).html()).contains("không phát sinh khoản nào phải trả thêm");
    }

    @Test
    void namesAndDescriptionsAreEscapedInTheHtml() {
        mail.quoteIssued(booking("<script>alert(1)</script>", "khach@example.com"), List.of(line("<b>Khoang</b>", 1)),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, Instant.parse("2026-11-06T13:54:00Z"));

        String html = sender.sent.get(0).html();
        assertThat(html).doesNotContain("<script>").doesNotContain("<b>Khoang</b>").contains("&lt;script&gt;");
    }

    @Test
    void nothingIsSentWithoutAnEmailOrWhenMailIsNotConfigured() {
        mail.paymentReceived(booking("Customer Test", " "), BillMailService.Payment.DEPOSIT, BigDecimal.TEN, "1", Instant.now(), null);
        sender.configured = false;
        mail.paymentReceived(booking("Customer Test", "khach@example.com"), BillMailService.Payment.DEPOSIT, BigDecimal.TEN, "1", Instant.now(), null);

        assertThat(sender.sent).isEmpty();
    }

    @Test
    void aMailFailureNeverBreaksThePaymentOrQuote() {
        sender.fail = true;

        assertThatCode(() -> mail.paymentReceived(booking("Customer Test", "khach@example.com"), BillMailService.Payment.BALANCE,
                BigDecimal.TEN, "1", Instant.now(), null)).doesNotThrowAnyException();
    }
}
