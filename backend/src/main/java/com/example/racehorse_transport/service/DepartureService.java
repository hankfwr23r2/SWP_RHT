package com.example.racehorse_transport.service;

// Hợp đồng gọi chéo giữa hai luồng (docs/KE_HOACH_TRIEN_KHAI.md mục 3.3).
// Cài đặt: luồng A (task A1). Gọi: luồng A. Chưa có cài đặt thì bên gọi test bằng mock.
public interface DepartureService {

    // Giữ khoang trên chuyến. Khóa dòng departure (SELECT ... FOR UPDATE) rồi mới cộng booked_stalls;
    // vượt capacity_stalls thì ném lỗi nghiệp vụ.
    void reserve(Long departureId, int stalls);

    // Nhả khoang: hủy đơn, từ chối, báo giá hết hạn, loại ngựa, đổi chuyến
    void release(Long departureId, int stalls);
}
