package com.example.racehorse_transport.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    /**
     * Tải một tệp lên hệ thống lưu trữ.
     * @param file File từ client gửi lên (ảnh, PDF...)
     * @param folder Thư mục phân loại (ví dụ: "horses", "clearance", "settlement")
     * @return Đường dẫn công khai của tệp đã tải lên.
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * Xóa một file khỏi hệ thống lưu trữ theo publicId hoặc url
     * @param publicId Hoặc URL của file
     */
    void deleteFile(String publicId);
}
