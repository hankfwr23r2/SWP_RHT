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

// Mail hóa đơn gửi khách ở các mốc tiền: báo giá, đã nhận cọc / 70% / quyết toán, phát hành bảng quyết toán.
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

    public void quoteIssued(Booking booking, List<QuoteLineDto> lines, BigDecimal total, BigDecimal deposit,
            BigDecimal balance, Instant expiresAt) {
        StringBuilder rows = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (QuoteLineDto line : lines == null ? List.<QuoteLineDto>of() : lines) {
            rows.append(row(line.getLabel(), money(line.getAmount())));
            text.append("- ").append(line.getLabel()).append(": ").append(money(line.getAmount())).append('\n');
        }
        rows.append(row("<b>Tổng cộng</b>", "<b>" + money(total) + "</b>", true));
        text.append("Tổng cộng: ").append(money(total)).append('\n')
                .append("Đặt cọc 30%: ").append(money(deposit)).append(" (hạn ").append(time(expiresAt)).append(")\n")
                .append("Còn lại 70%: ").append(money(balance)).append(" (trả khi kíp xe nhận ngựa xong)\n");
        String body = "<p>Nhà xe đã gửi báo giá cho đơn <b>" + code(booking) + "</b>.</p>"
                + table(rows.toString())
                + "<p>Đặt cọc 30%: <b>" + money(deposit) + "</b>, hạn đặt cọc <b>" + time(expiresAt) + "</b>.<br>"
                + "Còn lại 70%: <b>" + money(balance) + "</b>, thanh toán khi kíp xe nhận ngựa và ký biên bản giao nhận.</p>";
        send(booking, "Báo giá đơn " + code(booking), "Báo giá vận chuyển", body, text.toString());
    }

    public void paymentReceived(Booking booking, String what, BigDecimal amount, String transactionNo, Instant paidAt, String extra) {
        String rows = row("Khoản thanh toán", esc(what)) + row("Số tiền", "<b>" + money(amount) + "</b>")
                + row("Mã giao dịch VNPAY", esc(transactionNo == null ? "-" : transactionNo)) + row("Thời gian", time(paidAt))
                + (extra == null || extra.isBlank() ? "" : row("Ghi chú", esc(extra)));
        String text = what + ": " + money(amount) + "\nMã giao dịch VNPAY: " + (transactionNo == null ? "-" : transactionNo)
                + "\nThời gian: " + time(paidAt) + (extra == null || extra.isBlank() ? "" : "\n" + extra) + "\n";
        send(booking, "Đã nhận " + what + " · đơn " + code(booking), "Biên nhận thanh toán",
                "<p>Nhà xe đã nhận khoản thanh toán của đơn <b>" + code(booking) + "</b>.</p>" + table(rows), text);
    }

    public void settlementIssued(Booking booking, List<ExpenseItemDto> customerItems, BigDecimal total, Instant dueAt) {
        boolean nothingToPay = total == null || total.signum() == 0;
        StringBuilder rows = new StringBuilder();
        StringBuilder text = new StringBuilder();
        for (ExpenseItemDto item : customerItems == null ? List.<ExpenseItemDto>of() : customerItems) {
            String label = item.getDescription() != null ? item.getDescription() : item.getLabel();
            rows.append(row(label, money(item.getAmount())));
            text.append("- ").append(label).append(": ").append(money(item.getAmount())).append('\n');
        }
        String body;
        if (nothingToPay) {
            body = "<p>Chuyến đi của đơn <b>" + code(booking) + "</b> không phát sinh khoản nào phải trả thêm. "
                    + "Mời bạn vào đơn chấm điểm để đóng đơn.</p>";
            text.append("Chuyến đi không phát sinh khoản nào phải trả thêm. Mời bạn vào đơn chấm điểm để đóng đơn.\n");
        } else {
            rows.append(row("<b>Tổng phải trả</b>", "<b>" + money(total) + "</b>", true));
            body = "<p>Bảng quyết toán đơn <b>" + code(booking) + "</b> gồm các khoản phát sinh bạn chịu theo chính sách:</p>"
                    + table(rows.toString())
                    + "<p>Hạn thanh toán: <b>" + time(dueAt) + "</b>. Ảnh chứng từ từng khoản có trong trang đơn.</p>";
            text.append("Tổng phải trả: ").append(money(total)).append("\nHạn thanh toán: ").append(time(dueAt)).append('\n');
        }
        send(booking, "Bảng quyết toán đơn " + code(booking), "Bảng quyết toán", body, text.toString());
    }

    private void send(Booking booking, String subject, String title, String bodyHtml, String bodyText) {
        String to = booking.getCustomerID() != null && booking.getCustomerID().getUser() != null
                ? booking.getCustomerID().getUser().getEmail() : null;
        if (to == null || to.isBlank() || !sender.isConfigured()) return;
        String name = booking.getCustomerID().getFullName() != null ? booking.getCustomerID().getFullName() : "bạn";
        String link = webBase + "/orders/" + code(booking);
        String html = "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1e293b\">"
                + "<h2 style=\"color:#c2410c;margin:0 0 12px\">" + title + "</h2>"
                + "<p>Chào " + esc(name) + ",</p>" + bodyHtml
                + "<p><a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:10px 18px;background:#c2410c;color:#fff;"
                + "border-radius:8px;text-decoration:none\">Xem đơn " + code(booking) + "</a></p>"
                + "<p style=\"color:#64748b;font-size:12px\">Vận chuyển Ngựa · thư tự động, vui lòng không trả lời.</p></div>";
        String text = "Chào " + name + ",\n\n" + title + " · đơn " + code(booking) + "\n" + bodyText + "\nXem đơn: " + link + "\n";
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

    private static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount == null ? BigDecimal.ZERO : amount) + " ₫";
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
