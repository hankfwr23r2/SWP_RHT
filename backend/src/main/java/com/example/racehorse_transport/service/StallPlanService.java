package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.dispatch.StallPlanApplyRequestDto;
import com.example.racehorse_transport.dto.dispatch.StallPlanResponseDto;
import com.example.racehorse_transport.entity.HorseLeg;
import com.example.racehorse_transport.entity.VehicleTrip;
import java.util.List;

// Vị trí khoang trên lượt xe (B1, quy tắc #39, #40). Chủ sở hữu horse_leg.stall_position.
public interface StallPlanService {

    StallPlanResponseDto plan(Long vehicleTripId);

    // Xếp lại tự động cả xe, chỉ trả về, không lưu
    StallPlanResponseDto suggest(Long vehicleTripId);

    // Lưu cả xe khi lượt xe chưa xuất phát; sơ đồ gửi kèm khác dữ liệu thật thì 409
    StallPlanResponseDto apply(Long vehicleTripId, StallPlanApplyRequestDto request, String username);

    // Gọi khi gán ngựa vào xe (đang giữ khóa lượt xe): đặt con mới vào chỗ trống, không đủ thì dồn các con chưa lên xe
    void placeNew(HorseLeg leg);

    // Xếp lại trên xe mới trong giao dịch đã khóa lượt.
    void repack(VehicleTrip trip, List<HorseLeg> onTrip);
}
