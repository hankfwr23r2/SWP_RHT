package com.example.racehorse_transport.service.impl;

import com.example.racehorse_transport.entity.BookingAssignment;

import com.example.racehorse_transport.repository.BookingAssignmentRepository;
import com.example.racehorse_transport.service.BookingAssignmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookingAssignmentServiceImpl implements BookingAssignmentService {

    private final BookingAssignmentRepository bookingAssignmentRepository;

    @Autowired
    public BookingAssignmentServiceImpl(BookingAssignmentRepository bookingAssignmentRepository) {
        this.bookingAssignmentRepository = bookingAssignmentRepository;
    }

    @Override
    public List<BookingAssignment> findAll() {
        return bookingAssignmentRepository.findAll();
    }

    @Override
    public Optional<BookingAssignment> findById(Integer id) {
        return bookingAssignmentRepository.findById(id);
    }

    @Override
    public BookingAssignment save(BookingAssignment entity) {
        return bookingAssignmentRepository.save(entity);
    }

    @Override
    public void deleteById(Integer id) {
        bookingAssignmentRepository.deleteById(id);
    }

    // =========================================================================
    // PHẦN VIỆC CỦA DEV 3: Điều phối (Flow 3)
    // =========================================================================
    // TODO (Dev 3): Gán tài xế, gán Escort cho chuyến đi.
    // - Viết hàm assignStaffToRoute(Integer routeId, Integer staffId, String role)

}
