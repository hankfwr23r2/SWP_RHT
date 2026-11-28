package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.enums.HandoverType;

// Theo dõi hành trình (docs/DB_SCHEMA_59_BANG.md mục 10.1 và 17), hàm thuần
public final class TrackingPolicy {

    public static final int PHONE_SUFFIX_LENGTH = 4;

    private TrackingPolicy() {}

    // Ngựa đang trong tay ai (docs/DB_SCHEMA_59_BANG.md mục 10.1). Suy ra, không lưu ở DB
    public enum CustodyHolder {
        // Chưa nhận, hoặc đã trả lại người gửi
        SHIPPER,
        COMPANY,
        // Đối tác: trại cách ly, chuồng gửi, đại lý
        PARTY,
        CONSIGNEE
    }

    // Bên nhận của lần giao nhận DONE mới nhất. Chưa có lần nào thì ngựa còn ở người gửi
    public static CustodyHolder holderAfter(HandoverType type, boolean toParty) {
        if (type == null) return CustodyHolder.SHIPPER;
        return switch (type) {
            case PICKUP, HUB_IN, HUB_OUT, SHIFT -> CustodyHolder.COMPANY;
            case STAY_IN, STAY_OUT -> toParty ? CustodyHolder.PARTY : CustodyHolder.COMPANY;
            case DELIVERY -> CustodyHolder.CONSIGNEE;
            // Đơn nhập trả ở cửa khẩu: đại lý nhận lại thay người gửi
            case RETURN -> toParty ? CustodyHolder.PARTY : CustodyHolder.SHIPPER;
        };
    }

    public static boolean isRedundantShift(HandoverType type, Long previousStaffId, Long receivingStaffId) {
        return type == HandoverType.SHIFT && previousStaffId != null && previousStaffId.equals(receivingStaffId);
    }

    // Tra cứu công khai: 4 số cuối điện thoại người gửi, bỏ qua dấu cách, dấu chấm, mã quốc gia
    public static boolean phoneSuffixMatches(String phone, String suffix) {
        if (phone == null || suffix == null || suffix.length() != PHONE_SUFFIX_LENGTH) return false;
        String digits = phone.replaceAll("\\D", "");
        return digits.length() >= PHONE_SUFFIX_LENGTH && digits.endsWith(suffix);
    }
}
