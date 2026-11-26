package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.dto.booking.InsuranceDeclarationRequestDto;
import com.example.racehorse_transport.exception.BookingRuleException;

public final class InsuranceDeclarationPolicy {

    private InsuranceDeclarationPolicy() {}

    public static void validate(InsuranceDeclarationRequestDto request) {
        switch (request.coverageOption()) {
            case PERSONAL -> {
                if (request.policyNumber() == null || request.policyNumber().isBlank()
                        || request.policyNumber().trim().length() > 50
                        || request.validFrom() == null || request.validTo() == null
                        || request.validTo().isBefore(request.validFrom())) {
                    throw new BookingRuleException("Bảo hiểm tự mua cần số hợp đồng và thời hạn hợp lệ.");
                }
            }
            case NONE -> {
                if (!Boolean.TRUE.equals(request.acceptedWaiver())) {
                    throw new BookingRuleException("Cần đồng ý miễn trách cho từng con ngựa không mua bảo hiểm.");
                }
            }
            case ARRANGED -> {
                // Chỉ ghi nhận lựa chọn. Chuyên viên bổ sung thông tin bảo hiểm sau.
            }
        }
    }
}
