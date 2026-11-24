package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.health.HorseStayResponseDto;
import com.example.racehorse_transport.dto.health.StablePartnerResponseDto;
import com.example.racehorse_transport.entity.Party;
import com.example.racehorse_transport.entity.TripStop;
import com.example.racehorse_transport.dto.operation.HubStablingRequestDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.Location;
import java.time.Instant;
import java.util.List;

// Thời gian ngựa ở một nơi, không trên xe (B3). Chủ sở hữu horse_stay.
public interface HorseStayService {

    // Giao vào hub xong: mở lưu trú tập kết ở hub, dự kiến tới khi chặng sau bắt đầu
    void openAssembly(HorseLeg alightedLeg, Instant at);

    // Nhận ra khỏi hub hoặc chuồng: đóng lưu trú đang diễn ra của con ngựa. Gửi chuồng sau giao thất bại thì
    // ghi phí lưu chuồng và chăm sóc theo số ngày (người nhận chịu, chờ quản lý duyệt)
    void closeActive(Long bookingHorseId, Instant at, Long actorUserId);

    // Giao thất bại, xe về hub: giữ ngựa tại hub (STABLING) tới hạn STABLING_MAX_DAYS, báo khách hạn và phí mỗi ngày
    void openFailedDeliveryStabling(HorseLeg failedLeg, Location hub, Instant at);

    // Scheduler mỗi sáng: ngựa giữ sau giao thất bại đã quá hạn thì nhắc khách và quản lý, kèm phí tạm tính. Trả số con quá hạn
    int remindOverdueStabling();

    List<HorseStayResponseDto> activeAt(Long locationId);

    // Nghỉ đêm ở trạm (STAY_IN): mở lưu trú OVERNIGHT do đối tác chuồng vận hành, dự kiến tới giờ rời trạm
    void openOvernight(HorseLeg leg, TripStop stop, Party operator, Instant at);

    // Đối tác có vai trò STABLE còn hiệu lực hôm nay (quy tắc #26)
    Party requireStablePartner(Long partyId);

    List<StablePartnerResponseDto> stablePartners();

    // Khách tự chở tới hub (pickup_method SELF): điều phối hẹn giờ, lập lưu trú tập kết dự kiến ở hub đầu tuyến trục, tới giờ xuất phát
    HorseStayResponseDto planSelfDropOff(Long bookingHorseId, Instant plannedStartAt);

    // Con bị loại khỏi đơn: bỏ lịch hẹn nhận ở hub chưa bắt đầu
    void dropAppointments(Long bookingHorseId);

    // Lịch hẹn nhận ngựa ở hub: lưu trú dự kiến chưa bắt đầu
    List<HorseStayResponseDto> appointmentsAt(Long locationId);

    // Người nhận tự tới hub (delivery_method SELF): hẹn giờ tới nhận, ghi vào giờ kết thúc dự kiến của lưu trú tập kết
    HorseStayResponseDto setCollectionTime(Long horseStayId, Instant collectAt);

    // Quá giờ hẹn mà người nhận chưa tới: kết thúc tập kết, mở lưu trú gửi chuồng tại hub, ghi phí gửi chuồng người nhận chịu
    HorseStayResponseDto stableOverdue(Long horseStayId, HubStablingRequestDto request, String username);
}
