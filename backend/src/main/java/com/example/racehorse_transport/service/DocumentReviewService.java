package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.booking.BookingDocumentResponseDto;
import com.example.racehorse_transport.dto.booking.DocumentUploadRequestDto;
import com.example.racehorse_transport.dto.booking.DocumentApprovalRequestDto;
import com.example.racehorse_transport.dto.booking.PartnerDocumentUploadRequestDto;
import java.util.List;

public interface DocumentReviewService {
    List<BookingDocumentResponseDto> list(Long bookingId);

    com.example.racehorse_transport.dto.booking.BookingDocumentChecklistResponseDto checklist(
            Long bookingId, String customerUsername);
    BookingDocumentResponseDto attach(Long bookingId, String username, DocumentUploadRequestDto request);
    BookingDocumentResponseDto attachForPartner(Long bookingId, String username, PartnerDocumentUploadRequestDto request);
    BookingDocumentResponseDto approve(Long bookingId, Long documentId, String username, DocumentApprovalRequestDto request);
    BookingDocumentResponseDto reject(Long bookingId, Long documentId, String username, String reason);
}
