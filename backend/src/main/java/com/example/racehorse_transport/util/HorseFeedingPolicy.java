package com.example.racehorse_transport.util;

import com.example.racehorse_transport.dto.booking.HorseFeedingDto;

// Chuẩn hóa thông tin cho ăn khách khai: bỏ khoảng trắng thừa, giới hạn độ dài, không khai gì thì không lưu.
public final class HorseFeedingPolicy {

    public static final int MAX_USUAL_TIMES_LENGTH = 100;
    public static final int MAX_RATION_LENGTH = 500;

    private HorseFeedingPolicy() { }

    public static HorseFeedingDto normalize(HorseFeedingDto feeding) {
        if (feeding == null) return null;
        String usualTimes = clean(feeding.getUsualTimes());
        String ration = clean(feeding.getRation());
        if (usualTimes != null && usualTimes.length() > MAX_USUAL_TIMES_LENGTH) {
            throw new IllegalArgumentException("Giờ ăn quen của ngựa không được dài quá " + MAX_USUAL_TIMES_LENGTH + " ký tự.");
        }
        if (ration != null && ration.length() > MAX_RATION_LENGTH) {
            throw new IllegalArgumentException("Khẩu phần và ghi chú cho ăn không được dài quá " + MAX_RATION_LENGTH + " ký tự.");
        }
        boolean ownFeed = Boolean.TRUE.equals(feeding.getOwnFeed());
        if (usualTimes == null && ration == null && !ownFeed) return null;
        return HorseFeedingDto.builder().usualTimes(usualTimes).ration(ration).ownFeed(ownFeed).build();
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
