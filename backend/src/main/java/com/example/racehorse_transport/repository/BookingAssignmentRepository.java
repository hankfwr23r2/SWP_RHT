package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.BookingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingAssignmentRepository extends JpaRepository<BookingAssignment, Integer> {
    List<BookingAssignment> findByBookingID_Id(Integer bookingId);
    List<BookingAssignment> findByStaffID_Id(Integer staffId);
    void deleteByBookingID_Id(Integer bookingId);
}
