package com.example.racehorse_transport.policy;

import com.example.racehorse_transport.dto.regulation.DocumentRequirementResponseDto;
import com.example.racehorse_transport.entity.enums.BookingStatus;
import com.example.racehorse_transport.entity.enums.DocumentStatus;
import com.example.racehorse_transport.entity.enums.DocumentSubmitter;
import static com.example.racehorse_transport.entity.enums.BookingStatus.*;
import com.example.racehorse_transport.exception.BookingRuleException;

public final class DocumentReviewPolicy {
    private DocumentReviewPolicy() {}

    public static boolean requiredForQuoting(DocumentRequirementResponseDto requirement) {
        return Boolean.TRUE.equals(requirement.mandatory()) && requirement.submitterType() == DocumentSubmitter.CUSTOMER;
    }

    public static void requireCustomerUpload(BookingStatus status, DocumentRequirementResponseDto requirement) {
        if (requirement.submitterType() != DocumentSubmitter.CUSTOMER) {
            throw BookingRuleException.conflict("Yêu cầu giấy tờ này không do khách hàng của đơn nộp.");
        }
        if (status == CONFIRMED && requiredForQuoting(requirement)) {
            return;
        }
        BookingReviewPolicy.requireStatus(status, PENDING_INTAKE, UNDER_REVIEW, NEEDS_RESUBMISSION);
    }

    public static void requirePartnerUpload(BookingStatus status, DocumentSubmitter submitter) {
        requireStaffReview(status);
        if (submitter != DocumentSubmitter.COMPANY && submitter != DocumentSubmitter.CONSIGNEE) {
            throw BookingRuleException.conflict("Giấy tờ này chỉ do khách hàng của đơn nộp.");
        }
    }

    public static void requireStaffReview(BookingStatus status) {
        BookingReviewPolicy.requireStatus(status, UNDER_REVIEW, NEEDS_RESUBMISSION, QUOTING, AWAITING_DEPOSIT, CONFIRMED);
    }

    public static void requireUploaded(DocumentStatus status) {
        if (status != DocumentStatus.UPLOADED) {
            throw BookingRuleException.conflict("Giấy tờ đã được kết luận.");
        }
    }

    public static void requireRevocationBeforePickup(BookingStatus status, boolean received) {
        if (received || status == IN_OPERATION || status == DELIVERED || status == COMPLETED) {
            throw BookingRuleException.conflict("Giấy đã theo xe, xử lý qua sự cố giấy tờ.");
        }
        requireStaffReview(status);
    }

    public static void requireApproved(DocumentStatus status) {
        if (status != DocumentStatus.APPROVED) {
            throw BookingRuleException.conflict("Chỉ thu hồi được giấy tờ đã duyệt.");
        }
    }
}
