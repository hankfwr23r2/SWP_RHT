package com.example.racehorse_transport.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

// Khung HTML cho thư gửi khách: bảng hai cột, định dạng tiền và giờ. Không chứa nội dung nghiệp vụ.
// Thư điện tử chỉ hiển thị đúng khi style viết thẳng vào thẻ, nên màu nằm ở đây thay vì CSS.
public final class MailHtml {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BUSINESS_ZONE);

    private MailHtml() {
    }

    public static String page(String title, String greeting, String bodyHtml, String link, String linkLabel, String footer) {
        return "<div style=\"font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#1e293b\">"
                + "<h2 style=\"color:#c2410c;margin:0 0 12px\">" + esc(title) + "</h2>"
                + "<p>" + esc(greeting) + "</p>" + bodyHtml
                + "<p><a href=\"" + esc(link) + "\" style=\"display:inline-block;padding:10px 18px;background:#c2410c;color:#fff;"
                + "border-radius:8px;text-decoration:none\">" + esc(linkLabel) + "</a></p>"
                + "<p style=\"color:#64748b;font-size:12px\">" + esc(footer) + "</p></div>";
    }

    public static String paragraph(String text) {
        return "<p>" + esc(text) + "</p>";
    }

    public static String table(String rows) {
        return "<table style=\"width:100%;border-collapse:collapse;margin:8px 0 12px\">" + rows + "</table>";
    }

    // label và value là chữ thuần, được thoát ký tự HTML ở đây
    public static String row(String label, String value, boolean strong) {
        String border = strong ? "border-top:2px solid #1e293b;font-weight:bold" : "border-top:1px solid #e2e8f0";
        return "<tr><td style=\"padding:6px 0;" + border + "\">" + esc(label) + "</td>"
                + "<td style=\"padding:6px 0;text-align:right;white-space:nowrap;" + border + "\">" + esc(value) + "</td></tr>";
    }

    public static String money(BigDecimal amount, boolean english) {
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount == null ? BigDecimal.ZERO : amount) + (english ? " VND" : " ₫");
    }

    public static String time(Instant at) {
        return at == null ? "-" : TIME.format(at);
    }

    public static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
