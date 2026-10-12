package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.file.FileUploadResponse;
import com.example.racehorse_transport.response.ApiResponse;
import com.example.racehorse_transport.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final StorageService storageService;

    /**
     * Upload một file lên Cloudinary
     * POST /api/v1/files/upload
     * form-data: file (MultipartFile), folder (String - optional)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "documents") String folder) {

        String url = storageService.uploadFile(file, folder);

        FileUploadResponse response = FileUploadResponse.builder()
                .url(url)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response, "Upload file lên Cloudinary thành công"));
    }

    /**
     * Upload nhiều file cùng lúc lên Cloudinary (hữu ích cho nhiều trang hộ chiếu, biên bản, hóa đơn)
     * POST /api/v1/files/upload-multiple
     * form-data: files (List<MultipartFile>), folder (String - optional)
     */
    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", required = false, defaultValue = "documents") String folder) {

        List<FileUploadResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = storageService.uploadFile(file, folder);
                responses.add(FileUploadResponse.builder()
                        .url(url)
                        .originalFilename(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .size(file.getSize())
                        .build());
            }
        }

        return ResponseEntity.ok(ApiResponse.success(responses, "Upload danh sách file thành công"));
    }
}
