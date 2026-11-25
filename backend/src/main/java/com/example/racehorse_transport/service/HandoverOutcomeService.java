package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.enums.HandoverType;
import com.example.racehorse_transport.dto.operation.HandoverRefusalRequestDto;
import org.springframework.web.multipart.MultipartFile;
import java.time.Instant;
import java.util.List;

// Tác động của giao nhận lên chặng, lưu trú, đơn và thanh toán (B2). HandoverService gọi khi hoàn tất hoặc không nhận một con.
public interface HandoverOutcomeService {

    void prepareUnpaidHubReturn(com.example.racehorse_transport.entity.HorseStay stay, Instant at);

    void lockBookingsForCompletion(HandoverType type, List<HorseLeg> matched);

    // Đánh dấu lần giao nhận DONE; con quét đúng chip lên xe hoặc xuống xe. Nhận từ khách (hoặc từ đại lý với đơn nhập, sau khi
    // kiểm đủ giấy #35) thì lập khoản số dư; bàn giao đại lý thì chặng đại lý liền kề bắt đầu hoặc kết thúc; giao xong con cuối thì đơn DELIVERED (#16)
    void complete(Handover handover, List<HorseLeg> matched, Instant at, Long actorUserId);

    void requireSelfDeliveryBeforeCheckIn(Long horseId);

    boolean canBoardAtHub(Long bookingId);

    // Ghi đón hụt và gỡ kế hoạch của con chưa nhận. Quyết định sau đó mới có thể loại con hoặc nhả chỗ trục.
    void refuse(Long bookingHorseId, String reason, Instant at, Long actorUserId);

    void refuseAtStop(Long bookingHorseId, HandoverRefusalRequestDto request, MultipartFile photo,
                      Instant arrivedAt, Instant at, Long actorUserId);

    // Quá hạn trả số dư: luồng A hủy đơn (từ chối nếu chưa quá hạn hoặc VNPAY còn chờ kết quả), rồi hủy mọi chặng còn lại của các con đã nhận
    boolean canReturnUnpaid(List<HorseLeg> boarded);

    void returnUnpaid(List<HorseLeg> boarded, Instant at, Long actorUserId);

    // Nhận hoặc trả ngựa tại hub với khách tự chở (handover gắn horse_stay, đã DONE): nhận thì lập khoản số dư,
    // trả thì con cuối của đơn giao xong làm đơn DELIVERED (#16); cả hai báo khách
    void atHub(Handover handover, HorseLeg leg, Long actorUserId);
}
