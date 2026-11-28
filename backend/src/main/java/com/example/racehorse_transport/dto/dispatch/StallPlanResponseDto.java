package com.example.racehorse_transport.dto.dispatch;

import com.example.racehorse_transport.entity.enums.HorseGender;
import java.util.List;

// Sơ đồ khoang của lượt xe (màn 3.9a). editable: lượt xe chưa xuất phát. warnings: ưu tiên không đạt, không chặn
public record StallPlanResponseDto(Long vehicleTripId, Integer capacity, Boolean editable, List<StallHorseDto> horses, List<String> warnings) {

    // locked: con đã lên xe, giữ nguyên vị trí. boardSequence, alightSequence: số thứ tự điểm dừng lên, xuống
    public record StallHorseDto(Long horseLegId, String horseName, String bookingCode, HorseGender sex, Integer seats,
                                Integer boardSequence, Integer alightSequence, String boardStopName, String alightStopName,
                                Integer stallPosition, Boolean locked) {}
}
