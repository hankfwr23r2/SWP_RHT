package com.example.racehorse_transport.repository;

import com.example.racehorse_transport.entity.TripDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripDocumentRepository extends JpaRepository<TripDocument, Integer> {
    @Query("SELECT td FROM TripDocument td WHERE td.bookingID.id = :bookingId")
    List<TripDocument> findByBookingId(@Param("bookingId") Integer bookingId);
}
