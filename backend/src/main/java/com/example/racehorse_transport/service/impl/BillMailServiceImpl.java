package com.example.racehorse_transport.service.impl;

import static com.example.racehorse_transport.util.MailHtml.money;
import static com.example.racehorse_transport.util.MailHtml.paragraph;
import static com.example.racehorse_transport.util.MailHtml.row;
import static com.example.racehorse_transport.util.MailHtml.table;
import static com.example.racehorse_transport.util.MailHtml.time;

import com.example.racehorse_transport.client.MailClient;
import com.example.racehorse_transport.client.VnpayClient;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Payment;
import com.example.racehorse_transport.entity.PaymentTransaction;
import com.example.racehorse_transport.entity.Quotation;
import com.example.racehorse_transport.entity.QuotationLine;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.PaymentStatus;
import com.example.racehorse_transport.entity.enums.PaymentTransactionStatus;
import com.example.racehorse_transport.entity.enums.PaymentType;
import com.example.racehorse_transport.entity.enums.QuotationStatus;
import com.example.racehorse_transport.policy.BillStatementPolicy;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.PaymentRepository;
import com.example.racehorse_transport.repository.PaymentTransactionRepository;
import com.example.racehorse_transport.repository.QuotationLineRepository;
import com.example.racehorse_transport.repository.QuotationRepository;
import com.example.racehorse_transport.service.BillMailService;
import com.example.racehorse_transport.util.MailHtml;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

// Theo BillMailServiceImpl của nhánh main, đổi nguồn số liệu sang quotation, quotation_line, payment, payment_transaction.
// Dữ liệu đọc lại sau khi transaction gốc đã lưu, nên khoản hoàn tạo cùng lúc với lệnh hủy cũng có trong mail.
@Slf4j
@Service
public class BillMailServiceImpl implements BillMailService {

    enum Kind { QUOTE, DEPOSIT_RECEIPT, SCHEDULE_CHANGE, CANCELLED, STATEMENT }

    record Mail(String to, String subject, String html, String text) {
    }

    private final MailClient mail;
    private final BookingRepository bookings;
    private final QuotationRepository quotations;
    private final QuotationLineRepository lines;
    private final PaymentRepository payments;
    private final PaymentTransactionRepository transactions;
    private final TransactionTemplate readOnly;
    private final String webBase;

    public BillMailServiceImpl(MailClient mail, BookingRepository bookings, QuotationRepository quotations,
                               QuotationLineRepository lines, PaymentRepository payments,
                               PaymentTransactionRepository transactions, PlatformTransactionManager transactionManager,
                               @Value("${app.customer-web-url:http://localhost:5173}") String webBase) {
        this.mail = mail;
        this.bookings = bookings;
        this.quotations = quotations;
        this.lines = lines;
        this.payments = payments;
        this.transactions = transactions;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
        this.webBase = webBase == null ? "" : webBase.replaceAll("/+$", "");
    }

    @Override
    public void statusChanged(Long bookingId, BookingStatus from, BookingStatus to, String reason) {
        Kind kind = kindOf(from, to);
        if (kind == null) return;
        Runnable task = () -> {
            try {
                Mail message = readOnly.execute(status -> compose(bookingId, kind, reason));
                if (message != null) mail.sendHtml(message.to(), message.subject(), message.html(), message.text());
            } catch (RuntimeException ex) {
                log.warn("Không gửi được mail {} của đơn {}: {}", kind, bookingId, ex.getMessage());
            }
        };
        // Chỉ gửi khi giao dịch đã lưu xong, để không báo khách về việc rồi bị hoàn tác
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    static Kind kindOf(BookingStatus from, BookingStatus to) {
        if (to == BookingStatus.AWAITING_DEPOSIT) return Kind.QUOTE;
        if (to == BookingStatus.CONFIRMED && from == BookingStatus.AWAITING_DEPOSIT) return Kind.DEPOSIT_RECEIPT;
        if (to == BookingStatus.SCHEDULE_CHANGE_REQUIRED) return Kind.SCHEDULE_CHANGE;
        if (to == BookingStatus.CANCELLED) return Kind.CANCELLED;
        if (to == BookingStatus.COMPLETED) return Kind.STATEMENT;
        return null;
    }

    Mail compose(Long bookingId, Kind kind, String reason) {
        Booking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null || booking.getCustomer() == null || booking.getCustomer().getUser() == null) return null;
        String to = booking.getCustomer().getUser().getEmail();
        if (to == null || to.isBlank()) return null;
        boolean en = "en".equalsIgnoreCase(booking.getCustomer().getUser().getPreferredLanguage());
        String code = booking.getCode();
        return switch (kind) {
            case QUOTE -> quote(booking, en).map(body -> mail(booking, to.trim(), en,
                    en ? "Quotation for order " + code : "Báo giá đơn " + code, en ? "Transport quotation" : "Báo giá vận chuyển",
                    body, "")).orElse(null);
            case DEPOSIT_RECEIPT -> mail(booking, to.trim(), en, en ? "Deposit received · order " + code : "Đã nhận tiền cọc · đơn " + code,
                    en ? "Payment receipt" : "Biên nhận thanh toán", depositReceipt(booking, en), "");
            case SCHEDULE_CHANGE -> mail(booking, to.trim(), en, en ? "Please choose a new departure · order " + code
                            : "Cần chọn lại chuyến · đơn " + code, en ? "New departure needed" : "Cần chọn lại chuyến",
                    scheduleChange(reason, en), "#reschedule");
            case CANCELLED -> mail(booking, to.trim(), en, en ? "Order cancelled · " + code : "Đã hủy đơn " + code,
                    en ? "Order cancelled" : "Đơn đã hủy", cancellation(booking, reason, en), "");
            case STATEMENT -> mail(booking, to.trim(), en, en ? "Final statement · order " + code : "Bảng quyết toán · đơn " + code,
                    en ? "Final statement" : "Bảng quyết toán", statement(booking, en), "");
        };
    }

