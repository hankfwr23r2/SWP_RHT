package com.example.racehorse_transport.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.racehorse_transport.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageServiceImpl implements StorageService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File tải lên không được để trống!");
        }

        try {
            // Đặt tên file ngẫu nhiên để tránh trùng lặp
            String originalFilename = file.getOriginalFilename();
            String cleanName = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(0, originalFilename.lastIndexOf("."))
                    : "file";
            cleanName = cleanName.replaceAll("[^a-zA-Z0-9_-]", "_");
            String publicId = (folder != null && !folder.isBlank() ? folder + "/" : "") + cleanName + "_" + UUID.randomUUID().toString().substring(0, 8);

            // Upload lên Cloudinary với resource_type: auto (hỗ trợ cả ảnh PNG/JPG và tài liệu PDF)
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "public_id", publicId,
                    "resource_type", "auto",
                    "overwrite", true
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("Uploaded file to Cloudinary successfully: url={}", secureUrl);
            return secureUrl;
        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Lỗi trong quá trình upload file lên Cloudinary: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Deleted file from Cloudinary: publicId={}", publicId);
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary: {}", e.getMessage(), e);
        }
    }
}
