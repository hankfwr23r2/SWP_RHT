package com.example.racehorse_transport.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.racehorse_transport.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
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
    public void deleteFile(String fileReference) {
        if (fileReference == null || fileReference.isBlank()) return;
        try {
            String publicId = fileReference;
            String resourceType = "image";
            if (fileReference.startsWith("http://") || fileReference.startsWith("https://")) {
                String path = URI.create(fileReference).getPath();
                String[] uploadPath = path.split("/upload/", 2);
                if (uploadPath.length != 2) {
                    throw new IllegalArgumentException("URL Cloudinary không hợp lệ.");
                }

                String[] resourcePath = uploadPath[0].split("/");
                String candidateType = resourcePath[resourcePath.length - 1];
                if (candidateType.equals("image") || candidateType.equals("raw") || candidateType.equals("video")) {
                    resourceType = candidateType;
                }

                String assetPath = uploadPath[1];
                if (assetPath.matches("^v\\d+/.*")) {
                    assetPath = assetPath.replaceFirst("^v\\d+/", "");
                }
                int extension = assetPath.lastIndexOf('.');
                publicId = extension > assetPath.lastIndexOf('/') ? assetPath.substring(0, extension) : assetPath;
            }

            cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", resourceType));
            log.info("Deleted file from Cloudinary: publicId={}", publicId);
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary: {}", e.getMessage(), e);
            throw new IllegalStateException("Không thể xóa tệp hồ sơ ngựa khỏi bộ lưu trữ.", e);
        }
    }
}
