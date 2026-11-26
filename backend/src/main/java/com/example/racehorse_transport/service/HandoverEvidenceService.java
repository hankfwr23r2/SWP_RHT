package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.DeliveryRecipientRequestDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperRequestDto;
import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Document;
import com.example.racehorse_transport.entity.Handover;
import com.example.racehorse_transport.entity.HandoverHorse;
import java.util.List;
import java.util.Set;
import com.example.racehorse_transport.entity.HorseLeg;
import org.springframework.web.multipart.MultipartFile;

public interface HandoverEvidenceService {
    java.util.Set<Long> photographedLegs(Long handoverId);

    void attachPhoto(HandoverHorse horse, MultipartFile photo, Long actor);
    void attachRecord(Long handoverId, MultipartFile record, Long actor);
    List<Document> originals(
            Long handoverId, Set<Long> bookingIds, boolean receiving);
    void recordHubPapers(Handover handover, Long bookingId, boolean receiving,
                         List<HandoverPaperRequestDto> selections);
    void requirePapers(Long handoverId, Set<Long> bookingIds, boolean receiving);
    void returnOriginals(Handover handover, Set<Long> bookingIds);
    String requireRecipient(Booking booking,
                            DeliveryRecipientRequestDto recipient);
    boolean hasRecord(Long handoverId);
    void requireHorseEvidence(HandoverHorse horse);
}
