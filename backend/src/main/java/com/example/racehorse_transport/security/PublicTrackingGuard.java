package com.example.racehorse_transport.security;

import com.example.racehorse_transport.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Chặn dò 4 số cuối điện thoại khi tra cứu đơn công khai: một đơn nhập sai quá MAX_FAILURES lần
// trong WINDOW thì khóa tra cứu đơn đó tới hết WINDOW (kể cả khi lần sau nhập đúng).
// Đếm theo đơn có thật chứ không theo IP: mã đơn ngẫu nhiên nên không dò được mã, chỉ còn dò số điện thoại
// của một mã đã biết; IP thì đổi được và sau proxy Vercel/Render không đáng tin.
// Lưu trong bộ nhớ: khởi động lại thì đếm lại từ đầu, chạy nhiều instance thì mỗi instance đếm riêng.
@Component
@RequiredArgsConstructor
public class PublicTrackingGuard {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<Integer, Failures> failures = new ConcurrentHashMap<>();

    private record Failures(Instant firstAt, int count) {
    }

    public void checkAllowed(Integer bookingId, String code) {
        Failures current = failures.get(bookingId);
        if (current == null) return;
        if (expired(current)) {
            failures.remove(bookingId, current);
            return;
        }
        if (current.count() >= MAX_FAILURES) {
            throw new TooManyAttemptsException("Đơn " + code + " đã bị nhập sai số điện thoại quá nhiều lần. Vui lòng thử lại sau "
                    + WINDOW.toMinutes() + " phút.");
        }
    }

    public void recordFailure(Integer bookingId) {
        failures.compute(bookingId, (id, current) -> current == null || expired(current)
                ? new Failures(clock.instant(), 1)
                : new Failures(current.firstAt(), current.count() + 1));
    }

    public void recordSuccess(Integer bookingId) {
        failures.remove(bookingId);
    }

    private boolean expired(Failures entry) {
        return !clock.instant().isBefore(entry.firstAt().plus(WINDOW));
    }
}