    private java.util.Optional<String> quote(Booking booking, boolean en) {
        return quotations.findByBooking_IdAndStatus(booking.getId(), QuotationStatus.SENT).map(quotation -> {
            StringBuilder rows = new StringBuilder();
            for (QuotationLine line : lines.findByQuotation_IdOrderByLineNumber(quotation.getId())) {
                String label = line.getDescription() != null && !line.getDescription().isBlank() ? line.getDescription()
                        : line.getBillableService() != null ? line.getBillableService().getName() : "-";
                if (Boolean.TRUE.equals(line.getPassThrough())) label += en ? " (collected on behalf)" : " (thu hộ)";
                rows.append(row(label, money(line.getAmount(), en), false));
            }
            rows.append(row(en ? "Total" : "Tổng cộng", money(quotation.getTotalAmount(), en), true));
            rows.append(row(en ? "Deposit" : "Tiền cọc", money(quotation.getDepositAmount(), en), false));
            rows.append(row(en ? "Remaining balance" : "Số dư", money(BillStatementPolicy.balance(
                    quotation.getTotalAmount(), quotation.getDepositAmount()), en), false));
            String note = en
                    ? "Please pay the deposit by " + time(quotation.getExpiresAt()) + " to keep your place on the departure. "
                      + "The remaining balance is paid when the company receives your horses; the exact deadline is shown on the order page."
                    : "Vui lòng đặt cọc trước " + time(quotation.getExpiresAt()) + " để giữ chỗ trên chuyến. "
                      + "Số dư thanh toán khi công ty nhận ngựa; hạn cụ thể hiện trên trang đơn.";
            return paragraph(en ? "Here is the quotation for order " + booking.getCode() + "."
                    : "Công ty gửi báo giá cho đơn " + booking.getCode() + ".") + table(rows.toString()) + paragraph(note);
        });
    }

    private String depositReceipt(Booking booking, boolean en) {
        List<Payment> all = payments.findByBooking_IdOrderById(booking.getId());
        Payment deposit = all.stream().filter(p -> p.getPaymentType() == PaymentType.DEPOSIT && p.getStatus() == PaymentStatus.PAID)
                .max(Comparator.comparing(Payment::getId)).orElse(null);
        String transaction = transactions.findByPayment_Booking_IdAndPayment_PaymentTypeAndGatewayCodeAndStatus(booking.getId(),
                        PaymentType.DEPOSIT, VnpayClient.GATEWAY_CODE, PaymentTransactionStatus.SUCCESS).stream()
                .map(PaymentTransaction::getGatewayTransactionNumber).filter(Objects::nonNull).reduce((a, b) -> b).orElse("-");
        var summary = BillStatementPolicy.statement(acceptedTotal(booking), all);
        String rows = row(en ? "Payment" : "Khoản thanh toán", en ? "Deposit" : "Tiền cọc", false)
                + row(en ? "Amount" : "Số tiền", money(deposit == null ? null : deposit.getAmount(), en), true)
                + row(en ? "VNPAY transaction" : "Mã giao dịch VNPAY", transaction, false)
                + row(en ? "Time" : "Thời gian", time(deposit == null ? null : deposit.getPaidAt()), false)
                + (booking.getWaybillNumber() == null ? "" : row(en ? "Waybill" : "Vận đơn", booking.getWaybillNumber(), false))
                + row(en ? "Remaining balance" : "Số dư còn lại", money(summary.outstanding(), en), false);
        return paragraph(en ? "We have received the deposit for order " + booking.getCode() + ". Your place on the departure is confirmed."
                : "Công ty đã nhận tiền cọc của đơn " + booking.getCode() + ". Chỗ trên chuyến đã được xác nhận.") + table(rows);
    }

