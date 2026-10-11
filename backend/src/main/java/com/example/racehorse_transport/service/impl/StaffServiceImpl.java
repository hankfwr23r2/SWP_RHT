package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.Staff;

import com.example.racehorse_transport.repository.StaffRepository;
import com.example.racehorse_transport.service.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StaffServiceImpl implements StaffService {

    private final StaffRepository staffRepository;

    @Autowired
    public StaffServiceImpl(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public List<Staff> findAll() {
        return staffRepository.findAll();
    }

    @Override
    public Optional<Staff> findById(Integer id) {
        return staffRepository.findById(id);
    }

    @Override
    public Staff save(Staff entity) {
        return staffRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        staffRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 1 (Lead): Core (Flow 1)
    // =========================================================================
    // TODO (Dev 1): CRUD Nhân viên.
    // - (Đã có sẵn CRUD cơ bản, bổ sung gán Role, gán bằng lái...)

}
