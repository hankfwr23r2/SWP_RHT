package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.complaint.ComplaintCreateRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintOnBehalfRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintReplyRequestDto;
import com.example.racehorse_transport.dto.complaint.ComplaintResolveRequestDto;
import com.example.racehorse_transport.dto.complaint.DeliveryComplaintResponseDto;

public interface DeliveryComplaintService {
    DeliveryComplaintResponseDto view(Long bookingId, String username);
    DeliveryComplaintResponseDto confirm(Long bookingId, String username);
    DeliveryComplaintResponseDto submit(Long bookingId, ComplaintCreateRequestDto request, String username);
    DeliveryComplaintResponseDto submitOnBehalf(Long bookingId, ComplaintOnBehalfRequestDto request, String username);
    DeliveryComplaintResponseDto reply(Long bookingId, ComplaintReplyRequestDto request, String username);
    DeliveryComplaintResponseDto resolve(Long bookingId, ComplaintResolveRequestDto request, String username);
}