    private String scheduleChange(String reason, boolean en) {
        String why = reason == null || reason.isBlank() ? "" : (en ? "Reason: " : "Lý do: ") + reason.strip();
        return paragraph(en ? "Your order can no longer travel on the planned departure. Please choose another departure "
                        + "or cancel the order on the order page."
                : "Đơn không còn đi được chuyến đã chọn. Vui lòng chọn chuyến khác hoặc hủy đơn trên trang đơn.")
                + (why.isEmpty() ? "" : paragraph(why));
    }

    private String cancellation(Booking booking, String reason, boolean en) {
        var summary = BillStatementPolicy.statement(acceptedTotal(booking), payments.findByBooking_IdOrderById(booking.getId()));
        String why = reason == null || reason.isBlank() ? "" : paragraph((en ? "Reason: " : "Lý do: ") + reason.strip());
        String intro = paragraph(en ? "Order " + booking.getCode() + " has been cancelled." : "Đơn " + booking.getCode() + " đã hủy.");
        if (summary.paid().signum() == 0) return intro + why;
        Instant refundDue = payments.findByBooking_IdOrderById(booking.getId()).stream()
                .filter(p -> p.getPaymentType() == PaymentType.REFUND && p.getStatus() == PaymentStatus.PENDING)
                .map(Payment::getDueAt).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String rows = row(en ? "Paid" : "Đã thanh toán", money(summary.paid(), en), false)
                + row(en ? "Refund" : "Được hoàn", money(summary.refunded().add(summary.refundPending()), en), false)
                + row(en ? "Retained by the company" : "Công ty giữ lại", money(summary.retained(), en), true)
                + (refundDue == null ? "" : row(en ? "Refund by" : "Hạn hoàn", time(refundDue), false));
        return intro + why + table(rows);
    }

    private String statement(Booking booking, boolean en) {
        List<Payment> all = payments.findByBooking_IdOrderById(booking.getId());
        var summary = BillStatementPolicy.statement(acceptedTotal(booking), all);
        StringBuilder rows = new StringBuilder(row(en ? "Accepted quotation" : "Báo giá đã chấp nhận", money(summary.quoted(), en), false));
        if (summary.extraCharges().signum() > 0) rows.append(row(en ? "Extra charges" : "Phụ thu", money(summary.extraCharges(), en), false));
        for (Payment p : all) {
            if (p.getStatus() != PaymentStatus.PAID) continue;
            rows.append(row(label(p.getPaymentType(), en) + " · " + time(p.getPaidAt()), money(p.getAmount(), en), false));
        }
        rows.append(row(en ? "Outstanding" : "Còn nợ", money(summary.outstanding(), en), true));
        return paragraph(en ? "Order " + booking.getCode() + " has been settled and closed. Thank you for choosing us."
                : "Đơn " + booking.getCode() + " đã quyết toán và đóng. Cảm ơn quý khách đã sử dụng dịch vụ.") + table(rows.toString());
    }

    private java.math.BigDecimal acceptedTotal(Booking booking) {
        return quotations.findByBooking_IdAndStatus(booking.getId(), QuotationStatus.ACCEPTED)
                .map(Quotation::getTotalAmount).orElse(java.math.BigDecimal.ZERO);
    }

    private static String label(PaymentType type, boolean en) {
        return switch (type) {
            case DEPOSIT -> en ? "Deposit" : "Tiền cọc";
            case BALANCE -> en ? "Remaining balance" : "Số dư";
            case EXTRA_CHARGE -> en ? "Extra charge" : "Phụ thu";
            case REFUND -> en ? "Refund" : "Hoàn tiền";
        };
    }

    private Mail mail(Booking booking, String to, boolean en, String subject, String title, String body, String anchor) {
        String name = booking.getCustomer().getFullName();
        String greeting = (en ? "Hello " : "Chào ") + (name == null || name.isBlank() ? (en ? "there" : "quý khách") : name) + ",";
        String link = webBase + "/orders/" + booking.getId() + anchor;
        String linkLabel = (en ? "View order " : "Xem đơn ") + booking.getCode();
        String footer = en ? "Horse Transport · automated message, please do not reply." : "Vận chuyển Ngựa · thư tự động, vui lòng không trả lời.";
        String html = MailHtml.page(title, greeting, body, link, linkLabel, footer);
        // Bản chữ thuần cho trình đọc thư không hiện HTML: mỗi dòng bảng một dòng "nhãn: giá trị"
        String plain = body.replace("</p>", "\n").replace("<tr>", "\n").replaceAll("</td><td[^>]*>", ": ")
                .replaceAll("<[^>]+>", "").replaceAll("[ \\t]+", " ").replaceAll("\n\\s*\n+", "\n").trim();
        String text = greeting + "\n\n" + title + " · " + booking.getCode() + "\n" + HtmlUtils.htmlUnescape(plain)
                + "\n\n" + linkLabel + ": " + link + "\n";
        return new Mail(to, subject, html, text);
    }
}
