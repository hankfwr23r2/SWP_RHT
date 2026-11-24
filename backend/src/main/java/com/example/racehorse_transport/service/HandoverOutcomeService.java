package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HorseLeg;
import java.time.Instant;
import java.util.List;

// Tác động của giao nhận lên chặng, lưu trú, đơn và thanh toán (B2). HandoverService gọi khi hoàn tất hoặc không nhận một con.
public interface HandoverOutcomeService {

    // Đánh dấu lần giao nhận DONE; con quét đúng chip lên xe hoặc xuống xe. Nhận từ khách (hoặc từ đại lý với đơn nhập, sau khi
    // kiểm đủ giấy #35) thì lập khoản số dư; bàn giao đại lý thì chặng đại lý liền kề bắt đầu hoặc kết thúc; giao xong con cuối thì đơn DELIVERED (#16)
    void complete(Handover handover, List<HorseLeg> matched, Instant at, Long actorUserId);

    // Không nhận một con lúc nhận: luồng A loại khỏi đơn, nhả chỗ, thay báo giá; ở đây hủy các chặng chưa chạy của con đó
    void refuse(HorseLeg leg, String reason, Long actorUserId);

    // Quá hạn trả số dư: luồng A hủy đơn (từ chối nếu chưa quá hạn hoặc VNPAY còn chờ kết quả), rồi hủy mọi chặng còn lại của các con đã nhận
    void returnUnpaid(List<HorseLeg> boarded, Instant at, Long actorUserId);
}
