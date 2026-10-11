package com.example.racehorse_transport.service;

import com.example.racehorse_transport.entity.BookingAssignment;

import java.util.List;
import java.util.Optional;

public interface BookingAssignmentService {
    List<BookingAssignment> findAll();
    Optional<BookingAssignment> findById(Integer id);
    BookingAssignment save(BookingAssignment entity);
    void deleteById(Integer id);
}
