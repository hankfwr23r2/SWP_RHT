package com.example.racehorse_transport.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "VEHICLE")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "VehicleID", nullable = false)
    private Integer id;

    @Size(max = 100)
    @Column(name = "vehicle_type", length = 100)
    private String vehicleType;

    @Size(max = 50)
    @Column(name = "license_plate", length = 50)
    private String licensePlate;

    @Size(max = 50)
    @Column(name = "status", length = 50)
    private String status;

    // =========================================================================
    // HELPER / DERIVED BUSINESS PROPERTIES (Không lưu DB, đảm bảo nghiệp vụ & UI)
    // =========================================================================

    /**
     * Suy ra mã xe định danh theo chuẩn hệ thống: VH-001, VH-002,...
     */
    public String getVehicleCode() {
        return "VH-" + String.format("%03d", id != null ? id : 0);
    }

    public void setVehicleCode(String vehicleCode) {
        // No-op: Mã xe được chuẩn hóa suy từ ID để đồng bộ frontend
    }

    /**
     * Suy ra sức chứa (số ngăn chở ngựa) tự động từ vehicle_type:
     * - Nếu chuỗi chứa số (2, 4, 6, 9) -> lấy số đó (ví dụ "Xe 2 ngăn", "4_stalls" -> 2, 4)
     * - Nếu là light -> 2, medium -> 4, heavy -> 9
     * - Mặc định: 2 ngăn
     */
    public Integer getCapacity() {
        if (vehicleType == null || vehicleType.isBlank()) {
            return 2;
        }
        String cleanDigits = vehicleType.replaceAll("\\D+", "");
        if (!cleanDigits.isEmpty()) {
            try {
                int cap = Integer.parseInt(cleanDigits);
                if (cap > 0 && cap <= 20) {
                    return cap;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        String lower = vehicleType.toLowerCase();
        if (lower.contains("heavy")) return 9;
        if (lower.contains("medium")) return 4;
        if (lower.contains("light") || lower.contains("van")) return 2;
        return 2;
    }

    public void setCapacity(Integer capacity) {
        // No-op: Tương thích với các mapper hoặc test builder
    }

    /**
     * Suy ra tên hiển thị của xe cho giao diện điều phối & quản lý
     */
    public String getVehicleName() {
        return "Xe chuyên dụng " + getCapacity() + " ngăn (" + (licensePlate != null ? licensePlate : "Chưa có biển") + ")";
    }

    public void setVehicleName(String vehicleName) {
        // No-op
    }

    /**
     * Toàn bộ hệ thống quản lý tập trung duy nhất tại Trụ sở chính — Việt Nam (Đã bỏ depot)
     */
    public String getHomeDepot() {
        return "Trụ sở chính — Việt Nam";
    }

    public void setHomeDepot(String homeDepot) {
        // No-op
    }

    public String getCurrentLocation() {
        return "Trụ sở chính — Việt Nam";
    }

    public void setCurrentLocation(String currentLocation) {
        // No-op
    }
}