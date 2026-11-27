package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.entity.HorseInsurance;
import com.example.racehorse_transport.entity.enums.HorseInsuranceStatus;
import com.example.racehorse_transport.entity.enums.InsuranceCoverageOption;
import com.example.racehorse_transport.exception.BookingRuleException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class InsuranceReviewPolicy {
    public static final String POLICY_DOCUMENT_CODE = "INSURANCE_POLICY";

    private InsuranceReviewPolicy() {}

    public static void requireStatus(HorseInsurance insurance, HorseInsuranceStatus status) {
        if (insurance.getStatus() != status) {
            throw BookingRuleException.conflict("Thao tác không hợp lệ ở trạng thái hiện tại của bảo hiểm.");
        }
    }

    public static void requirePersonal(HorseInsurance insurance) {
        if (insurance.getCoverageOption() != InsuranceCoverageOption.PERSONAL) {
            throw BookingRuleException.conflict("Chỉ bảo hiểm tự mua được yêu cầu bổ sung hợp đồng.");
        }
    }

    // Chỉ bảo hiểm nhờ mua hộ có báo giá (số tiền, phí, hạn), luôn bằng VND. Bảo hiểm tự có chỉ cần tệp hợp đồng.
    public static void validateEvidence(InsuranceCoverageOption option, String insurerName, BigDecimal coverage,
            BigDecimal premium, Instant expiresAt, Instant now) {
        if (option != InsuranceCoverageOption.ARRANGED) {
            throw BookingRuleException.conflict("Chỉ bảo hiểm nhờ mua hộ mới ghi báo giá.");
        }
        if (insurerName == null || insurerName.isBlank() || insurerName.strip().length() > 200) {
            throw new BookingRuleException("Cần nhập tên hãng bảo hiểm mua hộ.");
        }
        if (!isQuoteCurrent(expiresAt, now)) {
            throw new BookingRuleException("Bảo hiểm mua hộ cần báo giá còn hạn.");
        }
        validateAmount(coverage);
        validateAmount(premium);
    }

    public static void validateForQuoting(HorseInsurance insurance, String horseName, LocalDate pickup,
            LocalDate departure, Instant now) {
        if (insurance == null || insurance.getStatus() != HorseInsuranceStatus.CONFIRMED) {
            throw BookingRuleException.conflict("Ngựa " + horseName + " chưa có bảo hiểm đã xác nhận.");
        }
        if (insurance.getCoverageOption() == InsuranceCoverageOption.PERSONAL
                && !coversTransport(insurance.getValidFrom(), insurance.getValidTo(), pickup, departure)) {
            throw BookingRuleException.conflict("Bảo hiểm của ngựa " + horseName
                    + " phải còn hiệu lực vào ngày đón và ngày xuất phát.");
        }
        if (insurance.getCoverageOption() == InsuranceCoverageOption.ARRANGED
                && !isQuoteCurrent(insurance.getQuoteExpiresAt(), now)) {
            throw BookingRuleException.conflict("Báo giá bảo hiểm của ngựa " + horseName + " đã hết hạn hoặc chưa có hạn.");
        }
    }

    private static boolean isQuoteCurrent(Instant expiresAt, Instant now) {
        return expiresAt != null && expiresAt.isAfter(now);
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(new BigDecimal("10000000000000")) >= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new BookingRuleException("Số tiền bảo hiểm không hợp lệ.");
        }
    }

    public static void validateConfirmation(HorseInsurance insurance, LocalDate pickup, LocalDate departure,
            Instant now, boolean hasPolicyFile) {
        requireStatus(insurance, HorseInsuranceStatus.DRAFT);
        if (insurance.getCoverageOption() == InsuranceCoverageOption.PERSONAL && !hasPolicyFile) {
            throw BookingRuleException.conflict("Chưa có tệp hợp đồng bảo hiểm tự mua.");
        }
        if (insurance.getCoverageOption() == InsuranceCoverageOption.NONE) {
            if (insurance.getWaiverTermsAcceptance() == null) {
                throw BookingRuleException.conflict("Ngựa chưa có cam kết miễn trách.");
            }
            return;
        }
        if (insurance.getCoverageOption() == InsuranceCoverageOption.PERSONAL) {
            validatePeriod(insurance.getValidFrom(), insurance.getValidTo(), pickup, departure);
            return;
        }
        validateEvidence(insurance.getCoverageOption(), insurance.getInsurerName(), insurance.getCoverageAmount(),
                insurance.getPremiumAmount(), insurance.getQuoteExpiresAt(), now);
    }

    public static void validatePeriod(LocalDate start, LocalDate end, LocalDate pickup, LocalDate departure) {
        if (!coversTransport(start, end, pickup, departure)) {
            throw BookingRuleException.conflict("Hợp đồng phải còn hiệu lực vào ngày đón và ngày xuất phát.");
        }
    }

    private static boolean coversTransport(LocalDate start, LocalDate end, LocalDate pickup, LocalDate departure) {
        return start != null && end != null && !start.isAfter(pickup) && !end.isBefore(pickup)
                && (departure == null || (!start.isAfter(departure) && !end.isBefore(departure)));
    }
}
