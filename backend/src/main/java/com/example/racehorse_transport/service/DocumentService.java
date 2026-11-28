package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.enums.DocumentSubject;
import org.springframework.web.multipart.MultipartFile;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng B (task P0.3-B). Gọi: luồng A, B. Chưa có cài đặt thì bên gọi test bằng mock.
public interface DocumentService {

    // Tải tệp lên kho lưu trữ, ghi một dòng document gắn với đúng một chủ sở hữu.
    // ownerSubject phải khớp document_type.owner_type của loại giấy. Trả về id của document.
    Long attach(MultipartFile file, String documentTypeCode, DocumentSubject ownerSubject, Long ownerId, Long uploadedByUserId);
}
