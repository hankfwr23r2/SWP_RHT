package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Booking;
import com.example.racehorse_transport.entity.Horse;
import com.example.racehorse_transport.entity.HorseHealthLog;
import com.example.racehorse_transport.entity.Log;
import com.example.racehorse_transport.entity.Staff;
import com.example.racehorse_transport.repository.BookingRepository;
import com.example.racehorse_transport.repository.HorseHealthLogRepository;
import com.example.racehorse_transport.repository.HorseRepository;
import com.example.racehorse_transport.repository.LogRepository;
import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.HorseHealthLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class HorseHealthLogServiceImpl implements HorseHealthLogService {

    private final HorseHealthLogRepository horseHealthLogRepository;
    private final HorseRepository horseRepository;
    private final BookingRepository bookingRepository;
    private final StaffRepository staffRepository;
    private final LogRepository logRepository;

    @Autowired
    public HorseHealthLogServiceImpl(HorseHealthLogRepository horseHealthLogRepository,
                                     HorseRepository horseRepository,
                                     BookingRepository bookingRepository,
                                     StaffRepository staffRepository,
                                     LogRepository logRepository) {
        this.horseHealthLogRepository = horseHealthLogRepository;
        this.horseRepository = horseRepository;
        this.bookingRepository = bookingRepository;
        this.staffRepository = staffRepository;
        this.logRepository = logRepository;
    }

    @Override
    public List<HorseHealthLog> findAll() {
        return horseHealthLogRepository.findAll();
    }

    @Override
    public Optional<HorseHealthLog> findById(Integer id) {
        return horseHealthLogRepository.findById(id);
    }

    @Override
    public HorseHealthLog save(HorseHealthLog entity) {
        return horseHealthLogRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        horseHealthLogRepository.deleteById(id);
    }

    @Override
    public List<HorseHealthLog> findByHorseId(Integer horseId) {
        if (horseId == null) {
            throw new IllegalArgumentException("horseId không được để trống!");
        }
        return horseHealthLogRepository.findByHorseId(horseId);
    }

    @Override
    public List<HorseHealthLog> findByBookingId(Integer bookingId) {
        if (bookingId == null) {
            throw new IllegalArgumentException("bookingId không được để trống!");
        }
        return horseHealthLogRepository.findByBookingId(bookingId);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 4: Vận hành chuyến đi & Bàn giao (Execution) - Flow 4, 6
    // =========================================================================

    /**
     * API: Escort (Nhân viên áp tải) cập nhật sức khỏe ngựa trên chuyến đi.
     * Người đảm nhận: Dev 4
     */
    @Override
    @Transactional
    public HorseHealthLog updateHorseHealthStatusByEscort(Integer horseId, Integer bookingId, String healthStatus,
                                                           BigDecimal temperature, BigDecimal weight, String symptoms,
                                                           Integer escortStaffId) {
        // 1. Kiểm tra con ngựa này có tồn tại trong hệ thống không
        Horse horse = horseRepository.findById(horseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ngựa có ID: " + horseId));

        // 2. Tìm chuyến đi / booking (nếu có)
        Booking booking = null;
        if (bookingId != null) {
            booking = bookingRepository.findById(bookingId).orElse(null);
        }

        // 3. Tìm nhân viên áp tải (nếu có)
        Staff escort = null;
        if (escortStaffId != null) {
            escort = staffRepository.findById(escortStaffId).orElse(null);
        }

        // 4. Khởi tạo đối tượng HorseHealthLog mới
        HorseHealthLog healthLog = new HorseHealthLog();
        healthLog.setId((int) (horseHealthLogRepository.count() + 1));
        healthLog.setHorseID(horse);
        healthLog.setBookingID(booking);
        healthLog.setReportBy(escort);
        healthLog.setHealthStatus(healthStatus);
        healthLog.setTempature(temperature);
        healthLog.setWeight(weight);
        healthLog.setSymptoms(symptoms);

        // 5. Lưu vào Database
        HorseHealthLog saved = horseHealthLogRepository.save(healthLog);

        // 6. Nếu healthStatus là "Khẩn cấp" -> Bắn notification hoặc lưu Log cảnh báo cho Manager
        if (healthStatus != null && (healthStatus.toLowerCase().contains("khẩn cấp")
                || healthStatus.toLowerCase().contains("emergency")
                || healthStatus.toLowerCase().contains("nguy kịch")
                || healthStatus.toLowerCase().contains("sốt cao"))) {
            Log emergencyLog = new Log();
            emergencyLog.setId((int) (logRepository.count() + 1));
            emergencyLog.setBookingID(booking);
            emergencyLog.setStaffID(escort);
            emergencyLog.setLogType("EMERGENCY_HEALTH_ALERT");
            emergencyLog.setStatus("ALERT");
            emergencyLog.setNote("CẢNH BÁO SỨC KHỎE NGỰA [Mã " + horseId + " - " + horse.getHorseName() + "]: " 
                    + healthStatus
                    + (temperature != null ? " | Thân nhiệt: " + temperature + "°C" : "")
                    + (symptoms != null ? " | Triệu chứng: " + symptoms : ""));
            emergencyLog.setCreatedAt(Instant.now());
            logRepository.save(emergencyLog);
        }

        return saved;
    }

    @Override
    @Transactional
    public HorseHealthLog updateHorseHealthStatusByEscort(Integer horseId, Integer routeId, String healthStatus, String note) {
        return updateHorseHealthStatusByEscort(horseId, null, healthStatus, null, null, note, null);
    }
}