package com.example.racehorse_transport.dto.operation;

import com.example.racehorse_transport.entity.enums.HandoverDocumentDirection;

// Giấy tờ gốc của các đơn trong lần giao nhận (vd. hộ chiếu ngựa). direction: đã nhận hoặc đã trả trong lần này, NULL = chưa
public record HandoverPaperResponseDto(Long documentId, String documentTypeName, String ownerLabel, String bookingCode,
                                       HandoverDocumentDirection direction) {}
