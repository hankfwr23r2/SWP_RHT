package com.example.racehorse_transport.service;

// Đọc tham số kỹ thuật trong bảng system_config. Chủ sở hữu bảng: luồng A (task A2 thêm phần quản trị).
public interface SystemConfigService {

    // Tham số kiểu số nguyên. Thiếu khóa hoặc giá trị không phải số thì ném lỗi cấu hình.
    int getInt(String key);
}
