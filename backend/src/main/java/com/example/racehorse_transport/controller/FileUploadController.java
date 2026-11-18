package com.example.racehorse_transport.controller;

import com.example.racehorse_transport.dto.file.FileUploadResponseDto;
import com.example.racehorse_transport.dto.common.ApiResponseDto;
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
     * Tải một tệp lên hệ thống lưu trữ.
     * POST /api/v1/files/upload
     * form-data: file (MultipartFile), folder (String - optional)
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto<FileUploadResponseDto>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "documents") String folder) {

        String url = storageService.uploadFile(file, folder);

        FileUploadResponseDto response = FileUploadResponseDto.builder()
                .url(url)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();

        return ResponseEntity.ok(ApiResponseDto.success(response, "Tải tệp lên thành công"));
    }

    /**
     * Tải nhiều tệp cùng lúc lên hệ thống lưu trữ.
     * POST /api/v1/files/upload-multiple
     * form-data: files (List<MultipartFile>), folder (String - optional)
     */
    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseDto<List<FileUploadResponseDto>>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "folder", required = false, defaultValue = "documents") String folder) {

        List<FileUploadResponseDto> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = storageService.uploadFile(file, folder);
                responses.add(FileUploadResponseDto.builder()
                        .url(url)
                        .originalFilename(file.getOriginalFilename())
                        .contentType(file.getContentType())
                        .size(file.getSize())
                        .build());
            }
        }

        return ResponseEntity.ok(ApiResponseDto.success(responses, "Upload danh sách file thành công"));
    }
}
