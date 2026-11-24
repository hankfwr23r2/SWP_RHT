package com.example.racehorse_transport.service;

import com.example.racehorse_transport.dto.operation.CrewTripResponseDto;
import com.example.racehorse_transport.dto.operation.HandoverPaperResponseDto;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

// Biên bản giao nhận và giấy tờ gốc của một lần giao nhận (B2, NGHIEP_VU GĐ6 bước 6–7, GĐ8 bước 4–5). Chủ sở hữu handover_document.
public interface HandoverPaperService {

    // Ảnh hoặc bản quét biên bản đã ký: document HANDOVER_RECORD gắn handover. Nhận và giao ngựa phải có trước khi hoàn tất
    CrewTripResponseDto attachRecord(Long handoverId, MultipartFile file, String username);

    // Giấy gốc (giấy bắt buộc đã duyệt) của các đơn trong lần giao nhận, kèm đã nhận hoặc trả chưa
    List<HandoverPaperResponseDto> papers(Long handoverId, String username);

    // Nhận ngựa từ khách hoặc đại lý: RECEIVED. Giao ngựa: RETURNED
    List<HandoverPaperResponseDto> markPaper(Long handoverId, Long documentId, String username);
}
