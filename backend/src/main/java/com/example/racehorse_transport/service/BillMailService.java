package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.manager.QuoteLineDto;
import com.example.racehorse_transport.dto.settlement.ExpenseItemDto;
import com.example.racehorse_transport.entity.Booking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Mail hóa đơn (tiếng Việt hoặc tiếng Anh theo lựa chọn VI/EN của khách) gửi ở các mốc tiền: báo giá, đã nhận cọc / 70% / quyết toán, phát hành bảng quyết toán.
// Chỉ gửi sau khi giao dịch đã lưu; mail lỗi thì ghi log, không làm hỏng việc đang làm.
@Slf4j
@Service
public class BillMailService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(VN);

    private final OtpMailSender sender;
    private final String webBase;

    // Địa chỉ trang khách lấy theo trang trả về của VNPay (local: localhost:5173, trên mạng: Vercel)
    public BillMailService(OtpMailSender sender, @Value("${vnpay.return-url:}") String vnpayReturnUrl) {
        this.sender = sender;
        this.webBase = originOf(vnpayReturnUrl);
    }

    // Khoản thanh toán trong mail biên nhận, có sẵn tên tiếng Việt và tiếng Anh
    public enum Payment {
        DEPOSIT("Đặt cọc 30%", "Deposit (30%)"),
        BALANCE("Thanh toán 70% còn lại", "Remaining balance (70%)"),
        SETTLEMENT("Thanh toán quyết toán", "Final settlement");

        private final String vi;
        private final String en;

        Payment(String vi, String en) {
            this.vi = vi;
            this.en = en;
        }
    }

    public void scheduleChangeRequired(Booking booking) {
        boolean en = english(booking);
        String day = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VN).format(booking.getDepartureDate());
        String message = en
                ? "The requested departure date " + day + " has passed before preparations were completed. Please choose a new date, cancel the unpaid request, or ask for support on the order page. The company will review and confirm your new schedule."
                : "Ngày khởi hành dự kiến " + day + " đã qua, nhưng đơn chưa hoàn tất chuẩn bị chuyến. Vui lòng chọn ngày mới, hủy yêu cầu chưa thanh toán hoặc liên hệ hỗ trợ trong trang đơn hàng. Công ty sẽ kiểm tra và xác nhận lịch mới.";
        send(booking, (en ? "Please confirm a new schedule: " : "Cần xác nhận lại lịch: ") + code(booking),
                en ? "New departure date needed" : "Cần xác nhận lại lịch", "<p>" + esc(message) + "</p>", message);
    }

    public void quoteIssued(Booking booking, List<QuoteLineDto> lines, BigDecimal total, BigDecimal deposit,
            BigDecimal balance, Instant expiresAt) {
        boolean en = english(booking);
        StringBuilder rows = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (QuoteLineDto line : lines == null ? List.<QuoteLineDto>of() : lines) {
            rows.append(row(line.getLabel(), money(line.getAmount(), en)));
            text.append("- ").append(line.getLabel()).append(": ").append(money(line.getAmount(), en)).append('\n');
        }
        String totalLabel = en ? "Total" : "Tổng cộng";
        String depositLabel = en ? "Deposit (30%)" : "Đặt cọc 30%";
        String balanceLabel = en ? "Remaining balance (70%)" : "Còn lại 70%";
        rows.append(row("<b>" + totalLabel + "</b>", "<b>" + money(total, en) + "</b>", true));
        text.append(totalLabel).append(": ").append(money(total, en)).append('\n')
                .append(depositLabel).append(": ").append(money(deposit, en))
                .append(en ? " (due " : " (hạn ").append(time(expiresAt)).append(")\n")
                .append(balanceLabel).append(": ").append(money(balance, en))
                .append(en ? " (paid once the crew has picked up the horses)\n" : " (trả khi kíp xe nhận ngựa xong)\n");
        String body = en
                ? "<p>We have sent you the quotation for order <b>" + code(booking) + "</b>.</p>" + table(rows.toString())
                        + "<p>" + depositLabel + ": <b>" + money(deposit, en) + "</b>, due by <b>" + time(expiresAt) + "</b>.<br>"
                        + balanceLabel + ": <b>" + money(balance, en) + "</b>, paid once the crew has picked up the horses and signed the handover record.</p>"
                : "<p>Hệ thống đã gửi báo giá cho đơn <b>" + code(booking) + "</b>.</p>" + table(rows.toString())
                        + "<p>" + depositLabel + ": <b>" + money(deposit, en) + "</b>, hạn đặt cọc <b>" + time(expiresAt) + "</b>.<br>"
                        + balanceLabel + ": <b>" + money(balance, en) + "</b>, thanh toán khi kíp xe nhận ngựa và ký biên bản giao nhận.</p>";
        send(booking, en ? "Quotation for order " + code(booking) : "Báo giá đơn " + code(booking),
                en ? "Transport quotation" : "Báo giá vận chuyển", body, text.toString());
    }

    public void paymentReceived(Booking booking, Payment payment, BigDecimal amount, String transactionNo, Instant paidAt, String waybillNo) {
        boolean en = english(booking);
        String what = en ? payment.en : payment.vi;
        String txn = transactionNo == null ? "-" : transactionNo;
        String waybill = waybillNo == null || waybillNo.isBlank() ? null : (en ? "Waybill: " : "Vận đơn: ") + waybillNo;
        String rows = row(en ? "Payment" : "Khoản thanh toán", esc(what)) + row(en ? "Amount" : "Số tiền", "<b>" + money(amount, en) + "</b>")
                + row(en ? "VNPAY transaction" : "Mã giao dịch VNPAY", esc(txn)) + row(en ? "Time" : "Thời gian", time(paidAt))
                + (waybill == null ? "" : row(en ? "Note" : "Ghi chú", esc(waybill)));
        String text = what + ": " + money(amount, en) + (en ? "\nVNPAY transaction: " : "\nMã giao dịch VNPAY: ") + txn
                + (en ? "\nTime: " : "\nThời gian: ") + time(paidAt) + (waybill == null ? "" : "\n" + waybill) + "\n";
        send(booking, en ? "Payment received: " + what + " · order " + code(booking) : "Đã nhận " + what + " · đơn " + code(booking),
                en ? "Payment receipt" : "Biên nhận thanh toán",
                (en ? "<p>We have received the payment for order <b>" : "<p>Hệ thống đã nhận khoản thanh toán của đơn <b>")
                        + code(booking) + "</b>.</p>" + table(rows), text);
    }

    /** Retired: internal company expenses must never be mailed to customers. */
    @Deprecated
    public void settlementIssued(Booking booking, List<ExpenseItemDto> customerItems, BigDecimal total, Instant dueAt) {
        // Kept for callers upgrading from the former customer settlement workflow.
    }

    // Ngôn ngữ khách chọn trên giao diện (lưu ở tài khoản); chưa chọn thì tiếng Việt
    private static boolean english(Booking booking) {
        return booking.getCustomerID() != null && booking.getCustomerID().getUser() != null
                && "en".equalsIgnoreCase(booking.getCustomerID().getUser().getPreferredLanguage());
    }

    private void send(Booking booking, String subject, String title, String bodyHtml, String bodyText) {
        String to = booking.getCustomerID() != null && booking.getCustomerID().getUser() != null
                ? booking.getCustomerID().getUser().getEmail() : null;
        if (to == null || to.isBlank() || !sender.isConfigured()) return;
        boolean en = english(booking);
        String name = booking.getCustomerID().getFullName() != null ? booking.getCustomerID().getFullName() : (en ? "there" : "bạn");
        String link = webBase + "/orders/" + code(booking);
        String view = (en ? "View order " : "Xem đơn ") + code(booking);
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1e293b\">"
                + "<h2 style=\"color:#c2410c;margin:0 0 12px\">" + title + "</h2>"
                + "<p>" + (en ? "Hello " : "Chào ") + esc(name) + ",</p>" + bodyHtml
                + "<p><a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:10px 18px;background:#c2410c;color:#fff;"
                + "border-radius:8px;text-decoration:none\">" + view + "</a></p>"
                + "<p style=\"color:#64748b;font-size:12px\">"
                + (en ? "Horse Transport · automated message, please do not reply." : "Vận chuyển Ngựa · thư tự động, vui lòng không trả lời.")
                + "</p></div>";
        String text = (en ? "Hello " : "Chào ") + name + ",\n\n" + title + " · " + code(booking) + "\n" + bodyText
                + "\n" + view + ": " + link + "\n";
        Runnable task = () -> {
            try {
                sender.sendHtml(to.trim(), subject, html, text);
            } catch (RuntimeException ex) {
                log.warn("Không gửi được mail \"{}\" tới {}: {}", subject, to, ex.getMessage());
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

    private static String table(String rows) {
        return "<table style=\"width:100%;border-collapse:collapse;margin:8px 0 12px\">" + rows + "</table>";
    }

    private static String row(String label, String value) {
        return row(label, value, false);
    }

    private static String row(String label, String value, boolean strong) {
        String border = strong ? "border-top:2px solid #1e293b" : "border-top:1px solid #e2e8f0";
        return "<tr><td style=\"padding:6px 0;" + border + "\">" + (strong ? label : esc(label)) + "</td>"
                + "<td style=\"padding:6px 0;text-align:right;white-space:nowrap;" + border + "\">" + value + "</td></tr>";
    }

    private static String money(BigDecimal amount, boolean en) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount == null ? BigDecimal.ZERO : amount) + (en ? " VND" : " ₫");
    }

    private static String time(Instant at) {
        return at == null ? "-" : TIME.format(at);
    }

    private static String code(Booking booking) {
        return "BK-" + String.format("%03d", booking.getId());
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }

    private static String originOf(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            if (uri.getScheme() == null || uri.getHost() == null) return "";
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }
}
