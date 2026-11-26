package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.service.LocalDemoService;
import io.swagger.v3.oas.annotations.Hidden;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

// Trang công cụ của chế độ chạy thử, chỉ có khi bật profile local-demo
@RestController
@RequestMapping("/public/demo")
@Profile("local-demo")
@Hidden
@RequiredArgsConstructor
public class LocalDemoController {

    private static final String BASE = "/api/v1/public/demo";
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final LocalDemoService demo;

    @GetMapping(produces = "text/html;charset=UTF-8")
    public String page() {
        return html("Tua giờ chạy thử", """
                <p>Giờ của hệ thống: <b>%s</b></p>
                <form method="post" action="%s/clock/advance"><p>
                <button name="hours" value="1">+1 giờ</button> <button name="hours" value="6">+6 giờ</button>
                <button name="hours" value="24">+1 ngày</button> <button name="hours" value="72">+3 ngày</button>
                </p></form>
                <form method="post" action="%s/clock/reset"><button>Về giờ thật</button></form>
                <p>Tua xong hãy tải lại trang web đang mở. Các việc tự động (đóng nhận chỗ, hết hạn báo giá, nhả chuyến, tự đóng đơn) chạy ngay sau khi tua.</p>
                """.formatted(TIME.format(demo.now()), BASE, BASE));
    }

    @PostMapping("/clock/advance")
    public ResponseEntity<Void> advance(@RequestParam int hours) {
        demo.advance(Duration.ofHours(hours));
        return redirect(BASE);
    }

    @PostMapping("/clock/reset")
    public ResponseEntity<Void> reset() {
        demo.reset();
        return redirect(BASE);
    }

    // Cho giao diện chạy cùng giờ với hệ thống
    @GetMapping(value = "/clock.js", produces = "text/javascript;charset=UTF-8")
    public ResponseEntity<String> clockScript() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("""
                (function (shift) {
                  if (!shift) return;
                  var Real = Date;
                  window.Date = class extends Real {
                    constructor(...args) { if (args.length) super(...args); else super(Real.now() + shift); }
                    static now() { return Real.now() + shift; }
                  };
                })(%d);
                """.formatted(demo.offset().toMillis()));
    }

    @GetMapping("/files/{name}")
    public ResponseEntity<Resource> file(@PathVariable String name) {
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .body(demo.file(name));
    }

    @GetMapping(value = "/vnpay", produces = "text/html;charset=UTF-8")
    public String payPage(@RequestParam Map<String, String> request) {
        StringBuilder fields = new StringBuilder();
        request.forEach((name, value) -> fields.append("<input type=\"hidden\" name=\"%s\" value=\"%s\">"
                .formatted(HtmlUtils.htmlEscape(name), HtmlUtils.htmlEscape(value))));
        BigDecimal amount = new BigDecimal(request.getOrDefault("vnp_Amount", "0")).movePointLeft(2);
        return html("Thanh toán giả lập", """
                <p>Không có tiền thật. Số tiền: <b>%,d đ</b></p><p>%s</p>
                <form method="post" action="%s/vnpay">%s
                <button name="success" value="true">Trả thành công</button>
                <button name="success" value="false">Hủy giao dịch</button></form>
                """.formatted(amount.longValue(), HtmlUtils.htmlEscape(request.getOrDefault("vnp_OrderInfo", "")), BASE, fields));
    }

    @PostMapping("/vnpay")
    public ResponseEntity<Void> pay(@RequestParam Map<String, String> request, @RequestParam boolean success) {
        request.remove("success");
        Map<String, String> result = demo.pay(request, success);
        UriComponentsBuilder target = UriComponentsBuilder.fromPath("/payment/vnpay-return");
        result.keySet().forEach(name -> target.queryParam(name, "{" + name + "}"));
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(target.encode().buildAndExpand(result).toUri()).build();
    }

    private static ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(path)).build();
    }

    private static String html(String title, String body) {
        return """
                <!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>body{font:16px/1.6 sans-serif;max-width:640px;margin:48px auto;padding:0 16px}button{padding:10px 16px;font:inherit;margin:4px 0}</style>
                <h1>%s</h1>%s</html>
                """.formatted(title, title, body);
    }
}
